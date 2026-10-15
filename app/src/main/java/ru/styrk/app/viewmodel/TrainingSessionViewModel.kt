package ru.styrk.app.viewmodel

import ru.styrk.app.data.model.PairedExercises

import androidx.lifecycle.ViewModel
// Неделимая запись в базу: тренировка вместе с упражнениями
// и подходами. Приходит из room-ktx — того же набора, что уже даёт
// нам suspend-запросы к базе.
import androidx.room.withTransaction
import ru.styrk.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.styrk.app.data.local.database.WorkoutDatabase
import ru.styrk.app.data.local.entity.*
import org.json.JSONArray
import org.json.JSONObject
import ru.styrk.app.notifications.ReminderScheduler
import ru.styrk.app.data.backup.BackupManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import ru.styrk.app.ui.common.ExercisePhoto

/** Сколько держится полоска с отменой. Четыре секунды — успеть прочитать
 *  и нажать, но не настолько долго, чтобы она мешала. */
private const val UNDO_MILLIS = 4000L

class TrainingSessionViewModel(private val database: WorkoutDatabase) : ViewModel() {

    private val _state            = MutableStateFlow(CurrentWorkoutState())
    private val _completedWorkouts= MutableStateFlow<List<CompletedWorkout>>(emptyList())
    private val _exerciseHistory  = MutableStateFlow<Map<String, ExerciseHistory>>(emptyMap())
    private val _plannedWorkouts  = MutableStateFlow<List<PlannedWorkout>>(emptyList())
    private val _customExercises  = MutableStateFlow<List<CustomExercise>>(emptyList())
    private val _myWorkouts       = MutableStateFlow<List<MyWorkout>>(emptyList())
    private val _lastCompletedWorkoutId = MutableStateFlow<Long?>(null)

    // ТАЙМЕР ОТДЫХА: момент последней записи подхода (epoch millis),
    // null = таймер не идёт. Хранится ТОЛЬКО в памяти (не в БД, схему
    // не трогаем) — после перезапуска приложения таймер запустится
    // заново при следующем подходе, это осознанный компромисс.
    private val _restTimerStart = MutableStateFlow<Long?>(null)

    // ЖУРНАЛ ТЕЛА (этап «тело»): записи веса и замеров, отсортированы по
    // дате ПО ВОЗРАСТАНИЮ (старые первые — так удобнее строить графики)
    private val _bodyMetrics = MutableStateFlow<List<BodyMetricEntity>>(emptyList())
    private val _userProfile = MutableStateFlow<UserProfileEntity?>(null)

    val state: StateFlow<CurrentWorkoutState>     = _state
    val completedWorkouts = _completedWorkouts.asStateFlow()
    val exerciseHistory   = _exerciseHistory.asStateFlow()
    val plannedWorkouts   = _plannedWorkouts.asStateFlow()
    val customExercises   = _customExercises.asStateFlow()
    val myWorkouts        = _myWorkouts.asStateFlow()
    val restTimerStart = _restTimerStart.asStateFlow()

    // Сигнал MainScreen'у переключиться на вкладку тренировки (счётчик-триггер)
    private val _navigateToTraining = MutableStateFlow(0)
    val navigateToTraining = _navigateToTraining.asStateFlow()

    /**
     * Счётчик сохранённых тренировок.
     *
     * Нужен, чтобы отправить свежую тренировку на сервер сразу, а не при
     * следующем запуске приложения. Сам обмен отсюда не запускается:
     * для него нужен контекст приложения, а тащить его в ViewModel ради
     * одного вызова — плохой обмен. MainScreen следит за этим числом
     * и делает обмен у себя.
     */
    private val _workoutSaved = MutableStateFlow(0)
    val workoutSaved = _workoutSaved.asStateFlow()
    val bodyMetrics = _bodyMetrics.asStateFlow()
    val userProfile = _userProfile.asStateFlow()

    // ВАЖНЫЙ ИНВАРИАНТ: _completedWorkouts ВСЕГДА отсортирован по dateTime
    // по убыванию — [0] самая свежая тренировка, последняя — самая старая.
    // Это гарантируется этой функцией — используйте её при любом изменении
    // списка вместо прямого присваивания _completedWorkouts.value.
    private fun setCompletedWorkouts(list: List<CompletedWorkout>) {
        _completedWorkouts.value = list.sortedByDescending { it.dateTime }
    }

    // ==================== ЦЕНТРАЛЬНОЕ ИЗМЕНЕНИЕ СОСТОЯНИЯ ====================

    private fun setState(newState: CurrentWorkoutState) {
        _state.value = newState
        persistInProgressWorkout(newState)
    }

    private fun persistInProgressWorkout(s: CurrentWorkoutState) {
        viewModelScope.launch {
            if (!s.trainingStarted || s.currentScreen == "start" || s.currentScreen == "summary") {
                database.inProgressWorkoutDao().clear()
                return@launch
            }
            database.inProgressWorkoutDao().save(
                InProgressWorkoutEntity(
                    currentScreen = s.currentScreen,
                    athleteWeight = s.athleteWeight,
                    muscleGroup = s.selectedMuscleGroup?.name,
                    workoutStartTime = s.workoutStartTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    exercisesJson = exercisesToJson(s.selectedExercises),
                    manualDurationMinutes = s.manualDurationMinutes
                )
            )
        }
    }

    fun loadInProgressWorkout() {
        viewModelScope.launch {
            val saved = database.inProgressWorkoutDao().get() ?: return@launch
            val group = saved.muscleGroup?.let { g -> MuscleGroup.entries.find { it.name == g } }
            _state.value = CurrentWorkoutState(
                workoutStartTime = Instant.ofEpochMilli(saved.workoutStartTime).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                athleteWeight = saved.athleteWeight,
                selectedMuscleGroup = group,
                selectedExercises = jsonToExercises(saved.exercisesJson),
                trainingStarted = true,
                currentScreen = saved.currentScreen,
                manualDurationMinutes = saved.manualDurationMinutes
            )
        }
    }

    private fun exercisesToJson(exercises: List<WorkoutExercise>): String {
        val arr = JSONArray()
        exercises.forEach { ex ->
            val obj = JSONObject()
            obj.put("name", ex.name)
            obj.put("muscleGroup", ex.muscleGroup?.name ?: JSONObject.NULL)
            obj.put("measureType", ex.measureType.name)
            val setsArr = JSONArray()
            ex.sets.forEach { s ->
                val sObj = JSONObject()
                sObj.put("weight", s.weight.toDouble())
                sObj.put("reps", s.reps)
                s.seconds?.let { sObj.put("seconds", it) }
                s.distanceKm?.let { sObj.put("distanceKm", it.toDouble()) }
                s.load?.let { sObj.put("load", it.toDouble()) }
                s.speed?.let { sObj.put("speed", it.toDouble()) }
                setsArr.put(sObj)
            }
            obj.put("sets", setsArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToExercises(json: String): List<WorkoutExercise> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<WorkoutExercise>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name")
                val groupStr = if (obj.isNull("muscleGroup")) null else obj.getString("muscleGroup")
                val group = groupStr?.let { s -> MuscleGroup.entries.find { it.name == s } }
                val measureType = obj.optString("measureType", "")
                    .let { s -> MeasureType.entries.find { it.name == s } } ?: MeasureType.WEIGHT_REPS
                val setsArr = obj.getJSONArray("sets")
                val sets = mutableListOf<WorkoutSet>()
                for (j in 0 until setsArr.length()) {
                    val sObj = setsArr.getJSONObject(j)
                    sets.add(WorkoutSet(
                        weight = sObj.getDouble("weight").toFloat(),
                        reps = sObj.getInt("reps"),
                        seconds = if (sObj.has("seconds")) sObj.getInt("seconds") else null,
                        distanceKm = if (sObj.has("distanceKm")) sObj.getDouble("distanceKm").toFloat() else null,
                        load = if (sObj.has("load")) sObj.getDouble("load").toFloat() else null,
                        speed = if (sObj.has("speed")) sObj.getDouble("speed").toFloat() else null
                    ))
                }
                // id НЕ сохраняем/восстанавливаем — при восстановлении
                // каждый WorkoutExercise получает свежий UUID автоматически,
                // это нормально: id важен только для UI-ключей в рамках
                // текущей композиции, не для истории
                list.add(WorkoutExercise(name = name, muscleGroup = group, sets = sets, measureType = measureType))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ==================== WORKOUT ACTIONS ====================

    fun selectMuscleGroup(group: MuscleGroup) { setState(_state.value.copy(selectedMuscleGroup = group)) }

    // ИСПРАВЛЕНО: разрешено добавлять одно и то же упражнение (по имени)
    // несколько раз за тренировку — например жим лёжа → другое упражнение →
    // снова жим лёжа. Крашей не будет, т.к. у каждого экземпляра свой
    // уникальный id (см. WorkoutExercise), используемый как ключ в списке.
    // ═══ Последнее добавленное упражнение (п4, v1.8.5) ═══
    //
    // Экран тренировки подводит новую карточку к середине. Раньше он
    // пытался сам заметить прибавление в списке, но это не работало:
    // упражнение выбирается на ДРУГОМ экране, и к моменту возврата
    // экран тренировки создаётся заново, забыв, что было до перехода.
    // Поэтому «кто именно добавлен» помнит ViewModel — она живёт всё
    // время тренировки.
    private val _lastAddedExerciseId = MutableStateFlow<String?>(null)
    val lastAddedExerciseId: StateFlow<String?> = _lastAddedExerciseId.asStateFlow()

    /** Экран прокрутился к карточке — отметку можно снять. */
    fun consumeLastAdded() { _lastAddedExerciseId.value = null }

    fun addExercise(name: String, measureTypeOverride: MeasureType? = null) {
        val group = _state.value.selectedMuscleGroup ?: return
        val exercise = WorkoutExercise(
            name = name,
            muscleGroup = group,
            // override нужен для только что созданного своего упражнения:
            // его тип может ещё не успеть загрузиться из БД в customExercises
            measureType = measureTypeOverride ?: resolveMeasureType(name, group)
        )
        setState(_state.value.copy(selectedExercises = _state.value.selectedExercises + exercise))
        _lastAddedExerciseId.value = exercise.id
    }

    /**
     * Как считается упражнение с таким именем: ищем в библиотеке, потом в
     * своих упражнениях. Не нашли — классика WEIGHT_REPS.
     */
    private fun resolveMeasureType(name: String, group: MuscleGroup?): MeasureType {
        ExerciseLibrary.exercises.find {
            it.name.equals(name, ignoreCase = true) && (group == null || group.sameAs(it.muscleGroup))
        }?.let { return it.measureType }
        _customExercises.value.find {
            it.name.equals(name, ignoreCase = true) && (group == null || group.sameAs(it.muscleGroup))
        }?.let { return it.measureType }
        /* Не нашли в своём блоке — ищем по всему справочнику.

           Понадобилось, когда поиск стал показывать упражнения из чужих
           блоков: планку можно добавить к прессу, но лежит она в «Улице
           и доме». Без этого шага она попадала бы сюда как «вес ×
           повторы», и человека спросили бы, сколько килограммов он
           простоял.

           Порядок важен: свой блок спрашиваем первым. Одноимённые
           упражнения в разных блоках существуют («Подтягивания» есть
           и в спине, и в улице), и там, где человек стоит, ответ
           правильнее. */
        ExerciseLibrary.exercises.find { it.name.equals(name, ignoreCase = true) }
            ?.let { return it.measureType }
        _customExercises.value.find { it.name.equals(name, ignoreCase = true) }
            ?.let { return it.measureType }
        return MeasureType.WEIGHT_REPS
    }

    /**
     * Поменять местами два упражнения (п5, v1.8.5).
     *
     * Вызывается во время перетаскивания карточки: как только её середина
     * заходит на соседа, они меняются местами. Экран при этом не «прыгает» —
     * карточка остаётся под пальцем.
     *
     * Ограничение «только упражнения без подходов и только внутри своей
     * группы» проверяется на экране, здесь просто перестановка.
     */
    fun swapExercises(idA: String, idB: String) {
        if (idA == idB) return
        val list = _state.value.selectedExercises
        val a = list.indexOfFirst { it.id == idA }
        val b = list.indexOfFirst { it.id == idB }
        if (a < 0 || b < 0) return
        val result = list.toMutableList()
        result[a] = list[b]
        result[b] = list[a]
        setState(_state.value.copy(selectedExercises = result))
    }

    // Убрать КОНКРЕТНЫЙ экземпляр упражнения (по id, не по имени — иначе
    // при дублях удалились бы сразу все одноимённые карточки)
    fun removeExercise(exerciseId: String) {
        setState(_state.value.copy(
            selectedExercises = _state.value.selectedExercises.filterNot { it.id == exerciseId }
        ))
    }

    fun removeMuscleGroup(group: MuscleGroup) {
        setState(_state.value.copy(
            selectedExercises = _state.value.selectedExercises.filterNot { it.muscleGroup == group }
        ))
    }

    fun updateAthleteWeight(w: Float) { setState(_state.value.copy(athleteWeight = w)) }
    /**
     * Старт тренировки по кнопке «НАЧАТЬ ТРЕНИРОВКУ».
     *
     * п7 (v1.8.1): здесь ЗАНОВО ставится время начала. Раньше оно
     * фиксировалось при создании состояния (запуск приложения или сброс),
     * поэтому брошенные попытки («дошёл до выбора упражнений и вышел»)
     * оставляли старое время и длительность тренировки была завышена.
     */
    fun startTraining() {
        setState(
            _state.value.copy(
                trainingStarted = true,
                currentScreen = "weight",
                workoutStartTime = LocalDateTime.now()
            )
        )
    }

    /**
     * Запуск ЗАПЛАНИРОВАННОЙ тренировки (в т.ч. заранее, до её даты).
     * Загружает упражнения плана в сессию, определяет их группу и тип
     * измерения из библиотеки или своих упражнений, открывает ввод веса
     * → сразу тренировку. Переключение вкладки делает MainScreen.
     */
    /**
     * Цели той тренировки, которую начали из плана.
     *
     * Живут только в памяти: как только тренировка закончена, в журнал
     * пишется сделанное, а не задуманное. Хранить их дальше значило бы
     * смешивать одно с другим.
     */
    private val _activeTargets = MutableStateFlow<Map<String, ExerciseTarget>>(emptyMap())
    val activeTargets = _activeTargets.asStateFlow()

    fun startPlannedWorkout(pw: PlannedWorkout) {
        _activeTargets.value = pw.targets
        val exercises = pw.exercises.map { name ->
            val cat = ExerciseLibrary.exercises.find { it.name.equals(name, ignoreCase = true) }
            val custom = _customExercises.value.find { it.name.equals(name, ignoreCase = true) }
            WorkoutExercise(
                name = name,
                muscleGroup = cat?.muscleGroup ?: custom?.muscleGroup,
                measureType = cat?.measureType ?: custom?.measureType ?: MeasureType.WEIGHT_REPS
            )
        }
        val lastWeight = _completedWorkouts.value.firstOrNull()?.athleteWeight
        _restTimerStart.value = null
        setState(CurrentWorkoutState(
            workoutStartTime = LocalDateTime.now(),
            athleteWeight = lastWeight,
            selectedMuscleGroup = exercises.firstOrNull()?.muscleGroup,
            selectedExercises = exercises,
            trainingStarted = true,
            currentScreen = "weight"
        ))
        _navigateToTraining.value = _navigateToTraining.value + 1
    }
    /**
     * Начать запись ПРОШЕДШЕЙ тренировки.
     *
     * Дальше всё идёт обычным ходом: выбор упражнений, ввод подходов,
     * «Завершить». Отдельного экрана для этого нет и не нужно —
     * записывается то же самое, разница только в том, когда это было.
     *
     * Время начала и длительность человек называет сам: у прошедшей
     * тренировки часам считать нечего.
     *
     * Вес атлета подставляется из последней тренировки — как и при
     * обычном старте. Если он с тех пор изменился, человек поправит
     * на экране ввода веса.
     */
    fun startBackdatedWorkout(startedAt: LocalDateTime, durationMinutes: Int) {
        _activeTargets.value = emptyMap()
        _restTimerStart.value = null
        val lastWeight = _completedWorkouts.value.firstOrNull()?.athleteWeight
        setState(CurrentWorkoutState(
            workoutStartTime = startedAt,
            athleteWeight = lastWeight,
            trainingStarted = true,
            currentScreen = "weight",
            manualDurationMinutes = durationMinutes.coerceIn(0, 24 * 60)
        ))
        _navigateToTraining.value = _navigateToTraining.value + 1
    }

    fun setCurrentScreen(s: String) {
        // п7 (v1.8.1): возврат на стартовый экран = попытка не состоялась.
        // Сбрасываем отсчёт, иначе «висящее» время попадёт в следующую
        // тренировку и её длительность будет завышена.
        if (s == "start") {
            _restTimerStart.value = null
            setState(
                _state.value.copy(
                    currentScreen = "start",
                    trainingStarted = false,
                    workoutStartTime = LocalDateTime.now(),
                    // Снимаем и признак записи задним числом. Иначе
                    // человек, передумавший записывать прошлое, начал бы
                    // обычную тренировку — а она молча легла бы в журнал
                    // прошлой датой.
                    manualDurationMinutes = null
                )
            )
            return
        }
        // Запускаем таймер отдыха при первом входе на экран тренировки,
        // чтобы он показывал время ещё до первого записанного подхода
        if (s == "training" && _restTimerStart.value == null) {
            _restTimerStart.value = System.currentTimeMillis()
        }
        setState(_state.value.copy(currentScreen = s))
    }

    /**
     * Подход, который вписал тренер (см. LiveSync).
     *
     * Ложится в первую карточку упражнения с таким названием; нет её —
     * упражнение добавляется, как если бы человек выбрал его сам.
     * false — тренировки сейчас нет, вписывать некуда.
     */
    fun applyCoachSet(name: String, type: MeasureType, set: WorkoutSet): Boolean {
        val s = _state.value
        if (!s.trainingStarted || s.isBackdated) return false
        var ex = s.selectedExercises.find { it.name == name }
        if (ex == null) {
            val group = s.selectedMuscleGroup
                ?: ExerciseLibrary.exercises.find { it.name.equals(name, ignoreCase = true) }?.muscleGroup
                ?: _customExercises.value.find { it.name.equals(name, ignoreCase = true) }?.muscleGroup
            ex = WorkoutExercise(name = name, muscleGroup = group, measureType = type)
            setState(s.copy(selectedExercises = s.selectedExercises + ex))
        }
        addSetToExercise(ex.id, set)
        return true
    }

    // Старый вариант (вес×повторы) — делегирует общему, сохранён для
    // совместимости вызовов
    // МОДЕЛЬ V2: добавить подход ЛЮБОГО типа конкретному экземпляру (по id)
    fun addSetToExercise(exerciseId: String, set: WorkoutSet) {
        val target = _state.value.selectedExercises.find { it.id == exerciseId } ?: return
        // Было ли упражнение пустым до этого подхода — понадобится ниже
        val wasEmpty = target.sets.isEmpty()

        val updated = _state.value.selectedExercises.map { ex ->
            if (ex.id == exerciseId) ex.copy(sets = ex.sets + set) else ex
        }
        setState(_state.value.copy(selectedExercises = updated))

        // Упражнение только что стало выполненным — поднимаем его над
        // теми, к которым ещё не приступали.
        if (wasEmpty) liftDoneAbovePending(target.muscleGroup)

        updateStatsForSet(target, set)
        // Записан новый подход — таймер отдыха начинает отсчёт заново
        _restTimerStart.value = System.currentTimeMillis()
    }

    /**
     * Поднять выполненные упражнения группы над невыполненными.
     *
     * Сделанное копится сверху, оставшееся — снизу, и список сам собой
     * читается как «вот это уже позади, вот это ещё предстоит». Иначе
     * записанный подход остаётся посреди нетронутых карточек и приходится
     * глазами выискивать, где ты остановился.
     *
     * Перестановка идёт ТОЛЬКО внутри мест, занятых этой группой:
     * другие группы не сдвигаются. Порядок внутри выполненных и внутри
     * оставшихся сохраняется — меняется лишь то, что одни целиком
     * оказываются выше других.
     *
     * Вызывается в момент, когда упражнение впервые получило подход.
     * При удалении подходов обратно вниз ничего не опускается: карточка,
     * уехавшая из-под пальца сама по себе, пугает сильнее, чем помогает.
     */
    private fun liftDoneAbovePending(group: MuscleGroup?) {
        if (group == null) return
        val all = _state.value.selectedExercises
        val slots = all.indices.filter { all[it].muscleGroup == group }
        if (slots.size < 2) return

        val inGroup = slots.map { all[it] }
        val done = inGroup.filter { it.sets.isNotEmpty() }
        val pending = inGroup.filter { it.sets.isEmpty() }
        val reordered = done + pending
        if (reordered.map { it.id } == inGroup.map { it.id }) return

        val result = all.toMutableList()
        slots.forEachIndexed { i, position -> result[position] = reordered[i] }
        setState(_state.value.copy(selectedExercises = result))
    }

    // Редактирование уже введённого подхода — тоже по id экземпляра,
    // чтобы при дублях правился только тот конкретный подход в той
    // конкретной карточке, по которой тапнули
    // МОДЕЛЬ V2: редактирование подхода любого типа
    fun updateSet(exerciseId: String, index: Int, set: WorkoutSet) {
        val updated = _state.value.selectedExercises.map { ex ->
            if (ex.id == exerciseId && index in ex.sets.indices) {
                val newSets = ex.sets.toMutableList()
                newSets[index] = set
                ex.copy(sets = newSets)
            } else ex
        }
        setState(_state.value.copy(selectedExercises = updated))
        // БАГ 4: после редактирования пересчитываем рекорд с нуля — иначе он
        // "застревал" на старом значении (инкрементальный апдейт при add
        // только повышал рекорд, редактирование его не трогало)
        _state.value.selectedExercises.find { it.id == exerciseId }
            ?.let { recomputeExerciseStats(it.name) }
    }

    /**
     * БАГ 4: полный пересчёт рекордов упражнения из ВСЕХ подходов —
     * прошлых (архив) и текущей сессии. В отличие от updateStatsForSet,
     * умеет и понижать рекорд (если рекордный подход отредактировали вниз).
     */
    private fun recomputeExerciseStats(name: String) {
        data class Src(val set: WorkoutSet, val date: LocalDateTime?, val bodyWeight: Float?, val mt: MeasureType)
        val srcs = ArrayList<Src>()
        _completedWorkouts.value.forEach { w ->
            w.exercises.filter { it.name == name }.forEach { ex ->
                ex.sets.forEach { s -> srcs.add(Src(s, w.dateTime, w.athleteWeight, ex.measureType)) }
            }
        }
        _state.value.selectedExercises.filter { it.name == name }.forEach { ex ->
            ex.sets.forEach { s -> srcs.add(Src(s, LocalDateTime.now(), _state.value.athleteWeight, ex.measureType)) }
        }
        if (srcs.isEmpty()) return
        val mt = srcs.first().mt
        val base = ExerciseHistory(exerciseName = name, measureType = mt)
        val updated: ExerciseHistory = when (mt) {
            MeasureType.WEIGHT_REPS -> {
                val maxWeightVal = srcs.maxOf { it.set.weight }
                // среди подходов с максимальным весом берём тот, где больше повторов
                val maxW = srcs.filter { it.set.weight == maxWeightVal }.maxByOrNull { it.set.reps }!!
                val maxV = srcs.maxByOrNull { it.set.weight * it.set.reps }!!
                base.copy(
                    maxWeight = maxW.set.weight,
                    maxWeightReps = maxW.set.reps,
                    maxReps = srcs.maxOf { it.set.reps },
                    bestVolume = PairedExercises.volume(maxV.set.weight, maxV.set.reps, name).toFloat(),
                    bestVolumeWeight = maxV.set.weight,
                    bestVolumeReps = maxV.set.reps,
                    recordDate = maxW.date,
                    athleteWeight = maxW.bodyWeight
                )
            }
            MeasureType.REPS -> {
                val m = srcs.maxByOrNull { it.set.reps }!!
                base.copy(maxReps = m.set.reps, recordDate = m.date, athleteWeight = m.bodyWeight)
            }
            MeasureType.TIME -> {
                val m = srcs.maxByOrNull { it.set.seconds ?: 0 }!!
                base.copy(bestSeconds = m.set.seconds, recordDate = m.date, athleteWeight = m.bodyWeight)
            }
            MeasureType.DISTANCE -> {
                val m = srcs.maxByOrNull { it.set.distanceKm ?: 0f }!!
                base.copy(bestDistanceKm = m.set.distanceKm, recordDate = m.date, athleteWeight = m.bodyWeight)
            }
            MeasureType.CARDIO -> return
        }
        _exerciseHistory.value = _exerciseHistory.value + (name to updated)
        viewModelScope.launch {
            database.exerciseHistoryDao().save(ExerciseHistoryEntity(
                exerciseName = updated.exerciseName, maxWeight = updated.maxWeight,
                maxWeightReps = updated.maxWeightReps, maxReps = updated.maxReps,
                bestVolume = updated.bestVolume,
                recordDate = updated.recordDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                athleteWeight = updated.athleteWeight,
                bestVolumeWeight = updated.bestVolumeWeight, bestVolumeReps = updated.bestVolumeReps,
                measureType = updated.measureType.name,
                bestSeconds = updated.bestSeconds, bestDistanceKm = updated.bestDistanceKm
            ))
        }
    }

    fun resetWorkout() {
        _restTimerStart.value = null
        // Цели относились к той тренировке, которая закончилась.
        // Оставить их значило бы показать чужие цифры в следующей.
        _activeTargets.value = emptyMap()
        setState(CurrentWorkoutState())
        _lastCompletedWorkoutId.value = null
        loadArchiveFromDatabase()
        loadRecordsFromDatabase()
    }

    // ==================== ПОВТОР ГРУППЫ ИЗ ПРОШЛОЙ ТРЕНИРОВКИ ====================
    // _completedWorkouts гарантированно отсортирован от новых к старым,
    // поэтому просто идём по списку СНАЧАЛА (без .asReversed() — раньше
    // это было ошибкой и из-за неё показывались данные не из последней
    // тренировки, а из более старой)

    fun getLastMuscleGroupExercises(group: MuscleGroup): List<String> {
        val last = _completedWorkouts.value
            .firstOrNull { workout -> workout.exercises.any { group.sameAs(it.muscleGroup) } }
            ?: return emptyList()
        return last.exercises.filter { group.sameAs(it.muscleGroup) }.map { it.name }
    }

    fun repeatLastMuscleGroup(group: MuscleGroup) {
        val lastWithGroup = _completedWorkouts.value
            .firstOrNull { workout -> workout.exercises.any { group.sameAs(it.muscleGroup) } }
            ?: return

        val alreadyAdded = _state.value.selectedExercises.map { it.name }.toSet()
        val toAdd = lastWithGroup.exercises
            .filter { group.sameAs(it.muscleGroup) && it.name !in alreadyAdded }
            .map { WorkoutExercise(name = it.name, muscleGroup = group, measureType = it.measureType) }

        if (toAdd.isEmpty()) return
        setState(_state.value.copy(selectedExercises = _state.value.selectedExercises + toAdd))
        _lastAddedExerciseId.value = toAdd.first().id
    }

    /**
     * Отметить запись удалённой для последующей отправки на сервер.
     * uuid может быть null, если строки уже нет — тогда отмечать нечего.
     */
    private suspend fun markDeleted(kind: String, uuid: String?) {
        if (uuid.isNullOrBlank()) return
        database.syncTombstoneDao().put(SyncTombstoneEntity(kind = kind, uuid = uuid))
    }

    // ==================== FINISH WORKOUT ====================

    /**
     * Завершение тренировки.
     *
     * v1.8.2 (п1): можно передать СВОЁ время окончания — экран тренировки
     * спрашивает подтверждение, если тренировка длится дольше 3 часов
     * (человек мог забыть нажать «Завершить»). По умолчанию — «сейчас».
     */
    fun finishCurrentWorkout(endTime: LocalDateTime? = null) {
        val s = _state.value
        // ИСПРАВЛЕНО: фиксируем момент ЗАВЕРШЕНИЯ один раз и используем его
        // и для БД, и для памяти — раньше в памяти использовалось время
        // НАЧАЛА тренировки (s.workoutStartTime), а в БД — момент
        // завершения, из-за чего сортировка и "последняя тренировка"
        // работали по-разному до и после перезагрузки архива из БД.
        // не раньше начала тренировки — защита от некорректного ввода
        // У записи задним числом и дата, и длительность — те, что назвал
        // человек. Считать их по часам нельзя: часы показывают сегодня,
        // а тренировка была неделю назад.
        val manual = s.manualDurationMinutes
        val finishTime = if (manual != null) s.workoutStartTime.plusMinutes(manual.toLong())
                         else (endTime ?: LocalDateTime.now()).coerceAtLeast(s.workoutStartTime)
        val durationMinutes = manual?.toLong()
            ?: Duration.between(s.workoutStartTime, finishTime).toMinutes()

        /* В запись идут только упражнения, в которых есть подходы.

           Добавленное и несделанное упражнение — это намерение, а не
           запись. Раньше оно ложилось в архив целиком: «Жим ногами —
           Нет подходов», и итог говорил «4 упражнения», когда сделано
           было два. Архив — про то, что было, а не про то, что
           собирались. Группы мышц в заголовке считаются так же:
           группа без единого подхода тренировкой не была. */
        val done = s.selectedExercises.filter { it.sets.isNotEmpty() }
            .ifEmpty { s.selectedExercises }
        val usedGroups = done.mapNotNull { it.muscleGroup }.distinct()
        val muscleGroupSummary =
            if (usedGroups.isNotEmpty()) usedGroups.joinToString(", ") { it.title }
            else s.selectedMuscleGroup?.title ?: "Не выбрано"

        /* Опознавательный знак — заранее, а не тем, что база придумает сама:
           он нужен и записи в базе, и копии в памяти. Без него свежая
           тренировка в архиве до перезапуска не знала своего uuid —
           и ни «В Телеграм» из «Поделиться», ни заметку тренера к ней
           привязать было нельзя. */
        val newUuid = java.util.UUID.randomUUID().toString()

        viewModelScope.launch {
            /* Запись целиком или никак.
             *
             * Тренировка ложится в базу тремя шагами: сама тренировка,
             * упражнения, подходы. И тренировка с первого же шага
             * помечена как «надо отправить».
             *
             * Между первым шагом и последним есть окно в доли секунды.
             * Обмен с сервером, стартовавший именно в нём, отправлял
             * тренировку ПУСТОЙ — а потом снимал пометку «надо
             * отправить». Подходы дописывались секундой позже, но
             * тренировку больше никто не трогал: повода отправить её
             * заново не возникало никогда. На сервере она оставалась
             * пустой навсегда, и это было видно только со стороны —
             * в сравнении с другом и в браузере.
             *
             * Внутри неделимой записи такого окна нет: для всех, кто
             * смотрит в базу со стороны, тренировка либо есть целиком,
             * либо её ещё нет вовсе.
             */
            database.withTransaction {
                val wid = database.workoutHistoryDao().insertWorkout(
                    CompletedWorkoutEntity(
                        date = finishTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        duration = durationMinutes,
                        athleteWeight = s.athleteWeight,
                        muscleGroup = muscleGroupSummary,
                        uuid = newUuid
                    )
                )
                done.forEach { ex ->
                    database.completedWorkoutExerciseDao().insert(
                        CompletedWorkoutExerciseEntity(
                            workoutId = wid,
                            exerciseName = ex.name,
                            muscleGroup = ex.muscleGroup?.name,
                            measureType = ex.measureType.name
                        )
                    )
                    ex.sets.forEach { set -> database.completedWorkoutSetDao().insert(CompletedWorkoutSetEntity(
                        workoutId = wid, exerciseName = ex.name,
                        weight = set.weight, reps = set.reps,
                        seconds = set.seconds, distanceKm = set.distanceKm,
                        load = set.load, speed = set.speed
                    )) }
                }
                database.inProgressWorkoutDao().clear()
                _lastCompletedWorkoutId.value = wid
            }

            /* Сигнал «тренировка сохранена» подаём ЗДЕСЬ, внутри
             * корутины и уже после транзакции.
             *
             * По этому сигналу главный экран сразу отправляет свежую
             * тренировку на сервер. Раньше строка стояла ниже, за
             * закрывающей скобкой `launch` — то есть выполнялась
             * НЕМЕДЛЕННО, не дожидаясь, пока запись доедет до базы.
             * Обмен и запись шли наперегонки: если обмен успевал
             * прочитать базу первым, тренировки там ещё не было,
             * и на сервер она не уезжала. Оставалась помеченной
             * неотправленной и ждала следующего запуска приложения —
             * а человек в это время открывал мини-приложение
             * и не находил там своей тренировки.
             *
             * Транзакция выше защищает от ПОЛУПУСТОЙ записи. Эта
             * строка — от ОТСУТСТВУЮЩЕЙ. Разные беды, разные средства.
             */
            _workoutSaved.value = _workoutSaved.value + 1
        }

        setCompletedWorkouts(_completedWorkouts.value + CompletedWorkout(
            dateTime = finishTime,
            athleteWeight = s.athleteWeight,
            muscleGroup = muscleGroupSummary,
            // То же, что легло в базу: без пустых упражнений. Иначе
            // архив до первого перезапуска показывал бы одно, а после — другое.
            exercises = done,
            durationMinutes = durationMinutes,
            uuid = newUuid
        ))
        // ── Рекорды у записи задним числом считаются заново ──
        //
        // По ходу тренировки рекорд обновляется на ходу, и это верно,
        // пока речь о сегодняшнем дне: новый результат либо выше
        // прежнего, либо нет.
        //
        // С прошлым так нельзя. Тренировка недельной давности может
        // содержать результат выше всего, что было ДО неё, но ниже
        // того, что человек сделал ПОСЛЕ. Сравнение с текущим рекордом
        // тут даёт неверный ответ в обе стороны: либо затрёт более
        // поздний и лучший результат, либо поставит ему сегодняшнюю дату.
        //
        // Поэтому для таких записей рекорд выводится заново из всей
        // истории — она к этому моменту уже пополнена свежей записью
        // с настоящей, старой датой.
        if (manual != null) {
            s.selectedExercises.map { it.name }.distinct()
                .forEach { recomputeExerciseStats(it) }
        }

        _restTimerStart.value = null
        _state.value = _state.value.copy(currentScreen = "summary")
        // Сигнал «сохранено» подаётся выше, внутри корутины после
        // транзакции. Здесь его быть не должно: экран итога открывается
        // сразу, а отправка на сервер ждёт, пока запись ляжет в базу.
    }

    fun saveWorkoutNotes(notes: String) {
        val wid = _lastCompletedWorkoutId.value ?: return
        val trimmed = notes.trim().ifBlank { null }
        viewModelScope.launch {
            database.workoutHistoryDao().updateNotes(wid, trimmed, System.currentTimeMillis())
        }
        setCompletedWorkouts(_completedWorkouts.value.map {
            if (it.id == wid) it.copy(notes = trimmed) else it
        })
    }

    // ==================== УДАЛЕНИЕ С ВОЗМОЖНОСТЬЮ ОТМЕНЫ ====================
    //
    // Настоящая отмена возможна только если запись из базы ещё не ушла.
    // Поэтому удаление идёт в два шага: сразу пропадает с экрана,
    // а из базы уходит через несколько секунд. Восстановление —
    // это просто перечитывание из базы, где запись всё ещё лежит.
    //
    // Так делает телеграм, и это единственный честный способ: вернуть
    // уже удалённую запись со всеми подходами и отметками для сервера
    // из ничего нельзя.

    data class UndoAction(val text: String, val undo: () -> Unit)

    private val _undo = MutableStateFlow<UndoAction?>(null)
    val undo = _undo.asStateFlow()

    private var pendingCommit: (suspend () -> Unit)? = null
    private var pendingJob: Job? = null

    private fun scheduleDelete(
        text: String,
        hide: () -> Unit,
        restore: () -> Unit,
        commit: suspend () -> Unit
    ) {
        // Если предыдущее удаление ещё висит, доводим его до конца.
        // Иначе второе удаление тихо отменило бы первое, и человек
        // увидел бы вернувшуюся запись без всякой причины.
        commitPendingDelete()

        hide()
        pendingCommit = commit
        _undo.value = UndoAction(text) {
            pendingJob?.cancel()
            pendingJob = null
            pendingCommit = null
            _undo.value = null
            restore()
        }
        pendingJob = viewModelScope.launch {
            delay(UNDO_MILLIS)
            commitPendingDelete()
        }
    }

    /** Довести отложенное удаление до базы прямо сейчас. */
    fun commitPendingDelete() {
        val commit = pendingCommit ?: return
        pendingCommit = null
        pendingJob = null
        _undo.value = null
        // Отдельная задача: этот вызов может прийти из той самой,
        // которую вот-вот отменят.
        viewModelScope.launch { commit() }
    }

    override fun onCleared() {
        // Приложение закрывают — отменять уже некому, доводим до конца.
        commitPendingDelete()
        super.onCleared()
    }

    fun requestDeleteWorkout(id: Long) {
        val gone = _completedWorkouts.value.find { it.id == id } ?: return
        scheduleDelete(
            text = "Тренировка удалена",
            hide = { setCompletedWorkouts(_completedWorkouts.value.filterNot { it.id == id }) },
            restore = { loadArchiveFromDatabase(); loadRecordsFromDatabase() },
            commit = { deleteWorkoutNow(id) }
        )
    }

    fun requestDeletePlannedWorkout(id: Long) {
        _plannedWorkouts.value.find { it.id == id } ?: return
        scheduleDelete(
            text = "План удалён",
            hide = { _plannedWorkouts.value = _plannedWorkouts.value.filterNot { it.id == id } },
            restore = { loadPlannedWorkouts() },
            commit = { deletePlannedWorkoutNow(id) }
        )
    }

    fun requestDeleteBodyMetric(id: Long) {
        _bodyMetrics.value.find { it.id == id } ?: return
        scheduleDelete(
            text = "Замер удалён",
            hide = { _bodyMetrics.value = _bodyMetrics.value.filterNot { it.id == id } },
            restore = { loadBodyMetrics() },
            commit = { deleteBodyMetricNow(id) }
        )
    }

    // ==================== УДАЛЕНИЕ ТРЕНИРОВКИ ИЗ АРХИВА ====================

    /** Удалить немедленно, без отмены. Обычные экраны зовут requestDeleteWorkout. */
    fun deleteWorkout(id: Long) {
        setCompletedWorkouts(_completedWorkouts.value.filterNot { it.id == id })
        viewModelScope.launch { deleteWorkoutNow(id) }
    }

    private suspend fun deleteWorkoutNow(id: Long) {
        run {
            // Сначала запоминаем опознавательный знак записи, потом удаляем.
            // Без этой отметки тренировка вернулась бы обратно при первом
            // же обмене с сервером — с другого устройства, где про удаление
            // никто не знает.
            markDeleted(SyncKind.WORKOUT, database.workoutHistoryDao().getUuid(id))
            database.completedWorkoutExerciseDao().deleteByWorkout(id)
            database.completedWorkoutSetDao().deleteByWorkout(id)
            database.workoutHistoryDao().deleteById(id)
        }
    }

    // ==================== EXERCISE STATS ====================

    /**
     * Обновление рекордов с учётом типа упражнения:
     *  WEIGHT_REPS — классическая логика (макс. вес / объём)
     *  REPS        — макс. повторы
     *  TIME        — лучшее (наибольшее) время
     *  DISTANCE    — макс. дистанция
     *  CARDIO      — рекорды не ведутся
     */
    private fun updateStatsForSet(target: WorkoutExercise, set: WorkoutSet) {
        if (target.measureType == MeasureType.WEIGHT_REPS) {
            updateExerciseStats(target.name, set.weight, set.reps)
            return
        }
        if (target.measureType == MeasureType.CARDIO) return

        val cur = _exerciseHistory.value[target.name]
        val isNewRecord = when (target.measureType) {
            MeasureType.REPS     -> set.reps > (cur?.maxReps ?: 0)
            MeasureType.TIME     -> (set.seconds ?: 0) > (cur?.bestSeconds ?: 0)
            MeasureType.DISTANCE -> (set.distanceKm ?: 0f) > (cur?.bestDistanceKm ?: 0f)
            else -> false
        }
        if (!isNewRecord) return

        val updated = (cur ?: ExerciseHistory(exerciseName = target.name)).copy(
            measureType = target.measureType,
            maxReps = if (target.measureType == MeasureType.REPS) set.reps else cur?.maxReps ?: 0,
            bestSeconds = if (target.measureType == MeasureType.TIME) set.seconds else cur?.bestSeconds,
            bestDistanceKm = if (target.measureType == MeasureType.DISTANCE) set.distanceKm else cur?.bestDistanceKm,
            recordDate = LocalDateTime.now(),
            athleteWeight = _state.value.athleteWeight
        )
        _exerciseHistory.value = _exerciseHistory.value + (target.name to updated)
        viewModelScope.launch {
            database.exerciseHistoryDao().save(ExerciseHistoryEntity(
                exerciseName = updated.exerciseName, maxWeight = updated.maxWeight,
                maxWeightReps = updated.maxWeightReps, maxReps = updated.maxReps,
                bestVolume = updated.bestVolume,
                recordDate = updated.recordDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                athleteWeight = updated.athleteWeight,
                bestVolumeWeight = updated.bestVolumeWeight, bestVolumeReps = updated.bestVolumeReps,
                measureType = updated.measureType.name,
                bestSeconds = updated.bestSeconds, bestDistanceKm = updated.bestDistanceKm
            ))
        }
    }

    fun updateExerciseStats(exerciseName: String, weight: Float, reps: Int) {
        val volume = PairedExercises.volume(weight, reps, exerciseName).toFloat()
        val cur = _exerciseHistory.value[exerciseName]
        val curMaxWeight = cur?.maxWeight ?: 0f
        val isNewWeight  = weight > curMaxWeight
        val isNewVolume  = volume > (cur?.bestVolume ?: 0f)
        val updated = ExerciseHistory(
            exerciseName     = exerciseName,
            maxWeight        = maxOf(curMaxWeight, weight),
            // повторы рекордного подхода: при новом макс.весе — эти повторы;
            // при том же весе — лучшие повторы; иначе прежние
            maxWeightReps    = when {
                weight > curMaxWeight  -> reps
                weight == curMaxWeight -> maxOf(cur?.maxWeightReps ?: 0, reps)
                else                   -> cur?.maxWeightReps ?: 0
            },
            maxReps          = maxOf(cur?.maxReps ?: 0, reps),
            bestVolume       = maxOf(cur?.bestVolume ?: 0f, volume),
            recordDate       = if (isNewWeight) LocalDateTime.now() else cur?.recordDate ?: LocalDateTime.now(),
            athleteWeight    = if (isNewWeight) _state.value.athleteWeight else cur?.athleteWeight,
            bestVolumeWeight = if (isNewVolume) weight else cur?.bestVolumeWeight,
            bestVolumeReps   = if (isNewVolume) reps   else cur?.bestVolumeReps
        )
        _exerciseHistory.value = _exerciseHistory.value + (exerciseName to updated)
        viewModelScope.launch {
            database.exerciseHistoryDao().save(ExerciseHistoryEntity(
                exerciseName = updated.exerciseName, maxWeight = updated.maxWeight,
                maxWeightReps = updated.maxWeightReps, maxReps = updated.maxReps,
                bestVolume = updated.bestVolume,
                recordDate = updated.recordDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                athleteWeight = updated.athleteWeight,
                bestVolumeWeight = updated.bestVolumeWeight, bestVolumeReps = updated.bestVolumeReps,
                measureType = updated.measureType.name,
                bestSeconds = updated.bestSeconds, bestDistanceKm = updated.bestDistanceKm
            ))
        }
    }

    // ==================== LOAD FROM DB ====================

    fun loadArchiveFromDatabase() {
        viewModelScope.launch {
            val loaded = database.workoutHistoryDao().getAllWorkouts().map { e ->
                val exEntities = database.completedWorkoutExerciseDao().getByWorkout(e.id)
                val allSets    = database.completedWorkoutSetDao().getByWorkout(e.id).groupBy { it.exerciseName }
                CompletedWorkout(
                    id = e.id,
                    // Знак записи несём в модель: по нему находится
                    // заметка тренера, пришедшая с сервера.
                    uuid = e.uuid,
                    dateTime = Instant.ofEpochMilli(e.date).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    athleteWeight = e.athleteWeight, muscleGroup = e.muscleGroup,
                    durationMinutes = e.duration ?: 0,
                    notes = e.notes,
                    exercises = exEntities.map { ex ->
                        WorkoutExercise(
                            name = ex.exerciseName,
                            muscleGroup = ex.muscleGroup?.let { name -> MuscleGroup.entries.find { it.name == name } },
                            measureType = ex.measureType?.let { mt -> MeasureType.entries.find { it.name == mt } }
                                ?: MeasureType.WEIGHT_REPS,
                            sets = (allSets[ex.exerciseName] ?: emptyList()).map {
                                WorkoutSet(it.weight, it.reps, it.seconds, it.distanceKm, it.load, it.speed)
                            }
                        )
                    }
                )
            }
            setCompletedWorkouts(loaded)
        }
    }

    fun loadRecordsFromDatabase() {
        viewModelScope.launch {
            _exerciseHistory.value = database.exerciseHistoryDao().getAll()
                .associateBy { it.exerciseName }
                .mapValues { (_, e) -> ExerciseHistory(
                    exerciseName = e.exerciseName, maxWeight = e.maxWeight, maxWeightReps = e.maxWeightReps,
                    maxReps = e.maxReps, bestVolume = e.bestVolume,
                    recordDate = e.recordDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
                    athleteWeight = e.athleteWeight, bestVolumeWeight = e.bestVolumeWeight, bestVolumeReps = e.bestVolumeReps,
                    measureType = e.measureType?.let { mt -> MeasureType.entries.find { it.name == mt } }
                        ?: MeasureType.WEIGHT_REPS,
                    bestSeconds = e.bestSeconds, bestDistanceKm = e.bestDistanceKm
                )}
        }
    }

    fun loadPlannedWorkouts() {
        viewModelScope.launch {
            _plannedWorkouts.value = database.plannedWorkoutDao().getAll().map { e ->
                val rows = database.plannedWorkoutDao().getExercises(e.id)
                PlannedWorkout(
                    id = e.id, date = LocalDate.ofEpochDay(e.date),
                    hour = e.hour, minute = e.minute, muscleGroup = e.muscleGroup,
                    exercises = rows.map { it.exerciseName },
                    // Пустые цели в карту не кладём: пусть отсутствие
                    // означает «не задано», а не объект из трёх null.
                    targets = rows.mapNotNull { r ->
                        val t = ExerciseTarget(r.targetSets, r.targetReps, r.targetWeight,
                            ExerciseTarget.planFromString(r.targetPlan))
                        if (t.isEmpty) null else r.exerciseName to t
                    }.toMap(),
                    createdAt = e.updatedAt
                )
            }
        }
    }

    fun addPlannedWorkout(
        date: LocalDate, hour: Int, minute: Int,
        muscleGroup: String, exercises: List<String>
    ) = addPlannedWorkout(listOf(date), hour, minute, muscleGroup, exercises, emptyMap())

    /**
     * Запланировать одну и ту же тренировку сразу на несколько дней.
     *
     * Каждый день — отдельная запись со своим опознавательным знаком
     * и своими напоминаниями. Одна запись на несколько дат была бы
     * удобнее в базе и хуже во всём остальном: отменить один день
     * из пяти стало бы невозможно, а именно это и нужно чаще всего.
     */
    fun addPlannedWorkout(
        dates: List<LocalDate>,
        hour: Int,
        minute: Int,
        muscleGroup: String,
        exercises: List<String>,
        targets: Map<String, ExerciseTarget> = emptyMap()
    ) {
        if (dates.isEmpty()) return
        viewModelScope.launch {
            dates.distinct().sorted().forEach { date ->
                val id = database.plannedWorkoutDao().insert(
                    PlannedWorkoutEntity(
                        date = date.toEpochDay(), hour = hour,
                        minute = minute, muscleGroup = muscleGroup
                    )
                )
                exercises.forEach { name ->
                    val t = targets[name]
                    database.plannedWorkoutDao().insertExercise(
                        PlannedExerciseEntity(
                            workoutId = id, exerciseName = name,
                            targetSets = t?.sets, targetReps = t?.reps,
                            targetWeight = t?.weight,
                            targetPlan = t?.plan?.takeIf { it.isNotEmpty() }
                                ?.let { ExerciseTarget.planToJson(it).toString() }
                        )
                    )
                }
                // Напоминания: утром в день тренировки и за два часа.
                ReminderScheduler.schedulePlanned(id, date, hour, minute, muscleGroup)
            }
            loadPlannedWorkouts()
        }
    }

    fun deletePlannedWorkout(id: Long) {
        viewModelScope.launch { deletePlannedWorkoutNow(id) }
    }

    private suspend fun deletePlannedWorkoutNow(id: Long) {
        markDeleted(SyncKind.PLANNED, database.plannedWorkoutDao().getUuid(id))
        database.plannedWorkoutDao().deleteExercisesByWorkout(id)
        database.plannedWorkoutDao().deleteById(id)
        // Снимаем оба напоминания этой тренировки
        ReminderScheduler.cancelPlanned(id)
        loadPlannedWorkouts()
    }

    // ==================== СВОИ УПРАЖНЕНИЯ ====================

    // ═══════════════════ СВОИ ТРЕНИРОВКИ ═══════════════════
    //
    // Образец, а не событие: название, группы и набор упражнений.
    // Тот, кто ходит по своей программе, повторяет один и тот же набор
    // из недели в неделю; набирать его заново каждый раз — работа
    // на пустом месте.

    fun loadMyWorkouts() {
        viewModelScope.launch {
            _myWorkouts.value = database.myWorkoutDao().getAll().map { e ->
                MyWorkout(
                    id = e.id,
                    title = e.title,
                    muscleGroup = e.muscleGroup,
                    exercises = database.myWorkoutDao().getExercises(e.id).map { it.exerciseName },
                    uuid = e.uuid,
                    updatedAt = e.updatedAt
                )
            }
        }
    }

    /**
     * Сохранить проведённую тренировку как образец.
     *
     * Берётся только состав: что делали и в каком порядке. Веса,
     * повторы и дата остаются в журнале — образец отвечает на вопрос
     * «что делать», а не «что вышло».
     */
    fun saveWorkoutAsMy(workout: CompletedWorkout, title: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val name = title.trim().ifBlank { workout.muscleGroup }
        val names = workout.exercises.map { it.name }
        if (names.isEmpty()) { onResult(false, "В тренировке нет упражнений"); return }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = database.myWorkoutDao().insert(
                MyWorkoutEntity(title = name, muscleGroup = workout.muscleGroup,
                    updatedAt = now, dirty = 1)
            )
            names.forEachIndexed { i, n ->
                database.myWorkoutDao().insertExercise(
                    MyWorkoutExerciseEntity(myWorkoutId = id, exerciseName = n, position = i)
                )
            }
            loadMyWorkouts()
            onResult(true, "Сохранено как своя тренировка")
        }
    }

    /**
     * Удалить образец.
     *
     * Отметка об удалении нужна обязательно: без неё образец вернулся бы
     * с сервера при следующем обмене, и человек решил бы, что удаление
     * не работает.
     */
    fun deleteMyWorkout(id: Long) {
        viewModelScope.launch {
            val uuid = database.myWorkoutDao().getUuid(id)
            database.myWorkoutDao().deleteExercises(id)
            database.myWorkoutDao().deleteById(id)
            if (uuid != null) {
                database.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.MY_WORKOUT, uuid = uuid)
                )
            }
            loadMyWorkouts()
        }
    }

    /** Начать тренировку по образцу. Дальше всё как обычно. */
    fun startMyWorkout(my: MyWorkout) {
        _activeTargets.value = emptyMap()
        val exercises = my.exercises.map { name ->
            val cat = ExerciseLibrary.exercises.find { it.name.equals(name, ignoreCase = true) }
            val custom = _customExercises.value.find { it.name.equals(name, ignoreCase = true) }
            WorkoutExercise(
                name = name,
                muscleGroup = cat?.muscleGroup ?: custom?.muscleGroup,
                measureType = cat?.measureType ?: custom?.measureType ?: MeasureType.WEIGHT_REPS
            )
        }
        if (exercises.isEmpty()) return
        val lastWeight = _completedWorkouts.value.firstOrNull()?.athleteWeight
        _restTimerStart.value = null
        setState(CurrentWorkoutState(
            workoutStartTime = LocalDateTime.now(),
            athleteWeight = lastWeight,
            selectedMuscleGroup = exercises.firstOrNull()?.muscleGroup,
            selectedExercises = exercises,
            trainingStarted = true,
            currentScreen = "weight"
        ))
        _navigateToTraining.value = _navigateToTraining.value + 1
    }

    /**
     * Удалить своё упражнение — одно, по названию.
     *
     * Уходит только из списка своих упражнений: сделанные подходы,
     * архив и рекорды остаются как были. Отметка об удалении уезжает
     * на сервер, иначе упражнение вернулось бы с другого устройства
     * при первом же обмене.
     */
    fun deleteCustomExercise(name: String) {
        val target = _customExercises.value.find { it.name == name } ?: return
        viewModelScope.launch {
            val uuid = database.customExerciseDao().getUuid(target.id)
            database.customExerciseDao().deleteById(target.id)
            if (uuid != null) {
                database.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.CUSTOM_EXERCISE, uuid = uuid)
                )
            }
            loadCustomExercises()
        }
    }

    fun loadCustomExercises() {
        viewModelScope.launch {
            _customExercises.value = database.customExerciseDao().getAll().mapNotNull { e ->
                val group = MuscleGroup.entries.find { it.name == e.muscleGroup } ?: return@mapNotNull null
                CustomExercise(
                    id = e.id, name = e.name, muscleGroup = group,
                    measureType = e.measureType?.let { mt -> MeasureType.entries.find { it.name == mt } }
                        ?: MeasureType.WEIGHT_REPS,
                    description = e.description,
                    photoPath = e.photoPath
                )
            }
        }
    }

    /**
     * Переименование своего упражнения (п8). Обновляет не только строку в
     * custom_exercises, но и ВСЮ историю (архив, подходы, рекорды, планы),
     * чтобы рекорды и статистика не отвязались от упражнения.
     */
    fun renameCustomExercise(
        group: MuscleGroup, oldName: String, newName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) { onResult(false, "Название не может быть пустым"); return }
        if (trimmed.equals(oldName, ignoreCase = true)) { onResult(false, "Название не изменилось"); return }
        val target = _customExercises.value.find { group.sameAs(it.muscleGroup) && it.name == oldName }
        if (target == null) { onResult(false, "Упражнение не найдено"); return }
        val collision = ExerciseLibrary.exercises.any { it.name.equals(trimmed, ignoreCase = true) } ||
                _customExercises.value.any { it.name.equals(trimmed, ignoreCase = true) }
        if (collision) { onResult(false, "Такое упражнение уже есть"); return }

        viewModelScope.launch {
            database.customExerciseDao().rename(target.id, trimmed, System.currentTimeMillis())
            database.completedWorkoutExerciseDao().renameExercise(oldName, trimmed)
            database.completedWorkoutSetDao().renameExercise(oldName, trimmed)
            database.exerciseHistoryDao().renameExercise(oldName, trimmed)
            database.plannedWorkoutDao().renameExercise(oldName, trimmed)
            database.myWorkoutDao().renameExercise(oldName, trimmed)
            // Список упражнений входит в состав плановой тренировки и
            // отдельно не синхронизируется, поэтому после переименования
            // помечаем плановые тренировки изменёнными.
            database.plannedWorkoutDao().markAllDirty(System.currentTimeMillis())
            database.myWorkoutDao().markAllDirty(System.currentTimeMillis())
            loadCustomExercises()
            loadArchiveFromDatabase()
            loadRecordsFromDatabase()
            loadPlannedWorkouts()
            onResult(true, "Переименовано")
        }
    }

    /**
     * Описание и снимок своего упражнения.
     *
     * Старый снимок стираем с телефона, когда его заменили или убрали.
     * Иначе во внутренней памяти копились бы картинки, которых человек
     * не видит и удалить не может: приложение положило их туда само.
     *
     * Стираем ПОСЛЕ записи в базу, а не до. Упадёт запись — снимок ещё
     * на месте и покажется по-прежнему; сотри мы его первым, человек
     * остался бы и без нового, и без старого.
     */
    fun saveCustomExerciseDetails(
        group: MuscleGroup, name: String, description: String?, photoPath: String?
    ) {
        // Ищем здесь, а не на экране: список своих упражнений живёт
        // тут и всегда полон. Экрану достаточно знать название.
        val target = _customExercises.value.find {
            group.sameAs(it.muscleGroup) && it.name == name
        } ?: return
        val prev = target.photoPath
        val text = description?.trim()?.ifBlank { null }
        viewModelScope.launch {
            database.customExerciseDao()
                .setDetails(target.id, text, photoPath, System.currentTimeMillis())
            if (prev != null && prev != photoPath) ExercisePhoto.forget(prev)
            loadCustomExercises()
        }
    }

    fun addCustomExercise(name: String, group: MuscleGroup, measureType: MeasureType = MeasureType.WEIGHT_REPS) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val alreadyExists = _customExercises.value.any {
            group.sameAs(it.muscleGroup) && it.name.equals(trimmed, ignoreCase = true)
        }
        val existsInLibrary = ExerciseLibrary.exercises.any {
            group.sameAs(it.muscleGroup) && it.name.equals(trimmed, ignoreCase = true)
        }
        if (alreadyExists || existsInLibrary) return

        viewModelScope.launch {
            database.customExerciseDao().insert(CustomExerciseEntity(
                name = trimmed, muscleGroup = group.name, measureType = measureType.name
            ))
            loadCustomExercises()
        }
    }

    // ==================== GETTERS ====================
    // _completedWorkouts гарантированно DESC (новые первые) — см.
    // setCompletedWorkouts(). Поэтому first() = самая свежая тренировка.

    fun getDaysSinceLastWorkout(): Long? {
        val w = _completedWorkouts.value; if (w.isEmpty()) return null
        // БАГ 5: считаем КАЛЕНДАРНЫЕ дни в местном времени, а не 24-часовые
        // интервалы. Раньше тренировка "вчера вечером" (10 ч назад) через
        // Duration.toDays() давала 0 → показывало "тренировался сегодня".
        return ChronoUnit.DAYS.between(w.first().dateTime.toLocalDate(), LocalDate.now())
    }

    /**
     * БАГ 3: рекордный ли это подход — с учётом типа упражнения.
     * Используется для значка 🏆 на всех типах (не только вес×повторы).
     */
    fun isSetRecord(name: String, measureType: MeasureType, set: WorkoutSet): Boolean {
        val r = _exerciseHistory.value[name] ?: return false
        return when (measureType) {
            // Со своим весом (maxWeight = 0) рекорд считается по повторам
            MeasureType.WEIGHT_REPS ->
                // ИСПРАВЛЕНО (v1.8.1, п5): рекорд засчитывается если
                //  – взят НОВЫЙ максимальный вес (даже с меньшим числом повторов), ИЛИ
                //  – тот же максимальный вес, но повторов не меньше рекордных.
                // Раньше требовалось И то и другое — новый макс. вес с меньшим
                // числом повторов рекордом не считался.
                if (r.maxWeight > 0f)
                    set.weight > r.maxWeight ||
                            (set.weight > 0f && set.weight == r.maxWeight && set.reps >= r.maxWeightReps)
                else set.reps > 0 && set.reps >= r.maxReps
            MeasureType.REPS        -> set.reps > 0 && set.reps >= r.maxReps
            MeasureType.TIME        -> (set.seconds ?: 0) > 0 && (set.seconds ?: 0) >= (r.bestSeconds ?: 0)
            MeasureType.DISTANCE    -> (set.distanceKm ?: 0f) > 0f && (set.distanceKm ?: 0f) >= (r.bestDistanceKm ?: 0f)
            MeasureType.CARDIO      -> false
        }
    }
    // ИСПРАВЛЕНО: раньше здесь был .asReversed(), что при DESC-порядке
    // списка означало перебор от САМОЙ СТАРОЙ тренировки к новой — отсюда
    // "прошлая тренировка" в карточке упражнения показывала не последние,
    // а устаревшие данные. Теперь идём по списку как есть (новые первыми).
    fun getPreviousSet(name: String, index: Int): WorkoutSet? {
        for (w in _completedWorkouts.value) {
            val ex = w.exercises.find { it.name == name }
            if (ex != null && ex.sets.size > index) return ex.sets[index]
        }
        return null
    }
    // Только упражнения с подходами — ровно те, что лягут в архив.
    fun getCurrentExercisesCount() = _state.value.selectedExercises.count { it.sets.isNotEmpty() }
    fun getCurrentSetsCount()      = _state.value.selectedExercises.sumOf { it.sets.size }
    fun getCurrentVolume()         = _state.value.selectedExercises.sumOf { ex -> ex.sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) } }.toFloat()

    // ==================== ЖУРНАЛ ТЕЛА ====================

    fun loadBodyMetrics() {
        viewModelScope.launch { _bodyMetrics.value = database.bodyMetricDao().getAll() }
    }

    // ==================== ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ ====================

    fun loadUserProfile() {
        viewModelScope.launch { _userProfile.value = database.userProfileDao().get() }
    }

    fun saveUserProfile(
        name: String?, photoPath: String?, heightCm: Float?, weightKg: Float?,
        // Пол меняется в том же окне «Профиль» — и пишется той же
        // записью. Отдельный вызов setSex следом читал бы ещё старый
        // профиль и затирал бы только что сохранённое имя.
        sex: String? = _userProfile.value?.sex
    ) {
        val prev = _userProfile.value
        val prevWeight = prev?.weightKg
        viewModelScope.launch {
            database.userProfileDao().upsert(
                UserProfileEntity(id = 1, name = name, photoPath = photoPath,
                    heightCm = heightCm, weightKg = weightKg,
                    // Пол — тот, что выбран в окне «Профиль» (или прежний).
                    // Без этой строки запись профиля стирала бы его
                    // молча: карточка собирается заново, и всё, чего
                    // в ней не перечислили, уходит в пустоту.
                    sex = sex)
            )
            // ИСПРАВЛЕНО: вес из профиля пишем и в журнал тела, иначе он жил
            // отдельно от графика веса (в шапке было одно число, на графике —
            // другое). Теперь источник веса один: журнал + тренировки.
            if (weightKg != null && weightKg != prevWeight) {
                database.bodyMetricDao().insert(
                    BodyMetricEntity(date = LocalDate.now().toEpochDay(), weight = weightKg)
                )
                loadBodyMetrics()
            }
            loadUserProfile()
        }
    }

    /**
     * Пол — только для разрядных таблиц.
     *
     * Спрашивается один раз и меняется только здесь: кнопка «посчитать
     * по женским» рядом с разрядом превращала бы его в тумблер «а
     * покажи, как было бы у неё», то есть в игрушку. И нажать такую
     * кнопку случайно проще, чем заметить, что числа съехали.
     *
     * null — законный ответ: человек вправе не отвечать, и тогда
     * лестница просто не показывается.
     */
    fun setSex(sex: String?) {
        viewModelScope.launch {
            val cur = _userProfile.value ?: UserProfileEntity(id = 1)
            database.userProfileDao().upsert(
                cur.copy(
                    sex = sex,
                    updatedAt = System.currentTimeMillis(),
                    dirty = 1
                )
            )
            loadUserProfile()
        }
    }

    fun addBodyMetric(entity: BodyMetricEntity) {
        viewModelScope.launch {
            database.bodyMetricDao().insert(entity)
            loadBodyMetrics()
        }
    }

    fun deleteBodyMetric(id: Long) {
        viewModelScope.launch { deleteBodyMetricNow(id) }
    }

    private suspend fun deleteBodyMetricNow(id: Long) {
        markDeleted(SyncKind.BODY_METRIC, database.bodyMetricDao().getUuid(id))
        database.bodyMetricDao().deleteById(id)
        loadBodyMetrics()
    }

    /**
     * История веса для графика: объединяет вес атлета из тренировок
     * (вводится на старте каждой тренировки) и ручные записи журнала тела.
     * Несколько значений за один день усредняются. Отсортировано по дате.
     */
    fun getWeightHistory(): List<Pair<LocalDate, Float>> {
        // ИСПРАВЛЕНО: за один день берём ПОСЛЕДНЮЮ запись, а не среднее.
        // Раньше вес 91.2 из тренировки и 93.0 из профиля за один день
        // усреднялись в 92.1 — пользователь видел число, которого не вводил.
        // Приоритет внутри дня: запись журнала тела/профиля (свежий id)
        // важнее веса, введённого на старте тренировки.
        data class Entry(val date: LocalDate, val weight: Float, val rank: Long)

        val fromWorkouts = _completedWorkouts.value.mapNotNull { w ->
            w.athleteWeight?.let {
                // ранг по времени тренировки (в пределах суток)
                Entry(w.dateTime.toLocalDate(), it, w.dateTime.toLocalTime().toSecondOfDay().toLong())
            }
        }
        val fromMetrics = _bodyMetrics.value.mapNotNull { m ->
            m.weight?.let {
                // ручные записи всегда «позже» тренировочных за тот же день
                Entry(LocalDate.ofEpochDay(m.date), it, 1_000_000L + m.id)
            }
        }
        return (fromWorkouts + fromMetrics)
            .groupBy { it.date }
            .mapNotNull { (d, list) -> list.maxByOrNull { it.rank }?.let { d to it.weight } }
            .sortedBy { it.first }
    }

    // ==================== БЭКАП / ВОССТАНОВЛЕНИЕ ====================

    /** Сохраняет все данные в файл, выбранный пользователем (SAF-uri). */
    fun exportBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = BackupManager.exportJson(database)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Не удалось открыть файл для записи")
                }
                onResult(true, "Бэкап сохранён")
            } catch (e: Exception) {
                onResult(false, "Ошибка сохранения: ${e.message}")
            }
        }
    }

    /**
     * Восстанавливает данные из файла бэкапа. ЗАМЕНЯЕТ все текущие данные
     * (архив, рекорды, план, свои упражнения, черновик тренировки).
     */
    fun importBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("Не удалось открыть файл")

                BackupManager.importJson(database, json, context.filesDir)

                // Полная перезагрузка всего состояния из восстановленной БД
                _restTimerStart.value = null
                setState(CurrentWorkoutState())
                loadArchiveFromDatabase()
                loadRecordsFromDatabase()
                loadCustomExercises()
                loadPlannedWorkouts()
                loadMyWorkouts()
                loadBodyMetrics()
                loadUserProfile()

                onResult(true, "Данные восстановлены")
            } catch (e: IllegalArgumentException) {
                onResult(false, e.message ?: "Неверный формат файла")
            } catch (e: Exception) {
                onResult(false, "Ошибка восстановления: ${e.message}")
            }
        }
    }
}