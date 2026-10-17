package ru.styrk.app.ui.records

import ru.styrk.app.data.model.PairedExercises

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.CustomExercise
import ru.styrk.app.data.model.ExerciseHistory
import ru.styrk.app.data.model.ExerciseLibrary
import ru.styrk.app.data.model.MeasureType
import ru.styrk.app.data.model.ProgressionAdvisor
import ru.styrk.app.ui.common.SetFormat
import ru.styrk.app.ui.common.AllRecordsButton
import ru.styrk.app.ui.common.FullHistoryChart
import ru.styrk.app.ui.common.SimpleLineChart
import ru.styrk.app.ui.common.ExerciseHowToSection
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.style.TextOverflow
import ru.styrk.app.ui.common.CappedFontScale
import ru.styrk.app.ui.common.DATA_FONT_CAP

private val PRIORITY_EXERCISES = listOf(
    "Жим штанги лёжа",
    "Становая тяга",
    "Приседания со штангой",
    "Подъём штанги на бицепс"
)

private fun fmtW(weight: Float): String =
    if (weight == weight.toInt().toFloat()) weight.toInt().toString() else "%.1f".format(weight)

/**
 * Группа мышц по названию: сначала справочник, потом свои упражнения.
 *
 * Своих тут раньше не было, и поиск по слову «спина» находил рекорды
 * в готовых упражнениях, а в своих — нет. По названию находил, по группе
 * молчал, и понять эту разницу со стороны было нельзя.
 */
private fun muscleGroupOf(name: String, custom: List<CustomExercise>): String =
    ExerciseLibrary.exercises.find { it.name == name }?.muscleGroup?.title?.lowercase()
        ?: custom.find { it.name == name }?.muscleGroup?.title?.lowercase()
        ?: ""

private fun matchesRecord(record: ExerciseHistory, q: String, custom: List<CustomExercise>): Boolean {
    if (q.isBlank()) return true
    val query = q.trim().lowercase()
    return record.exerciseName.lowercase().contains(query) ||
            muscleGroupOf(record.exerciseName, custom).contains(query) ||
            record.maxWeight.toInt().toString().contains(query)
}

// ═══════════════════════════════════════════════════════════════════
//  КОРНЕВОЙ ЭКРАН
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseRecordsScreen(
    viewModel: TrainingSessionViewModel,
    searchQuery: String = ""
) {
    val records by viewModel.exerciseHistory.collectAsState()
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val bodyMetrics by viewModel.bodyMetrics.collectAsState()
    // Свои упражнения: нужны поиску, чтобы он знал их группу мышц.
    val customExercises by viewModel.customExercises.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadUserProfile()
        viewModel.loadBodyMetrics()
    }

    /* Вес тела для разрядных таблиц.

       Берём его тем же способом, что и профиль: самая свежая запись
       из общей истории (тренировки плюс журнал тела), а если истории
       нет — значение из профиля. Считать здесь по-своему нельзя:
       весовая категория поехала бы, и на двух экранах одного
       приложения человек увидел бы два разных разряда. */
    val weightHistory = remember(completedWorkouts, bodyMetrics) { viewModel.getWeightHistory() }
    val bodyWeight = weightHistory.lastOrNull()?.second ?: userProfile?.weightKg

    // Рекорд, по которому открыт bottom sheet с графиками прогресса
    var detailRecord by remember { mutableStateOf<ExerciseHistory?>(null) }

    val mainRecords = records.values
        .filter { it.exerciseName in PRIORITY_EXERCISES && matchesRecord(it, searchQuery, customExercises) }
        .sortedBy { PRIORITY_EXERCISES.indexOf(it.exerciseName) }

    /* «Другие рекорды» — сверху то, что делали последним (R184).
       Раньше сортировка шла по дате рекорда: упражнение, которое
       делаешь каждую неделю, но без нового рекорда, уезжало вниз,
       под то, что делал один раз полгода назад. Журнал отсортирован
       от новых к старым, поэтому первая встреча упражнения — самая
       свежая. При равенстве — по дате рекорда. */
    val lastDone = remember(completedWorkouts) {
        val m = HashMap<String, java.time.LocalDateTime>()
        for (w in completedWorkouts) for (e in w.exercises) m.putIfAbsent(e.name, w.dateTime)
        m
    }
    val otherRecords = records.values
        .filter { it.exerciseName !in PRIORITY_EXERCISES && matchesRecord(it, searchQuery, customExercises) }
        .sortedWith(
            compareByDescending<ExerciseHistory> { lastDone[it.exerciseName] ?: it.recordDate }
                .thenByDescending { it.recordDate }
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        /* Полоска «Сравнить» отсюда УБРАНА — она переехала на свою
           вкладку «Друзья», рядом со всей остальной чужой статистикой.

           Держать её здесь дальше значило бы иметь две двери в одну
           комнату на соседних вкладках. А эта вкладка — про свои числа:
           чужие начинались прямо над строкой «ОСНОВНЫЕ РЕКОРДЫ»
           и сбивали, с чьими рекордами человек имеет дело. */

        /* Разряд стоит первым и только при пустом поиске.

           Первым — потому что это единственная строка на экране,
           которая отвечает на вопрос «а много ли это»: сто двадцать
           килограммов сами по себе не значат ничего, пока не с чем
           сверить. При поиске его нет: человек ищет упражнение,
           а не себя. */
        if (searchQuery.isBlank()) {
            item {
                RankBoard(
                    records = records,
                    sex = userProfile?.sex,
                    bodyWeight = bodyWeight,
                    onPickSex = { viewModel.setSex(it) }
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        item { SectionLabel("ОСНОВНЫЕ РЕКОРДЫ") }

        if (mainRecords.isEmpty()) {
            item { EmptyHint(if (searchQuery.isBlank()) "Выполните тренировки с основными упражнениями" else "Ничего не найдено") }
        } else {
            items(mainRecords) { rec -> RecordCard(rec, true) { detailRecord = rec } }
        }

        item { Spacer(Modifier.height(6.dp)) }
        item { SectionLabel("ДРУГИЕ РЕКОРДЫ") }

        if (otherRecords.isEmpty()) {
            item { EmptyHint(if (searchQuery.isBlank()) "Здесь появятся рекорды по другим упражнениям" else "Ничего не найдено") }
        } else {
            items(otherRecords) { rec -> RecordCard(rec, false) { detailRecord = rec } }
        }
    }

    // ===== ПРОГРЕСС ПО УПРАЖНЕНИЮ (bottom sheet, тап по карточке) =====
    detailRecord?.let { rec ->
        val type = rec.measureType
        // Упражнение со своим весом: тип вес×повторы, но веса нет (max = 0).
        // Тогда прогресс и рекорд показываем по ПОВТОРАМ, а не по кг.
        val bodyweight = type == MeasureType.WEIGHT_REPS && rec.maxWeight <= 0f
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val chartDateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yy") }

        // Точки по тренировкам, где встречалось упражнение (по ИМЕНИ —
        // общая история упражнения). completedWorkouts отсортирован DESC,
        // для графика сортируем по дате по возрастанию.
        val mainPoints = remember(rec.exerciseName, completedWorkouts) {
            completedWorkouts.mapNotNull { w ->
                val sets = w.exercises.filter { it.name == rec.exerciseName }.flatMap { it.sets }
                if (sets.isEmpty()) null else {
                    val v: Float? = when {
                        type == MeasureType.WEIGHT_REPS && bodyweight -> sets.maxOf { it.reps }.toFloat()
                        type == MeasureType.WEIGHT_REPS -> sets.maxOf { it.weight }
                        type == MeasureType.REPS -> sets.maxOf { it.reps }.toFloat()
                        type == MeasureType.TIME -> sets.maxOf { it.seconds ?: 0 }.toFloat()
                        type == MeasureType.DISTANCE -> sets.maxOf { it.distanceKm ?: 0f }
                        else -> null
                    }
                    v?.let { value -> w.dateTime.toLocalDate() to value }
                }
            }.sortedBy { it.first }
        }

        ModalBottomSheet(
            onDismissRequest = { detailRecord = null },
            sheetState = sheetState,
            containerColor = White
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "ПРОГРЕСС: ${rec.exerciseName}",
                    color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                val mainTitle = when (type) {
                    MeasureType.WEIGHT_REPS -> if (bodyweight) "МАКС. ПОВТОРЫ ПО ТРЕНИРОВКАМ" else "МАКС. ВЕС ПО ТРЕНИРОВКАМ, КГ"
                    MeasureType.REPS -> "МАКС. ПОВТОРЫ ПО ТРЕНИРОВКАМ"
                    MeasureType.TIME -> "ЛУЧШЕЕ ВРЕМЯ ПО ТРЕНИРОВКАМ, СЕК"
                    MeasureType.DISTANCE -> "ДИСТАНЦИЯ ПО ТРЕНИРОВКАМ, КМ"
                    MeasureType.CARDIO -> "ПРОГРЕСС"
                }
                var showAllMain by remember { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(16.dp)).padding(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            mainTitle, color = Black, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                        if (mainPoints.size > 1) {
                            AllRecordsButton { showAllMain = true }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SimpleLineChart(
                        values = mainPoints.map { it.second },
                        startLabel = mainPoints.firstOrNull()?.first?.format(chartDateFmt),
                        endLabel = mainPoints.lastOrNull()?.first?.format(chartDateFmt),
                        pointLabels = mainPoints.map { it.first.format(chartDateFmt) }
                    )
                }
                if (showAllMain) {
                    FullHistoryChart(
                        values = mainPoints.map { it.second },
                        labels = mainPoints.map { it.first.format(chartDateFmt) },
                        title = rec.exerciseName,
                        onClose = { showAllMain = false }
                    )
                }

                // Для классики — второй график: объём за тренировку
                if (type == MeasureType.WEIGHT_REPS && !bodyweight) {
                    Spacer(Modifier.height(12.dp))
                    val volumePoints = remember(rec.exerciseName, completedWorkouts) {
                        completedWorkouts.mapNotNull { w ->
                            val sets = w.exercises.filter { it.name == rec.exerciseName }.flatMap { it.sets }
                            if (sets.isEmpty()) null
                            else w.dateTime.toLocalDate() to
                                    sets.sumOf { PairedExercises.volume(it.weight, it.reps, rec.exerciseName) }.toFloat()
                        }.sortedBy { it.first }
                    }
                    var showAllVolume by remember { mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(16.dp)).padding(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                "ОБЪЁМ ЗА ТРЕНИРОВКУ, КГ", color = Black, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                            )
                            if (volumePoints.size > 1) {
                                AllRecordsButton { showAllVolume = true }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SimpleLineChart(
                            values = volumePoints.map { it.second },
                            startLabel = volumePoints.firstOrNull()?.first?.format(chartDateFmt),
                            endLabel = volumePoints.lastOrNull()?.first?.format(chartDateFmt),
                            pointLabels = volumePoints.map { it.first.format(chartDateFmt) },
                            unit = "кг"
                        )
                        // Гантели парами: объём за обе. Без этой строки рекорд
                        // 4000 при подходе 200 × 10 выглядит ошибкой счёта (R198).
                        if (PairedExercises.isPaired(rec.exerciseName)) {
                            Spacer(Modifier.height(8.dp))
                            val w = rec.bestVolumeWeight
                            val r = rec.bestVolumeReps
                            Text(
                                "Объём считается за обе гантели: 2 × вес × повторы." +
                                        (if (w != null && r != null)
                                            " Рекорд ${rec.bestVolume.toInt()} кг = 2 × ${fmtW(w)} × $r."
                                        else ""),
                                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                            )
                        }
                    }
                    if (showAllVolume) {
                        FullHistoryChart(
                            values = volumePoints.map { it.second },
                            labels = volumePoints.map { it.first.format(chartDateFmt) },
                            title = "Объём: " + rec.exerciseName,
                            unit = "кг",
                            onClose = { showAllVolume = false }
                        )
                    }

                    // ===== РЕКОМЕНДАЦИЯ ПО ПРОГРЕССИИ (п4) =====
                    Spacer(Modifier.height(12.dp))
                    val bodyWeight = userProfile?.weightKg
                        ?: completedWorkouts.firstOrNull()?.athleteWeight
                    // последний рабочий подход (макс. вес в самой свежей тренировке с этим упражнением)
                    val lastTopSet = completedWorkouts
                        .firstOrNull { w -> w.exercises.any { it.name == rec.exerciseName } }
                        ?.exercises?.filter { it.name == rec.exerciseName }?.flatMap { it.sets }
                        ?.maxByOrNull { it.weight }
                    val advice = ProgressionAdvisor.advise(
                        name = rec.exerciseName,
                        history = rec,
                        lastTopSet = lastTopSet,
                        recentMainPoints = mainPoints.map { it.second },
                        bodyWeight = bodyWeight
                    )
                    Column(
                        Modifier.fillMaxWidth().background(Black, RoundedCornerShape(16.dp)).padding(14.dp)
                    ) {
                        Text("РЕКОМЕНДАЦИЯ", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(advice.nextTarget, color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(advice.trendLine, color = White.copy(alpha = 0.85f), fontSize = 12.sp)
                        advice.levelLine?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Как прогрессировать", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(advice.method, color = White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Техника", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        advice.technique.forEach { tip ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("•", color = Gold, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(tip, color = White, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }

                // ===== КАК ВЫПОЛНЯТЬ (R184) =====
                // Свёрнутый блок с гифкой и техникой — в самом низу, чтобы
                // не мешать графикам. Для своих упражнений — своё описание
                // и снимок.
                val own = customExercises.find { it.name == rec.exerciseName }
                Spacer(Modifier.height(12.dp))
                ExerciseHowToSection(
                    name = rec.exerciseName,
                    ownDescription = own?.description,
                    ownPhotoPath = own?.photoPath
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  КАРТОЧКА РЕКОРДА
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecordCard(record: ExerciseHistory, isPrimary: Boolean, onClick: () -> Unit = {}) {
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        // Шапка
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGray, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.exerciseName, color = White, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (isPrimary) {
                    Spacer(Modifier.width(8.dp))
                    Text("🏆", fontSize = 18.sp)
                }
            }
        }

        // Тело
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

            // МОДЕЛЬ V2: содержимое карточки зависит от типа упражнения
            when (record.measureType) {
                MeasureType.REPS -> MetricRow(
                    icon     = "🏆",
                    label    = "макс. повторы",
                    bigValue = "${record.maxReps}",
                    subValue = "повт.",
                    date     = record.recordDate?.format(dateFmt),
                    subDate  = record.athleteWeight?.let { "%.1f кг".format(it) }
                )
                MeasureType.TIME -> MetricRow(
                    icon     = "🏆",
                    label    = "лучшее время",
                    bigValue = SetFormat.time(record.bestSeconds ?: 0),
                    subValue = "мин:сек",
                    date     = record.recordDate?.format(dateFmt),
                    subDate  = record.athleteWeight?.let { "%.1f кг".format(it) }
                )
                MeasureType.DISTANCE -> MetricRow(
                    icon     = "🏆",
                    label    = "макс. дистанция",
                    bigValue = SetFormat.num(record.bestDistanceKm ?: 0f),
                    subValue = "км",
                    date     = record.recordDate?.format(dateFmt),
                    subDate  = record.athleteWeight?.let { "%.1f кг".format(it) }
                )
                else -> {
                    if (record.maxWeight > 0f) {
                        // ─ МАКС. ВЕС ────────────────────────────────
                        MetricRow(
                            icon        = "🏆",
                            label       = "макс. вес",
                            bigValue    = "${record.maxWeight.toInt()}",
                            subValue    = "× ${record.maxWeightReps}",
                            date        = record.recordDate?.format(dateFmt),
                            subDate     = record.athleteWeight?.let { "%.1f кг".format(it) }
                        )

                        // Толстый разделитель
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(DarkGray.copy(alpha = 0.22f))
                        )
                        Spacer(Modifier.height(10.dp))

                        // ─ МАКС. ОБЪЁМ ──────────────────────────────
                        val volSubValue = if (record.bestVolumeWeight != null && record.bestVolumeReps != null)
                            "${fmtW(record.bestVolumeWeight)} × ${record.bestVolumeReps}"
                        else "кг"

                        MetricRow(
                            icon        = "📊",
                            label       = "макс. объём",
                            bigValue    = "${record.bestVolume.toInt()}",
                            subValue    = volSubValue,
                            date        = record.recordDate?.format(dateFmt),
                            subDate     = record.athleteWeight?.let { "%.1f кг".format(it) }
                        )
                    } else {
                        // Упражнение со СВОИМ ВЕСОМ (без отягощения): вес и
                        // объём в кг = 0, поэтому рекорд — по числу повторов
                        MetricRow(
                            icon        = "🏆",
                            label       = "макс. повторов (свой вес)",
                            bigValue    = "${record.maxReps}",
                            subValue    = "повт.",
                            date        = record.recordDate?.format(dateFmt),
                            subDate     = record.athleteWeight?.let { "%.1f кг".format(it) }
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  СТРОКА МЕТРИКИ
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MetricRow(
    icon: String, label: String,
    bigValue: String, subValue: String,
    date: String?, subDate: String?
) {
    Column {
        Text(label, fontSize = 11.sp, color = DarkGray)
        Spacer(Modifier.height(6.dp))
        // В одном ряду: значок, большое число, подпись, дата и вес.
        // Тесно даже при обычном шрифте, а при крупном число «1000»
        // рассыпалось в столбик по буквам — на снимке это выглядело
        // как поломка, а не как нехватка места.
        //
        // Потолок 1,25 оставляет запас на увеличение, а запрет переноса
        // у каждой надписи не даёт им рассыпаться, если запаса не хватит.
        CappedFontScale(DATA_FONT_CAP) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Иконка в золотом круге
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Gold.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 20.sp) }

            Spacer(Modifier.width(12.dp))

            // Большое число + подпись
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(bigValue, color = Black, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp, maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Text(subValue, fontSize = 14.sp, color = DarkGray,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp))
            }

            // Дата + вес атлета
            if (date != null || subDate != null) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    date?.let {
                        Text(it, fontSize = 12.sp, color = DarkGray,
                            maxLines = 1, softWrap = false)
                    }
                    subDate?.let {
                        Text(it, fontSize = 11.sp, color = DarkGray.copy(alpha = 0.7f),
                            maxLines = 1, softWrap = false)
                    }
                }
            }
        }
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

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(12.dp)).padding(16.dp),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = DarkGray, textAlign = TextAlign.Center)
    }
}