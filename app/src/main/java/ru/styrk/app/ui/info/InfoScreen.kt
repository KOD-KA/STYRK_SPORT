package ru.styrk.app.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.core.net.toUri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import ru.styrk.app.data.model.MuscleGroup
import ru.styrk.app.data.model.InfoContent
import ru.styrk.app.data.model.Legend
import ru.styrk.app.data.model.ProgramDay
import ru.styrk.app.data.model.TrainingProgram
import ru.styrk.app.data.model.TrainingTip
import ru.styrk.app.data.model.WorkoutPreset
import ru.styrk.app.data.model.WorkoutPresets
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import ru.styrk.app.data.model.MyWorkout
import android.content.Intent
import android.widget.Toast
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
//  ДАННЫЕ
// ═══════════════════════════════════════════════════════════════════

// Советы, программы и легенды лежат в data/model/InfoContent.kt —
// оттуда же веб-дневник собирает свою копию (tools/собрать_инфо.py).

private data class VideoItem(val title: String, val subtitle: String, val watchUrl: String, val thumbnailUrl: String)

// YouTube-видео (техника)
private fun yt(title: String, subtitle: String, id: String) = VideoItem(
    title, subtitle,
    "https://www.youtube.com/watch?v=$id",
    "https://img.youtube.com/vi/$id/hqdefault.jpg"
)

// RUTUBE-видео (зарубежные атлеты). Обложку отдаёт API рутуба по id —
// Coil загружает её по редиректу так же, как ютубовские превью.
private fun rt(title: String, subtitle: String, id: String) = VideoItem(
    title, subtitle,
    "https://rutube.ru/video/$id/",
    "https://rutube.ru/api/video/$id/thumbnail/?redirect=1"
)

private data class VideoSection(val title: String, val videos: List<VideoItem>)

private val VIDEO_SECTIONS = listOf(
    VideoSection("ТЕХНИКА УПРАЖНЕНИЙ", listOf(
        yt("Жим штанги лёжа", "Правильная техника", "nWo_m0REGMA"),
        yt("Становая тяга", "Разбор техники", "ofe8YeSF4F0"),
        yt("Приседания со штангой", "Техника и программа", "aXh2nVAq6-c"),
        yt("Подъём на бицепс", "Техника выполнения", "4VbAyt64r18")
    )),
    VideoSection("ЗВЁЗДЫ ЖЕЛЕЗА · RUTUBE", listOf(
        rt("Арнольд: тренировка груди", "Классика от 7× Мистера Олимпия", "cb1bdb52558e62f54e0ff6777b7ac404"),
        rt("Арнольд: программа, день 1", "Как тренировался Железный Арни", "94dc5afcb4af0d0e04f9b93596707899"),
        rt("Арнольд: бицепс и трицепс", "Продвинутая программа рук", "9ee06938966af90ad0fa330b0dc18731"),
        rt("Крис Бамстед: верх тела", "6× Мистер Олимпия Classic", "245f3dae535f69ffc23aa4808e245cd6"),
        rt("Программа Криса Бамстеда", "Полный разбор на русском", "2ceb2ed3f2aecbc7e574b0571226d706"),
        rt("Бамстед и Дэвид Лейд", "Совместная тренировка в Майами", "a468011ea28e81d106d6219bcb6e294a"),
        rt("Ронни Коулмэн: плечи", "Убойная тренировка плеч", "ff6c33e2883685326360fda06b4ede42"),
        rt("Коулмэн: жим ногами 1044 кг", "Легендарный рекорд", "a95ae4d511a36875884bfd4f9cfac440")
    )),
    VideoSection("ВОРКАУТ · RUTUBE", listOf(
        rt("Программа Ганнибала", "Дойдёшь до конца?", "1ea40c9966fc0bf904c3ce3e945a68ee"),
        rt("Ганнибал: 580 повторений", "Тренировка за 45 минут", "56c8011862e03ed585f24ea38d0f036a"),
        rt("Ганнибал: база для новичков", "С чего начать воркаут", "a01b0cd700408ea79ecfe29146d4e453"),
        rt("Крис Хериа: калистеника", "Тренировка с собственным весом", "d7a13190c9e0a746b292f1b4f0a4a095"),
        rt("Крис Хериа: выход силой", "Обучение, часть 1", "cd408669f2c9fbbb2fd717dfb2218d87"),
        rt("Крис Хериа: стойка на руках", "4 шага к стойке", "ba482e3f4621ce8ac7b29c109c522bf8"),
        rt("Андрей Смаев: подтягивания +170 кг", "Рекорд стритлифтинга", "470740558460e985bfe299e5966d7d31"),
        rt("Метод тренировок Смаева", "Как тренируется богатырь", "d28d9b2e808c617234a0612dc12af7d4")
    )),
    VideoSection("МОТИВАЦИЯ · RUTUBE", listOf(
        rt("Дэвид Гоггинс: 10 минут", "Речь, меняющая жизнь", "f65bbf14a09262e6872dcc2b98e3c97b"),
        rt("Гоггинс бежит и мотивирует", "Час дисциплины", "40748036de8f910aa521ddfcfee11d3e"),
        rt("Гоггинс: жёсткая речь", "Мотивация для жизни", "abb95412f53d988cce8c277dbc6d7884"),
        rt("Бамстед: мотивация в зале", "Настрой чемпиона", "f2309a14d320d2cb32ce6f67eed7fd15"),
        rt("Коулмэн: light weight baby!", "Классика мотивации", "9a2e03afcca6c1ae3d083f5e3dde1aa0"),
        rt("Ганнибал: король воркаута", "Street Workout мотивация", "af474b52390b061cdf8e5b022a334c9d")
    ))
)

// ═══════════════════════════════════════════════════════════════════
//  ROOT
// ═══════════════════════════════════════════════════════════════════

/** Что открыто в «Подробнее». */
private sealed interface InfoDetail {
    data class Preset(val group: MuscleGroup, val preset: WorkoutPreset) : InfoDetail
    data class Program(val program: TrainingProgram) : InfoDetail
    data class LegendD(val legend: Legend) : InfoDetail
}

/** Что начать по готовому списку — ждёт подтверждения, если тренировка уже идёт. */
private data class PendingStart(val title: String, val muscleGroup: String, val exercises: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(viewModel: TrainingSessionViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    var planningDay by remember { mutableStateOf<ProgramDay?>(null) }
    var detail by remember { mutableStateOf<InfoDetail?>(null) }
    var pendingStart by remember { mutableStateOf<PendingStart?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val workoutState by viewModel.state.collectAsState()

    /* Начать по готовому списку. Тот же путь, что у своих тренировок:
       образец без сохранения — название, группа и упражнения. Если
       тренировка уже идёт, сначала спрашиваем: молча заменить её значило
       бы потерять записанные подходы. */
    val startList: (String, String, List<String>) -> Unit = { title, group, exercises ->
        val p = PendingStart(title, group, exercises)
        if (workoutState.trainingStarted) {
            pendingStart = p
        } else {
            detail = null
            viewModel.startMyWorkout(MyWorkout(title = title, muscleGroup = group, exercises = exercises))
        }
        Unit
    }

    Column(Modifier.fillMaxSize().background(White)) {
        // Вкладок стало три, и на узком экране они не помещаются в ряд —
        // поэтому ряд прокручивается вбок, а не жмётся и не переносится.
        Row(
            Modifier.fillMaxWidth().background(White)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            InfoTab("ТРЕНИРОВКИ", selectedTab == 0) { selectedTab = 0 }
            Spacer(Modifier.width(20.dp))
            InfoTab("СОВЕТЫ", selectedTab == 1) { selectedTab = 1 }
            Spacer(Modifier.width(20.dp))
            InfoTab("ВИДЕО", selectedTab == 2) { selectedTab = 2 }
            Spacer(Modifier.width(20.dp))
            InfoTab("О ПРИЛОЖЕНИИ", selectedTab == 3) { selectedTab = 3 }
        }
        HorizontalDivider(color = SoftGray)

        when (selectedTab) {
            0 -> WorkoutsTab(
                viewModel = viewModel,
                onPlanDay = { planningDay = it },
                onDetail = { detail = it }
            )
            1 -> TipsTab()
            2 -> VideoTab()
            else -> AboutTab()
        }
    }

    // ── SHEET: ПОДРОБНЕЕ ────────────────────────────────────────
    detail?.let { d ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = detailSheetState,
            containerColor = if (d is InfoDetail.LegendD) Black else White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            when (d) {
                is InfoDetail.Preset -> WorkoutDetailContent(
                    title = d.group.title + " · " + d.preset.title,
                    subtitle = d.preset.note,
                    stats = listOf(
                        "${d.preset.exercises.size}" to "упражн.",
                        d.preset.volume to "объём"
                    ),
                    guide = d.preset.guide,
                    exercises = d.preset.exercises,
                    onStart = { startList(d.group.title + " · " + d.preset.title, d.group.title, d.preset.exercises) },
                    onPlan = {
                        detail = null
                        planningDay = ProgramDay(d.preset.title, d.group.title, d.preset.exercises)
                    }
                )
                is InfoDetail.Program -> ProgramDetailContent(
                    program = d.program,
                    onPlanDay = { day -> detail = null; planningDay = day }
                )
                is InfoDetail.LegendD -> WorkoutDetailContent(
                    title = d.legend.name,
                    subtitle = d.legend.tagline,
                    stats = listOf(
                        "${d.legend.plannableExercises.size}" to "упражн.",
                        d.legend.muscleGroup to "группы"
                    ),
                    guide = emptyList(),
                    exercises = d.legend.plannableExercises,
                    onStart = { startList(d.legend.name, d.legend.muscleGroup, d.legend.plannableExercises) },
                    onPlan = {
                        detail = null
                        planningDay = ProgramDay(d.legend.name, d.legend.muscleGroup, d.legend.plannableExercises)
                    },
                    legend = d.legend
                )
            }
        }
    }

    // ── ТРЕНИРОВКА УЖЕ ИДЁТ ─────────────────────────────────────
    pendingStart?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            containerColor = White,
            title = { Text("Тренировка уже идёт", fontWeight = FontWeight.Bold) },
            text = { Text("Начать «${p.title}» вместо неё? Записанные в текущей тренировке подходы не сохранятся.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingStart = null; detail = null
                    viewModel.startMyWorkout(MyWorkout(title = p.title, muscleGroup = p.muscleGroup, exercises = p.exercises))
                }) { Text("Начать", color = Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingStart = null }) { Text("Отмена", color = DarkGray) }
            }
        )
    }

    // ── SHEET: ЗАПЛАНИРОВАТЬ ДЕНЬ ПРОГРАММЫ ─────────────────────
    planningDay?.let { day ->
        ModalBottomSheet(
            onDismissRequest = { planningDay = null },
            sheetState = sheetState,
            containerColor = White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            QuickPlanSheet(
                day = day,
                onSave = { date, hour, minute ->
                    viewModel.addPlannedWorkout(date, hour, minute, day.muscleGroup, day.exercises)
                    planningDay = null
                }
            )
        }
    }
}

@Composable
private fun InfoTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Black else DarkGray)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(if (selected) 48.dp else 0.dp).height(3.dp).background(Black, RoundedCornerShape(2.dp)))
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВКЛАДКА: О ПРИЛОЖЕНИИ
//
//  Сюда переехало то, что раньше лежало в профиле: музыка, поддержка
//  автора, сведения о программе. К программе это относится, к человеку
//  и его тренировкам — нет. Профиль теперь только про пользователя.
// ═══════════════════════════════════════════════════════════════════

private const val MUSIC_URL =
    "https://music.yandex.ru/playlists/f2f96823-c096-e64b-8257-9acc9cbfd21d"

/* Прежняя ссылка на Boosty убрана.

   Она вела на страницу ПОДПИСКИ: человек нажимал «Поддержать»
   и оказывался там, где с него просят деньги каждый месяц и надо
   разбираться, за что. Теперь вместо неё — окно с двумя разовыми
   способами (DonateDialog), там же лежат и адреса. */

private const val PRIVACY_URL = "https://styrk.ru/privacy"
private const val SITE_URL = "https://styrk.ru"
private const val AUTHOR_MAIL = "mailto:support@styrk.ru"

@Composable
private fun AboutTab() {
    val context = LocalContext.current
    // Тренерство спрашивают один раз, а профиль открывают каждый день —
    // поэтому кнопка живёт здесь, а не там.
    var showCoach by remember { mutableStateOf(false) }
    if (showCoach) {
        ru.styrk.app.ui.friends.CoachDialog(onDismiss = { showCoach = false })
    }
    var showDonate by remember { mutableStateOf(false) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { null }
    }
    // Открываем во внешнем приложении. try/catch на случай, когда
    // открывать нечем: без браузера ссылка уронила бы приложение.
    val open: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Black, RoundedCornerShape(16.dp))
                .padding(18.dp)) {
                Text("STYRK", color = White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                Spacer(Modifier.height(6.dp))
                Text("Дневник тренировок: подходы, рекорды, архив, календарь " +
                        "с напоминаниями, таймер отдыха и журнал тела.",
                    color = White.copy(.72f), fontSize = 13.sp, lineHeight = 19.sp)
                versionName?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("версия $it", color = White.copy(.45f), fontSize = 12.sp)
                }
            }
        }

        item { SectionLabel("Тренерам") }

        item {
            AboutRow("🎓", "Стать тренером",
                "Планировать тренировки за своих учеников") { showCoach = true }
        }

        item { SectionLabel("Ещё") }

        item {
            AboutRow("🎧", "Музыка для тренировок",
                "Плейлист в Яндекс Музыке") { open(MUSIC_URL) }
        }
        item {
            AboutRow("❤️", "Поддержать автора",
                "Разово, картой или через Сбер") { showDonate = true }
        }
        item {
            AboutRow("✉", "Написать автору",
                "Ошибка, пожелание или просто спасибо") { open(AUTHOR_MAIL) }
        }

        item { SectionLabel("Документы") }

        item {
            AboutRow("🔒", "Политика конфиденциальности",
                "Что происходит с вашими данными") { open(PRIVACY_URL) }
        }
        item {
            AboutRow("🌐", "Сайт приложения",
                "styrk.ru — там же веб-версия дневника") { open(SITE_URL) }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Text("Без аккаунта записи не покидают телефон. " +
                    "Аккаунт нужен только для копии на сервере.",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }

    if (showDonate) {
        DonateDialog(onDismiss = { showDonate = false }, onOpen = { url ->
            showDonate = false
            open(url)
        })
    }
}

@Composable
private fun AboutRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = DarkGray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text("↗", color = DarkGray, fontSize = 14.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВКЛАДКА: ГОТОВЫЕ ТРЕНИРОВКИ
//
//  Один ряд кнопок на всё: «Мои», «Программы», блоки мышц и в конце —
//  «Легенды». По пять подборок на каждый блок: масса, сила, рельеф,
//  вариант без железа и короткая на полчаса. Сначала выбирается блок,
//  потом подборка — так же, как человек и думает: «сегодня грудь,
//  но времени мало».
//
//  Программы и легенды раньше лежали в «Советах», в конце длинного
//  списка, и до них просто не доходили. Им место здесь — среди
//  тренировок, которые можно начать или поставить в календарь.
// ═══════════════════════════════════════════════════════════════════

/** Что показано под рядом кнопок. */
private enum class WorkoutsMode { MINE, PROGRAMS, GROUP, LEGENDS }

@Composable
private fun WorkoutsTab(
    viewModel: TrainingSessionViewModel,
    onPlanDay: (ProgramDay) -> Unit,
    onDetail: (InfoDetail) -> Unit
) {
    val groups = remember { MuscleGroup.visible.filter { WorkoutPresets.has(it) } }
    var selGroup by remember { mutableStateOf(groups.first()) }
    var expanded by remember { mutableStateOf<Int?>(0) }
    var mode by remember { mutableStateOf(WorkoutsMode.GROUP) }
    var expandedProgram by remember { mutableStateOf<Int?>(null) }
    var expandedLegend by remember { mutableStateOf<Int?>(null) }
    val presets = WorkoutPresets.of(selGroup)

    /* Свои тренировки живут здесь, а не среди советов.
       Сюда приходят за набором упражнений на сегодня — и своё
       должно попадаться на глаза раньше чужого. */
    val myWorkouts by viewModel.myWorkouts.collectAsState()
    var confirmDeleteMy by remember { mutableStateOf<MyWorkout?>(null) }
    LaunchedEffect(Unit) { viewModel.loadMyWorkouts() }

    /* Подтверждение удаления.
       Образец собирался руками, и вернуть его нечем: тренировка,
       из которой он сделан, могла уйти из архива. */
    confirmDeleteMy?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteMy = null },
            title = { Text("Удалить «${target.title}»?", fontWeight = FontWeight.Bold) },
            text = { Text("Проведённые тренировки останутся на месте — " +
                    "удалится только этот образец.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMyWorkout(target.id); confirmDeleteMy = null
                }) { Text("Удалить", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMy = null }) { Text("Отмена") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {

        // Ряд кнопок — прокручивается вбок, как и в подборе упражнений
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            /* «Мои» и «Программы» — такие же кнопки, как группа мышц,
               и стоят первыми: своё и недельные планы ищут чаще всего. */
            item {
                Chip(if (myWorkouts.isEmpty()) "Мои" else "Мои · ${myWorkouts.size}",
                    mode == WorkoutsMode.MINE) { mode = WorkoutsMode.MINE }
            }
            item {
                Chip("Программы", mode == WorkoutsMode.PROGRAMS) { mode = WorkoutsMode.PROGRAMS }
            }
            items(groups.size) { i ->
                val g = groups[i]
                Chip(g.title, mode == WorkoutsMode.GROUP && g == selGroup) {
                    mode = WorkoutsMode.GROUP; selGroup = g; expanded = 0
                }
            }
            // «Легенды» — в конце и заметнее остальных: чёрная с золотом.
            item {
                val on = mode == WorkoutsMode.LEGENDS
                Text(
                    "🏆 Легенды",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier
                        .background(Black, RoundedCornerShape(20.dp))
                        .then(if (on) Modifier.border(1.5.dp, Gold, RoundedCornerShape(20.dp)) else Modifier)
                        .clickable { mode = WorkoutsMode.LEGENDS }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        when (mode) {
            WorkoutsMode.MINE -> MyWorkoutsList(
                items = myWorkouts,
                onStart = { viewModel.startMyWorkout(it) },
                onDelete = { confirmDeleteMy = it }
            )

            WorkoutsMode.PROGRAMS -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("План на неделю: дни идут по порядку, каждый можно поставить " +
                            "в календарь кнопкой «+».",
                        fontSize = 12.sp, color = DarkGray, lineHeight = 17.sp)
                }
                items(InfoContent.PROGRAMS.size) { i ->
                    val program = InfoContent.PROGRAMS[i]
                    val open = expandedProgram == i
                    ProgramCard(
                        program = program,
                        expanded = open,
                        onToggle = { expandedProgram = if (open) null else i },
                        onPlanDay = onPlanDay,
                        onDetail = { onDetail(InfoDetail.Program(program)) }
                    )
                }
            }

            WorkoutsMode.LEGENDS -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Как тренировались те, на кого равняются. Не методика под копирку, " +
                            "а разбор подхода — и упражнения, которые можно повторить.",
                        fontSize = 12.sp, color = DarkGray, lineHeight = 17.sp)
                }
                items(InfoContent.LEGENDS.size) { i ->
                    val legend = InfoContent.LEGENDS[i]
                    val open = expandedLegend == i
                    LegendCard(
                        legend = legend,
                        expanded = open,
                        onPlan = { onPlanDay(ProgramDay(legend.name, legend.muscleGroup, legend.plannableExercises)) },
                        onDetail = { onDetail(InfoDetail.LegendD(legend)) }
                    ) { expandedLegend = if (open) null else i }
                }
            }

            WorkoutsMode.GROUP -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presets.size) { i ->
                    PresetCard(
                        preset = presets[i],
                        expanded = expanded == i,
                        onToggle = { expanded = if (expanded == i) null else i },
                        onPlan = {
                            onPlanDay(
                                ProgramDay(
                                    label = presets[i].title,
                                    muscleGroup = selGroup.title,
                                    exercises = presets[i].exercises
                                )
                            )
                        },
                        onDetail = { onDetail(InfoDetail.Preset(selGroup, presets[i])) }
                    )
                }
            }
        }
    }
}

/** Кнопка ряда: выбранная — чёрная, остальные — серые. */
@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) White else DarkGray,
        modifier = Modifier
            .background(if (on) Black else SoftGray, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** «подробнее →» — как в календаре: золотом и мелко. */
@Composable
private fun MoreLink(color: androidx.compose.ui.graphics.Color = Gold, onClick: () -> Unit) {
    Text(
        "подробнее →",
        color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable { onClick() }.padding(vertical = 6.dp)
    )
}

@Composable
private fun PresetCard(
    preset: WorkoutPreset,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlan: () -> Unit,
    onDetail: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(14.dp))) {

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black)
                    Spacer(Modifier.width(8.dp))
                    Text("${preset.exercises.size} упр.", fontSize = 11.sp, color = DarkGray)
                }
                Spacer(Modifier.height(3.dp))
                Text(preset.note, fontSize = 12.sp, color = DarkGray)
            }
            Spacer(Modifier.width(10.dp))
            Text(if (expanded) "▲" else "▼", fontSize = 12.sp, color = DarkGray)
        }

        if (expanded) {
            HorizontalDivider(color = LightGray)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

                // Объём — золотом: это единственное число в карточке,
                // и именно его ищут глазами.
                Text(preset.volume, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(White, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        preset.exercises.forEachIndexed { n, ex ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Text(
                                    "${n + 1}",
                                    fontSize = 11.sp, color = DarkGray,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(ex, fontSize = 13.sp, color = Black)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.size(34.dp)
                            .background(Black, CircleShape)
                            .clickable { onPlan() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Порядок важен: первым идёт самое тяжёлое движение",
                        fontSize = 11.sp, color = DarkGray, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    MoreLink(onClick = onDetail)
                }
            }
        }
    }
}

/** Список своих наборов — или объяснение, откуда они берутся. */
@Composable
private fun MyWorkoutsList(
    items: List<MyWorkout>,
    onStart: (MyWorkout) -> Unit,
    onDelete: (MyWorkout) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (items.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(SoftGray, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("Пока пусто", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Проведите тренировку, откройте её в архиве и нажмите " +
                                "«Сохранить как свою». Набор упражнений останется здесь, " +
                                "и повторить его можно будет одним нажатием.",
                        fontSize = 12.sp, color = DarkGray, lineHeight = 17.sp
                    )
                }
            }
        }
        items(items.size) { i ->
            val my = items[i]
            MyWorkoutCard(my = my, onStart = { onStart(my) }, onDelete = { onDelete(my) })
        }
    }
}

/**
 * Карточка своей тренировки: название, группы, состав и два действия.
 *
 * Состав показан сразу, а не под раскрытием: список короткий, а вопрос
 * «что там внутри» — единственный, ради которого сюда заходят.
 */
@Composable
private fun MyWorkoutCard(my: MyWorkout, onStart: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(my.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black)
                Spacer(Modifier.height(2.dp))
                Text(
                    my.muscleGroup + " · " + my.exercises.size + " " +
                            plural(my.exercises.size, "упражнение", "упражнения", "упражнений"),
                    fontSize = 12.sp, color = DarkGray
                )
            }
            Text("✕", fontSize = 15.sp, color = DarkGray,
                modifier = Modifier.clickable { onDelete() }.padding(start = 12.dp, end = 2.dp))
        }

        if (my.exercises.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            my.exercises.forEach { name ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•", color = Gold, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(name, fontSize = 13.sp, color = Black, lineHeight = 17.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .background(Black, RoundedCornerShape(12.dp))
                .clickable { onStart() },
            contentAlignment = Alignment.Center
        ) {
            Text("НАЧАТЬ", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** «1 упражнение», «2 упражнения», «5 упражнений». */
private fun plural(n: Int, one: String, few: String, many: String): String {
    val a = kotlin.math.abs(n) % 100
    val b = a % 10
    return when {
        a in 11..19 -> many
        b in 2..4   -> few
        b == 1      -> one
        else        -> many
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВКЛАДКА: СОВЕТЫ
//
//  Только советы: программы и легенды переехали в «Тренировки».
//  Вторая часть — «Из зала»: то, что рассказывают друг другу у стойки,
//  но отобранное по исследованиям, а популярные мифы — разобранные.
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TipsTab() {
    var expandedTip by remember { mutableStateOf<Int?>(null) }
    var expandedGym by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionLabel("СОВЕТЫ") }

        items(InfoContent.TIPS.size) { index ->
            val tip = InfoContent.TIPS[index]
            val expanded = expandedTip == index
            TipCard(tip, expanded) { expandedTip = if (expanded) null else index }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { SectionLabel("ИЗ ЗАЛА") }
        item {
            Text("Неофициально, но проверено исследованиями. Это не медицинский совет: " +
                    "при болезнях — сначала к врачу.",
                fontSize = 12.sp, color = DarkGray, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
        }

        items(InfoContent.GYM_TIPS.size) { index ->
            val tip = InfoContent.GYM_TIPS[index]
            val expanded = expandedGym == index
            TipCard(tip, expanded) { expandedGym = if (expanded) null else index }
        }

        // ── ВЕРСИЯ ПРИЛОЖЕНИЯ ────────────────────────────────────────
        item {
            val context = LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    null
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "STYRK" + (versionName?.let { " · v$it" } ?: ""),
                fontSize = 11.sp,
                color = DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TipCard(tip: TrainingTip, expanded: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Gold.copy(alpha = 0.18f), CircleShape), Alignment.Center) {
                Text(tip.icon, fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(tip.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black, modifier = Modifier.weight(1f))
            Text(if (expanded) "▲" else "▼", fontSize = 11.sp, color = DarkGray)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(tip.body, fontSize = 13.sp, color = DarkGray, lineHeight = 19.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  КАРТОЧКА ПРОГРАММЫ — раскрывается, показывает дни с "+"
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ProgramCard(
    program: TrainingProgram,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlanDay: (ProgramDay) -> Unit,
    onDetail: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(14.dp))) {

        // Шапка — тап раскрывает/сворачивает
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(program.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text("${program.days.size} дн.", fontSize = 11.sp, color = DarkGray)
                }
                Spacer(Modifier.height(3.dp))
                Text(program.subtitle, fontSize = 12.sp, color = DarkGray)
            }
            Spacer(Modifier.width(10.dp))
            Text(if (expanded) "▲" else "▼", fontSize = 12.sp, color = DarkGray)
        }

        if (expanded) {
            HorizontalDivider(color = LightGray)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                program.days.forEachIndexed { idx, day ->
                    ProgramDayRow(day = day, onPlanClick = { onPlanDay(day) })
                    if (idx < program.days.lastIndex) Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MoreLink(onClick = onDetail)
                }
            }
        }
    }
}

@Composable
private fun ProgramDayRow(day: ProgramDay, onPlanClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Black)
                Spacer(Modifier.width(6.dp))
                Text("• ${day.muscleGroup}", fontSize = 12.sp, color = DarkGray)
            }
            Spacer(Modifier.height(6.dp))
            day.exercises.forEach { ex ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(5.dp).background(DarkGray, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(ex, fontSize = 13.sp, color = Black)
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // Кнопка "+" — запланировать этот день
        Box(
            modifier = Modifier.size(34.dp)
                .background(Black, CircleShape)
                .clickable { onPlanClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  SHEET: БЫСТРОЕ ПЛАНИРОВАНИЕ ДНЯ ПРОГРАММЫ
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QuickPlanSheet(
    day: ProgramDay,
    onSave: (LocalDate, Int, Int) -> Unit
) {
    val today = LocalDate.now()
    val next14Days = remember { (0..13).map { today.plusDays(it.toLong()) } }
    var selDate by remember { mutableStateOf(today) }
    var hourStr by remember { mutableStateOf("19") }
    var minStr  by remember { mutableStateOf("00") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("ЗАПЛАНИРОВАТЬ ТРЕНИРОВКУ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Black,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Spacer(Modifier.height(6.dp))

        // Превью того что планируем
        Column(
            modifier = Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text("${day.label} • ${day.muscleGroup}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Black)
            Spacer(Modifier.height(4.dp))
            Text(day.exercises.joinToString(", "), fontSize = 12.sp, color = DarkGray)
        }

        Spacer(Modifier.height(16.dp))
        Text("Дата", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(next14Days.size) { i ->
                val date = next14Days[i]
                val sel = date == selDate
                val dayName = date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() }
                Box(
                    modifier = Modifier.width(52.dp).heightIn(min = 56.dp)
                        .background(if (sel) DarkGray else SoftGray, RoundedCornerShape(10.dp))
                        .clickable { selDate = date },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayName, fontSize = 10.sp, color = if (sel) White.copy(.7f) else DarkGray)
                        Text("${date.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (sel) White else Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Время", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val minuteFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            OutlinedTextField(hourStr, { hourStr = it.filter(Char::isDigit).take(2) },
                label = { Text("Час", color = DarkGray) }, modifier = Modifier.width(90.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { minuteFocusRequester.requestFocus() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Black, unfocusedBorderColor = LightGray,
                    focusedTextColor = Black, unfocusedTextColor = Black, cursorColor = Black))
            Text(":", color = Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(minStr, { minStr = it.filter(Char::isDigit).take(2) },
                label = { Text("Мин", color = DarkGray) },
                modifier = Modifier.width(90.dp).focusRequester(minuteFocusRequester), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Black, unfocusedBorderColor = LightGray,
                    focusedTextColor = Black, unfocusedTextColor = Black, cursorColor = Black))
        }

        Spacer(Modifier.height(20.dp))

        val hour = hourStr.toIntOrNull()?.coerceIn(0, 23) ?: 19
        val min  = minStr.toIntOrNull()?.coerceIn(0, 59)  ?: 0

        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .background(Black, RoundedCornerShape(12.dp))
                .clickable { onSave(selDate, hour, min) },
            contentAlignment = Alignment.Center
        ) {
            Text("СОХРАНИТЬ", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВКЛАДКА: ВИДЕО (с превью-картинками)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun VideoTab() {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        VIDEO_SECTIONS.forEach { section ->
            item(span = { GridItemSpan(2) }) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel(section.title)
                }
            }
            items(section.videos.size) { index ->
                val video = section.videos[index]
                VideoCard(video) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, video.watchUrl.toUri()))
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        // Превью с YouTube-обложкой
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(DarkGray)
            )
            // Затемнение + play-кнопка поверх превью
            Box(
                modifier = Modifier.fillMaxSize().background(Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(White.copy(alpha = 0.92f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Black, fontSize = 15.sp)
                }
            }
        }

        // Подписи под превью
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Black, lineHeight = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(video.subtitle, fontSize = 11.sp, color = DarkGray)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВСПОМОГАТЕЛЬНЫЕ
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = LightGray)
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkGray)
        HorizontalDivider(Modifier.weight(1f), color = LightGray)
    }
}
// ═══════════════════════════════════════════════════════════════════
//  КАРТОЧКА ЛЕГЕНДЫ
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LegendCard(
    legend: Legend,
    expanded: Boolean,
    onPlan: () -> Unit,
    onDetail: () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Black, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(legend.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(legend.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = White)
                Text(legend.tagline, fontSize = 11.sp, color = Gold)
            }
            // «+» — запланировать (попробовать повторить) тренировку легенды.
            // Своя clickable-зона, тап по ней не разворачивает карточку.
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(White, CircleShape)
                    .clickable { onPlan() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(if (expanded) "▲" else "▼", fontSize = 11.sp, color = White.copy(alpha = 0.6f))
        }
        // Цитата — сразу под именем, даже в свёрнутой карточке: ради неё
        // карточку и открывают.
        legend.quote?.let { q ->
            Spacer(Modifier.height(8.dp))
            Text("«$q»", fontSize = 12.sp, color = White.copy(alpha = 0.8f), lineHeight = 17.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(legend.description, fontSize = 12.sp, color = White.copy(alpha = 0.85f), lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = White.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            legend.workout.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(5.dp).background(Gold, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(line, fontSize = 12.sp, color = White, lineHeight = 17.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MoreLink(onClick = onDetail)
            }
        }
    }
}