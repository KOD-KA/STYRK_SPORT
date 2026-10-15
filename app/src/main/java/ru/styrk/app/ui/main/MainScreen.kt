package ru.styrk.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ru.styrk.app.data.local.database.AppDatabaseProvider
import ru.styrk.app.navigation.AppNavigation
import ru.styrk.app.navigation.BottomNavItem
import ru.styrk.app.ui.common.UndoBar
import ru.styrk.app.ui.components.StyrkBottomBar
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.SystemBarsAppearance
import ru.styrk.app.ui.theme.White
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import kotlinx.coroutines.launch

// consumeWindowInsets в старых версиях Compose был «опытным»; в нынешней
// он стабилен, и пометка ниже просто ничего не делает. Стоит для
// надёжности сборки на любой версии.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {

    // ДОБАВЛЕНО (этап «профиль»): 5-я вкладка Profile
    val items = listOf(
        BottomNavItem.Training,
        BottomNavItem.Records,
        BottomNavItem.Calendar,
        BottomNavItem.Info,
        BottomNavItem.Profile
    )

    // Состояние текущей вкладки хранит PagerState — он же используется и для
    // свайпа, и для подсветки нижней навигации, всегда синхронизированы.
    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()

    // TrainingSessionViewModel создаётся здесь (единственное место, которое
    // одновременно знает вкладку Pager и под-экран тренировки — только здесь
    // можно принять ЕДИНСТВЕННОЕ решение о цвете статус-бара, без гонок).
    val context = LocalContext.current
    val database = remember { AppDatabaseProvider.getDatabase(context) }
    val trainingViewModel = remember { TrainingSessionViewModel(database) }
    val trainingState by trainingViewModel.state.collectAsState()

    // Ушли на другую вкладку — клавиатура от прежней уходит вместе с ней.
    // Иначе поле на прошлой вкладке продолжало держать её открытой,
    // и она висела поверх календаря или профиля.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pagerState.currentPage) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    // С сервера приехали изменения — при любом обмене, не только при
    // возвращении в приложение, — перечитываем всё, что показано.
    val dataVersion = ru.styrk.app.sync.DataChanged.version
    LaunchedEffect(dataVersion) {
        if (dataVersion > 0) {
            trainingViewModel.loadArchiveFromDatabase()
            trainingViewModel.loadRecordsFromDatabase()
            trainingViewModel.loadCustomExercises()
            trainingViewModel.loadPlannedWorkouts()
            trainingViewModel.loadBodyMetrics()
            trainingViewModel.loadUserProfile()
        }
    }

    // Тренер видит тренировку вживую и может вписать подход — см. LiveSync.
    LaunchedEffect(Unit) { ru.styrk.app.sync.LiveSync.run(context, trainingViewModel) }

    // Полоска «удалено — отменить» одна на всё приложение. Если бы её
    // рисовала каждая вкладка, при переходе на другую она бы исчезала,
    // а отсчёт до настоящего удаления продолжался — отменять стало бы
    // нечем.
    val undo by trainingViewModel.undo.collectAsState()

    // Свежая тренировка уезжает на сервер сразу после сохранения,
    // а не при следующем запуске. Если сети нет — не страшно: запись
    // помечена неотправленной и уедет при первой возможности.
    val workoutSaved by trainingViewModel.workoutSaved.collectAsState()
    LaunchedEffect(workoutSaved) {
        if (workoutSaved > 0) {
            ru.styrk.app.sync.SyncManager(context, database).sync()
        }
    }

    /* Тихий обмен с сервером — при запуске И при каждом возвращении
       в приложение.

       Раньше он был только при запуске, и этого не хватало: дневник
       живёт в двух местах, человек уходит в мини-приложение Телеграма,
       записывает что-то там, возвращается — а приложение всё это время
       висело свёрнутым и ничего не спрашивало. Данные расходились
       до следующего полного перезапуска.

       Сигнал о возвращении приходит из MainActivity (AppResumed):
       `onResume` есть у любой Activity на любом Android, в отличие
       от Compose-овского Lifecycle, который в свежих версиях переехал
       в отдельную библиотеку.

       Без аккаунта обмен не делает ничего, ошибки не показывает:
       приложение работает и без сервера, и дёргать человека сообщением
       «нет связи» на старте тренировки незачем. */
    val resumed by ru.styrk.app.AppResumed.ticks.collectAsState()
    // Момент последнего обмена. При запуске срабатывают оба повода
    // подряд — первая отрисовка и первый `onResume`; второй обмен
    // в ту же секунду не нужен никому.
    var lastSyncAt by remember { mutableStateOf(0L) }
    LaunchedEffect(resumed) {
        val now = System.currentTimeMillis()
        if (now - lastSyncAt >= 5000) {
            lastSyncAt = now
            val outcome = ru.styrk.app.sync.SyncManager(context, database).sync()
            if (outcome is ru.styrk.app.sync.SyncManager.Outcome.Ok && outcome.received > 0) {
                // Приехали чужие изменения — перечитываем то, что показано
                trainingViewModel.loadArchiveFromDatabase()
                trainingViewModel.loadRecordsFromDatabase()
                trainingViewModel.loadCustomExercises()
                trainingViewModel.loadPlannedWorkouts()
                trainingViewModel.loadBodyMetrics()
                trainingViewModel.loadUserProfile()
            }
        }
    }

    // Запуск плановой тренировки из календаря — переключаемся на вкладку 0
    val navToTraining by trainingViewModel.navigateToTraining.collectAsState()
    LaunchedEffect(navToTraining) {
        if (navToTraining > 0) pagerState.animateScrollToPage(0, animationSpec = tween(420))
    }

    // п6: системная кнопка «назад» на под-экранах тренировки возвращает
    // на предыдущий под-экран (в т.ч. с «введите вес» → на старт), а не
    // сворачивает приложение. Активен только когда открыта вкладка тренировки.
    // v1.6.1 (п2): с экрана самой тренировки выход — через ДВА подтверждения:
    // 1) «Вы уверены, что хотите вернуться назад?» — всегда;
    // 2) «Данные будут утеряны» — только если хоть один подход уже записан.
    var showExitConfirm by remember { mutableStateOf(false) }
    var showLossConfirm by remember { mutableStateOf(false) }
    val hasEnteredData = trainingState.selectedExercises.any { it.sets.isNotEmpty() }

    BackHandler(
        enabled = pagerState.currentPage == 0 &&
                trainingState.currentScreen in listOf("weight", "muscles", "exercises", "training")
    ) {
        when (trainingState.currentScreen) {
            "weight"    -> trainingViewModel.setCurrentScreen("start")
            "muscles"   -> trainingViewModel.setCurrentScreen(
                if (trainingState.selectedExercises.isEmpty()) "weight" else "training")
            "exercises" -> trainingViewModel.setCurrentScreen("muscles")
            "training"  -> showExitConfirm = true
        }
    }

    // Подтверждение 1: точно назад?
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Вы уверены, что хотите вернуться назад?", fontWeight = FontWeight.Bold) },
            text = { Text("Ведение тренировки будет закрыто.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    if (hasEnteredData) showLossConfirm = true
                    else trainingViewModel.resetWorkout()
                }) { Text("Да, назад", color = Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Отмена", color = DarkGray) }
            }
        )
    }

    // Подтверждение 2: данные будут потеряны
    if (showLossConfirm) {
        AlertDialog(
            onDismissRequest = { showLossConfirm = false },
            containerColor = White,
            titleContentColor = Black,
            textContentColor = DarkGray,
            title = { Text("Данные будут утеряны", fontWeight = FontWeight.Bold) },
            text = { Text("В тренировке есть записанные подходы. Если выйти, они не сохранятся.") },
            confirmButton = {
                TextButton(onClick = {
                    showLossConfirm = false
                    trainingViewModel.resetWorkout()
                }) { Text("Всё равно выйти", color = Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLossConfirm = false }) { Text("Отмена", color = DarkGray) }
            }
        )
    }

    // ЕДИНСТВЕННЫЙ источник правды для статус-бара во всём приложении.
    // Вкладка 0 (тренировка): все под-экраны чёрные, кроме "muscles".
    // Вкладки 1–4 (рекорды/календарь/инфо/профиль): всегда белые.
    val isBlackBar = pagerState.currentPage == 0 && trainingState.currentScreen != "muscles"
    val barColor = if (isBlackBar) Black else White
    SystemBarsAppearance(darkIcons = !isBlackBar)

    // Рекорды — для строки рекорда в карточке упражнения (см.
    // LocalExerciseHistory): карточку открывают четыре экрана.
    val exerciseHistory by trainingViewModel.exerciseHistory.collectAsState()

    androidx.compose.runtime.CompositionLocalProvider(
        ru.styrk.app.ui.common.LocalExerciseHistory provides exerciseHistory
    ) {
    Box(Modifier.fillMaxSize()) {

        /* Пока открыта клавиатура, нижней панели не видно.

           Она всё равно оказывается ПОД клавиатурой — нажать нельзя,
           а место под себя она продолжает занимать. Получалась полоса
           пустоты между содержимым и клавиатурой, ровно в высоту
           панели: экран и так стал вдвое ниже, и терять ещё сотню
           точек ни за что не хочется.

           Проверяем не «показана ли клавиатура» (это ещё опытная
           часть Compose), а её высоту: ноль — значит её нет. */
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!keyboard) {
                    StyrkBottomBar(
                        items = items,
                        selectedIndex = pagerState.currentPage
                    ) { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index, animationSpec = tween(420))
                        }
                    }
                }
            }
        ) { padding ->

            AppNavigation(
                trainingViewModel = trainingViewModel,
                pagerState = pagerState,
                /* consumeWindowInsets — и белая полоса над клавиатурой уходит.

                   Откуда она бралась. Scaffold отдаёт содержимому отступ
                   снизу под нижнюю панель, а экраны с полями ввода
                   (тренировка, выбор упражнений, итог) сами отступают
                   на высоту клавиатуры. Клавиатура меряется от самого
                   низа экрана — а низ уже занят отступом панели. Итого
                   место панели учитывалось дважды, и между содержимым
                   и клавиатурой висела пустая полоса ровно в её высоту.

                   consumeWindowInsets говорит всему, что ниже: «вот
                   столько снизу уже занято». Экран с клавиатурой
                   отступает теперь только на недостающее — и край
                   содержимого ложится ровно на край клавиатуры. */
                modifier = Modifier.padding(padding).consumeWindowInsets(padding)
            )
        }

        // Полоска с отменой удаления — над нижней панелью, поверх всего.
        UndoBar(
            text = undo?.text,
            onUndo = { undo?.undo?.invoke() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        )

        // Ссылка «Восстановление пароля» из письма открыла приложение —
        // окно нового пароля поверх всего.
        ru.styrk.app.DeepLinks.resetToken?.let { t ->
            ru.styrk.app.ui.profile.ResetPasswordDialog(t) {
                ru.styrk.app.DeepLinks.resetToken = null
            }
        }

        // Полоса-подложка под системными иконками (время/батарея).
        // С API 35 window.statusBarColor игнорируется системой — цвет
        // статус-бара может нарисовать только само приложение.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(barColor)
        )
    }
    }
}
