package ru.styrk.app.ui.records

import ru.styrk.app.ui.common.ruPlural
import ru.styrk.app.data.model.PairedExercises

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.CompletedWorkout
import ru.styrk.app.data.model.ShareText
import ru.styrk.app.ui.common.ShareChip
import ru.styrk.app.ui.common.ShareDialog
import ru.styrk.app.data.model.ExerciseHistory
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.time.format.DateTimeFormatter
import ru.styrk.app.ui.common.CappedFontScale
import ru.styrk.app.ui.common.CHROME_FONT_CAP
import ru.styrk.app.ui.common.DATA_FONT_CAP

private fun fmtW(weight: Float): String =
    if (weight == weight.toInt().toFloat()) weight.toInt().toString() else "%.1f".format(weight)

private fun matchesArchive(workout: CompletedWorkout, q: String): Boolean {
    if (q.isBlank()) return true
    val query = q.trim().lowercase()
    return workout.muscleGroup.lowercase().contains(query) ||
            workout.dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yy")).contains(query) ||
            workout.exercises.any { it.name.lowercase().contains(query) }
}

// ═══════════════════════════════════════════════════════════════════
//  КОРНЕВОЙ ЭКРАН (табы + поиск)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutArchiveScreen(viewModel: TrainingSessionViewModel) {

    val workouts        by viewModel.completedWorkouts.collectAsState()
    val exerciseHistory by viewModel.exerciseHistory.collectAsState()

    var selectedTab  by remember { mutableStateOf(0) }
    var showSearch   by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }
    var detailWorkout by remember { mutableStateOf<CompletedWorkout?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current


    // Сбрасываем поиск при смене таба
    LaunchedEffect(selectedTab) {
        searchQuery = ""
        showSearch = false
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── ТАБЫ + ИКОНКА ПОИСКА ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* Три подписи вместо двух — и потолок на размер шрифта.

               Он здесь не из осторожности, а по арифметике: при
               системном шрифте покрупнее «РЕКОРДЫ ДРУЗЬЯ АРХИВ» плюс
               кнопка поиска перестают помещаться в 320 точек ширины,
               и подписи начинают налезать друг на друга. Потолок
               в 1,1 — тот же, что у всей несущей обвязки приложения.

               Порядок вкладок как в мини-приложении: свои рекорды,
               потом чужие, потом архив. Номера при этом прежние —
               0 рекорды, 1 архив, — чтобы ничего больше не трогать. */
            CappedFontScale(CHROME_FONT_CAP) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabItem("РЕКОРДЫ", selectedTab == 0) {
                        selectedTab = 0
                    }
                    Spacer(Modifier.width(18.dp))
                    TabItem("ДРУЗЬЯ", selectedTab == 2) {
                        selectedTab = 2
                    }
                    Spacer(Modifier.width(18.dp))
                    TabItem("АРХИВ", selectedTab == 1) {
                        selectedTab = 1
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Кнопка поиска / закрытия. На вкладке друзей её нет:
            // искать там нечего, таблица и так короткая.
            if (selectedTab != 2) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (showSearch) DarkGray else SoftGray,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            showSearch = !showSearch
                            if (!showSearch) {
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = if (showSearch) "✕" else "🔍",
                        fontSize = if (showSearch) 14.sp else 16.sp,
                        color    = if (showSearch) White else Black
                    )
                }
            }
        }

        HorizontalDivider(color = SoftGray, thickness = 1.dp)

        // ── ПОИСКОВАЯ СТРОКА (анимирована) ────────────────────────────
        AnimatedVisibility(
            visible = showSearch,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            val hint = if (selectedTab == 0)
                "Упражнение, группа мышц или вес рекорда..."
            else
                "Упражнение, группа мышц или дата..."

            LaunchedEffect(showSearch) {
                if (showSearch) focusRequester.requestFocus()
            }

            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text(hint, fontSize = 13.sp, color = DarkGray) },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                trailingIcon  = {
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            "✕",
                            modifier = Modifier
                                .clickable { searchQuery = "" }
                                .padding(8.dp),
                            fontSize = 14.sp,
                            color    = DarkGray
                        )
                    }
                },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = DarkGray,
                    unfocusedBorderColor = LightGray,
                    cursorColor          = Black,
                    focusedTextColor     = Black,
                    unfocusedTextColor   = Black
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // ── КОНТЕНТ ───────────────────────────────────────────────────
        when (selectedTab) {
            0 -> ExerciseRecordsScreen(viewModel = viewModel, searchQuery = searchQuery)
            2 -> FriendsBoardScreen()
            1 -> ArchiveGrid(
                workouts        = workouts,
                exerciseHistory = exerciseHistory,
                searchQuery     = searchQuery,
                onCardClick     = { detailWorkout = it }
            )
        }
    }

    // ── BOTTOM SHEET С ДЕТАЛЯМИ ────────────────────────────────────
    detailWorkout?.let { workout ->
        ModalBottomSheet(
            onDismissRequest = { detailWorkout = null },
            sheetState       = sheetState,
            containerColor   = White,
            shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            WorkoutDetailSheet(
                workout = workout,
                viewModel = viewModel,
                onDeleted = { detailWorkout = null }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ТАБ-КНОПКА
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TabItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color      = if (selected) Black else DarkGray)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(if (selected) 48.dp else 0.dp).height(3.dp)
            .background(Black, RoundedCornerShape(2.dp)))
    }
}

// ═══════════════════════════════════════════════════════════════════
//  СЕТКА АРХИВА
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ArchiveGrid(
    workouts:        List<CompletedWorkout>,
    exerciseHistory: Map<String, ExerciseHistory>,
    searchQuery:     String,
    onCardClick:     (CompletedWorkout) -> Unit
) {
    val filtered = workouts.filter { matchesArchive(it, searchQuery) }

    if (filtered.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (searchQuery.isBlank()) "Тренировок пока нет" else "Ничего не найдено",
                color = DarkGray
            )
        }
        return
    }

    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        contentPadding        = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(filtered) { workout ->
            ArchiveCard(workout, exerciseHistory) { onCardClick(workout) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  КАРТОЧКА АРХИВА
// ═══════════════════════════════════════════════════════════════════

/**
 * Цифры одинаковой ширины.
 *
 * В обычном шрифте единица уже остальных цифр, и «11.08.26» короче,
 * чем «08.08.26». В столбце дат это выглядит как дрожание: строки
 * вроде и выровнены, а глаз видит, что что-то плавает. tnum включает
 * табличные цифры — все одной ширины.
 */
private val TABULAR = TextStyle(fontFeatureSettings = "tnum")

/** Ширина столбца под кубок. Занята всегда — иначе строки с рекордом
 *  и без него верстаются по-разному, и числа съезжают. */
private val TROPHY_W = 22.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchiveCard(
    workout:         CompletedWorkout,
    exerciseHistory: Map<String, ExerciseHistory>,
    onClick:         () -> Unit
) {
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy")
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val h = workout.durationMinutes / 60
    val m = workout.durationMinutes % 60
    val dur = when { h > 0 && m > 0 -> "$h ч $m мин"; h > 0 -> "$h ч"; else -> "$m мин" }

    val recordCount = workout.exercises.count { ex ->
        val rec  = exerciseHistory[ex.name] ?: return@count false
        val best = ex.sets.maxOfOrNull { it.weight } ?: return@count false
        best >= rec.maxWeight
    }

    // workout.dateTime — момент ОКОНЧАНИЯ тренировки; время начала
    // вычисляем вычитанием длительности
    val startTime = workout.dateTime.minusMinutes(workout.durationMinutes)
    val timeRange = "${startTime.format(timeFmt)} - ${workout.dateTime.format(timeFmt)}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGray, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Карточки идут по две в ряд, то есть на дату со временем
            // остаётся половина экрана.
            //
            // В прошлый раз я обрезал время многоточием — «10:…».
            // Это было плохо: обрезанное время не сообщает ничего,
            // и человек видит не «мне не хватило места», а «приложение
            // потеряло данные».
            //
            // Теперь время уходит ПОД дату, когда не влезает рядом.
            // Пока влезает — строка выглядит ровно как прежде.
            CappedFontScale(DATA_FONT_CAP) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(workout.dateTime.format(dateFmt), color = White, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, style = TABULAR,
                    maxLines = 1, softWrap = false)
                Text(timeRange, color = White.copy(alpha = 0.75f), fontSize = 12.sp,
                    style = TABULAR, maxLines = 1, softWrap = false)
            }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {

            // Ровно две строки всегда — даже если группа одна.
            // Именно из-за этого карточки в соседних колонках разъезжались:
            // у «Груди» строка одна, у «Груди, трицепса, плеч» — две,
            // и всё, что ниже, оказывалось на разной высоте.
            Text(
                workout.muscleGroup, fontSize = 13.sp, color = Black, lineHeight = 18.sp,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))

            // Место под значок рекорда занято всегда, даже когда рекорда
            // нет. Иначе строка веса подпрыгивает от карточки к карточке.
            Row(
                // heightIn вместо height: при крупном шрифте строка
                // с весом переставала помещаться в жёсткие 24 точки,
                // и значок рекорда налезал на цифры.
                Modifier.fillMaxWidth().heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("%.1f кг".format(workout.athleteWeight ?: 0f), color = Black,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, style = TABULAR,
                    maxLines = 1, softWrap = false, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                if (recordCount > 0) {
                    Row(
                        modifier = Modifier
                            .background(Gold.copy(alpha = 0.30f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("🏆", fontSize = 13.sp, maxLines = 1, softWrap = false)
                        Text("× $recordCount", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = Color(0xFF6B5010), maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(dur, fontSize = 13.sp, color = DarkGray, maxLines = 1, softWrap = false)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ДЕТАЛЬНЫЙ BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutDetailSheet(
    workout: CompletedWorkout,
    viewModel: TrainingSessionViewModel,
    onDeleted: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var saveAsMyTitle by remember { mutableStateOf<String?>(null) }
    var savedHint by remember { mutableStateOf<String?>(null) }
    val dateFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.forLanguageTag("ru"))
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val totalSets   = workout.exercises.sumOf { it.sets.size }
    /* Показываем только сделанное. Старые записи хранят и упражнения
       без подходов — их добавили, но не выполнили; в архиве они
       висели блоками «Нет подходов». Новые записи таких уже не содержат. */
    val shownExercises = workout.exercises.filter { it.sets.isNotEmpty() }
        .ifEmpty { workout.exercises }
    val totalVolume = workout.exercises.sumOf { ex -> ex.sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) } }.toInt()
    val userProfile by viewModel.userProfile.collectAsState()
    val kcal = ru.styrk.app.data.model.CalorieCalculator.estimateKcal(
        workout.exercises, workout.durationMinutes,
        workout.athleteWeight ?: userProfile?.weightKg,
        userProfile?.heightCm
    )
    val h = workout.durationMinutes / 60
    val m = workout.durationMinutes % 60
    val dur = when { h > 0 && m > 0 -> "$h ч $m мин"; h > 0 -> "$h ч"; else -> "$m мин" }
    val detailStartTime = workout.dateTime.minusMinutes(workout.durationMinutes)
    val detailTimeRange = "${detailStartTime.format(timeFmt)} - ${workout.dateTime.format(timeFmt)}"

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {

        item {
            Row(Modifier.fillMaxWidth().background(DarkGray).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(workout.dateTime.format(dateFmt), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("$detailTimeRange  •  ${workout.muscleGroup}",
                        color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                ShareChip { showShare = true }
            }
        }

        item {
            // Шесть блоков в один ряд — самое тесное место в приложении.
            // При крупном шрифте они не помещались никак, и подписи
            // рассыпались в столбик по буквам.
            //
            // FlowRow вместо Row: пока всё влезает, ряд выглядит ровно
            // как прежде; когда перестаёт — блоки переносятся на вторую
            // строку. Это лучше, чем сжимать их до нечитаемости: свод
            // по тренировке смотрят, чтобы прочитать числа.
            FlowRow(Modifier.fillMaxWidth().background(SoftGray).padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryBlock("%.1f кг".format(workout.athleteWeight ?: 0f), "вес")
                SummaryDivider()
                SummaryBlock("${shownExercises.size}", "упражн.")
                SummaryDivider()
                SummaryBlock("$totalSets", ruPlural(totalSets, "подход", "подхода", "подходов"))
                SummaryDivider()
                SummaryBlock("$totalVolume кг", "объём")
                SummaryDivider()
                SummaryBlock(dur, "время")
                SummaryDivider()
                SummaryBlock("~$kcal", "ккал")
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        items(shownExercises) { exercise ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                Box(Modifier.fillMaxWidth().background(Black, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(exercise.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Column(Modifier.fillMaxWidth().background(White, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // МОДЕЛЬ V2: колонки «Вес/Повт./Объём» — только для классики;
                    // для остальных типов одна колонка «Результат»
                    val isClassic = exercise.measureType == ru.styrk.app.data.model.MeasureType.WEIGHT_REPS
                    Row(Modifier.fillMaxWidth()) {
                        Text("#",      Modifier.width(28.dp), fontSize = 11.sp, color = DarkGray)
                        if (isClassic) {
                            Text("Вес",   Modifier.weight(1f),   fontSize = 11.sp, color = DarkGray)
                            Text("Повт.", Modifier.weight(1f),   fontSize = 11.sp, color = DarkGray)
                            Text("Объём", Modifier.weight(1f),   fontSize = 11.sp, color = DarkGray)
                        } else {
                            Text("Результат", Modifier.weight(1f), fontSize = 11.sp, color = DarkGray)
                        }
                        // Столбец под кубок — и в шапке тоже, иначе заголовки
                        // стоят не над своими числами.
                        Spacer(Modifier.width(TROPHY_W))
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = LightGray)
                    Spacer(Modifier.height(4.dp))

                    if (exercise.sets.isEmpty()) {
                        Text("Нет подходов", fontSize = 13.sp, color = DarkGray)
                    } else {
                        exercise.sets.forEachIndexed { idx, set ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}", Modifier.width(28.dp), color = Black, fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold, style = TABULAR)
                                if (isClassic) {
                                    Text(if (set.weight > 0f) "${fmtW(set.weight)} кг" else "без веса",
                                        Modifier.weight(1f), color = Black, fontSize = 14.sp, style = TABULAR)
                                    Text("× ${set.reps}", Modifier.weight(1f), color = Black,
                                        fontSize = 14.sp, style = TABULAR)
                                    Text("${PairedExercises.volume(set.weight, set.reps, exercise.name).toInt()} кг", Modifier.weight(1f),
                                        fontSize = 13.sp, color = DarkGray, style = TABULAR)
                                } else {
                                    Text(ru.styrk.app.ui.common.SetFormat.value(exercise.measureType, set),
                                        Modifier.weight(1f), color = Black, fontSize = 14.sp, style = TABULAR)
                                }
                                // Кубок у рекордного подхода. Место под него занято
                                // ВСЕГДА, даже когда рекорда нет.
                                //
                                // Раньше значок просто дописывался в конец строки,
                                // и на строках с ним трём столбцам доставалось меньше
                                // ширины — числа съезжали влево ровно в тех строках,
                                // где стоит кубок. Выглядело так, будто цифры плавают.
                                Box(Modifier.width(TROPHY_W), contentAlignment = Alignment.Center) {
                                    if (viewModel.isSetRecord(exercise.name, exercise.measureType, set)) {
                                        Text("🏆", fontSize = 12.sp)
                                    }
                                }
                            }
                            if (idx < exercise.sets.lastIndex)
                                HorizontalDivider(color = SoftGray, thickness = 0.5.dp)
                        }
                        if (isClassic) {
                            val exVol = exercise.sets.sumOf { PairedExercises.volume(it.weight, it.reps, exercise.name) }.toInt()
                            val exMax = exercise.sets.maxOf { it.weight }
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = LightGray)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Макс: ${fmtW(exMax)} кг", fontSize = 12.sp, color = DarkGray, style = TABULAR)
                                Text("Всего: $exVol кг",         fontSize = 12.sp, color = DarkGray, style = TABULAR)
                            }
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

        // ── ЗАМЕТКА ТРЕНЕРА (если есть) ─────────────────────────────
        //
        // Стоит рядом с самой тренировкой, а не отдельным списком
        // где-то ещё: человек читает «спину держи ровнее» и тут же
        // видит, о какой спине речь.
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                ru.styrk.app.ui.friends.CoachCommentBlock(workout.uuid)
            }
        }

        // ── СОХРАНИТЬ КАК СВОЮ ──────────────────────────────────────
        //
        // Берётся только состав: что делали и в каком порядке. Веса
        // и повторы остаются здесь, в журнале. Образец отвечает
        // на вопрос «что делать», а не «что вышло».
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Black, RoundedCornerShape(12.dp))
                    .clickable { saveAsMyTitle = workout.muscleGroup }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Сохранить как свою", color = White, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold)
            }
        }

        savedHint?.let { hint ->
            item {
                Text(
                    hint, color = DarkGray, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )
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

    // ── ПОДЕЛИТЬСЯ ──────────────────────────────────────────────────
    if (showShare) {
        ShareDialog(
            subject = ShareText.subject(workout),
            text = ShareText.workout(workout) { n, t, s -> viewModel.isSetRecord(n, t, s) },
            fileName = ShareText.fileName(workout),
            workoutUuid = workout.uuid,
            onDismiss = { showShare = false }
        )
    }

    /* Название спрашиваем сразу, а не потом.
       «Грудь, Трицепс» подставлено как есть — чаще всего этого хватает,
       а кому нужно своё имя, тот его и напишет. Заставлять придумывать
       название до того, как человек понял, что вообще сохраняет,
       было бы наоборот. */
    saveAsMyTitle?.let { current ->
        AlertDialog(
            onDismissRequest = { saveAsMyTitle = null },
            title = { Text("Сохранить как свою", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Останется набор упражнений: " +
                            workout.exercises.joinToString(" · ") { it.name },
                        fontSize = 13.sp, color = DarkGray, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = current,
                        onValueChange = { saveAsMyTitle = it },
                        label = { Text("Название") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveWorkoutAsMy(workout, current) { _, message ->
                        savedHint = message
                    }
                    saveAsMyTitle = null
                }) { Text("Сохранить", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { saveAsMyTitle = null }) { Text("Отмена") }
            }
        )
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

@Composable private fun SummaryBlock(value: String, label: String) {
    // Запрет переноса тут не про красоту: без него «подходов»
    // разваливается на буквы, как только блоку становится тесно.
    Column(
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Black,
            maxLines = 1, softWrap = false)
        Text(label, fontSize = 10.sp, color = DarkGray, maxLines = 1, softWrap = false)
    }
}
@Composable private fun SummaryDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(LightGray))
}