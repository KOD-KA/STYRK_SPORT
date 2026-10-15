package ru.styrk.app.ui.calendar

import ru.styrk.app.ui.common.ruPlural
import ru.styrk.app.data.model.PairedExercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.data.model.*
import ru.styrk.app.ui.common.ExerciseGuideDialog
import ru.styrk.app.ui.common.MarkBox
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import ru.styrk.app.ui.common.CappedFontScale
import ru.styrk.app.ui.common.CHROME_FONT_CAP
import ru.styrk.app.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import ru.styrk.app.data.model.CustomExercise

private val Blue = Color(0xFF4285F4)
/* Совместная тренировка помечается НЕ цветом, а формой — колечком.

   Цвета в приложении заняты и добавлять четвёртый некуда: чёрный,
   белый, золото и синий, и каждый уже что-то значит. Зелёный, который
   тут стоял сначала, был честно чужим: он не из этой палитры и первым
   бросался в глаза, хотя по важности идёт последним.

   Колечко того же белого, что и точка тренировки: та же семья, но
   не закрашено — «не только моё». Лишнего цвета нет, а отличить
   с одного взгляда можно. */
private fun fmtW(w: Float) = if (w == w.toInt().toFloat()) w.toInt().toString() else "%.1f".format(w)
/** «4 сентября» — для подписи кнопки записи задним числом. */
private val SHORT_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

private val DAY_LABELS = listOf("пн","вт","ср","чт","пт","сб","вс")

// ═══════════════════════════════════════════════════════════════════
//  ROOT
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: TrainingSessionViewModel) {
    val workouts        by viewModel.completedWorkouts.collectAsState()
    val exerciseHistory by viewModel.exerciseHistory.collectAsState()
    val plannedWorkouts by viewModel.plannedWorkouts.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    // День, который надо открыть в календаре, — из вкладки «Тренер».
    var openDay by remember { mutableStateOf<LocalDate?>(null) }
    var detailWorkout by remember { mutableStateOf<CompletedWorkout?>(null) }
    var detailPlanned by remember { mutableStateOf<PlannedWorkout?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)


    LaunchedEffect(Unit) { viewModel.loadPlannedWorkouts() }

    val workoutsByDate = remember(workouts) { workouts.groupBy { it.dateTime.toLocalDate() } }
    val recordDays = remember(workouts, exerciseHistory) {
        workouts.filter { w -> w.exercises.any { ex ->
            val r = exerciseHistory[ex.name] ?: return@any false
            ex.sets.any { it.weight >= r.maxWeight }
        }}.map { it.dateTime.toLocalDate() }.toSet()
    }
    val plannedByDate = remember(plannedWorkouts) { plannedWorkouts.groupBy { it.date } }

    // Свои упражнения: их описание и снимок показывает карточка
    // упражнения. Читаем один раз здесь и передаём вниз — иначе каждое
    // окно подписывалось бы на модель отдельно.
    val customExercises by viewModel.customExercises.collectAsState()

    // День, за который человек собрался записать прошедшую тренировку.
    // Не пусто — показываем окно ввода времени и длительности.
    var recordPastFor by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize().background(White)) {
        // ── ТАБЫ ────────────────────────────────────────────────────
        // «ЗАПЛАНИРОВАННОЕ» — пятнадцать букв, самая длинная подпись
        // в приложении. При крупном системном шрифте она переносилась
        // на вторую строку, и полоса вкладок разъезжалась.
        //
        // Ширина — по содержимому, как и была. Пробовал растянуть вкладки
        // на всю ширину: расчёт выигрывал две точки на самом узком экране,
        // а взамен вкладки при обычном шрифте отъезжали от левого края
        // и вставали по центру. То есть чинил случай, который бывает
        // редко, ценой вида, который человек видит каждый день.
        // Плохая сделка — вернул как было.
        // Третья вкладка «Тренер» — только у того, у кого тренер есть.
        // Ряд прокручивается вбок: три подписи на узком экране с крупным
        // шрифтом иначе не поместились бы.
        ru.styrk.app.ui.friends.CoachWatch()
        val hasCoach = ru.styrk.app.ui.friends.CoachFeed.coaches.isNotEmpty()
        LaunchedEffect(hasCoach) { if (!hasCoach && selectedTab == 2) selectedTab = 0 }
        CappedFontScale(CHROME_FONT_CAP) {
            Row(
                Modifier.fillMaxWidth().background(White)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                CalTab("КАЛЕНДАРЬ",       selectedTab == 0) { selectedTab = 0 }
                Spacer(Modifier.width(20.dp))
                CalTab("ЗАПЛАНИРОВАННОЕ", selectedTab == 1) { selectedTab = 1 }
                if (hasCoach) {
                    Spacer(Modifier.width(20.dp))
                    CalTab("ТРЕНЕР", selectedTab == 2) { selectedTab = 2 }
                }
            }
        }
        HorizontalDivider(color = SoftGray)

        when (selectedTab) {
            0 -> MonthTab(workoutsByDate, recordDays, plannedByDate, exerciseHistory,
                onWorkoutClick = { detailWorkout = it },
                onPlannedClick = { detailPlanned = it },
                onRecordPast   = { recordPastFor = it },
                openDay = openDay,
                onOpenDayDone = { openDay = null },
                onInviteAccepted = { plan ->
                    // Копия ложится своей записью: с этой минуты план
                    // принадлежит принявшему и живёт своей жизнью.
                    viewModel.addPlannedWorkout(
                        listOf(LocalDate.ofEpochDay(plan.date)),
                        plan.hour, plan.minute, plan.muscleGroup,
                        plan.exercises, plan.targets
                    )
                })
            1 -> WeekTab(plannedByDate, workoutsByDate, viewModel)
            2 -> ru.styrk.app.ui.friends.CoachTab(
                workoutDays = workoutsByDate.keys,
                plannedKeys = plannedWorkouts.map {
                    "${it.date.toEpochDay()}:${it.hour}:${it.minute}"
                }.toSet(),
                onOpenDay = { d -> openDay = d; selectedTab = 0 },
                onAdd = { plan ->
                    viewModel.addPlannedWorkout(
                        listOf(LocalDate.ofEpochDay(plan.date)),
                        plan.hour, plan.minute, plan.muscleGroup,
                        plan.exercises, plan.targets
                    )
                }
            )
        }
    }

    detailWorkout?.let { w ->
        ModalBottomSheet(onDismissRequest = { detailWorkout = null }, sheetState = sheetState,
            containerColor = White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
            WorkoutDetailSheet(w, viewModel = viewModel, onDeleted = { detailWorkout = null })
        }
    }

    // ── ПОДРОБНО О ЗАПЛАНИРОВАННОЙ ──
    // В месячном календаре про план было видно только группу мышц
    // и время. Что именно в нём лежит, узнать было негде — а планируют
    // как раз ради этого. Во вкладке «Запланированное» упражнения
    // видны и так, поэтому там ничего не меняется.
    detailPlanned?.let { pl ->
        ModalBottomSheet(onDismissRequest = { detailPlanned = null }, sheetState = sheetState,
            containerColor = White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
            PlannedDetailSheet(
                planned = pl,
                customExercises = customExercises,
                onStart = { viewModel.startPlannedWorkout(pl); detailPlanned = null },
                onDelete = { viewModel.requestDeletePlannedWorkout(pl.id); detailPlanned = null }
            )
        }
    }

    // ── ЗАПИСЬ ПРОШЕДШЕЙ ТРЕНИРОВКИ ──
    recordPastFor?.let { day ->
        PastWorkoutDialog(
            day = day,
            onDismiss = { recordPastFor = null },
            onConfirm = { hour, minute, minutes ->
                recordPastFor = null
                viewModel.startBackdatedWorkout(day.atTime(hour, minute), minutes)
            }
        )
    }
}

/**
 * Когда была тренировка и сколько длилась.
 *
 * Спрашиваем ДО записи, а не после. У обычной тренировки время
 * считается само, здесь считать нечего — человек вспоминает. А вспомнить
 * проще сразу, пока думаешь именно об этом дне, чем в конце, когда уже
 * набрал десяток подходов и переключился на них.
 *
 * По умолчанию 19:00 и час — обычное время и обычная длительность.
 * Угадали или нет, но с чего-то начинать лучше, чем с пустых полей.
 */
@Composable
private fun PastWorkoutDialog(
    day: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, minutes: Int) -> Unit
) {
    var hourText by remember { mutableStateOf("19") }
    var minText  by remember { mutableStateOf("00") }
    var durText  by remember { mutableStateOf("60") }

    val hour = hourText.toIntOrNull()
    val minute = minText.toIntOrNull()
    val dur = durText.toIntOrNull()
    val valid = hour != null && hour in 0..23 &&
                minute != null && minute in 0..59 &&
                dur != null && dur in 1..1440

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        title = { Text("Тренировка " + day.format(SHORT_DAY), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Во сколько начали и сколько занимались. Потом добавите упражнения — как в обычной тренировке.",
                    color = DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumField(hourText, { hourText = it }, "час", Modifier.width(72.dp))
                    Text(":", modifier = Modifier.padding(horizontal = 6.dp),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Black)
                    NumField(minText, { minText = it }, "мин", Modifier.width(72.dp))
                }
                Spacer(Modifier.height(10.dp))
                NumField(durText, { durText = it }, "длительность, мин", Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(hour ?: 19, minute ?: 0, dur ?: 60) },
                enabled = valid
            ) { Text("Записать", color = if (valid) Black else LightGray,
                     fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = DarkGray) }
        }
    )
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        // Цифры и только цифры. Без этого в поле часа можно набрать
        // букву, и «Записать» просто не нажималось бы — без объяснений.
        onValueChange = { t -> onChange(t.filter { it.isDigit() }.take(4)) },
        label = { Text(label, color = DarkGray, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Black, unfocusedBorderColor = SoftGray,
            cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
        )
    )
}

// ═══════════════════════════════════════════════════════════════════
//  ВКЛАДКА 1: МЕСЯЦ
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MonthTab(
    workoutsByDate:  Map<LocalDate, List<CompletedWorkout>>,
    recordDays:      Set<LocalDate>,
    plannedByDate:   Map<LocalDate, List<PlannedWorkout>>,
    exerciseHistory: Map<String, ExerciseHistory>,
    onWorkoutClick:  (CompletedWorkout) -> Unit,
    onPlannedClick:  (PlannedWorkout) -> Unit,
    onRecordPast:    (LocalDate) -> Unit,
    // Открыть этот день (пришли из вкладки «Тренер»). null — ничего.
    openDay:         LocalDate? = null,
    onOpenDayDone:   () -> Unit = {},
    // Зов приняли — план надо положить к себе. Кладёт не блок зовов,
    // а тот, у кого есть модель: блок про сеть, а не про базу.
    onInviteAccepted: (ru.styrk.app.sync.SyncApi.InvitePlan) -> Unit
) {
    var yearMonth    by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    /* Куда прыгнуть после смены месяца.

       Смена месяца всегда сбрасывала выбранный день — и правильно:
       человек листает месяцы, а не тащит за собой число из прошлого.
       Но теперь по числу из строки «вас зовут ещё на…» можно перейти
       на день в соседнем месяце, и сброс съел бы этот переход: месяц
       поменялся, день обнулился, зов снова не виден.

       Поэтому у сброса появилось исключение, и ровно одно: если
       переход задуман, день берётся из него. */
    var jumpTo by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(yearMonth) { selectedDate = jumpTo; jumpTo = null }

    // Пришли из вкладки «Тренер» с конкретным днём — открываем его.
    LaunchedEffect(openDay) {
        val d = openDay ?: return@LaunchedEffect
        if (d.year != yearMonth.year || d.monthValue != yearMonth.monthValue) {
            jumpTo = d
            yearMonth = YearMonth.of(d.year, d.monthValue)
        } else {
            selectedDate = d
        }
        onOpenDayDone()
    }

    // Дни с зовами — для точек в сетке и для строки «зовут ещё на…».
    val inviteDays = ru.styrk.app.ui.friends.InviteFeed.days

    val monthWorkouts = workoutsByDate.entries
        .filter { (d, _) -> d.year == yearMonth.year && d.monthValue == yearMonth.monthValue }
        .flatMap { it.value }

    // Вкладка прокручивается.
    //
    // Была жёсткой: столбец во весь экран без прокрутки. При обычном
    // шрифте всё влезало, при крупном — сетка месяца и плашка выбранного
    // дня становились выше экрана, и низ просто обрезался. Обрезался
    // как раз блок выбранного дня с кнопкой «подробнее»: до него нельзя
    // было ни доскроллить, ни дотянуться.
    //
    // Отступ снизу — чтобы последняя строка не пряталась под нижнюю
    // панель: панель нарисована поверх содержимого.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        /* Зовы переехали ПОД календарь, на свой день.
           Здесь остался только невидимый загрузчик: он ходит на сервер
           и складывает ответ в общий запас, откуда его берут и точки
           в сетке, и карточка выбранного дня. Раньше блок висел тут
           целиком и показывал все зовы на любом выбранном числе. */
        ru.styrk.app.ui.friends.InviteWatch()

        // «Написать тренеру, когда мне удобно» отсюда ушла — на вкладку
        // «Тренер». Над календарём она висела на любом выбранном дне
        // и к самому календарю отношения не имела.

        // Мини-статистика месяца
        // Три счётчика в одной плашке. Подписи разной длины
        // («тренировок» и «запланировано»), а при SpaceEvenly ширина
        // зависела от текста: длинная подпись отжимала соседей, и все
        // три слипались в сплошную строку.
        //
        // Теперь каждый счётчик занимает ровно треть и подписан
        // по центру своей трети, независимо от длины слова.
        CappedFontScale(CHROME_FONT_CAP) {
            Row(Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MiniStat("${monthWorkouts.size}", "тренировок", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(24.dp).background(LightGray))
                MiniStat("${monthWorkouts.count { w -> w.exercises.any { ex ->
                    val r = exerciseHistory[ex.name] ?: return@any false; ex.sets.any { it.weight >= r.maxWeight }
                }}}", "рекордов 🏆", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(24.dp).background(LightGray))
                MiniStat("${plannedByDate.entries.count { (d, _) ->
                    d.year == yearMonth.year && d.monthValue == yearMonth.monthValue
                }}", "запланировано", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth().background(DarkGray, RoundedCornerShape(20.dp))) {
            // Навигация
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                NavCircle("<") { yearMonth = yearMonth.minusMonths(1) }
                val name = yearMonth.month.getDisplayName(JTextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() }
                Text("$name ${yearMonth.year}", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                NavCircle(">") { yearMonth = yearMonth.plusMonths(1) }
            }
            // Заголовки
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                DAY_LABELS.forEach { Box(Modifier.weight(1f), Alignment.Center) {
                    Text(it, color = White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }}
            }
            Spacer(Modifier.height(6.dp))
            // Сетка
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val cell: Dp = maxWidth / 7
                MonthGrid(yearMonth, workoutsByDate, recordDays, plannedByDate,
                    inviteDays, selectedDate, cell) { d ->
                    selectedDate = if (selectedDate == d) null else d
                }
            }
            Spacer(Modifier.height(4.dp))
            // Легенда
            // Три пояснения в один ряд. Место здесь кончается раньше
            // всего: три точки, три подписи и два просвета.
            //
            // Без потолка последняя подпись рассыпалась в столбик
            // по буквам — «з», «а», ... Запрет переноса у каждой
            // подписи страхует на случай, когда и потолка не хватит:
            // тогда слово обрежется с краю, а не развалится.
            CappedFontScale(CHROME_FONT_CAP) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Dot(Gold); Spacer(Modifier.width(5.dp))
                Text("рекорд", color = White.copy(.7f), fontSize = 11.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(14.dp))
                Dot(White.copy(.85f)); Spacer(Modifier.width(5.dp))
                Text("тренировка", color = White.copy(.7f), fontSize = 11.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(14.dp))
                Dot(Blue); Spacer(Modifier.width(5.dp))
                Text("запланировано", color = White.copy(.7f), fontSize = 11.sp,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            /* Четвёртая подпись — отдельной строкой, и только когда
               зовы вообще есть. В один ряд четыре пары «точка + слово»
               не помещаются даже при обычном шрифте: место здесь
               кончается раньше всего в приложении. */
            /* Только если колечко правда есть в ЭТОМ месяце. Раньше строка
               висела, когда зов был хоть где-то, — в сентябре подписывала
               метку, которой в сентябре нет. Подпись к пустому месту
               выглядит поломкой. */
            val togetherHere = inviteDays.any { d ->
                val ld = LocalDate.ofEpochDay(d)
                ld.year == yearMonth.year && ld.monthValue == yearMonth.monthValue
            }
            if (togetherHere) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    TogetherDot(7.dp); Spacer(Modifier.width(5.dp))
                    Text("вместе", color = White.copy(.7f), fontSize = 11.sp,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
            }
            // Инфо выбранного дня
            val selWorkout  = selectedDate?.let { workoutsByDate[it]?.firstOrNull() }
            val selPlanned  = selectedDate?.let { plannedByDate[it]?.firstOrNull() }
            if (selWorkout != null && selectedDate != null) {
                HorizontalDivider(color = White.copy(.12f))
                val hasRec = selWorkout.exercises.any { ex ->
                    val r = exerciseHistory[ex.name] ?: return@any false; ex.sets.any { it.weight >= r.maxWeight }
                }
                val h = selWorkout.durationMinutes/60; val m = selWorkout.durationMinutes%60
                Row(Modifier.fillMaxWidth().clickable { onWorkoutClick(selWorkout) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedDate!!.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (hasRec) { Spacer(Modifier.width(6.dp)); Text("🏆", fontSize = 14.sp) }
                        }
                        Text(selWorkout.muscleGroup, color = White.copy(.7f), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        selWorkout.athleteWeight?.let { Text("%.1f кг".format(it), color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        Text(if (h > 0) "$h ч $m мин" else "$m мин", color = White.copy(.7f), fontSize = 12.sp)
                        Text("подробнее →", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else if (selPlanned != null && selectedDate != null) {
                HorizontalDivider(color = White.copy(.12f))
                Row(Modifier.fillMaxWidth().clickable { onPlannedClick(selPlanned) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedDate!!.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(selPlanned.muscleGroup, color = White.copy(.7f), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%02d:%02d".format(selPlanned.hour, selPlanned.minute), color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (selPlanned.exercises.isEmpty()) "подробнее →"
                            else "${selPlanned.exercises.size} упр.  •  подробнее →",
                            color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else { Spacer(Modifier.height(12.dp)) }
        }

        /* Зовы выбранного дня — ПОД календарём, на светлом фоне.

           Не внутри чёрной карточки: та про сам месяц и про то, что
           уже случилось или запланировано. Зов — это вопрос, на который
           человек отвечает, и место ему рядом с кнопками дня, а не
           в сетке. */
        selectedDate?.let { chosenDay ->
            ru.styrk.app.ui.friends.InviteDay(
                day = chosenDay,
                onAccepted = onInviteAccepted,
                onJump = { d ->
                    if (d.year != yearMonth.year || d.monthValue != yearMonth.monthValue) {
                        jumpTo = d
                        yearMonth = YearMonth.of(d.year, d.monthValue)
                    } else {
                        selectedDate = d
                    }
                }
            )
        }

        // ── Записать прошедшую тренировку ──
        //
        // Показывается только на дне, который УЖЕ прошёл и на котором
        // тренировки ещё нет. На будущем дне такой кнопки быть не может
        // (запись о том, чего не было, — это не запись, а план, и для
        // него есть соседняя вкладка), а на дне с тренировкой она сбивала
        // бы с толку: непонятно, дописывает она или заменяет.
        val chosen = selectedDate
        if (chosen != null && chosen <= LocalDate.now() && workoutsByDate[chosen] == null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(SoftGray, RoundedCornerShape(14.dp))
                    .clickable { onRecordPast(chosen) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Записать тренировку за " + chosen.format(SHORT_DAY),
                    color = Black, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        // Запас снизу: нижняя панель нарисована ПОВЕРХ содержимого,
        // и без этого отступа последняя строка пряталась бы под ней —
        // прокрутка есть, а докрутить до конца нельзя.
        Spacer(Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════
//  СЕТКА МЕСЯЦА
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MonthGrid(
    ym: YearMonth, workoutsByDate: Map<LocalDate, List<CompletedWorkout>>,
    recordDays: Set<LocalDate>, plannedByDate: Map<LocalDate, List<PlannedWorkout>>,
    inviteDays: Set<Long>,
    selectedDate: LocalDate?, cell: Dp, onDayClick: (LocalDate) -> Unit
) {
    val offset = ym.atDay(1).dayOfWeek.value - 1
    val days   = ym.lengthOfMonth()
    val rows   = (offset + days + 6) / 7
    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val d = row*7 + col - offset + 1
                    Box(Modifier.size(cell)) {
                        if (d in 1..days) {
                            val date = ym.atDay(d)
                            MonthDayCell(d, cell,
                                hasWorkout = workoutsByDate.containsKey(date),
                                isRecord   = recordDays.contains(date),
                                isPlanned  = plannedByDate.containsKey(date),
                                isTogether = inviteDays.contains(date.toEpochDay()),
                                isSelected = date == selectedDate,
                                isToday    = date == LocalDate.now(),
                                onClick    = { onDayClick(date) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MonthDayCell(dayNum: Int, cell: Dp, hasWorkout: Boolean, isRecord: Boolean,
                         isPlanned: Boolean, isTogether: Boolean,
                         isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(cell).padding(3.dp).background(
        if (isSelected) White.copy(.18f) else Color.Transparent, RoundedCornerShape(10.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$dayNum", color = if (isToday) Gold else if (isSelected) White else White.copy(.88f),
                fontSize = 18.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.height(2.dp))
            /* Две точки в ряд, а не одна.

               Своё (тренировка, рекорд, план) и совместное — разные
               вещи, и складывать их в одну точку нельзя: цвет может
               быть только один, и любая очерёдность соврала бы про
               вторую половину. Поэтому у дня, где есть и то и другое,
               точек две. Где чего-то нет — на его месте пусто,
               и числа в сетке не съезжают. */
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.size(5.dp).background(when {
                    !hasWorkout && !isPlanned -> Color.Transparent
                    isRecord   -> Gold
                    hasWorkout -> White.copy(.85f)
                    else       -> Blue
                }, CircleShape))
                if (isTogether) TogetherDot(5.dp)
            }
        }
    }
}

// Правило «какие планы выполнены» переехало в data/model/PlanMatching.kt:
// оно нужно и календарю, и стартовому экрану, а две копии одного
// правила неизбежно разъезжаются.

// ═══════════════════════════════════════════════════════════════════
//  ВКЛАДКА 2: ЗАПЛАНИРОВАННОЕ (еженедельный вид)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekTab(
    plannedByDate:   Map<LocalDate, List<PlannedWorkout>>,
    workoutsByDate:  Map<LocalDate, List<CompletedWorkout>>,
    viewModel:       TrainingSessionViewModel
) {
    val customExercises by viewModel.customExercises.collectAsState()

    /* Нужны, чтобы отправить зов после сохранения плана.

       Стояли этажом выше, в CalendarScreen, — и это была ошибка сборки:
       окно планирования живёт здесь, в недельной вкладке, а не там.
       Со стороны похоже: обе функции про календарь. Компилятор увидел
       сразу, я — нет. */
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val today = LocalDate.now()
    var weekStart by remember {
        mutableStateOf(today.minusDays((today.dayOfWeek.value - 1).toLong()))
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(today) }
    var showAddSheet by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(weekStart) { selectedDate = null }

    val weekEnd = weekStart.plusDays(6)
    val fmtShort = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
    val weekLabel = "${weekStart.format(fmtShort)} — ${weekEnd.format(fmtShort)}"

    // Считаем один раз по всем планам сразу: только так тренировка
    // закрывает ровно один план, а не все подходящие.
    val doneIds = remember(plannedByDate, workoutsByDate) {
        donePlanIds(plannedByDate.values.flatten(), workoutsByDate)
    }

    // Невыполненные сверху: к ним человек и пришёл. Выполненные
    // остаются на экране, но ниже и тише — как отметка, что сделано.
    val selDayPlanned = (selectedDate?.let { plannedByDate[it] } ?: emptyList())
        .sortedBy { if (it.id in doneIds) 1 else 0 }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(14.dp))

        Column(Modifier.fillMaxWidth().background(DarkGray, RoundedCornerShape(20.dp))) {
            // Навигация по неделям
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NavCircle("<") { weekStart = weekStart.minusWeeks(1) }
                Text(weekLabel, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                NavCircle(">") { weekStart = weekStart.plusWeeks(1) }
            }

            // Заголовки + числа дней
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val cell: Dp = maxWidth / 7
                Row {
                    for (i in 0..6) {
                        val date = weekStart.plusDays(i.toLong())
                        val hasPlan = plannedByDate.containsKey(date)
                        val isSel   = date == selectedDate
                        val isTod   = date == today
                        Box(Modifier.width(cell).height(72.dp).padding(3.dp).background(
                            if (isSel) White.copy(.18f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { selectedDate = if (selectedDate == date) null else date },
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(DAY_LABELS[i], color = White.copy(.55f), fontSize = 11.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("${date.dayOfMonth}",
                                    color = if (isTod) Gold else if (isSel) White else White.copy(.88f),
                                    fontSize = 20.sp, fontWeight = if (isTod || isSel) FontWeight.Bold else FontWeight.Normal)
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.size(7.dp).background(if (hasPlan) Blue else Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Карточки запланированных тренировок выбранного дня
        if (selDayPlanned.isEmpty() && selectedDate != null) {
            Box(Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(14.dp))
                .padding(20.dp), contentAlignment = Alignment.Center) {
                Text("Нет тренировок на этот день", color = DarkGray, fontSize = 13.sp)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(selDayPlanned, key = { it.id }) { planned ->
                PlannedWorkoutCard(
                    planned = planned,
                    done = planned.id in doneIds,
                    onStart = { viewModel.startPlannedWorkout(planned) },
                    onDelete = { viewModel.requestDeletePlannedWorkout(planned.id) }
                )
            }

            item {
                // Кнопка "+" добавить тренировку
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .background(DarkGray, RoundedCornerShape(14.dp))
                    .clickable { showAddSheet = true }, contentAlignment = Alignment.Center) {
                    // «+» и надпись выравниваются по ЦЕНТРУ строки,
                    // а не по её низу. При крупном шрифте надпись
                    // переносилась на две строки, и плюс оставался
                    // болтаться внизу — как будто съехал.
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("+", color = White, fontSize = 22.sp, fontWeight = FontWeight.Light)
                        Text("Запланировать тренировку", color = White, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    // ── SHEET: ДОБАВИТЬ ПЛАНОВУЮ ТРЕНИРОВКУ ─────────────────────
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, sheetState = addSheetState,
            containerColor = White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
            AddPlannedWorkoutSheet(
                customExercises = customExercises,
                initialDate = selectedDate ?: today,
                weekStart   = weekStart,
                onSave      = { dates, hour, min, mg, exercises, targets, invite ->
                    viewModel.addPlannedWorkout(dates, hour, min, mg, exercises, targets)
                    showAddSheet = false
                    // Зовы уходят ПОСЛЕ того, как план записан у себя.
                    // Обратный порядок означал бы, что при обрыве связи
                    // человек теряет и зов, и план.
                    if (invite.isNotEmpty()) {
                        scope.launch {
                            val api = SyncApi(SyncPrefs(context))
                            dates.forEach { d ->
                                val plan = SyncApi.InvitePlan(
                                    date = d.toEpochDay(), hour = hour, minute = min,
                                    muscleGroup = mg, exercises = exercises, targets = targets
                                )
                                invite.forEach { handle -> api.invite(handle, plan) }
                            }
                        }
                    }
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  КАРТОЧКА ПЛАНОВОЙ ТРЕНИРОВКИ
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PlannedWorkoutCard(
    planned: PlannedWorkout,
    done: Boolean,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(14.dp))) {
        // Шапка. У выполненного плана она серая, а не чёрная: чёрная
        // читается как «здесь дело», и глаз возвращается к ней снова.
        Box(Modifier.fillMaxWidth()
            .background(if (done) DarkGray else Black, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Text("✓", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                Text(planned.muscleGroup, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                val dayName = planned.date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() }
                Text("$dayName • %02d:%02d".format(planned.hour, planned.minute), color = White.copy(.75f), fontSize = 13.sp)
            }
        }
        // Тело: упражнения
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (planned.exercises.isEmpty()) {
                Text("Упражнения не указаны", color = DarkGray, fontSize = 13.sp)
            } else {
                planned.exercises.forEach { ex ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(DarkGray, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(ex, fontSize = 13.sp, color = Black)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (done) {
                // Выполненный план не предлагает начать: тренировка
                // в этот день уже записана, и вторая по той же группе
                // почти наверняка означала бы, что человек нажал зря.
                Box(Modifier.fillMaxWidth().height(46.dp)
                    .background(SoftGray, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✓", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Выполнено", color = DarkGray, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                // Начать тренировку (можно и заранее, до даты плана)
                Box(Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    .background(Black, RoundedCornerShape(10.dp))
                    .clickable { onStart() }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("▶", color = White, fontSize = 12.sp)
                        Text("Начать тренировку", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Удалить
            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp)
                .background(LightGray, RoundedCornerShape(8.dp))
                .clickable { onDelete() }, contentAlignment = Alignment.Center) {
                Text("Удалить", fontSize = 13.sp, color = DarkGray, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  SHEET: ДОБАВИТЬ ПЛАНОВУЮ ТРЕНИРОВКУ
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddPlannedWorkoutSheet(
    initialDate: LocalDate, weekStart: LocalDate,
    // Свои упражнения: карточка показывает описание и снимок, которые
    // человек написал сам. Без списка их не найти.
    customExercises: List<CustomExercise>,
    onSave: (List<LocalDate>, Int, Int, String, List<String>, Map<String, ExerciseTarget>,
             List<String>) -> Unit
) {
    // Дней может быть несколько: «в понедельник, среду и пятницу — ноги»
    // это одно решение, а не три отдельных.
    val selDates = remember { mutableStateListOf(initialDate) }
    // Неделя, которая показана в окне. Раньше окно всегда показывало
    // ту неделю, что открыта на экране позади, и запланировать
    // на следующий месяц можно было только выйдя и перелистав календарь.
    var shownWeek by remember { mutableStateOf(weekStart) }
    var hourStr  by remember { mutableStateOf("19") }
    var minStr   by remember { mutableStateOf("00") }
    val selGroups = remember { mutableStateListOf<MuscleGroup>() }
    val selExercises = remember { mutableStateListOf<String>() }
    val targets = remember { mutableStateMapOf<String, ExerciseTarget>() }
    var showTargets by remember { mutableStateOf(false) }
    // Какое упражнение разбираем. Планируя, приходится выбирать
    // из двух десятков названий, половину из которых человек видит
    // впервые — ролик и техника решают это на месте, не выходя из окна.
    var guideName by remember { mutableStateOf<String?>(null) }
    // Кого зовём вместе с собой. Пусто почти всегда — и это нормально:
    // складка внизу закрыта, пока её не открыли.
    val invite = remember { mutableStateListOf<String>() }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

        item {
            Text("ЗАПЛАНИРОВАТЬ ТРЕНИРОВКУ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Black,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        // ── Дни: можно отметить несколько, неделю листать прямо здесь ──
        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Дни", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
                    Text(
                        if (selDates.size > 1) "выбрано ${selDates.size}" else "можно несколько",
                        fontSize = 11.sp, color = DarkGray
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Стрелки листают недели без ограничений: вперёд можно
                // уйти хоть на полгода, отмеченные дни при этом никуда
                // не деваются — их видно в счётчике и на кнопке.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp)
                        .background(SoftGray, RoundedCornerShape(10.dp))
                        .clickable { shownWeek = shownWeek.minusWeeks(1) },
                        contentAlignment = Alignment.Center) {
                        Text("‹", color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        weekTitle(shownWeek),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp, color = Black, fontWeight = FontWeight.Medium
                    )
                    Box(Modifier.size(36.dp)
                        .background(SoftGray, RoundedCornerShape(10.dp))
                        .clickable { shownWeek = shownWeek.plusWeeks(1) },
                        contentAlignment = Alignment.Center) {
                        Text("›", color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..6) {
                        val d = shownWeek.plusDays(i.toLong())
                        val sel = d in selDates
                        Box(Modifier.weight(1f).height(52.dp)
                            .background(if (sel) DarkGray else SoftGray, RoundedCornerShape(10.dp))
                            .clickable {
                                // Последний день снять нельзя: план без даты
                                // не бывает, а пустая кнопка «Сохранить»
                                // объясняла бы это хуже.
                                if (sel) { if (selDates.size > 1) selDates.remove(d) }
                                else selDates.add(d)
                            }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(DAY_LABELS[i], fontSize = 10.sp, color = if (sel) White.copy(.7f) else DarkGray)
                                Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    color = if (sel) White else Black)
                            }
                        }
                    }
                }
            }
        }

        // Отмеченные дни с других недель видно списком: иначе, пролистав
        // вперёд, человек не помнит, что уже отметил.
        if (selDates.any { it < shownWeek || it > shownWeek.plusDays(6) }) {
            item {
                Text(
                    "Также отмечено: " + selDates
                        .filter { it < shownWeek || it > shownWeek.plusDays(6) }
                        .sorted()
                        .joinToString(", ") {
                            it.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")))
                        },
                    fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Время ──
        item {
            Column {
                Text("Время", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val minuteFocusRequester = remember { FocusRequester() }
                    val focusManager = LocalFocusManager.current
                    OutlinedTextField(hourStr, { hourStr = it.filter(Char::isDigit).take(2) },
                        label = { Text("Час", color = DarkGray) }, modifier = Modifier.width(90.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { minuteFocusRequester.requestFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Black, unfocusedBorderColor = LightGray,
                            focusedTextColor = Black, unfocusedTextColor = Black, cursorColor = Black))
                    Text(":", color = Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(minStr, { minStr = it.filter(Char::isDigit).take(2) },
                        label = { Text("Мин", color = DarkGray) },
                        modifier = Modifier.width(90.dp).focusRequester(minuteFocusRequester), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Black, unfocusedBorderColor = LightGray,
                            focusedTextColor = Black, unfocusedTextColor = Black, cursorColor = Black))
                }
            }
        }

        // ── Группы мышц: тоже можно несколько ──
        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Группы мышц", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
                    Text("можно несколько", fontSize = 11.sp, color = DarkGray)
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in MuscleGroup.visible.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { g ->
                                val sel = g in selGroups
                                Box(Modifier.height(36.dp).background(
                                    if (sel) DarkGray else SoftGray, RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (sel) {
                                            selGroups.remove(g)
                                            // Снятая группа уносит свои упражнения:
                                            // иначе в плане остались бы строки
                                            // из блока, которого в нём уже нет.
                                            // Свои упражнения тоже уходят вместе
                                            // с группой: раньше их тут не было
                                            // вовсе, и об этом некому было помнить.
                                            val gone = ExerciseLibrary.exercises
                                                .filter { it.muscleGroup == g }.map { it.name } +
                                                    customExercises
                                                        .filter { it.muscleGroup == g }.map { it.name }
                                            val keep = ExerciseLibrary.exercises
                                                .filter { it.muscleGroup in selGroups }.map { it.name } +
                                                    customExercises
                                                        .filter { it.muscleGroup in selGroups }.map { it.name }
                                            selExercises.removeAll { it in gone && it !in keep }
                                            targets.keys.retainAll(selExercises.toSet())
                                        } else selGroups.add(g)
                                    }
                                    .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(g.title, fontSize = 13.sp, color = if (sel) White else Black,
                                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Упражнения по всем выбранным группам ──
        if (selGroups.isNotEmpty()) {
            item {
                Text("Упражнения (необязательно)", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
            }
            selGroups.forEach { g ->
                val libList = ExerciseLibrary.exercises.filter { it.muscleGroup == g }.map { it.name }
                // Свои упражнения этой группы. Раньше список строился
                // только по встроенному справочнику — и запланировать
                // своё упражнение было нельзя: его тут попросту не было.
                //
                // Совпавшее название отбрасываю: строка должна быть одна,
                // иначе список падает на повторном ключе.
                val ownList = customExercises
                    .filter { it.muscleGroup == g }
                    .map { it.name }
                    .distinct()
                    .filter { it !in libList }
                // Заголовок группы: в общей куче не понять, где чьи упражнения.
                if (selGroups.size > 1) {
                    item(key = "hdr_" + g.name) {
                        Text(g.title.uppercase(), fontSize = 10.sp, color = DarkGray,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    }
                }
                items(libList, key = { g.name + "_lib_" + it }) { ex ->
                    PlanExerciseRow(
                        name = ex,
                        checked = ex in selExercises,
                        isOwn = false,
                        ownPhotoPath = null,
                        targetLabel = targets[ex]?.takeIf { !it.isEmpty }?.label(),
                        onToggle = {
                            if (ex in selExercises) { selExercises.remove(ex); targets.remove(ex) }
                            else selExercises.add(ex)
                        },
                        onGuide = { guideName = ex }
                    )
                }
                // Свои — после готовых и под своим заголовком: ровно так же,
                // как на экране выбора упражнений. Одно и то же должно
                // выглядеть одинаково, где бы человек на него ни смотрел.
                if (ownList.isNotEmpty()) {
                    item(key = "ownhdr_" + g.name) {
                        Text("СВОИ УПРАЖНЕНИЯ", fontSize = 10.sp, color = DarkGray,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    }
                    items(ownList, key = { g.name + "_own_" + it }) { ex ->
                        PlanExerciseRow(
                            name = ex,
                            checked = ex in selExercises,
                            isOwn = true,
                            ownPhotoPath = customExercises.find { it.name == ex }?.photoPath,
                            targetLabel = targets[ex]?.takeIf { !it.isEmpty }?.label(),
                            onToggle = {
                                if (ex in selExercises) { selExercises.remove(ex); targets.remove(ex) }
                                else selExercises.add(ex)
                            },
                            onGuide = { guideName = ex }
                        )
                    }
                }
            }
        }

        // ── Цели: свёрнуто, потому что заполнять не обязательно ──
        if (selExercises.isNotEmpty()) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth()
                        .background(SoftGray, RoundedCornerShape(12.dp))
                        .clickable { showTargets = !showTargets }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (showTargets) "▾" else "▸", fontSize = 13.sp, color = DarkGray)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Цель на подход", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black)
                            Spacer(Modifier.height(2.dp))
                            val set = targets.count { !it.value.isEmpty }
                            Text(
                                if (set > 0) "задано для $set из ${selExercises.size}"
                                else "по желанию — подходы, повторы, вес",
                                fontSize = 12.sp, color = DarkGray
                            )
                        }
                    }
                    if (showTargets) {
                        Spacer(Modifier.height(10.dp))
                        Text("Что собираетесь сделать. В тренировке будет видно рядом с упражнением.",
                            fontSize = 11.sp, color = DarkGray, lineHeight = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        selExercises.forEach { ex ->
                            TargetRow(
                                name = ex,
                                target = targets[ex] ?: ExerciseTarget(),
                                // Способ подсчёта — из справочника. Своё упражнение
                                // там не найдётся — тогда поля как у обычного.
                                type = ExerciseLibrary.exercises.firstOrNull { it.name == ex }
                                    ?.measureType ?: MeasureType.WEIGHT_REPS,
                                onChange = { targets[ex] = it }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // ── Позвать друга ──
        // Стоит последней и закрытой: большинство тренируется в одиночку.
        item { ru.styrk.app.ui.friends.InviteFold(invite) }

        // ── Сохранить ──
        item {
            val hour = hourStr.toIntOrNull()?.coerceIn(0, 23) ?: 19
            val min  = minStr.toIntOrNull()?.coerceIn(0, 59)  ?: 0
            val canSave = selGroups.isNotEmpty() && selDates.isNotEmpty()
            Box(Modifier.fillMaxWidth().height(50.dp)
                .background(if (canSave) Black else LightGray, RoundedCornerShape(12.dp))
                .clickable(enabled = canSave) {
                    onSave(
                        selDates.toList(), hour, min,
                        selGroups.joinToString(", ") { it.title },
                        selExercises.toList(),
                        targets.filterValues { !it.isEmpty },
                        invite.toList()
                    )
                },
                contentAlignment = Alignment.Center) {
                Text(
                    if (selDates.size > 1) "СОХРАНИТЬ НА ${selDates.size} ДНЯ" else "СОХРАНИТЬ",
                    color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    guideName?.let { n ->
        val own = customExercises.find { it.name == n }
        ExerciseGuideDialog(
            name = n,
            ownDescription = own?.description,
            ownPhotoPath = own?.photoPath,
            onDismiss = { guideName = null }
        )
    }
}

/**
 * Строка упражнения в окне планирования.
 *
 * Одна на два списка — готовый справочник и свои упражнения. Держать
 * две одинаковые копии было бы ровно тем, на чём свои упражнения сюда
 * и не попали: правка одной копии второй не касается.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanExerciseRow(
    name: String,
    checked: Boolean,
    isOwn: Boolean,
    ownPhotoPath: String?,
    targetLabel: String?,
    onToggle: () -> Unit,
    onGuide: () -> Unit
) {
    // Нажатие отмечает, долгое нажатие показывает разбор.
    // Нажатие оставлено за отметкой, потому что отмечают
    // здесь постоянно, а разбор смотрят изредка.
    Row(Modifier.fillMaxWidth()
        .combinedClickable(onClick = onToggle, onLongClick = onGuide)
        .background(if (checked) DarkGray.copy(.07f) else Color.Transparent, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MarkBox(checked)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 14.sp, color = Black, modifier = Modifier.weight(1f, fill = false))
            // Звёздочка — та же пометка, что и на экране выбора.
            if (isOwn) {
                Spacer(Modifier.width(6.dp))
                Text("★", color = Gold, fontSize = 11.sp)
            }
        }
        targetLabel?.let {
            Text(it, fontSize = 11.sp, color = Gold, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
        }
        GuideSlot(name, ownPhotoPath) { onGuide() }
    }
}

/**
 * Поле показа ролика в списке упражнений.
 *
 * Ровно то же, что на экране выбора упражнений: 40 точек, ролик или
 * затемнённая заглушка с «i», если ролика нет по задумке (растяжка).
 * Поле есть всегда — иначе строки списка выходят разной длины,
 * а у растяжки карточку разбора открыть было бы просто не за что.
 *
 * Своё упражнение показывает здесь свой снимок, если он есть: готового
 * ролика у него не бывает, а «i» вместо готовой картинки — потеря даром.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideSlot(name: String, ownPhotoPath: String?, onOpen: () -> Unit) {
    val guide = ExerciseGuide.of(name)
    val slot = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(8.dp))
        .combinedClickable(onClick = onOpen, onLongClick = onOpen)
    // Свой снимок важнее готовой картинки: человек снимал именно свой
    // тренажёр. У своего упражнения готовой картинки к тому же и нет.
    if (ownPhotoPath != null) {
        AsyncImage(
            model = java.io.File(ownPhotoPath),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = slot
        )
    } else if (guide != null && guide.hasGif) {
        AsyncImage(
            model = ExerciseGuide.assetUri(guide.gifAsset),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = slot
        )
    } else {
        Box(slot.background(SoftGray), contentAlignment = Alignment.Center) {
            Text("i", color = DarkGray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** «18 — 24 августа» или «29 сент. — 5 окт.», если неделя на стыке. */
private fun weekTitle(start: LocalDate): String {
    val end = start.plusDays(6)
    val ru = Locale.forLanguageTag("ru")
    return if (start.month == end.month) {
        start.dayOfMonth.toString() + " — " +
                end.format(DateTimeFormatter.ofPattern("d MMMM", ru))
    } else {
        start.format(DateTimeFormatter.ofPattern("d MMM", ru)) + " — " +
                end.format(DateTimeFormatter.ofPattern("d MMM", ru))
    }
}

/**
 * Поля цели для одного упражнения. Пустое поле = не задано.
 *
 * Сколько полей — зависит от того, как упражнение считается. Раньше
 * их было три у всех, и у «Боковой планки» спрашивались повторы
 * и килограммы: у упражнения на время нет ни того ни другого.
 * Теперь вес — только там, где он есть, повторы — там, где их
 * считают, а у упражнений на время и дистанцию остаётся число
 * подходов: другой цели сохранить план пока не умеет.
 */
@Composable
private fun TargetRow(
    name: String,
    target: ExerciseTarget,
    type: MeasureType = MeasureType.WEIGHT_REPS,
    onChange: (ExerciseTarget) -> Unit
) {
    val withReps = type == MeasureType.WEIGHT_REPS || type == MeasureType.REPS
    val withWeight = type == MeasureType.WEIGHT_REPS
    Column(Modifier.fillMaxWidth()) {
        Text(name, fontSize = 13.sp, color = Black, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetField(
                value = target.sets?.toString() ?: "", label = "Подх.",
                onChange = { onChange(target.copy(sets = it.toIntOrNull()?.takeIf { n -> n > 0 })) }
            )
            if (withReps) TargetField(
                value = target.reps?.toString() ?: "", label = "Повт.",
                onChange = { onChange(target.copy(reps = it.toIntOrNull()?.takeIf { n -> n > 0 })) }
            )
            if (withWeight) TargetField(
                value = target.weight?.let { w ->
                    if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()
                } ?: "",
                label = "Вес, кг", decimal = true,
                onChange = {
                    onChange(target.copy(
                        weight = it.replace(',', '.').toFloatOrNull()?.takeIf { w -> w > 0f }
                    ))
                }
            )
        }
    }
}

@Composable
private fun TargetField(
    value: String, label: String,
    decimal: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = if (decimal) raw.filter { it.isDigit() || it == '.' || it == ',' }.take(5)
            else raw.filter(Char::isDigit).take(3)
            onChange(cleaned)
        },
        label = { Text(label, color = DarkGray, fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.width(100.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Black, unfocusedBorderColor = LightGray,
            focusedTextColor = Black, unfocusedTextColor = Black, cursorColor = Black
        )
    )
}

// ═══════════════════════════════════════════════════════════════════
//  BOTTOM SHEET ДЕТАЛЕЙ ТРЕНИРОВКИ
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutDetailSheet(
    workout: CompletedWorkout,
    viewModel: TrainingSessionViewModel,
    onDeleted: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("ru"))
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val totalSets   = workout.exercises.sumOf { it.sets.size }
    // Только сделанные упражнения — см. то же место в архиве.
    val shownExercises = workout.exercises.filter { it.sets.isNotEmpty() }
        .ifEmpty { workout.exercises }
    val totalVolume = workout.exercises.sumOf { ex -> ex.sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) } }.toInt()
    val h = workout.durationMinutes/60; val m = workout.durationMinutes%60
    val dur = if (h > 0 && m > 0) "$h ч $m мин" else if (h > 0) "$h ч" else "$m мин"

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Column(Modifier.fillMaxWidth().background(DarkGray).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(workout.dateTime.format(dateFmt), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("${workout.dateTime.format(timeFmt)}  •  ${workout.muscleGroup}", color = White.copy(.7f), fontSize = 13.sp)
        }}
        // Свод по тренировке. У календаря он СВОЙ, отдельный от такого же
        // окна в архиве, — я это упустил и чинил только архивное.
        //
        // Пять блоков в одну строку. Не влезают рядом — переносятся
        // на вторую, а не сжимаются до столбика из букв.
        item { FlowRow(Modifier.fillMaxWidth().background(SoftGray).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DStat("%.1f кг".format(workout.athleteWeight ?: 0f), "вес"); DVert()
            DStat("${shownExercises.size}", "упражн."); DVert()
            DStat("$totalSets", ruPlural(totalSets, "подход", "подхода", "подходов")); DVert()
            DStat("$totalVolume кг", "объём"); DVert()
            DStat(dur, "время")
        }}
        item { Spacer(Modifier.height(14.dp)) }
        items(shownExercises) { ex ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
                Box(Modifier.fillMaxWidth().background(Black, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(ex.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("#", Modifier.width(28.dp), fontSize = 11.sp, color = DarkGray)
                        Text("Вес",   Modifier.weight(1f), fontSize = 11.sp, color = DarkGray)
                        Text("Повт.", Modifier.weight(1f), fontSize = 11.sp, color = DarkGray)
                        Text("Объём", Modifier.weight(1f), fontSize = 11.sp, color = DarkGray)
                    }
                    Spacer(Modifier.height(4.dp)); HorizontalDivider(color = LightGray); Spacer(Modifier.height(4.dp))
                    ex.sets.forEachIndexed { idx, set ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx+1}", Modifier.width(28.dp), color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${fmtW(set.weight)} кг", Modifier.weight(1f), color = Black, fontSize = 14.sp)
                            Text("× ${set.reps}", Modifier.weight(1f), color = Black, fontSize = 14.sp)
                            Text("${PairedExercises.volume(set.weight, set.reps, ex.name).toInt()} кг", Modifier.weight(1f), fontSize = 13.sp, color = DarkGray)
                        }
                        if (idx < ex.sets.lastIndex) HorizontalDivider(color = SoftGray, thickness = .5.dp)
                    }
                    if (ex.sets.isNotEmpty()) {
                        val vol = ex.sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) }.toInt()
                        val max = ex.sets.maxOf { it.weight }
                        Spacer(Modifier.height(6.dp)); HorizontalDivider(color = LightGray); Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Макс: ${fmtW(max)} кг", fontSize = 12.sp, color = DarkGray)
                            Text("Всего: $vol кг", fontSize = 12.sp, color = DarkGray)
                        }
                    }
                }
            }
        }

        // ── ЗАМЕТКИ (если есть) ─────────────────────────────────────
        workout.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                        .background(SoftGray, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("ЗАМЕТКИ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkGray)
                    Spacer(Modifier.height(6.dp))
                    Text(notes, fontSize = 14.sp, color = Black, lineHeight = 19.sp)
                }
            }
        }

        // ── УДАЛИТЬ ТРЕНИРОВКУ ──────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(SoftGray, RoundedCornerShape(12.dp))
                    .clickable { showDeleteConfirm = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Удалить тренировку", color = Color(0xFFE53935), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Удалить тренировку?", fontWeight = FontWeight.Bold) },
            text = { Text("Тренировка и все данные о ней (упражнения, подходы) будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.requestDeleteWorkout(workout.id)
                    showDeleteConfirm = false
                    onDeleted()
                }) { Text("Удалить", color = Color(0xFFE53935), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена", color = DarkGray) }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВСПОМОГАТЕЛЬНЫЕ
// ═══════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════
//  ОКНО: ПОДРОБНО О ЗАПЛАНИРОВАННОЙ ТРЕНИРОВКЕ
//
//  Устроено так же, как окно прошедшей тренировки: тёмная шапка,
//  полоса чисел, список упражнений. Чисел здесь меньше — тренировки
//  ещё не было, и показывать объём или время, которых пока нет,
//  было бы враньём.
// ═══════════════════════════════════════════════════════════════════

// Две оговорки в одной: две подряд Kotlin не допускает —
// эта пометка не повторяемая, у неё список внутри.
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlannedDetailSheet(
    planned: PlannedWorkout,
    customExercises: List<CustomExercise>,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Здесь разбор открывается и обычным нажатием: отмечать в этом
    // списке нечего, он только показывает, что запланировано.
    var guideName by remember { mutableStateOf<String?>(null) }
    val dateFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("ru"))
    val weekday = planned.date.dayOfWeek
        .getDisplayName(JTextStyle.FULL, Locale.forLanguageTag("ru"))
        .replaceFirstChar { it.uppercase() }
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), planned.date)

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {

        item {
            Column(Modifier.fillMaxWidth().background(DarkGray)
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(planned.date.format(dateFmt), color = White,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("%s  •  %02d:%02d  •  %s".format(
                    weekday, planned.hour, planned.minute, planned.muscleGroup),
                    color = White.copy(.7f), fontSize = 13.sp)
            }
        }

        item {
            FlowRow(Modifier.fillMaxWidth().background(SoftGray)
                .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DStat("${planned.exercises.size}", "упражн."); DVert()
                DStat("%02d:%02d".format(planned.hour, planned.minute), "начало"); DVert()
                DStat(
                    when {
                        daysLeft == 0L -> "сегодня"
                        daysLeft == 1L -> "завтра"
                        daysLeft > 1L  -> "через $daysLeft дн."
                        else           -> "прошло"
                    },
                    "когда"
                )
            }
        }

        item { Spacer(Modifier.height(14.dp)) }

        if (planned.exercises.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("Упражнения не выбраны — можно начать и набрать их на месте",
                        color = DarkGray, fontSize = 13.sp)
                }
            }
        } else {
            itemsIndexed(planned.exercises) { i, ex ->
                Row(Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { guideName = ex },
                        onLongClick = { guideName = ex }
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = DarkGray, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Text(ex, color = Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    GuideSlot(ex, customExercises.find { it.name == ex }?.photoPath) { guideName = ex }
                }
                if (i < planned.exercises.lastIndex) {
                    HorizontalDivider(color = SoftGray,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .background(Black, RoundedCornerShape(14.dp))
                    .clickable { onStart() },
                    contentAlignment = Alignment.Center) {
                    Text("НАЧАТЬ ЭТУ ТРЕНИРОВКУ", color = White,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(SoftGray, RoundedCornerShape(14.dp))
                    .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center) {
                    Text("Удалить план", color = DarkGray, fontSize = 13.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = White,
            title = { Text("Удалить план?", fontWeight = FontWeight.Bold) },
            text = { Text("Запланированная тренировка на " +
                    planned.date.format(dateFmt) + " будет убрана из календаря.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Удалить", color = Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена", color = DarkGray)
                }
            }
        )
    }

    guideName?.let { n ->
        val own = customExercises.find { it.name == n }
        ExerciseGuideDialog(
            name = n,
            ownDescription = own?.description,
            ownPhotoPath = own?.photoPath,
            onDismiss = { guideName = null }
        )
    }
}

@Composable private fun CalTab(title: String, sel: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Column(modifier.clickable { onClick() }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
            color = if (sel) Black else DarkGray,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(if (sel) 48.dp else 0.dp).height(3.dp).background(Black, RoundedCornerShape(2.dp)))
    }

@Composable private fun NavCircle(label: String, onClick: () -> Unit) =
    Box(Modifier.size(36.dp).background(White.copy(.12f), CircleShape).clickable { onClick() }, Alignment.Center) {
        Text(label, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }

@Composable private fun MiniStat(v: String, l: String, modifier: Modifier = Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Black, maxLines = 1)
        // Подпись переносится, но не более чем на две строки: слово
        // «запланировано» при крупном шрифте в одну треть экрана
        // не влезает никак, и обрывать его многоточием — хуже,
        // чем перенести. Плашка от двух строк не ломается, у неё
        // высота по содержимому.
        Text(l, fontSize = 11.sp, color = DarkGray,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }

@Composable private fun Dot(c: Color) = Box(Modifier.size(7.dp).background(c, CircleShape))

/**
 * Колечко «вместе».
 *
 * Рисуется двумя кругами, а не рамкой: рамка толщиной меньше точки
 * на плотных экранах расплывается в серое пятно, а два круга
 * остаются двумя кругами при любой плотности. Внутренний — цвета
 * подложки календаря, то есть чёрный.
 */
@Composable private fun TogetherDot(size: Dp) = Box(
    Modifier.size(size).background(White.copy(.85f), CircleShape),
    contentAlignment = Alignment.Center
) {
    Box(Modifier.size(size * 0.42f).background(Black, CircleShape))
}
// Запрет переноса — страховка на случай, когда и переноса блоков мало.
// Тогда надпись обрежется с краю, а не рассыплется по буквам.
@Composable private fun DStat(v: String, l: String) = Column(
    Modifier.padding(horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Black,
        maxLines = 1, softWrap = false)
    Text(l, fontSize = 10.sp, color = DarkGray, maxLines = 1, softWrap = false) }
@Composable private fun DVert() = Box(Modifier.width(1.dp).height(28.dp).background(LightGray))