package ru.styrk.app.ui.training

import ru.styrk.app.ui.common.keyboardGap
import ru.styrk.app.data.model.PairedExercises
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import ru.styrk.app.data.model.ExerciseGuide
import ru.styrk.app.ui.common.ExerciseGuideDialog
import ru.styrk.app.data.model.MeasureType
import kotlin.time.Duration.Companion.seconds
import ru.styrk.app.data.model.MuscleGroup
import ru.styrk.app.ui.common.SetFormat
import ru.styrk.app.data.model.WorkoutExercise
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.TrainingBg
import ru.styrk.app.ui.theme.White
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.style.TextOverflow
import ru.styrk.app.ui.common.CappedFontScale
import ru.styrk.app.ui.common.DATA_FONT_CAP
import androidx.compose.ui.text.style.TextAlign

private fun formatW(weight: Float): String =
    if (weight == weight.toInt().toFloat()) weight.toInt().toString() else "%.1f".format(weight)

private val GreenColor = Color(0xFF4CAF50)
private val RedColor = Color(0xFFE53935)

@Composable
fun TrainingScreen(
    viewModel: TrainingSessionViewModel,
    onAddExerciseToGroup: (MuscleGroup) -> Unit,
    onAddNewGroup: () -> Unit,
    onFinishWorkout: (java.time.LocalDateTime?) -> Unit
) {
    val state by viewModel.state.collectAsState()
    // п4/п5 (v1.8.5): состояние списка нужно и для центрирования карточек,
    // и для перетаскивания — оба считают положение по видимым элементам.
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // Перетаскиваемая сейчас карточка и накопленный сдвиг пальца
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val restTimerStart by viewModel.restTimerStart.collectAsState()
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")

    // Тик раз в секунду для таймера отдыха (только пока этот экран открыт)
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restTimerStart) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1.seconds)
        }
    }

    // completedWorkouts гарантированно отсортирован от новых к старым —
    // firstOrNull() это последняя завершённая тренировка
    val lastWeight = completedWorkouts.firstOrNull()?.athleteWeight
    val weightDiff = if (lastWeight != null && state.athleteWeight != null)
        state.athleteWeight!! - lastWeight
    else null

    // Диалоги подтверждения удаления
    // п1 (v1.8.2): подтверждение времени окончания для долгих тренировок
    var showFinishTimeDialog by remember { mutableStateOf(false) }
    var confirmRemoveGroup by remember { mutableStateOf<MuscleGroup?>(null) }
    // ИСПРАВЛЕНО: храним весь WorkoutExercise (нужен и id для удаления,
    // и name для текста диалога), а не только имя
    var confirmRemoveExercise by remember { mutableStateOf<WorkoutExercise?>(null) }

    val groupedExercises: Map<MuscleGroup, List<WorkoutExercise>> = remember(state.selectedExercises) {
        state.selectedExercises
            .filter { it.muscleGroup != null }
            .groupBy { it.muscleGroup!! }
    }

    // Номер каждой карточки в LazyColumn. Считаем ровно в том порядке,
    // в каком строки добавляются ниже: заголовок группы, её упражнения,
    // кнопка «+». Без этой карты нельзя ни прокрутить к карточке,
    // ни понять, над кем висит палец при перетаскивании.
    val rowOfExercise = remember(groupedExercises) {
        val map = LinkedHashMap<String, Int>()
        var i = 0
        groupedExercises.forEach { (_, list) ->
            i++                       // заголовок группы
            list.forEach { ex -> map[ex.id] = i; i++ }
            i++                       // кнопка «+ упражнение»
        }
        map
    }
    // Обратная карта: по номеру строки — само упражнение и его группа
    val exerciseAtRow = remember(groupedExercises) {
        val map = HashMap<Int, Pair<MuscleGroup, WorkoutExercise>>()
        var i = 0
        groupedExercises.forEach { (g, list) ->
            i++
            list.forEach { ex -> map[i] = g to ex; i++ }
            i++
        }
        map
    }

    /**
     * Подвести карточку к середине экрана.
     *
     * Прокрутки «просто чтобы стало видно» тут мало: карточка оказывалась
     * у самого края. Поэтому сначала прокручиваем к строке, а потом
     * доводим точно — уже зная её настоящую высоту и высоту видимой части.
     */
    suspend fun centerOn(exerciseId: String) {
        val row = rowOfExercise[exerciseId] ?: return
        runCatching {
            listState.animateScrollToItem(row)
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == row } ?: return
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            /* Карточка выше окна — середины у неё быть не может.

               Раньше считалась только середина, и при открытой форме
               получалось вот что: карточка с подходами и полями ввода
               высокая, окно над клавиатурой низкое, «середина» уезжает
               так, что низ карточки — а вместе с ним поля ввода —
               остаётся под клавиатурой. Чем крупнее клавиатура,
               тем хуже.

               Поэтому: помещается — ставим по середине, как раньше;
               не помещается — прижимаем НИЗ карточки к низу окна.
               Внизу поля ввода, и смотреть человек будет туда. */
            val wanted = if (item.size >= viewport) viewport - item.size
                         else ((viewport - item.size) / 2).coerceAtLeast(0)
            listState.animateScrollBy((item.offset - wanted).toFloat())
        }
    }

    /* Карточка с открытой формой выходит на видное место — и делает это
       ЗАНОВО, когда клавиатура доехала.

       Раньше здесь стояла просто задержка в 300 мс, и это была догадка:
       клавиатура выезжает за разное время на разных телефонах, а её
       высота вдобавок меняется на ходу — подсказки слов, смена языка,
       вторая строка цифр. Промахнулись по времени — карточка встала
       не туда и осталась там.

       Теперь поводом служит сама высота клавиатуры. Пока она растёт,
       каждое изменение перезапускает ожидание и предыдущую прокрутку
       отменяет; прокрутка случается один раз — когда высота устоялась.
       Закрыли клавиатуру — сработает ещё раз и вернёт карточку
       на место. */
    var formOpenFor by remember { mutableStateOf<String?>(null) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(formOpenFor, imeBottom, rowOfExercise) {
        val id = formOpenFor ?: return@LaunchedEffect
        if (!rowOfExercise.containsKey(id)) return@LaunchedEffect
        delay(140)
        centerOn(id)
    }

    // п4: новое упражнение само выходит на середину экрана.
    // Сигнал приходит из ViewModel: упражнение выбирается на другом
    // экране, и заметить прибавление «на месте» этот экран не может —
    // при возврате он создаётся заново и ничего не помнит.
    val lastAdded by viewModel.lastAddedExerciseId.collectAsState()
    LaunchedEffect(lastAdded, rowOfExercise) {
        val id = lastAdded ?: return@LaunchedEffect
        if (!rowOfExercise.containsKey(id)) return@LaunchedEffect
        delay(150)                  // ждём, пока список разложится
        centerOn(id)
        viewModel.consumeLastAdded()
    }

    val headerTitle = if (groupedExercises.isNotEmpty())
        groupedExercises.keys.joinToString(", ") { it.title }.uppercase()
    else "ТРЕНИРОВКА"

    Column(modifier = Modifier.fillMaxSize()) {

        // ===== ШАПКА =====
        Column(
            modifier = Modifier.fillMaxWidth().background(Black).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(headerTitle, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            // Слева время начала, справа таймер отдыха и вес — всё
            // в одну строку. При крупном шрифте и таймер, и вес
            // рассыпались в столбик по буквам.
            CappedFontScale(DATA_FONT_CAP) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.isBackdated) "было: ${state.workoutStartTime.format(fmt)}"
                    else "начало: ${state.workoutStartTime.format(fmt)}",
                    color = White.copy(alpha = 0.65f),
                    fontSize = 12.sp, maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))

                // Таймер отдыха НАД весом: сбрасывается после каждой записи подхода
                Column(horizontalAlignment = Alignment.End) {
                    // Таймер отдыха — только у идущей тренировки.
                    //
                    // У записи о прошлом отдыхать не от чего: человек
                    // сидит и вспоминает, а не поднимает. Бегущие цифры
                    // тут не просто лишние — они врут, показывая время
                    // с открытия экрана как время между подходами.
                    if (state.isBackdated) {
                        Text("задним числом", color = Gold, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Spacer(Modifier.height(2.dp))
                    }
                    if (!state.isBackdated) restTimerStart?.let { start ->
                        val restSec = ((nowMs - start) / 1000).coerceAtLeast(0)
                        Text(
                            "⏱ %d:%02d".format(restSec / 60, restSec % 60),
                            color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, softWrap = false
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.athleteWeight?.let { "%.1f".format(it) } ?: "—", color = White,
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, softWrap = false)
                        Text(" кг", color = White, fontSize = 12.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                        if (weightDiff != null) {
                            Spacer(Modifier.width(4.dp))
                            val diffColor = when {
                                weightDiff > 0f -> GreenColor
                                weightDiff < 0f -> RedColor
                                else -> White
                            }
                            val sign = if (weightDiff > 0f) "+" else ""
                            Text("($sign%.1f)".format(weightDiff), color = diffColor,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            }
        }

        // ===== СПИСОК ГРУПП МЫШЦ + УПРАЖНЕНИЙ =====
        LazyColumn(
            state = listState,
            // п10: светло-серый фон, чтобы белые карточки упражнений
            // не сливались с фоном страницы
            //
            // imePadding: окно приложения растянуто на весь экран
            // (enableEdgeToEdge в MainActivity), и при этом «adjustResize»
            // из манифеста больше не ужимает его само — клавиатура
            // приходит как отступ, и применить его должны мы. Без этой
            // строки список считает, что видно его целиком, а нижняя
            // треть на самом деле закрыта клавиатурой: туда и уезжали
            // поля ввода.
            //
            // keyboardGap, а не imePadding: под списком стоит кнопка
            // «Завершить тренировку», и imePadding отступал на всю
            // клавиатуру ещё и поверх неё — отсюда была полоса над
            // клавиатурой ровно в высоту этой кнопки (см. KeyboardGap.kt).
            modifier = Modifier.weight(1f).background(TrainingBg).keyboardGap(),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            groupedExercises.forEach { (group, exercises) ->

                // Заголовок группы мышц с кнопкой удаления группы (✕)
                item(key = "group_${group.name}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 14.dp)
                            .background(Black, RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            group.title.uppercase(),
                            color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .size(26.dp)
                                .clickable { confirmRemoveGroup = group },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Упражнения этой группы — ключ по УНИКАЛЬНОМУ id экземпляра
                // (не по имени!), иначе повторное добавление одноимённого
                // упражнения крашит LazyColumn на дублирующихся ключах
                items(exercises, key = { it.id }) { exercise ->
                    // Двигать можно только то, куда ещё ничего не записано:
                    // порядок уже выполненных упражнений — это факт.
                    val movable = exercise.sets.isEmpty()
                    val isDragging = dragId == exercise.id

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                // чуть увеличиваем — видно, что карточка «взята»
                                val k = if (isDragging) 1.02f else 1f
                                scaleX = k; scaleY = k
                            }
                            .then(
                                if (!movable) Modifier
                                else Modifier.pointerInput(exercise.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragId = exercise.id
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = { dragId = null; dragOffset = 0f },
                                        onDragCancel = { dragId = null; dragOffset = 0f }
                                    ) { change, delta ->
                                        change.consume()
                                        dragOffset += delta.y

                                        val myRow = rowOfExercise[exercise.id]
                                            ?: return@detectDragGesturesAfterLongPress
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        val me = visible.firstOrNull { it.index == myRow }
                                            ?: return@detectDragGesturesAfterLongPress

                                        // Упор сверху: карточку не пускаем выше нижнего
                                        // края последней заполненной в этой группе.
                                        // Без упора она уезжала за неё, местами не
                                        // менялась и просто прыгала обратно при отпускании.
                                        val blockerRow = exerciseAtRow.entries
                                            .filter { (_, pair) ->
                                                pair.first == exercise.muscleGroup &&
                                                        pair.second.sets.isNotEmpty()
                                            }
                                            .maxOfOrNull { it.key }
                                        if (blockerRow != null) {
                                            visible.firstOrNull { it.index == blockerRow }?.let { blocker ->
                                                val floor = blocker.offset + blocker.size
                                                val minOffset = floor - me.offset.toFloat()
                                                if (dragOffset < minOffset) dragOffset = minOffset
                                            }
                                        }

                                        // Середина карточки в её текущем положении
                                        val middle = me.offset + dragOffset + me.size / 2f

                                        // Ниже какой строки нельзя подниматься: сразу за
                                        // последним упражнением группы, где уже есть подходы.
                                        // Выполненные упражнения — это уже история тренировки,
                                        // перепрыгивать через них нельзя.
                                        val lastFilledRow = exerciseAtRow.entries
                                            .filter { (_, pair) ->
                                                pair.first == exercise.muscleGroup &&
                                                        pair.second.sets.isNotEmpty()
                                            }
                                            .maxOfOrNull { it.key } ?: -1

                                        val target = visible.firstOrNull { other ->
                                            other.index != myRow &&
                                                    other.index > lastFilledRow &&
                                                    middle >= other.offset &&
                                                    middle <= other.offset + other.size &&
                                                    exerciseAtRow[other.index]?.let { (g, ex) ->
                                                        // меняться местами можно только с упражнением
                                                        // той же группы и тоже без подходов
                                                        g == exercise.muscleGroup && ex.sets.isEmpty()
                                                    } == true
                                        }

                                        if (target != null) {
                                            val targetId = exerciseAtRow[target.index]?.second?.id
                                            if (targetId != null) {
                                                viewModel.swapExercises(exercise.id, targetId)
                                                // Карточка уехала на место соседа — вычитаем
                                                // эту разницу, чтобы она осталась под пальцем
                                                dragOffset += (me.offset - target.offset)
                                            }
                                        }
                                    }
                                }
                            )
                    ) {
                        InlineExerciseBlock(
                            exercise = exercise,
                            viewModel = viewModel,
                            onRemove = { confirmRemoveExercise = exercise },
                            onFormOpened = { formOpenFor = exercise.id }
                        )
                    }
                }

                // Кнопка "+" добавить ещё упражнение в эту же группу
                item(key = "add_ex_${group.name}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp)
                            .heightIn(min = 46.dp)
                            .background(DarkGray, RoundedCornerShape(12.dp))
                            .clickable { onAddExerciseToGroup(group) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = White, fontSize = 20.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            // Кнопка "+" добавить ещё одну группу мышц
            item(key = "add_group") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp)
                        .heightIn(min = 48.dp)
                        .background(Black, RoundedCornerShape(12.dp))
                        .clickable { onAddNewGroup() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("+", color = White, fontSize = 20.sp, fontWeight = FontWeight.Light)
                        Text("Добавить группу мышц", color = White, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // ===== КНОПКА ЗАВЕРШИТЬ =====
        Column(
            modifier = Modifier.fillMaxWidth().background(White).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn, а не height: при крупном шрифте надпись
                    // «Завершить тренировку» переносилась на вторую строку
                    // и обрезалась по нижнему краю. Та же беда, что была
                    // на главном экране, и лечится так же.
                    .heightIn(min = 50.dp)
                    .background(SoftGray, RoundedCornerShape(14.dp))
                    .clickable {
                        // У записи задним числом ни о чём спрашивать не надо:
                        // и время, и длительность человек назвал в начале.
                        // Вопрос «не забыли ли вы нажать Завершить» тут
                        // бессмыслен — тренировка кончилась дни назад.
                        if (state.isBackdated) {
                            onFinishWorkout(null)
                        } else {
                            // Если тренировка идёт дольше 3 часов — вероятно, забыли
                            // нажать «Завершить». Спрашиваем реальное время окончания.
                            val hours = java.time.Duration.between(
                                state.workoutStartTime, java.time.LocalDateTime.now()
                            ).toHours()
                            if (hours >= 3) showFinishTimeDialog = true else onFinishWorkout(null)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (state.isBackdated) "СОХРАНИТЬ ТРЕНИРОВКУ" else "ЗАВЕРШИТЬ ТРЕНИРОВКУ",
                    color = Black, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
            }
        }
    }

    // ===== п1: ПОДТВЕРЖДЕНИЕ ВРЕМЕНИ ОКОНЧАНИЯ (тренировка > 3 часов) =====
    if (showFinishTimeDialog) {
        val start = state.workoutStartTime
        val now = java.time.LocalDateTime.now()
        // Даты на выбор: от дня начала до сегодня (если тренировка перешла
        // за полночь). Если всё в один день — выбора даты нет, только время.
        val dayOptions = remember(start, now) {
            val days = java.time.temporal.ChronoUnit.DAYS
                .between(start.toLocalDate(), now.toLocalDate()).toInt().coerceIn(0, 7)
            (0..days).map { start.toLocalDate().plusDays(it.toLong()) }
        }
        var selDate by remember { mutableStateOf(now.toLocalDate()) }
        var hourStr by remember { mutableStateOf("%02d".format(now.hour)) }
        var minStr by remember { mutableStateOf("%02d".format(now.minute)) }
        val dFmt = remember { java.time.format.DateTimeFormatter.ofPattern("dd.MM") }
        val dtFmt = remember { java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm") }

        val chosen = runCatching {
            java.time.LocalDateTime.of(
                selDate,
                java.time.LocalTime.of(
                    hourStr.toInt().coerceIn(0, 23),
                    minStr.toInt().coerceIn(0, 59)
                )
            )
        }.getOrNull()
        val valid = chosen != null && !chosen.isBefore(start)

        AlertDialog(
            onDismissRequest = { showFinishTimeDialog = false },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Когда закончилась тренировка?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Тренировка идёт уже больше 3 часов. Проверьте время окончания — " +
                                "возможно, вы забыли нажать «Завершить».",
                        fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Начало: " + start.format(dtFmt), color = Black, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))

                    if (dayOptions.size > 1) {
                        Text("Дата окончания", color = DarkGray, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dayOptions.forEach { d ->
                                val sel = d == selDate
                                Box(
                                    Modifier
                                        .background(
                                            if (sel) Black else SoftGray,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selDate = d }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        d.format(dFmt),
                                        color = if (sel) White else Black,
                                        fontSize = 13.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text("Время окончания", color = DarkGray, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hourStr, onValueChange = { hourStr = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("часы", color = DarkGray) },
                            modifier = Modifier.width(100.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Black, unfocusedBorderColor = SoftGray,
                                cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                            )
                        )
                        Text(" : ", color = Black, fontSize = 18.sp)
                        OutlinedTextField(
                            value = minStr, onValueChange = { minStr = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("минуты", color = DarkGray) },
                            modifier = Modifier.width(110.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Black, unfocusedBorderColor = SoftGray,
                                cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                            )
                        )
                    }
                    if (!valid) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Окончание не может быть раньше начала тренировки",
                            color = RedColor, fontSize = 12.sp
                        )
                    } else {
                        val mins = java.time.Duration.between(start, chosen).toMinutes()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Длительность: %d ч %02d мин".format(mins / 60, mins % 60),
                            color = DarkGray, fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        showFinishTimeDialog = false
                        onFinishWorkout(chosen)
                    }
                ) { Text("Завершить", color = if (valid) Black else DarkGray, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishTimeDialog = false }) { Text("Отмена", color = DarkGray) }
            }
        )
    }

    // ===== ДИАЛОГ: УДАЛИТЬ ГРУППУ МЫШЦ =====
    confirmRemoveGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { confirmRemoveGroup = null },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Убрать группу «${group.title}»?", fontWeight = FontWeight.Bold) },
            text = { Text("Все упражнения и подходы этой группы в текущей тренировке будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMuscleGroup(group)
                    confirmRemoveGroup = null
                }) { Text("Убрать", color = RedColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveGroup = null }) { Text("Отмена", color = DarkGray) }
            }
        )
    }

    // ===== ДИАЛОГ: УДАЛИТЬ УПРАЖНЕНИЕ =====
    confirmRemoveExercise?.let { ex ->
        AlertDialog(
            onDismissRequest = { confirmRemoveExercise = null },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Убрать упражнение?", fontWeight = FontWeight.Bold) },
            text = { Text("«${ex.name}» и все его подходы будут удалены из текущей тренировки.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeExercise(ex.id)
                    confirmRemoveExercise = null
                }) { Text("Убрать", color = RedColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveExercise = null }) { Text("Отмена", color = DarkGray) }
            }
        )
    }
}

@Composable
private fun InlineExerciseBlock(
    exercise: WorkoutExercise,
    viewModel: TrainingSessionViewModel,
    onRemove: () -> Unit,
    // Форма ввода открылась — экран должен подвести карточку к середине
    onFormOpened: () -> Unit = {}
) {
    // addingSet=true — форма добавления НОВОГО подхода в конец
    // editingIndex!=null — форма редактирования УЖЕ введённого подхода
    // п6 (v1.8.1): показ гифки+техники по тапу на название упражнения
    // Свои упражнения — чтобы карточка показала описание и снимок,
    // которые человек написал сам. Берём у модели прямо здесь: она уже
    // передана в этот блок, и тащить список отдельным параметром через
    // весь экран было бы лишним.
    val customExercises by viewModel.customExercises.collectAsState()
    var showGuide by remember(exercise.id) { mutableStateOf(false) }
    var addingSet by remember(exercise.id) { mutableStateOf(false) }
    var editingIndex by remember(exercise.id) { mutableStateOf<Int?>(null) }
    var weightInput by remember(exercise.id) { mutableStateOf("") }
    var repsInput by remember(exercise.id) { mutableStateOf("") }
    // Поля для новых типов упражнений (модель v2)
    var minInput by remember(exercise.id) { mutableStateOf("") }
    var secInput by remember(exercise.id) { mutableStateOf("") }
    var kmInput by remember(exercise.id) { mutableStateOf("") }
    var loadInput by remember(exercise.id) { mutableStateOf("") }
    var speedInput by remember(exercise.id) { mutableStateOf("") }

    fun clearInputs() {
        weightInput = ""; repsInput = ""; minInput = ""; secInput = ""
        kmInput = ""; loadInput = ""; speedInput = ""
    }

    // Все подходы прошлой тренировки для этого упражнения (по
    // ИМЕНИ — это общая история упражнения, а не конкретной
    // карточки) — нужны для "превью" ещё не введённых сегодня
    // подходов и для подсказки в полях ввода.
    //
    // ИСПРАВЛЕНО (R184): ключ — ещё и сам журнал. Раньше список
    // запоминался один раз по имени упражнения. Если приложение
    // поднималось с нуля посреди тренировки (вход через уведомление),
    // карточка рисовалась раньше, чем журнал успевал загрузиться из
    // базы, — и запоминала пустоту. Прошлые подходы появлялись, только
    // когда карточка пересоздавалась при переходе по вкладкам.
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val previousSets = remember(exercise.name, completedWorkouts) {
        val list = mutableListOf<ru.styrk.app.data.model.WorkoutSet>()
        var i = 0
        while (true) {
            val s = viewModel.getPreviousSet(exercise.name, i) ?: break
            list.add(s)
            i++
        }
        list
    }

    // Цель из плана, если тренировку начали из календаря.
    // Живёт только пока идёт тренировка: в журнал пишется сделанное,
    // а не задуманное. plan — подходы, которые расписал тренер.
    val activeTargets by viewModel.activeTargets.collectAsState()
    /* План по подходам. Если тренер расписал каждый подход — берём как
       есть. Если задал только общую цель «3 × 10 × 40 кг» — разворачиваем
       её в три одинаковых подхода: иначе назначенный вес не был виден
       нигде, кроме полоски «ЦЕЛЬ» наверху, и в поля не подставлялся. */
    val planSets = activeTargets[exercise.name]?.let { t ->
        t.plan.ifEmpty {
            val counted = exercise.measureType == MeasureType.WEIGHT_REPS ||
                    exercise.measureType == MeasureType.REPS
            val reps = t.reps
            if (counted && reps != null && reps > 0)
                List((t.sets ?: 1).coerceIn(1, 20)) {
                    ru.styrk.app.data.model.WorkoutSet(weight = t.weight ?: 0f, reps = reps)
                }
            else emptyList()
        }
    }.orEmpty()

    /* ПОДСКАЗКА В ПОЛЯХ: что было в этом подходе в прошлый раз.

       Полупрозрачным шрифтом, прямо в пустых полях. Нажали галочку,
       ничего не вводя, — подход запишется таким, как подсказано.
       Начали вводить — поле и так пустое, подсказка просто уходит.

       Откуда берётся, по порядку:
         1. план тренера на этот подход;
         2. для «вес × повторы» — САМЫЙ ТЯЖЁЛЫЙ подход из сегодняшних
            и прошлой тренировки, при равном весе — с бо́льшими повторами
            (R184). Раньше брался тот же по счёту подход прошлого раза:
            сделал сегодня 15 × 10, а третьему подходу подсказывало
            прошлые 12,5 × 12 — шаг назад;
         3. тот же подход прошлой тренировки;
         4. последний подход сегодня — если сегодня делаете больше
            подходов, чем в прошлый раз;
         5. последний подход прошлой тренировки.
       Пункты 3–5 остались для всего, где веса нет: свой вес, время,
       дистанция, кардио.
       При правке уже записанного подхода подсказки нет: там в полях
       стоят его собственные числа. */
    val hint: ru.styrk.app.data.model.WorkoutSet? =
        if (addingSet && editingIndex == null) {
            val i = exercise.sets.size
            planSets.getOrNull(i)
                ?: heaviestSet(exercise.measureType, exercise.sets + previousSets)
                ?: previousSets.getOrNull(i)
                ?: exercise.sets.lastOrNull()
                ?: previousSets.lastOrNull()
        } else null

    val focusManager = LocalFocusManager.current
    val repsFocusRequester = remember(exercise.id) { FocusRequester() }
    // п4 (v1.8.5): при открытии формы сразу ставим курсор в первое поле.
    // Побочный эффект здесь и есть цель: список сам прокручивается к
    // полю ввода и открывается клавиатура — искать, куда вводить,
    // больше не нужно.
    val firstFieldRequester = remember(exercise.id) { FocusRequester() }

    fun openAddForm() {
        editingIndex = null
        clearInputs()
        addingSet = true
        onFormOpened()
    }

    // Небольшая задержка нужна: поле появляется в дереве не мгновенно,
    // и requestFocus без неё промахивается. runCatching — на случай,
    // если форму успели закрыть до срабатывания.
    LaunchedEffect(addingSet, editingIndex) {
        if (addingSet || editingIndex != null) {
            delay(80)
            runCatching { firstFieldRequester.requestFocus() }
        }
    }

    fun openEditForm(index: Int, set: ru.styrk.app.data.model.WorkoutSet) {
        addingSet = false
        editingIndex = index
        onFormOpened()
        weightInput = if (set.weight > 0f) formatW(set.weight) else ""
        repsInput = if (set.reps > 0) set.reps.toString() else ""
        minInput = set.seconds?.let { (it / 60).toString() } ?: ""
        secInput = set.seconds?.let { (it % 60).toString() } ?: ""
        kmInput = set.distanceKm?.let { formatW(it) } ?: ""
        loadInput = set.load?.let { formatW(it) } ?: ""
        speedInput = set.speed?.let { formatW(it) } ?: ""
    }

    // ИСПРАВЛЕНО: передаём exercise.id (уникальный id экземпляра), а не
    // exercise.name — ViewModel ищет упражнение по id, поиск по имени
    // никогда не находил совпадения и подход просто не сохранялся
    fun confirmForm() {
        // Собираем подход в зависимости от типа упражнения.
        // return внутри ветки = не все обязательные поля заполнены.
        val newSet: ru.styrk.app.data.model.WorkoutSet = when (exercise.measureType) {
            // Пустое поле берёт число из подсказки (hint), если она есть:
            // «нажал галочку, ничего не вводя» = «так же, как подсказано».
            MeasureType.WEIGHT_REPS -> {
                // Вес МОЖНО оставить пустым — это «без доп. веса» (вес 0)
                val w = if (weightInput.isBlank()) hint?.weight ?: 0f
                        else weightInput.replace(",", ".").toFloatOrNull() ?: 0f
                val r = repsInput.toIntOrNull()
                    ?: (if (repsInput.isBlank()) hint?.reps?.takeIf { it > 0 } else null)
                    ?: return
                ru.styrk.app.data.model.WorkoutSet(weight = w, reps = r)
            }
            MeasureType.REPS -> {
                val r = repsInput.toIntOrNull()
                    ?: (if (repsInput.isBlank()) hint?.reps?.takeIf { it > 0 } else null)
                    ?: return
                ru.styrk.app.data.model.WorkoutSet(reps = r)
            }
            MeasureType.TIME -> {
                val typed = minInput.isNotBlank() || secInput.isNotBlank()
                val total = if (typed)
                    (minInput.toIntOrNull() ?: 0) * 60 + (secInput.toIntOrNull() ?: 0)
                else hint?.seconds ?: 0
                if (total <= 0) return
                ru.styrk.app.data.model.WorkoutSet(seconds = total)
            }
            MeasureType.DISTANCE -> {
                val km = kmInput.replace(",", ".").toFloatOrNull()
                    ?: (if (kmInput.isBlank()) hint?.distanceKm else null)
                    ?: return
                val typed = minInput.isNotBlank() || secInput.isNotBlank()
                val total = if (typed)
                    (minInput.toIntOrNull() ?: 0) * 60 + (secInput.toIntOrNull() ?: 0)
                else hint?.seconds ?: 0
                ru.styrk.app.data.model.WorkoutSet(distanceKm = km, seconds = total)
            }
            MeasureType.CARDIO -> {
                val m = minInput.toIntOrNull()
                    ?: (if (minInput.isBlank()) hint?.seconds?.let { it / 60 }?.takeIf { it > 0 } else null)
                    ?: return
                ru.styrk.app.data.model.WorkoutSet(
                    load = if (loadInput.isBlank()) hint?.load
                           else loadInput.replace(",", ".").toFloatOrNull(),
                    speed = if (speedInput.isBlank()) hint?.speed
                            else speedInput.replace(",", ".").toFloatOrNull(),
                    seconds = m * 60
                )
            }
        }
        val idx = editingIndex
        if (idx != null) {
            viewModel.updateSet(exercise.id, idx, newSet)
        } else {
            viewModel.addSetToExercise(exercise.id, newSet)
        }
        clearInputs()
        addingSet = false; editingIndex = null
        focusManager.clearFocus()
    }

    // ── КАРТОЧКА УПРАЖНЕНИЯ: РОЛИК, ГРУППЫ, ТЕХНИКА ──
    // Общее окно с экраном выбора. Заодно чинится старое поведение:
    // здесь техника показывалась только вместе с роликом, и у растяжки,
    // где ролика нет по задумке, вместо разбора стояло «пока нет описания».
    if (showGuide) {
        val own = customExercises.find { it.name == exercise.name }
        ExerciseGuideDialog(
            name = exercise.name,
            ownDescription = own?.description,
            ownPhotoPath = own?.photoPath,
            onDismiss = { showGuide = false }
        )
    }

    // При плане по подходам он виден в самих строках подходов,
    // и повторять его полоской «ЦЕЛЬ» незачем.
    val goal = if (planSets.isNotEmpty()) "" else activeTargets[exercise.name]?.label().orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp)
    ) {
        // Шапка упражнения с кнопкой удаления (✕)
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(DarkGray, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // п6: тап по названию открывает гифку и технику выполнения
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 30.dp)
                    .clickable { showGuide = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exercise.name,
                    color = White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
                if (ExerciseGuide.of(exercise.name) != null) {
                    Spacer(Modifier.width(6.dp))
                    Text("▶", color = White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(24.dp)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Цель из плана — полоской между шапкой и телом карточки.
        // Не в шапке: там уже название и крестик, третья вещь сделала бы
        // её тесной. И не в теле: цель относится к упражнению целиком,
        // а не к конкретному подходу.
        if (goal.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(SoftGray)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ЦЕЛЬ", color = Gold, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(10.dp))
                Text(goal, color = Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
                .background(White, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("#", style = MaterialTheme.typography.labelSmall, color = DarkGray, modifier = Modifier.width(28.dp))
                // Есть план тренера — в левом столбце он, а не прошлая
                // тренировка: сегодня важнее, что задумано.
                Text(if (planSets.isNotEmpty()) "план" else "прошлая тренировка",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (planSets.isNotEmpty()) Gold else DarkGray, modifier = Modifier.weight(1f))
                Text("текущая", style = MaterialTheme.typography.labelSmall, color = DarkGray, modifier = Modifier.weight(1f))
            }

            val leftSets = if (planSets.isNotEmpty()) planSets else previousSets
            val totalRows = maxOf(exercise.sets.size, leftSets.size)

            if (totalRows > 0) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = SoftGray)
                Spacer(Modifier.height(6.dp))
            }

            for (index in 0 until totalRows) {
                val currentSet = exercise.sets.getOrNull(index)
                val previousSet = leftSets.getOrNull(index)
                val isNextSlot = currentSet == null && index == exercise.sets.size
                val showInlinePlus = isNextSlot && previousSet != null
                val rowAlpha = if (currentSet != null || isNextSlot) 1f else 0.4f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(rowAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", color = Black, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))

                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = previousSet?.let { SetFormat.value(exercise.measureType, it) } ?: "—",
                            color = DarkGray, fontSize = 14.sp
                        )
                        if (previousSet != null && planSets.isEmpty() &&
                            viewModel.isSetRecord(exercise.name, exercise.measureType, previousSet)) {
                            Spacer(Modifier.width(4.dp))
                            Text("🏆", fontSize = 11.sp)
                        }
                    }

                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (currentSet != null) {
                            // Строка кликабельна — можно отредактировать уже
                            // введённый подход. Специально без визуальных
                            // подсказок (карандаша и т.п.) — так попросили.
                            Row(
                                modifier = Modifier.clickable { openEditForm(index, currentSet) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(SetFormat.value(exercise.measureType, currentSet), color = Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (viewModel.isSetRecord(exercise.name, exercise.measureType, currentSet)) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("🏆", fontSize = 11.sp)
                                }
                            }
                        } else if (showInlinePlus) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(SoftGray, RoundedCornerShape(8.dp))
                                    .clickable { openAddForm() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = DarkGray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!addingSet && editingIndex == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .background(SoftGray, RoundedCornerShape(10.dp))
                        .clickable { openAddForm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ подход", color = Black, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Набор полей зависит от того, как считается упражнение
                    // Подсказки из прошлого подхода — строками для полей.
                    val h = hint
                    val hSec = h?.seconds
                    val hW = h?.let { if (it.weight > 0f) formatW(it.weight) + " кг" else "без веса" }
                    val hReps = h?.reps?.takeIf { it > 0 }?.let { "$it повт." }
                    val hMin = hSec?.takeIf { it > 0 }?.let { "${it / 60} мин" }
                    val hS = hSec?.takeIf { it > 0 }?.let { "${it % 60} сек" }
                    when (exercise.measureType) {
                        MeasureType.WEIGHT_REPS -> {
                            SetField(weightInput, { weightInput = it }, "кг", "без веса",
                                KeyboardType.Decimal, ImeAction.Next, { repsFocusRequester.requestFocus() },
                                Modifier.weight(1f).focusRequester(firstFieldRequester), hint = hW)
                            SetField(repsInput, { repsInput = it }, "повт.", null,
                                KeyboardType.Number, ImeAction.Done, { confirmForm() },
                                Modifier.weight(1f).focusRequester(repsFocusRequester), hint = hReps)
                        }
                        MeasureType.REPS -> {
                            SetField(repsInput, { repsInput = it }, "повт.", null,
                                KeyboardType.Number, ImeAction.Done, { confirmForm() },
                                Modifier.weight(1f).focusRequester(firstFieldRequester), hint = hReps)
                        }
                        MeasureType.TIME -> {
                            SetField(minInput, { minInput = it }, "мин", null,
                                KeyboardType.Number, ImeAction.Next, { repsFocusRequester.requestFocus() },
                                Modifier.weight(1f).focusRequester(firstFieldRequester), hint = hMin)
                            SetField(secInput, { secInput = it }, "сек", null,
                                KeyboardType.Number, ImeAction.Done, { confirmForm() },
                                Modifier.weight(1f).focusRequester(repsFocusRequester), hint = hS)
                        }
                        MeasureType.DISTANCE -> {
                            SetField(kmInput, { kmInput = it }, "км", null,
                                KeyboardType.Decimal, ImeAction.Next, { repsFocusRequester.requestFocus() },
                                Modifier.weight(1f).focusRequester(firstFieldRequester),
                                hint = h?.distanceKm?.let { formatW(it) + " км" })
                            SetField(minInput, { minInput = it }, "мин", null,
                                KeyboardType.Number, ImeAction.Next, {},
                                Modifier.weight(1f).focusRequester(repsFocusRequester), hint = hMin)
                            SetField(secInput, { secInput = it }, "сек", null,
                                KeyboardType.Number, ImeAction.Done, { confirmForm() },
                                Modifier.weight(1f), hint = hS)
                        }
                        MeasureType.CARDIO -> {
                            SetField(loadInput, { loadInput = it }, "нагр.", null,
                                KeyboardType.Decimal, ImeAction.Next, {},
                                Modifier.weight(1f).focusRequester(firstFieldRequester),
                                hint = h?.load?.let { "нагр. " + formatW(it) })
                            SetField(speedInput, { speedInput = it }, "км/ч", null,
                                KeyboardType.Decimal, ImeAction.Next, {},
                                Modifier.weight(1f), hint = h?.speed?.let { formatW(it) + " км/ч" })
                            SetField(minInput, { minInput = it }, "мин", null,
                                KeyboardType.Number, ImeAction.Done, { confirmForm() },
                                Modifier.weight(1f), hint = hMin)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Black, RoundedCornerShape(12.dp))
                            .clickable { confirmForm() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                /* Подсказка про две гантели.

                   В поле пишется вес ОДНОЙ — так написано на самой
                   гантели и так человек называет свой вес вслух.
                   А в объём уйдёт вдвое больше, потому что поднимает
                   он две. Без этой строки цифра объёма выглядит
                   ошибкой, и первое, что приходит в голову, —
                   что дневник считает неправильно. */
                if (exercise.measureType == MeasureType.WEIGHT_REPS &&
                    PairedExercises.isPaired(exercise.name)
                ) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Вес одной гантели — в объём пойдёт вдвое больше",
                        color = DarkGray, fontSize = 11.sp
                    )
                }
            }
        }
    }
}
@Composable
private fun SetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** Число из прошлого подхода — полупрозрачно, прямо в пустом поле. */
    hint: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        // С подсказкой подпись поля не нужна: единица уже в самой
        // подсказке («35 кг»). А с подписью Material прячет подсказку,
        // пока поле не в фокусе, — во втором поле её не было бы видно.
        label = if (hint == null) ({ Text(label, color = DarkGray) }) else null,
        placeholder = if (hint != null) ({
            Text(hint, color = Black.copy(alpha = 0.35f), fontSize = 15.sp)
        }) else placeholder?.let { p ->
            { Text(p, color = DarkGray.copy(alpha = 0.6f), fontSize = 12.sp) }
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Black, unfocusedBorderColor = SoftGray, cursorColor = Black,
            focusedTextColor = Black, unfocusedTextColor = Black, focusedLabelColor = Black
        )
    )
}
/**
 * Самый тяжёлый подход для подсказки в полях (R184).
 *
 * Только для «вес × повторы» и только среди подходов с весом: у своего
 * веса, времени и дистанции «тяжелее» не означает ничего, там подсказка
 * идёт по-старому — тем же по счёту подходом прошлого раза.
 * При равном весе берётся подход с бо́льшим числом повторов.
 */
private fun heaviestSet(
    type: MeasureType,
    sets: List<ru.styrk.app.data.model.WorkoutSet>
): ru.styrk.app.data.model.WorkoutSet? =
    if (type != MeasureType.WEIGHT_REPS) null
    else sets.filter { it.weight > 0f }
        .maxWithOrNull(compareBy<ru.styrk.app.data.model.WorkoutSet>({ it.weight }, { it.reps }))
