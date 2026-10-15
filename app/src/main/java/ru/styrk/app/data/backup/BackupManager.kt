package ru.styrk.app.data.backup

import ru.styrk.app.data.local.database.WorkoutDatabase
import ru.styrk.app.data.local.entity.CompletedWorkoutEntity
import ru.styrk.app.data.local.entity.CompletedWorkoutExerciseEntity
import ru.styrk.app.data.local.entity.CompletedWorkoutSetEntity
import ru.styrk.app.data.local.entity.CustomExerciseEntity
import ru.styrk.app.data.local.entity.ExerciseHistoryEntity
import ru.styrk.app.data.local.entity.PlannedExerciseEntity
import ru.styrk.app.data.local.entity.PlannedWorkoutEntity
import ru.styrk.app.data.local.entity.BodyMetricEntity
import ru.styrk.app.data.local.entity.MyWorkoutEntity
import ru.styrk.app.data.local.entity.MyWorkoutExerciseEntity
import ru.styrk.app.data.local.entity.UserProfileEntity
import android.util.Base64
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.styrk.app.ui.common.ExercisePhoto
import ru.styrk.app.data.model.ExerciseRenames

/**
 * Экспорт/импорт всех данных приложения в JSON.
 *
 * ПРАВИЛА ВЕРСИОНИРОВАНИЯ ФОРМАТА:
 *  - formatVersion 7: + описание и снимок у своего упражнения
 *  - formatVersion 6: + свои тренировки-образцы (myWorkouts)
 *  - formatVersion 1: исходный формат (вес × повторы)
 *  - formatVersion 5: + фото профиля (Base64, сжатое ~512px JPEG)
 *  - formatVersion 4: + профиль (имя, рост, вес; фото не переносится)
 *  - formatVersion 3: + журнал тела (bodyMetrics)
 *  - formatVersion 2: + measureType у упражнений/своих упражнений,
 *    + seconds/distanceKm/load/speed у подходов,
 *    + measureType/bestSeconds/bestDistanceKm у рекордов
 *  - Парсер КАЖДОЙ старой версии остаётся навсегда — старые бэкапы
 *    обязаны восстанавливаться всегда.
 *  - Бэкап более новой версии, чем знает приложение, не импортируем.
 *  - ВЫГРУЖАЕМ под наименьшим номером, при котором ничего не теряется:
 *    файл без своих тренировок объявляется как v5 и открывается
 *    на старых установках. Объявлять всегда новейшую версию значило бы
 *    без нужды ломать перенос тем, кому новый раздел не нужен.
 */
object BackupManager {

    const val FORMAT_VERSION = 7

    // Идентификатор приложения в файле бэкапа. STYRK — текущий, VKA_SPORT —
    // историческое имя до ребрендинга (импорт принимает оба).
    private const val APP_ID = "STYRK"
    private const val LEGACY_APP_ID = "VKA_SPORT"

    // ==================== ЭКСПОРТ (всегда в новейшем формате) ====================

    suspend fun exportJson(db: WorkoutDatabase): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", APP_ID)

        // --- свои тренировки (образцы) ---
        // Собираются первыми, потому что от них зависит объявляемый
        // номер формата — см. ниже.
        //
        // Ни дат, ни весов, ни повторов: образец — это то, что человек
        // собирается повторять, а не то, что было. Форма записи ровно
        // та же, что уходит на сервер и лежит в бэкапе браузера, —
        // иначе одно и то же пришлось бы читать двумя способами.
        val myArr = JSONArray()
        db.myWorkoutDao().getAll().forEach { m ->
            val mObj = JSONObject()
            mObj.put("title", m.title)
            mObj.put("muscleGroup", m.muscleGroup)
            val mExArr = JSONArray()
            db.myWorkoutDao().getExercises(m.id).forEach { mExArr.put(it.exerciseName) }
            mObj.put("exercises", mExArr)
            myArr.put(mObj)
        }
        if (myArr.length() > 0) root.put("myWorkouts", myArr)

        // Номер формата — не «наш самый новый», а наименьший, при котором
        // файл читается без потерь.
        //
        // Разница видна на человеке со старой версией приложения или
        // с не обновлённым веб-дневником. Если своих тренировок нет,
        // терять в этом файле нечего — и незачем отказывать в переносе
        // только потому, что раздел появился в природе. Если образцы
        // есть, отказ правильный: v5 их не знает и молча выбросит,
        // а человек узнает о пропаже много позже и не свяжет с переносом.
        root.put("exportedAt", System.currentTimeMillis())

        // --- завершённые тренировки (с вложенными упражнениями и подходами) ---
        val workoutsArr = JSONArray()
        db.workoutHistoryDao().getAllWorkouts().forEach { w ->
            val wObj = JSONObject()
            wObj.put("date", w.date)
            w.duration?.let { wObj.put("duration", it) }
            w.athleteWeight?.let { wObj.put("athleteWeight", it.toDouble()) }
            wObj.put("muscleGroup", w.muscleGroup)
            w.notes?.let { wObj.put("notes", it) }

            val exArr = JSONArray()
            db.completedWorkoutExerciseDao().getByWorkout(w.id).forEach { ex ->
                val exObj = JSONObject()
                exObj.put("name", ex.exerciseName)
                ex.muscleGroup?.let { exObj.put("muscleGroup", it) }
                ex.measureType?.let { exObj.put("measureType", it) }
                exArr.put(exObj)
            }
            wObj.put("exercises", exArr)

            val setArr = JSONArray()
            db.completedWorkoutSetDao().getByWorkout(w.id).forEach { s ->
                val sObj = JSONObject()
                sObj.put("exerciseName", s.exerciseName)
                sObj.put("weight", s.weight.toDouble())
                sObj.put("reps", s.reps)
                s.seconds?.let { sObj.put("seconds", it) }
                s.distanceKm?.let { sObj.put("distanceKm", it.toDouble()) }
                s.load?.let { sObj.put("load", it.toDouble()) }
                s.speed?.let { sObj.put("speed", it.toDouble()) }
                setArr.put(sObj)
            }
            wObj.put("sets", setArr)

            workoutsArr.put(wObj)
        }
        root.put("workouts", workoutsArr)

        // --- рекорды ---
        val recArr = JSONArray()
        db.exerciseHistoryDao().getAll().forEach { r ->
            val rObj = JSONObject()
            rObj.put("exerciseName", r.exerciseName)
            rObj.put("maxWeight", r.maxWeight.toDouble())
            rObj.put("maxWeightReps", r.maxWeightReps)
            rObj.put("maxReps", r.maxReps)
            rObj.put("bestVolume", r.bestVolume.toDouble())
            r.recordDate?.let { rObj.put("recordDate", it) }
            r.athleteWeight?.let { rObj.put("athleteWeight", it.toDouble()) }
            r.bestVolumeWeight?.let { rObj.put("bestVolumeWeight", it.toDouble()) }
            r.bestVolumeReps?.let { rObj.put("bestVolumeReps", it) }
            r.measureType?.let { rObj.put("measureType", it) }
            r.bestSeconds?.let { rObj.put("bestSeconds", it) }
            r.bestDistanceKm?.let { rObj.put("bestDistanceKm", it.toDouble()) }
            recArr.put(rObj)
        }
        root.put("records", recArr)

        // --- запланированные тренировки ---
        val plannedArr = JSONArray()
        db.plannedWorkoutDao().getAll().forEach { p ->
            val pObj = JSONObject()
            pObj.put("date", p.date)
            pObj.put("hour", p.hour)
            pObj.put("minute", p.minute)
            pObj.put("muscleGroup", p.muscleGroup)
            val pExArr = JSONArray()
            db.plannedWorkoutDao().getExercises(p.id).forEach { pExArr.put(it.exerciseName) }
            pObj.put("exercises", pExArr)
            plannedArr.put(pObj)
        }
        root.put("planned", plannedArr)

        // --- свои упражнения ---
        val customArr = JSONArray()
        var customHasExtras = false
        db.customExerciseDao().getAll().forEach { c ->
            val cObj = JSONObject()
            cObj.put("name", c.name)
            cObj.put("muscleGroup", c.muscleGroup)
            c.measureType?.let { cObj.put("measureType", it) }
            c.description?.takeIf { it.isNotBlank() }?.let {
                cObj.put("description", it); customHasExtras = true
            }
            // Снимок кладём содержимым: путь на чужом устройстве
            // не значит ничего.
            ExercisePhoto.encode(c.photoPath)?.let {
                cObj.put("photo", it); customHasExtras = true
            }
            customArr.put(cObj)
        }
        root.put("customExercises", customArr)

        // ── Номер формата пишется здесь, а не в шапке ──
        //
        // Он зависит от того, что в файле оказалось, — а это выясняется
        // только когда всё собрано. Порядок ключей в JSON значения
        // не имеет, читающая сторона ищет их по имени.
        //
        // Правило прежнее: наименьший номер, при котором файл читается
        // без потерь. Объявлять новейшую версию всегда значило бы
        // без нужды ломать перенос тем, кому новые поля не нужны.
        root.put("formatVersion", when {
            customHasExtras -> FORMAT_VERSION      // 7: есть описание или снимок
            myArr.length() > 0 -> 6                // есть свои тренировки
            else -> 5
        })

        // --- журнал тела ---
        val bodyArr = JSONArray()
        db.bodyMetricDao().getAll().forEach { m ->
            val mObj = JSONObject()
            mObj.put("date", m.date)
            m.weight?.let { mObj.put("weight", it.toDouble()) }
            m.chest?.let { mObj.put("chest", it.toDouble()) }
            m.waist?.let { mObj.put("waist", it.toDouble()) }
            m.hips?.let { mObj.put("hips", it.toDouble()) }
            m.biceps?.let { mObj.put("biceps", it.toDouble()) }
            m.forearm?.let { mObj.put("forearm", it.toDouble()) }
            m.thigh?.let { mObj.put("thigh", it.toDouble()) }
            m.calf?.let { mObj.put("calf", it.toDouble()) }
            m.neck?.let { mObj.put("neck", it.toDouble()) }
            m.shoulders?.let { mObj.put("shoulders", it.toDouble()) }
            bodyArr.put(mObj)
        }
        root.put("bodyMetrics", bodyArr)

        // --- профиль (без фото — путь привязан к устройству) ---
        db.userProfileDao().get()?.let { p ->
            val pObj = JSONObject()
            p.name?.let { pObj.put("name", it) }
            p.heightCm?.let { pObj.put("heightCm", it.toDouble()) }
            p.weightKg?.let { pObj.put("weightKg", it.toDouble()) }
            // v5: фото профиля переносится внутри бэкапа (Base64).
            // Файл уже сжат при выборе (~512px JPEG), поэтому строка небольшая.
            p.photoPath?.let { path ->
                runCatching {
                    val f = File(path)
                    if (f.exists() && f.length() < 3_000_000) {
                        pObj.put("photoBase64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                    }
                }
            }
            root.put("profile", pObj)
        }

        root.toString(2)
    }

    // ==================== ИМПОРТ ====================

    suspend fun importJson(db: WorkoutDatabase, json: String, filesDir: File? = null) = withContext(Dispatchers.IO) {
        // Бэкап мог быть сделан до переименования упражнений — приводим
        // имена к нынешним, иначе у упражнения стало бы две истории.
        val root = try {
            JSONObject(ExerciseRenames.applyToJson(json))
        } catch (_: Exception) {
            throw IllegalArgumentException("Файл не является бэкапом STYRK")
        }

        // Принимаем и новый идентификатор STYRK, и старый VKA_SPORT —
        // бэкапы, сделанные до переименования, обязаны открываться.
        val appId = root.optString("app")
        if (appId != APP_ID && appId != LEGACY_APP_ID) {
            throw IllegalArgumentException("Файл не является бэкапом STYRK")
        }

        when (val v = root.optInt("formatVersion", -1)) {
            1 -> importV1(db, root)
            2 -> importV2(db, root)
            3 -> importV3(db, root)
            4 -> importV4(db, root)
            5 -> importV5(db, root, filesDir)
            6 -> importV6(db, root, filesDir)
            7 -> importV7(db, root, filesDir)
            else -> throw IllegalArgumentException(
                "Бэкап формата версии $v — эта версия приложения его не знает. Обновите приложение."
            )
        }
    }

    /**
     * Парсер формата v7 (текущий): всё из v6 + описание и снимок
     * у своего упражнения.
     *
     * v6-часть переиспользуется целиком, а свои упражнения к этому
     * моменту уже разложены ею (через v2). Поэтому здесь не вставка,
     * а ДОПОЛНЕНИЕ уже существующих записей: ищем по названию и группе
     * и дописываем два поля.
     *
     * Искать по названию можно смело: имя своего упражнения уникально
     * внутри группы — это проверяется при создании.
     */
    private suspend fun importV7(db: WorkoutDatabase, root: JSONObject, filesDir: File?) {
        importV6(db, root, filesDir)

        val arr = root.optJSONArray("customExercises") ?: JSONArray()
        val existing = db.customExerciseDao().getAll()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = (o.opt("name") as? String)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val group = (o.opt("muscleGroup") as? String).orEmpty()
            val row = existing.firstOrNull { it.name == name && it.muscleGroup == group }
                ?: continue

            val desc = (o.opt("description") as? String)?.takeIf { it.isNotBlank() }
            val photo = filesDir?.let { dir ->
                (o.opt("photo") as? String)?.let { ExercisePhoto.decode(dir, it) }
            }
            if (desc == null && photo == null) continue
            db.customExerciseDao().setDetails(row.id, desc, photo, System.currentTimeMillis())
        }
    }

    /**
     * Парсер формата v6. ЗАМОРОЖЕН. Всё из v5 + свои тренировки-образцы.
     *
     * v5-часть переиспользуется целиком — она читает только свои поля,
     * а очистка таблиц уже произошла внутри неё (importV2). Значит
     * my_workouts к этому моменту пуста и дублей не будет.
     */
    private suspend fun importV6(db: WorkoutDatabase, root: JSONObject, filesDir: File?) {
        importV5(db, root, filesDir)

        val myArr = root.optJSONArray("myWorkouts") ?: JSONArray()
        for (i in 0 until myArr.length()) {
            val mObj = myArr.optJSONObject(i) ?: continue
            // Типы проверяются, а не приводятся. Приведение сделало бы
            // из числа 1 упражнение «1», а из объекта —
            // «[object Object]»: человек увидел бы их в наборе и не
            // понял, откуда они взялись. Так же поступает и браузерная
            // версия — расхождение здесь означало бы, что один и тот же
            // испорченный файл на двух устройствах читается по-разному.
            val title = (mObj.opt("title") as? String)?.takeIf { it.isNotBlank() }
                ?: "Без названия"
            val newId = db.myWorkoutDao().insert(
                MyWorkoutEntity(
                    title = title,
                    muscleGroup = mObj.opt("muscleGroup") as? String ?: ""
                )
            )
            val exArr = mObj.optJSONArray("exercises") ?: JSONArray()
            var pos = 0
            for (j in 0 until exArr.length()) {
                val name = exArr.opt(j) as? String ?: continue
                if (name.isBlank()) continue
                db.myWorkoutDao().insertExercise(
                    MyWorkoutExerciseEntity(
                        myWorkoutId = newId,
                        exerciseName = name,
                        // Порядок задаёт номер, а не очередь вставки:
                        // первым в образце стоит самое тяжёлое движение,
                        // и менять это местами нельзя.
                        //
                        // Счётчик свой, а не j: пропущенная строка
                        // оставила бы в нумерации дыру, и после первого
                        // же испорченного файла позиции перестали бы
                        // идти подряд.
                        position = pos
                    )
                )
                pos++
            }
        }
    }

    /**
     * Парсер формата v5. ЗАМОРОЖЕН. Всё из v4 + фото профиля: оно
     * раскодируется в файл во внутренней памяти приложения, путь
     * записывается в профиль (пути с чужого устройства не переносимы).
     */
    private suspend fun importV5(db: WorkoutDatabase, root: JSONObject, filesDir: File?) {
        importV4(db, root)
        val p = root.optJSONObject("profile") ?: return
        val b64 = if (p.has("photoBase64")) p.getString("photoBase64") else return
        val dir = filesDir ?: return
        runCatching {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val file = File(dir, "profile_photo_restored_" + System.currentTimeMillis() + ".jpg")
            file.writeBytes(bytes)
            val cur = db.userProfileDao().get()
            db.userProfileDao().upsert(
                UserProfileEntity(
                    id = 1,
                    name = cur?.name,
                    photoPath = file.absolutePath,
                    heightCm = cur?.heightCm,
                    weightKg = cur?.weightKg
                )
            )
        }
    }

    /** Парсер формата v4. ЗАМОРОЖЕН. */
    private suspend fun importV4(db: WorkoutDatabase, root: JSONObject) {
        importV3(db, root)
        root.optJSONObject("profile")?.let { p ->
            db.userProfileDao().upsert(
                UserProfileEntity(
                    id = 1,
                    name = if (p.has("name")) p.getString("name") else null,
                    photoPath = null,
                    heightCm = if (p.has("heightCm")) p.getDouble("heightCm").toFloat() else null,
                    weightKg = if (p.has("weightKg")) p.getDouble("weightKg").toFloat() else null
                )
            )
        }
    }

    /**
     * Парсер формата v3. ЗАМОРОЖЕН. Всё из v2 + журнал тела: v2-часть
     * читает только свои поля, bodyMetrics докидываются отдельно
     * (clearAllTables уже произошёл внутри v2).
     */
    private suspend fun importV3(db: WorkoutDatabase, root: JSONObject) {
        importV2(db, root)

        val bodyArr = root.optJSONArray("bodyMetrics") ?: JSONArray()
        for (i in 0 until bodyArr.length()) {
            val mObj = bodyArr.getJSONObject(i)
            db.bodyMetricDao().insert(
                BodyMetricEntity(
                    date = mObj.getLong("date"),
                    weight = if (mObj.has("weight")) mObj.getDouble("weight").toFloat() else null,
                    chest = if (mObj.has("chest")) mObj.getDouble("chest").toFloat() else null,
                    waist = if (mObj.has("waist")) mObj.getDouble("waist").toFloat() else null,
                    hips = if (mObj.has("hips")) mObj.getDouble("hips").toFloat() else null,
                    biceps = if (mObj.has("biceps")) mObj.getDouble("biceps").toFloat() else null,
                    forearm = if (mObj.has("forearm")) mObj.getDouble("forearm").toFloat() else null,
                    thigh = if (mObj.has("thigh")) mObj.getDouble("thigh").toFloat() else null,
                    calf = if (mObj.has("calf")) mObj.getDouble("calf").toFloat() else null,
                    neck = if (mObj.has("neck")) mObj.getDouble("neck").toFloat() else null,
                    shoulders = if (mObj.has("shoulders")) mObj.getDouble("shoulders").toFloat() else null
                )
            )
        }
    }

    /**
     * Парсер формата v2. ЗАМОРОЖЕН — не редактировать (используется и как
     * основа importV3: читает только свои поля).
     */
    private suspend fun importV2(db: WorkoutDatabase, root: JSONObject) {
        db.clearAllTables()

        val workoutsArr = root.optJSONArray("workouts") ?: JSONArray()
        for (i in 0 until workoutsArr.length()) {
            val wObj = workoutsArr.getJSONObject(i)
            val newId = db.workoutHistoryDao().insertWorkout(
                CompletedWorkoutEntity(
                    date = wObj.getLong("date"),
                    duration = if (wObj.has("duration")) wObj.getLong("duration") else null,
                    athleteWeight = if (wObj.has("athleteWeight")) wObj.getDouble("athleteWeight").toFloat() else null,
                    muscleGroup = wObj.getString("muscleGroup"),
                    notes = if (wObj.has("notes")) wObj.getString("notes") else null
                )
            )
            val exArr = wObj.optJSONArray("exercises") ?: JSONArray()
            for (j in 0 until exArr.length()) {
                val exObj = exArr.getJSONObject(j)
                db.completedWorkoutExerciseDao().insert(
                    CompletedWorkoutExerciseEntity(
                        workoutId = newId,
                        exerciseName = exObj.getString("name"),
                        muscleGroup = if (exObj.has("muscleGroup")) exObj.getString("muscleGroup") else null,
                        measureType = if (exObj.has("measureType")) exObj.getString("measureType") else null
                    )
                )
            }
            val setArr = wObj.optJSONArray("sets") ?: JSONArray()
            for (j in 0 until setArr.length()) {
                val sObj = setArr.getJSONObject(j)
                db.completedWorkoutSetDao().insert(
                    CompletedWorkoutSetEntity(
                        workoutId = newId,
                        exerciseName = sObj.getString("exerciseName"),
                        weight = sObj.getDouble("weight").toFloat(),
                        reps = sObj.getInt("reps"),
                        seconds = if (sObj.has("seconds")) sObj.getInt("seconds") else null,
                        distanceKm = if (sObj.has("distanceKm")) sObj.getDouble("distanceKm").toFloat() else null,
                        load = if (sObj.has("load")) sObj.getDouble("load").toFloat() else null,
                        speed = if (sObj.has("speed")) sObj.getDouble("speed").toFloat() else null
                    )
                )
            }
        }

        val recArr = root.optJSONArray("records") ?: JSONArray()
        for (i in 0 until recArr.length()) {
            val rObj = recArr.getJSONObject(i)
            db.exerciseHistoryDao().save(
                ExerciseHistoryEntity(
                    exerciseName = rObj.getString("exerciseName"),
                    maxWeight = rObj.getDouble("maxWeight").toFloat(),
                    maxWeightReps = rObj.getInt("maxWeightReps"),
                    maxReps = rObj.getInt("maxReps"),
                    bestVolume = rObj.getDouble("bestVolume").toFloat(),
                    recordDate = if (rObj.has("recordDate")) rObj.getLong("recordDate") else null,
                    athleteWeight = if (rObj.has("athleteWeight")) rObj.getDouble("athleteWeight").toFloat() else null,
                    bestVolumeWeight = if (rObj.has("bestVolumeWeight")) rObj.getDouble("bestVolumeWeight").toFloat() else null,
                    bestVolumeReps = if (rObj.has("bestVolumeReps")) rObj.getInt("bestVolumeReps") else null,
                    measureType = if (rObj.has("measureType")) rObj.getString("measureType") else null,
                    bestSeconds = if (rObj.has("bestSeconds")) rObj.getInt("bestSeconds") else null,
                    bestDistanceKm = if (rObj.has("bestDistanceKm")) rObj.getDouble("bestDistanceKm").toFloat() else null
                )
            )
        }

        val plannedArr = root.optJSONArray("planned") ?: JSONArray()
        for (i in 0 until plannedArr.length()) {
            val pObj = plannedArr.getJSONObject(i)
            val newId = db.plannedWorkoutDao().insert(
                PlannedWorkoutEntity(
                    date = pObj.getLong("date"),
                    hour = pObj.getInt("hour"),
                    minute = pObj.getInt("minute"),
                    muscleGroup = pObj.getString("muscleGroup")
                )
            )
            val pExArr = pObj.optJSONArray("exercises") ?: JSONArray()
            for (j in 0 until pExArr.length()) {
                db.plannedWorkoutDao().insertExercise(
                    PlannedExerciseEntity(workoutId = newId, exerciseName = pExArr.getString(j))
                )
            }
        }

        val customArr = root.optJSONArray("customExercises") ?: JSONArray()
        for (i in 0 until customArr.length()) {
            val cObj = customArr.getJSONObject(i)
            db.customExerciseDao().insert(
                CustomExerciseEntity(
                    name = cObj.getString("name"),
                    muscleGroup = cObj.getString("muscleGroup"),
                    measureType = if (cObj.has("measureType")) cObj.getString("measureType") else null
                )
            )
        }
    }

    /** Парсер формата v1. ЗАМОРОЖЕН — не редактировать. */
    private suspend fun importV1(db: WorkoutDatabase, root: JSONObject) {
        // Полная очистка (штатный Room API, все таблицы, включая черновик)
        db.clearAllTables()

        // --- тренировки ---
        val workoutsArr = root.optJSONArray("workouts") ?: JSONArray()
        for (i in 0 until workoutsArr.length()) {
            val wObj = workoutsArr.getJSONObject(i)
            val newId = db.workoutHistoryDao().insertWorkout(
                CompletedWorkoutEntity(
                    date = wObj.getLong("date"),
                    duration = if (wObj.has("duration")) wObj.getLong("duration") else null,
                    athleteWeight = if (wObj.has("athleteWeight")) wObj.getDouble("athleteWeight").toFloat() else null,
                    muscleGroup = wObj.getString("muscleGroup"),
                    notes = if (wObj.has("notes")) wObj.getString("notes") else null
                )
            )
            val exArr = wObj.optJSONArray("exercises") ?: JSONArray()
            for (j in 0 until exArr.length()) {
                val exObj = exArr.getJSONObject(j)
                db.completedWorkoutExerciseDao().insert(
                    CompletedWorkoutExerciseEntity(
                        workoutId = newId,
                        exerciseName = exObj.getString("name"),
                        muscleGroup = if (exObj.has("muscleGroup")) exObj.getString("muscleGroup") else null
                    )
                )
            }
            val setArr = wObj.optJSONArray("sets") ?: JSONArray()
            for (j in 0 until setArr.length()) {
                val sObj = setArr.getJSONObject(j)
                db.completedWorkoutSetDao().insert(
                    CompletedWorkoutSetEntity(
                        workoutId = newId,
                        exerciseName = sObj.getString("exerciseName"),
                        weight = sObj.getDouble("weight").toFloat(),
                        reps = sObj.getInt("reps")
                    )
                )
            }
        }

        // --- рекорды ---
        val recArr = root.optJSONArray("records") ?: JSONArray()
        for (i in 0 until recArr.length()) {
            val rObj = recArr.getJSONObject(i)
            db.exerciseHistoryDao().save(
                ExerciseHistoryEntity(
                    exerciseName = rObj.getString("exerciseName"),
                    maxWeight = rObj.getDouble("maxWeight").toFloat(),
                    maxWeightReps = rObj.getInt("maxWeightReps"),
                    maxReps = rObj.getInt("maxReps"),
                    bestVolume = rObj.getDouble("bestVolume").toFloat(),
                    recordDate = if (rObj.has("recordDate")) rObj.getLong("recordDate") else null,
                    athleteWeight = if (rObj.has("athleteWeight")) rObj.getDouble("athleteWeight").toFloat() else null,
                    bestVolumeWeight = if (rObj.has("bestVolumeWeight")) rObj.getDouble("bestVolumeWeight").toFloat() else null,
                    bestVolumeReps = if (rObj.has("bestVolumeReps")) rObj.getInt("bestVolumeReps") else null
                )
            )
        }

        // --- запланированные ---
        val plannedArr = root.optJSONArray("planned") ?: JSONArray()
        for (i in 0 until plannedArr.length()) {
            val pObj = plannedArr.getJSONObject(i)
            val newId = db.plannedWorkoutDao().insert(
                PlannedWorkoutEntity(
                    date = pObj.getLong("date"),
                    hour = pObj.getInt("hour"),
                    minute = pObj.getInt("minute"),
                    muscleGroup = pObj.getString("muscleGroup")
                )
            )
            val pExArr = pObj.optJSONArray("exercises") ?: JSONArray()
            for (j in 0 until pExArr.length()) {
                db.plannedWorkoutDao().insertExercise(
                    PlannedExerciseEntity(workoutId = newId, exerciseName = pExArr.getString(j))
                )
            }
        }

        // --- свои упражнения ---
        val customArr = root.optJSONArray("customExercises") ?: JSONArray()
        for (i in 0 until customArr.length()) {
            val cObj = customArr.getJSONObject(i)
            db.customExerciseDao().insert(
                CustomExerciseEntity(
                    name = cObj.getString("name"),
                    muscleGroup = cObj.getString("muscleGroup")
                )
            )
        }
    }
}