package ru.styrk.app.ui.profile

import ru.styrk.app.data.model.PairedExercises

import android.net.Uri
import androidx.core.graphics.scale
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.styrk.app.data.local.entity.BodyMetricEntity
import ru.styrk.app.ui.common.AllRecordsButton
import ru.styrk.app.ui.common.FullHistoryChart
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.common.SetFormat
import ru.styrk.app.ui.common.SimpleLineChart
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Вкладка «Профиль» (страница 4 Pager).
 *
 * Один экран с прокруткой: шапка, физические данные, сводная статистика,
 * ТЕЛО (вес и замеры с графиками), сравнение с нормативами.
 *
 * v1.8.4: то, что открывают редко, свёрнуто в компактные строки —
 * журнал замеров и «Данные». Каждая строка открывает своё окно
 * и не занимает место на экране.
 *
 * Здесь только то, что относится к пользователю: его тело, его записи,
 * его аккаунт. Музыка, поддержка автора и сведения о программе
 * переехали в раздел «Инфо» — к самой программе они относятся,
 * а к человеку нет.
 */
@Composable
fun ProfileScreen(viewModel: ru.styrk.app.viewmodel.TrainingSessionViewModel) {

    val workouts by viewModel.completedWorkouts.collectAsState()
    val records by viewModel.exerciseHistory.collectAsState()
    val bodyMetrics by viewModel.bodyMetrics.collectAsState()
    val context = LocalContext.current
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yy") }

    // ИСПРАВЛЕНО: профиль тоже перезагружает архив — иначе вес из только что
    // завершённой тренировки не попадал в график (архив грузился лишь на
    // вкладке «Архив»)
    LaunchedEffect(Unit) {
        viewModel.loadBodyMetrics()
        viewModel.loadUserProfile()
        viewModel.loadArchiveFromDatabase()
        viewModel.loadRecordsFromDatabase()
    }

    // Workouts отсортирован DESC (новые первые) → самая ПЕРВАЯ тренировка
    // в жизни — это ПОСЛЕДНИЙ элемент списка. Здесь lastOrNull() корректен.
    val firstWorkoutDate = workouts.lastOrNull()?.dateTime?.toLocalDate()
    val daysSinceStart = firstWorkoutDate?.let {
        ChronoUnit.DAYS.between(it, LocalDate.now())
    }
    val last30 = workouts.count { it.dateTime.toLocalDate().isAfter(LocalDate.now().minusDays(30)) }
    val totalVolumeKg = workouts.sumOf { w ->
        w.exercises.sumOf { ex -> ex.sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) } }
    }

    val weightHistory = remember(workouts, bodyMetrics) { viewModel.getWeightHistory() }
    val currentWeight = weightHistory.lastOrNull()?.second
    val userProfile by viewModel.userProfile.collectAsState()
    // Вес для ИМТ и сравнения: САМАЯ СВЕЖАЯ запись (тренировки + журнал тела),
    // а если истории нет — значение из профиля
    val effectiveWeight = currentWeight ?: userProfile?.weightKg
    // Средний вес за последние 4 записи (тренировки + журнал) — сглаживает
    // суточные колебания, по нему удобнее судить о реальной динамике
    val avgRecentWeight = weightHistory.takeLast(4)
        .map { it.second }
        .takeIf { it.isNotEmpty() }
        ?.average()?.toFloat()
    val profileHeight = userProfile?.heightCm
    val bmi = if (profileHeight != null && profileHeight > 0f && effectiveWeight != null)
        effectiveWeight / ((profileHeight / 100f) * (profileHeight / 100f)) else null

    // ===== ЛАУНЧЕРЫ БЭКАПА (SAF) =====
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportBackup(context, it) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { pendingImportUri = it } }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Восстановить из бэкапа?") },
            text = {
                Text(
                    "Все текущие данные (архив, рекорды, план, свои упражнения, " +
                            "журнал тела, профиль и фото) будут ЗАМЕНЕНЫ данными из файла. " +
                            "Отменить это будет нельзя."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    viewModel.importBackup(context, uri) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("ВОССТАНОВИТЬ") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Отмена") }
            }
        )
    }

    // ===== ДИАЛОГ ДОБАВЛЕНИЯ ЗАМЕРОВ =====
    var showMeasureDialog by remember { mutableStateOf(false) }
    if (showMeasureDialog) {
        MeasureDialog(
            onDismiss = { showMeasureDialog = false },
            onSave = { entity ->
                viewModel.addBodyMetric(entity)
                showMeasureDialog = false
                Toast.makeText(context, "Замеры записаны", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ===== ФОТО ПРОФИЛЯ + РЕДАКТИРОВАНИЕ (п1, п2) =====
    var showProfileDialog by remember { mutableStateOf(false) }
    // п8: пояснение к ИМТ; п9: подтверждение удаления замера
    var showBmiInfo by remember { mutableStateOf(false) }
    var pendingDeleteMetric by remember { mutableStateOf<BodyMetricEntity?>(null) }
    // v1.8.4: содержимое свёрнутых строк — каждое в своём окне
    var showMetricsJournal by remember { mutableStateOf(false) }
    var showDataDialog     by remember { mutableStateOf(false) }
    // Вся история графика на весь экран — по кнопке в шапке карточки
    var showAllWeight by remember { mutableStateOf(false) }
    var showAllMetric by remember { mutableStateOf(false) }
    // Аккаунт для синхронизации (v1.8.5). Читаем почту при каждом открытии
    // окна: пользователь мог войти или выйти прямо в нём.
    var showAccountDialog  by remember { mutableStateOf(false) }
    // Друзья — там же, где аккаунт, и по той же причине: без аккаунта
    // их не бывает. Шестой вкладки заводить не стал, вид приложения
    // от этого не меняется.
    var showFriendsDialog  by remember { mutableStateOf(false) }
    var accountVersion     by remember { mutableIntStateOf(0) }
    val signedInEmail = remember(accountVersion, showAccountDialog) {
        ru.styrk.app.sync.SyncPrefs(context).let { if (it.isSignedIn) it.email else null }
    }
    // п7 (v1.8.5): выбранное фото сначала попадает в окно кадрирования,
    // и только оттуда — в профиль. Раньше картинка молча обрезалась по
    // центру, и попасть лицом в круг было делом случая.
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> pendingPhoto = uri }

    pendingPhoto?.let { uri ->
        PhotoCropDialog(
            uri = uri,
            onDismiss = { pendingPhoto = null },
            onSave = { path ->
                pendingPhoto = null
                if (path != null) {
                    viewModel.saveUserProfile(userProfile?.name, path, userProfile?.heightCm, userProfile?.weightKg)
                    Toast.makeText(context, "Фото обновлено", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    if (showProfileDialog) {
        ProfileEditDialog(
            initialName = userProfile?.name ?: "",
            initialHeight = userProfile?.heightCm,
            initialWeight = userProfile?.weightKg,
            initialSex = userProfile?.sex,
            onDismiss = { showProfileDialog = false },
            onSave = { name, h, w, sx ->
                viewModel.saveUserProfile(name.ifBlank { null }, userProfile?.photoPath, h, w,
                    sex = sx ?: userProfile?.sex)
                showProfileDialog = false
            }
        )
    }

    // ── п8: ЧТО ТАКОЕ ИМТ ──
    if (showBmiInfo) {
        AlertDialog(
            onDismissRequest = { showBmiInfo = false },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            confirmButton = { TextButton(onClick = { showBmiInfo = false }) { Text("Понятно", color = Black) } },
            title = { Text("Индекс массы тела (ИМТ)", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("ФОРМУЛА", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("ИМТ = вес (кг) ÷ рост² (м)", color = Black, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    if (profileHeight != null && effectiveWeight != null && bmi != null) {
                        val m = profileHeight / 100f
                        Text(
                            "Ваш расчёт: %.1f ÷ (%.2f × %.2f) = %.1f".format(
                                effectiveWeight, m, m, bmi
                            ),
                            color = Black, fontSize = 13.sp
                        )
                    } else {
                        Text(
                            "Заполните рост и вес в профиле — тогда покажем ваш расчёт",
                            color = DarkGray, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("НОРМЫ (ВОЗ)", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "менее 18.5 — дефицит массы",
                        "18.5 – 24.9 — норма",
                        "25.0 – 29.9 — избыточная масса",
                        "30.0 – 34.9 — ожирение I степени",
                        "35.0 – 39.9 — ожирение II степени",
                        "40.0 и выше — ожирение III степени"
                    ).forEach {
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = Black, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Важно: ИМТ не различает мышцы и жир. У атлетов с большой мышечной массой " +
                                "он часто показывает «избыток» при низком проценте жира — ориентируйтесь " +
                                "также на замеры и внешний вид.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }
            }
        )
    }

    // ── п9: ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ ЗАМЕРА ──
    pendingDeleteMetric?.let { metric ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMetric = null },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Удалить замер?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Запись за " + LocalDate.ofEpochDay(metric.date).format(dateFmt) +
                            " будет удалена без возможности восстановления."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.requestDeleteBodyMetric(metric.id)
                    pendingDeleteMetric = null
                }) { Text("Удалить", color = Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMetric = null }) { Text("Отмена", color = DarkGray) }
            }
        )
    }

    // ── ОКНО: ЖУРНАЛ ЗАМЕРОВ ──
    if (showMetricsJournal && bodyMetrics.isNotEmpty()) {
        MetricsJournalDialog(
            metrics = bodyMetrics,
            dateFmt = dateFmt,
            onDelete = { m ->
                // Сначала закрываем журнал, иначе подтверждение удаления
                // откроется поверх него и экран станет «двухслойным»
                showMetricsJournal = false
                pendingDeleteMetric = m
            },
            onDismiss = { showMetricsJournal = false }
        )
    }

    // ── ОКНО: ДАННЫЕ (бэкап / восстановление) ──
    if (showDataDialog) {
        Dialog(onDismissRequest = { showDataDialog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(White, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text("Данные", color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    emoji = "💾",
                    title = "Сохранить бэкап",
                    subtitle = "Все данные в один файл — архив, рекорды, план, свои упражнения, журнал тела"
                ) {
                    showDataDialog = false
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    exportLauncher.launch("styrk_backup_$today.json")
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    emoji = "📂",
                    title = "Восстановить из бэкапа",
                    subtitle = "Заменит текущие данные данными из файла (работает и со старыми версиями бэкапов)"
                ) {
                    showDataDialog = false
                    importLauncher.launch(arrayOf("*/*"))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RowAction("Закрыть", strong = true) { showDataDialog = false }
                }
            }
        }
    }

    // ── ОКНО: АККАУНТ ──
    if (showFriendsDialog) {
        ru.styrk.app.ui.friends.FriendsDialog(onDismiss = { showFriendsDialog = false })
    }

    if (showAccountDialog) {
        AccountDialog(
            onDismiss = { showAccountDialog = false; accountVersion++ },
            onDataChanged = {
                // Приехали данные с сервера — перечитываем всё, что
                // показано на экранах, иначе увидим их только после
                // перезапуска приложения.
                viewModel.loadArchiveFromDatabase()
                viewModel.loadRecordsFromDatabase()
                viewModel.loadCustomExercises()
                viewModel.loadPlannedWorkouts()
                viewModel.loadBodyMetrics()
                viewModel.loadUserProfile()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {

        Spacer(Modifier.height(16.dp))

        // ===== ШАПКА: фото + имя (п1) =====
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Black, CircleShape)
                    .clickable { photoLauncher.launch(arrayOf("image/*")) },
                contentAlignment = Alignment.Center
            ) {
                val photo = userProfile?.photoPath
                if (photo != null) {
                    AsyncImage(
                        model = java.io.File(photo),
                        contentDescription = "Фото профиля",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Default.Person, "Профиль", tint = White, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // Только имя: вес и рост показаны в карточках ниже,
                // дублировать их здесь не нужно
                Text(
                    userProfile?.name?.takeIf { it.isNotBlank() } ?: "Ваше имя",
                    color = Black, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    // Длинное имя обрывалось без всякого знака — выглядело
                    // как опечатка в самом имени. Многоточие честнее:
                    // сразу видно, что текст не поместился.
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Изменить", color = DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, softWrap = false,
                modifier = Modifier.clickable { showProfileDialog = true }.padding(8.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== ФИЗИЧЕСКИЕ ДАННЫЕ: рост / вес / ИМТ (п2) =====
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatCard(profileHeight?.let { "${it.toInt()}" } ?: "—", "рост, см", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatCard(effectiveWeight?.let { "%.1f".format(it) } ?: "—", "вес, кг", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatCard(
                bmi?.let { "%.1f".format(it) } ?: "—",
                bmiCategory(bmi),
                Modifier.weight(1f).clickable { showBmiInfo = true }
            )
        }

        /* Строки «Нажмите на ИМТ — что это за число» здесь больше нет.

           Её поставили, чтобы нажатие не осталось тайной, — а вышло,
           что под тремя плитками каждый день висит инструкция к одной
           из них. Экран профиля смотрят часто, а объяснение нужно
           один раз. Плитка ИМТ по-прежнему открывает его по нажатию;
           лишнего текста на экране нет. */
        Spacer(Modifier.height(12.dp))

        // ===== СВОДНАЯ СТАТИСТИКА (2×2) =====
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatCard(workouts.size.toString(), "тренировок всего", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatCard(last30.toString(), "за 30 дней", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatCard(records.size.toString(), "рекордов", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatCard(daysSinceStart?.let { "$it" } ?: "—", "дней с первой тренировки", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Black, RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (totalVolumeKg >= 1000)
                        "%.1f т".format(totalVolumeKg / 1000.0)
                    else
                        "%.0f кг".format(totalVolumeKg),
                    color = White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("общий поднятый объём", color = White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ===== ТЕЛО: ВЕС И ЗАМЕРЫ =====
        Text("ТЕЛО", color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // График веса
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SoftGray, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            // Шапка как в браузере: слева заголовок и текущее значение
            // строкой ниже, справа — кнопка всей истории. Раньше справа
            // стояло само число, и кнопке там было негде встать.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("ВЕС, КГ", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val sub = listOfNotNull(
                        currentWeight?.let { "%.1f кг".format(it) },
                        avgRecentWeight?.let {
                            "средний за %d: %.1f".format(minOf(4, weightHistory.size), it)
                        }
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, color = DarkGray, fontSize = 11.sp)
                    }
                }
                if (weightHistory.size > 1) {
                    AllRecordsButton { showAllWeight = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            SimpleLineChart(
                values = weightHistory.map { it.second },
                startLabel = weightHistory.firstOrNull()?.first?.format(dateFmt),
                endLabel = weightHistory.lastOrNull()?.first?.format(dateFmt),
                pointLabels = weightHistory.map { it.first.format(dateFmt) },
                unit = "кг"
            )
        }

        if (showAllWeight) {
            FullHistoryChart(
                values = weightHistory.map { it.second },
                labels = weightHistory.map { it.first.format(dateFmt) },
                title = "Вес, кг",
                unit = "кг",
                onClose = { showAllWeight = false }
            )
        }

        Spacer(Modifier.height(12.dp))

        // График выбранного замера
        val metricOptions: List<Pair<String, (BodyMetricEntity) -> Float?>> = remember {
            listOf(
                "Грудь" to { m: BodyMetricEntity -> m.chest },
                "Талия" to { m: BodyMetricEntity -> m.waist },
                "Бёдра" to { m: BodyMetricEntity -> m.hips },
                "Бицепс" to { m: BodyMetricEntity -> m.biceps },
                "Предплечье" to { m: BodyMetricEntity -> m.forearm },
                "Бедро" to { m: BodyMetricEntity -> m.thigh },
                "Икра" to { m: BodyMetricEntity -> m.calf },
                "Шея" to { m: BodyMetricEntity -> m.neck },
                "Плечи" to { m: BodyMetricEntity -> m.shoulders }
            )
        }
        var selectedMetric by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SoftGray, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            // Точки считаем до шапки: кнопка «ВСЕ ЗАПИСИ» должна знать,
            // есть ли что показывать ИМЕННО у выбранного замера. Замеров
            // может быть три, а обхват груди записан один раз.
            val selector = metricOptions[selectedMetric].second
            val metricPoints = bodyMetrics.mapNotNull { m ->
                selector(m)?.let { LocalDate.ofEpochDay(m.date) to it }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    "ЗАМЕРЫ, СМ", color = Black, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                if (metricPoints.size > 1) {
                    AllRecordsButton { showAllMetric = true }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Выбор метрики (горизонтальная прокрутка)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                metricOptions.forEachIndexed { index, (label, _) ->
                    val selected = index == selectedMetric
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (selected) Black else White,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedMetric = index }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            color = if (selected) White else Black,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            SimpleLineChart(
                values = metricPoints.map { it.second },
                startLabel = metricPoints.firstOrNull()?.first?.format(dateFmt),
                endLabel = metricPoints.lastOrNull()?.first?.format(dateFmt),
                pointLabels = metricPoints.map { it.first.format(dateFmt) },
                unit = "см"
            )

            if (showAllMetric) {
                FullHistoryChart(
                    values = metricPoints.map { it.second },
                    labels = metricPoints.map { it.first.format(dateFmt) },
                    title = metricOptions[selectedMetric].first + ", см",
                    unit = "см",
                    onClose = { showAllMetric = false }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Кнопка добавления замеров
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Black, RoundedCornerShape(14.dp))
                .clickable { showMeasureDialog = true }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+ ЗАПИСАТЬ ЗАМЕРЫ", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // Журнал замеров свёрнут в одну строку (v1.8.4): раньше список
        // занимал пол-экрана. Тап открывает окно со ВСЕЙ историей,
        // там же удаление отдельной записи.
        if (bodyMetrics.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            CompactRow(
                emoji = "📓",
                title = "Журнал замеров",
                subtitle = "записей: ${bodyMetrics.size} · последняя " +
                        LocalDate.ofEpochDay(bodyMetrics.last().date).format(dateFmt)
            ) { showMetricsJournal = true }
        }

        Spacer(Modifier.height(24.dp))

        /* ═══ РАЗРЯД ═══

           Раньше здесь стояли четыре карточки с придуманной лестницей
           «новичок — любитель — продвинутый — мастер», где «мастер»
           подписан как «уровень занимающихся 5+ лет». Подпись была
           честная, слово — нет: «мастер спорта» в этой стране значит
           вполне определённую вещь, и называть так результат по
           выдуманной таблице нельзя.

           Сама лестница переехала в Рекорды — там ей и место, это
           разговор про рекорды, а не про анкету. Здесь остались две
           строки: где человек стоит сейчас и по каким таблицам
           считать. Пол спрашивается ровно один раз и меняется только
           отсюда: кнопка «а посчитай по женским» рядом с разрядом
           превращала бы его в игрушку. */
        Text("РАЗРЯД", color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Нормы ЕВСК 2026 по сумме троеборья. Разряд присваивают " +
                    "на соревнованиях; здесь линейка, а не протокол.",
            color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))

        val sex = userProfile?.sex
        if (sex != "m" && sex != "f") {
            Text(
                "Таблицы у мужчин и у женщин разные — без этого разряд посчитать " +
                        "нечестно. Спросим один раз.",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SexButton("Мужские", Modifier.weight(1f)) { viewModel.setSex("m") }
                SexButton("Женские", Modifier.weight(1f)) { viewModel.setSex("f") }
            }
        } else {
            val male = sex == "m"
            val total = ru.styrk.app.data.model.Standards.LIFTS.sumOf { l ->
                val r = records[l.name]
                if (r != null && r.maxWeight > 0f)
                    ru.styrk.app.data.model.Standards.oneRM(r.maxWeight, r.maxWeightReps).toDouble()
                else 0.0
            }.toFloat()

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SoftGray, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                if (effectiveWeight == null || effectiveWeight <= 0f || total <= 0f) {
                    Text(
                        if (effectiveWeight == null || effectiveWeight <= 0f)
                            "Нужен вес тела: нормативы даны по весовым категориям."
                        else
                            "Сделайте присед, жим лёжа или становую — и здесь появится ступень.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )
                } else {
                    val st = ru.styrk.app.data.model.Standards.steps(male, effectiveWeight, "total", false)
                    val place = ru.styrk.app.data.model.Standards.place(total, st)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                ru.styrk.app.data.model.Standards.word(place.idx),
                                color = Black, fontSize = 20.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "сумма " + ru.styrk.app.data.model.Standards.fmt(total) + " кг · " +
                                        ru.styrk.app.data.model.Standards.classWord(male, effectiveWeight),
                                color = DarkGray, fontSize = 12.sp
                            )
                        }
                        Text("подробнее в Рекордах", color = DarkGray, fontSize = 10.sp)
                    }
                    place.need?.let { need ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "до " + ru.styrk.app.data.model.Standards.GENIT[place.next ?: 0] +
                                    " ещё " + ru.styrk.app.data.model.Standards.fmt(need - total) + " кг",
                            color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Пол выбирается один раз, а меняется только в «Изменить»
                // наверху профиля — рядом с именем, ростом и весом.
                // Ссылка «сменить» прямо тут была слишком лёгкой:
                // одно случайное касание — и все ступени пересчитаны
                // по чужим нормативам.
                Text(
                    "Нормативы " + (if (male) "мужские" else "женские") +
                            " — меняется в «Изменить» наверху.",
                    color = DarkGray, fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ===== СВЁРНУТЫЕ РАЗДЕЛЫ (v1.8.4) =====
        // Раньше здесь были три больших блока: бэкап, поддержка и «о
        // приложении». Они открываются редко, а места занимали много —
        // теперь это три строки, содержимое в отдельных окнах.
        CompactRow(
            emoji = "☁",
            title = if (signedInEmail != null) "Аккаунт" else "Войти или зарегистрироваться",
            subtitle = signedInEmail
                ?: "Копия тренировок на сервере и доступ с других устройств"
        ) { showAccountDialog = true }

        Spacer(Modifier.height(8.dp))

        // Абонемент — рядом с аккаунтом: и то, и другое про то, что
        // человек получает извне, а не про его тело.
        ru.styrk.app.ui.friends.SubsBlock()

        CompactRow(
            emoji = "👥",
            title = "Друзья",
            subtitle = if (signedInEmail != null)
                "Сравнить рекорды с тем, кого добавите"
            else
                "Нужен аккаунт — он же и для копии на сервере"
        ) { showFriendsDialog = true }

        Spacer(Modifier.height(8.dp))

        CompactRow(
            emoji = "💾",
            title = "Данные",
            subtitle = "Сохранить бэкап или восстановить из файла"
        ) { showDataDialog = true }

        Spacer(Modifier.height(24.dp))
    }
}

// ===== КОМПАКТНАЯ СТРОКА-ПУНКТ (открывает своё окно) =====
@Composable
private fun CompactRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 17.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("→", color = DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== ЖУРНАЛ ЗАМЕРОВ: ОТДЕЛЬНОЕ ОКНО =====
@Composable
private fun MetricsJournalDialog(
    metrics: List<BodyMetricEntity>,
    dateFmt: DateTimeFormatter,
    onDelete: (BodyMetricEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(White, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Журнал замеров", color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("всего записей: ${metrics.size}", color = DarkGray, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))

            // Новые сверху. Высота ограничена: длинный журнал прокручивается
            // внутри окна и не вылезает за края экрана.
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                metrics.reversed().forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(SoftGray, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            LocalDate.ofEpochDay(m.date).format(dateFmt),
                            color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        val summary = listOfNotNull(
                            m.weight?.let { "вес ${SetFormat.num(it)}" },
                            m.chest?.let { "грудь ${SetFormat.num(it)}" },
                            m.waist?.let { "талия ${SetFormat.num(it)}" },
                            m.hips?.let { "бёдра ${SetFormat.num(it)}" },
                            m.biceps?.let { "бицепс ${SetFormat.num(it)}" },
                            m.forearm?.let { "предплечье ${SetFormat.num(it)}" },
                            m.thigh?.let { "бедро ${SetFormat.num(it)}" },
                            m.calf?.let { "икра ${SetFormat.num(it)}" },
                            m.neck?.let { "шея ${SetFormat.num(it)}" },
                            m.shoulders?.let { "плечи ${SetFormat.num(it)}" }
                        ).joinToString(" · ").ifEmpty { "запись" }
                        Text(
                            summary, color = DarkGray, fontSize = 11.sp,
                            lineHeight = 15.sp, modifier = Modifier.weight(1f)
                        )
                        Text(
                            "✕", color = DarkGray, fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { onDelete(m) }
                                .padding(4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RowAction("Закрыть", strong = true) { onDismiss() }
            }
        }
    }
}

// ===== ДИАЛОГ ЗАМЕРОВ =====
@Composable
private fun MeasureDialog(
    onDismiss: () -> Unit,
    onSave: (BodyMetricEntity) -> Unit
) {
    var weightIn by remember { mutableStateOf("") }
    var chestIn by remember { mutableStateOf("") }
    var waistIn by remember { mutableStateOf("") }
    var hipsIn by remember { mutableStateOf("") }
    var bicepsIn by remember { mutableStateOf("") }
    var forearmIn by remember { mutableStateOf("") }
    var thighIn by remember { mutableStateOf("") }
    var calfIn by remember { mutableStateOf("") }
    var neckIn by remember { mutableStateOf("") }
    var shouldersIn by remember { mutableStateOf("") }

    fun parse(s: String): Float? = s.replace(",", ".").toFloatOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Замеры на сегодня") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Заполните только то, что измерили — остальное можно оставить пустым.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                MeasureField("Вес, кг", weightIn) { weightIn = it }
                MeasureField("Грудь, см", chestIn) { chestIn = it }
                MeasureField("Талия, см", waistIn) { waistIn = it }
                MeasureField("Бёдра (таз), см", hipsIn) { hipsIn = it }
                MeasureField("Бицепс, см", bicepsIn) { bicepsIn = it }
                MeasureField("Предплечье, см", forearmIn) { forearmIn = it }
                MeasureField("Бедро, см", thighIn) { thighIn = it }
                MeasureField("Икра, см", calfIn) { calfIn = it }
                MeasureField("Шея, см", neckIn) { neckIn = it }
                MeasureField("Плечи, см", shouldersIn) { shouldersIn = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val entity = BodyMetricEntity(
                    date = LocalDate.now().toEpochDay(),
                    weight = parse(weightIn),
                    chest = parse(chestIn),
                    waist = parse(waistIn),
                    hips = parse(hipsIn),
                    biceps = parse(bicepsIn),
                    forearm = parse(forearmIn),
                    thigh = parse(thighIn),
                    calf = parse(calfIn),
                    neck = parse(neckIn),
                    shoulders = parse(shouldersIn)
                )
                val hasAnything = listOf(
                    entity.weight, entity.chest, entity.waist, entity.hips,
                    entity.biceps, entity.forearm, entity.thigh, entity.calf,
                    entity.neck, entity.shoulders
                ).any { it != null }
                if (hasAnything) onSave(entity)
            }) { Text("СОХРАНИТЬ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun MeasureField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Black, cursorColor = Black,
            focusedTextColor = Black, unfocusedTextColor = Black
        )
    )
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            // fillMaxHeight работает вместе с IntrinsicSize.Min у ряда:
            // серый фон дотягивается до высоты самой высокой плашки,
            // и ряд перестаёт быть рваным при любом размере шрифта.
            .fillMaxHeight()
            .background(SoftGray, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        // Подпись переносится свободно — плашка растёт вместе с ней.
        // Ограничивать её нечем и незачем: высота здесь не задана.
        Text(label, color = DarkGray, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionButton(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** Кнопка выбора таблиц. Нажимается один раз в жизни. */
@Composable
private fun SexButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(Black, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Выбор пола в окне «Профиль»: выбранный — чёрный, другой — контуром. */
@Composable
private fun SexChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .border(1.dp, Black, RoundedCornerShape(10.dp))
            .background(if (selected) Black else White, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) White else Black, fontSize = 13.sp,
            fontWeight = FontWeight.Bold)
    }
}

private fun bmiCategory(bmi: Float?): String = when {
    bmi == null   -> "ИМТ"
    bmi < 18.5f   -> "ИМТ · дефицит"
    bmi < 25f     -> "ИМТ · норма"
    bmi < 30f     -> "ИМТ · избыток"
    else          -> "ИМТ · ожирение"
}

/**
 * Копирует выбранное фото во внутреннюю память приложения, возвращает путь.
 *
 * v1.7.1: фото УМЕНЬШАЕТСЯ до ~512px и пережимается в JPEG 85% — так оно
 * занимает ~50–150 КБ и спокойно помещается внутрь файла бэкапа (п2).
 */
/**
 * Загрузить картинку из галереи ПРАВИЛЬНО ориентированной.
 *
 * Раньше использовался BitmapFactory, а он читает только пиксели и не
 * смотрит в служебные данные снимка. Телефон при съёмке часто пишет кадр
 * как есть, а рядом отметку «повернуть на 90°». Галерея эту отметку
 * учитывает, BitmapFactory — нет. Отсюда и брались фото, лежащие на боку.
 *
 * ImageDecoder (доступен с Android 9, у нас минимум 10) отметку читает
 * и поворачивает кадр сам.
 */
private fun loadOrientedBitmap(
    context: android.content.Context,
    uri: Uri,
    maxSide: Int = 1600
): android.graphics.Bitmap? = try {
    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Мягкая распаковка: результат можно менять и рисовать
        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        val big = maxOf(info.size.width, info.size.height)
        if (big > maxSide) {
            val k = maxSide.toFloat() / big
            decoder.setTargetSize(
                (info.size.width * k).toInt().coerceAtLeast(1),
                (info.size.height * k).toInt().coerceAtLeast(1)
            )
        }
    }
} catch (_: Exception) {
    null
}

/**
 * Сохранить выбранный пользователем квадрат в файл 512×512.
 *
 * srcLeft/srcTop/srcSize — область в координатах исходной картинки,
 * посчитанная окном кадрирования по масштабу и сдвигу.
 */
private fun saveCroppedPhoto(
    context: android.content.Context,
    source: android.graphics.Bitmap,
    srcLeft: Int,
    srcTop: Int,
    srcSize: Int
): String? = try {
    val size = srcSize.coerceAtLeast(1)
    val left = srcLeft.coerceIn(0, (source.width - size).coerceAtLeast(0))
    val top = srcTop.coerceIn(0, (source.height - size).coerceAtLeast(0))
    val w = minOf(size, source.width - left).coerceAtLeast(1)
    val h = minOf(size, source.height - top).coerceAtLeast(1)

    val square = android.graphics.Bitmap.createBitmap(source, left, top, w, h)
    val out = square.scale(512, 512)

    val file = java.io.File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { stream ->
        out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, stream)
    }
    file.absolutePath
} catch (_: Exception) {
    null
}

// ═══════════════════════════════════════════════════════════════════
//  ОКНО КАДРИРОВАНИЯ ФОТО (п7, v1.8.5)
//
//  Двигать пальцем — сдвиг, щипок двумя — масштаб. В круг попадает
//  ровно то, что видно в рамке: что показано, то и сохранится.
//
//  Сдвиг ограничен так, чтобы картинка всегда закрывала рамку целиком
//  и по краям не появлялись пустые полосы.
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun PhotoCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { loadOrientedBitmap(context, uri) }

    if (bitmap == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Не удалось открыть фото", fontWeight = FontWeight.Bold) },
            text = { Text("Попробуйте выбрать другое изображение.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно", color = Black) } }
        )
        return
    }

    // Размер рамки в пикселях — узнаём после размещения
    var frame by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Масштаб, при котором картинка ровно закрывает рамку
    val baseScale = if (frame == 0) 1f
    else maxOf(frame.toFloat() / bitmap.width, frame.toFloat() / bitmap.height)

    fun clampOffsets() {
        val k = baseScale * scale
        val maxX = ((bitmap.width * k) - frame) / 2f
        val maxY = ((bitmap.height * k) - frame) / 2f
        offsetX = offsetX.coerceIn(-maxX.coerceAtLeast(0f), maxX.coerceAtLeast(0f))
        offsetY = offsetY.coerceIn(-maxY.coerceAtLeast(0f), maxY.coerceAtLeast(0f))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(White, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Фото профиля", color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Двигайте пальцем, щипок двумя пальцами — приблизить. " +
                        "Сохранится то, что видно в круге.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
            )
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(SoftGray)
                    .onSizeChanged { frame = minOf(it.width, it.height) }
                    .pointerInput(uri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                            clampOffsets()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Фото профиля",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val k = baseScale * scale
                            // Картинка рисуется по размеру рамки (Fit),
                            // поэтому доводим её до нужного масштаба сами
                            val fit = if (frame == 0) 1f
                            else minOf(frame.toFloat() / bitmap.width, frame.toFloat() / bitmap.height)
                            val extra = if (fit == 0f) 1f else k / fit
                            scaleX = extra
                            scaleY = extra
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End)
            ) {
                RowAction("Отмена") { onDismiss() }
                RowAction("Сохранить", strong = true) {
                    // Переводим рамку из координат экрана в координаты картинки
                    val k = baseScale * scale
                    val cropSize = (frame / k).toInt().coerceAtLeast(1)
                    val centerX = bitmap.width / 2f - offsetX / k
                    val centerY = bitmap.height / 2f - offsetY / k
                    val left = (centerX - cropSize / 2f).toInt()
                    val top = (centerY - cropSize / 2f).toInt()
                    onSave(saveCroppedPhoto(context, bitmap, left, top, cropSize))
                }
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    initialName: String,
    initialHeight: Float?,
    initialWeight: Float?,
    initialSex: String?,
    onDismiss: () -> Unit,
    onSave: (String, Float?, Float?, String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var sex by remember { mutableStateOf(initialSex?.takeIf { it == "m" || it == "f" }) }
    var height by remember { mutableStateOf(initialHeight?.toInt()?.toString() ?: "") }
    var weight by remember { mutableStateOf(initialWeight?.let { "%.1f".format(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Имя") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = height, onValueChange = { height = it },
                    label = { Text("Рост, см") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("Вес, кг") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Нормативы разрядов", color = DarkGray, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SexChoice("Мужские", sex == "m", Modifier.weight(1f)) { sex = "m" }
                    SexChoice("Женские", sex == "f", Modifier.weight(1f)) { sex = "f" }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    name.trim(),
                    height.toIntOrNull()?.toFloat(),
                    weight.replace(",", ".").toFloatOrNull(),
                    sex
                )
            }) { Text("СОХРАНИТЬ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}