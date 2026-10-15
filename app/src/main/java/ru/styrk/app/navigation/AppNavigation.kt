package ru.styrk.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import ru.styrk.app.ui.calendar.CalendarScreen
import ru.styrk.app.ui.info.InfoScreen
import ru.styrk.app.ui.profile.ProfileScreen
import ru.styrk.app.ui.records.WorkoutArchiveScreen
import ru.styrk.app.ui.training.TrainingFlowScreen
import ru.styrk.app.viewmodel.TrainingSessionViewModel

// TrainingSessionViewModel создаётся в MainScreen.kt и передаётся сюда
// параметром (MainScreen централизованно управляет статус-баром).
// Инстанс один на всё приложение.
@Composable
fun AppNavigation(
    trainingViewModel: TrainingSessionViewModel,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {

    LaunchedEffect(trainingViewModel) {
        trainingViewModel.loadArchiveFromDatabase()
        trainingViewModel.loadRecordsFromDatabase()
        trainingViewModel.loadCustomExercises()
        trainingViewModel.loadInProgressWorkout()
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),

        // v1.8.3: ПРИЧИНА РЫВКОВ ПРИ СМАХИВАНИИ.
        // По умолчанию соседняя вкладка начинает строиться только в тот
        // момент, когда палец уже потянул страницу. Экраны тяжёлые
        // (графики, сетка календаря, список архива), поэтому первые кадры
        // жеста уходили на сборку — визуально это и есть «дёрганье».
        // Значение 1 = держать готовыми соседей слева и справа: к началу
        // жеста страница уже построена, свайп идёт без пропуска кадров.
        // Больше 1 ставить не стоит — вырастет расход памяти, а разницы
        // на глаз уже нет.
        beyondViewportPageCount = 1
    ) { page ->
        when (page) {
            0 -> TrainingFlowScreen(viewModel = trainingViewModel)

            1 -> {
                // Обновляем архив/рекорды при каждом заходе на вкладку
                LaunchedEffect(Unit) {
                    trainingViewModel.loadArchiveFromDatabase()
                    trainingViewModel.loadRecordsFromDatabase()
                }
                WorkoutArchiveScreen(viewModel = trainingViewModel)
            }

            2 -> CalendarScreen(viewModel = trainingViewModel)

            3 -> InfoScreen(viewModel = trainingViewModel)

            // ДОБАВЛЕНО (этап «профиль»): 5-я вкладка
            4 -> ProfileScreen(viewModel = trainingViewModel)
        }
    }
}