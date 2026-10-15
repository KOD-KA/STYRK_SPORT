package ru.styrk.app.ui.training

import ru.styrk.app.ui.common.keyboardGap
import ru.styrk.app.ui.common.ruPlural
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.CalorieCalculator
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.White

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutSummaryScreen(
    viewModel: TrainingSessionViewModel,
    onFinish: () -> Unit
) {
    val exercisesCount = viewModel.getCurrentExercisesCount()
    val setsCount = viewModel.getCurrentSetsCount()
    val volume = viewModel.getCurrentVolume()

    // Калории — по только что завершённой тренировке (она уже в архиве,
    // completedWorkouts DESC → первый элемент)
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadUserProfile() }
    val kcal = completedWorkouts.firstOrNull()?.let {
        CalorieCalculator.estimateKcal(
            it.exercises, it.durationMinutes,
            it.athleteWeight ?: userProfile?.weightKg,
            userProfile?.heightCm
        )
    }

    var notes by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Заметка к тренировке — последнее поле ввода в приложении,
                // которое оставалось под клавиатурой.
                .keyboardGap()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Трофей
            Text(
                text = "🏋️",
                fontSize = 72.sp
            )

            Spacer(Modifier.height(20.dp))

            // Заголовок
            Text(
                text = "ТРЕНИРОВКА\nЗАВЕРШЕНА",
                color = White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )

            Spacer(Modifier.height(40.dp))

            // Три блока статистики
            // Три блока с крупными числами (36 пунктов). При увеличенном
            // шрифте они не помещаются в строку, и «подходов» рассыпается
            // по буквам. Не влезают рядом — переносятся ниже.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBlock(
                    value = "$exercisesCount",
                    label = ruPlural(exercisesCount, "упражнение", "упражнения", "упражнений")
                )
                StatBlock(
                    value = "$setsCount",
                    label = ruPlural(setsCount, "подход", "подхода", "подходов")
                )
                StatBlock(
                    value = "${volume.toInt()}",
                    label = "кг объём"
                )
            }

            kcal?.let {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "🔥 ~$it ккал",
                    color = White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── ЗАМЕТКИ О ТРЕНИРОВКЕ (самочувствие и т.п.) ──────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Как самочувствие? Заметки (необязательно)",
                    color = White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp),
                    placeholder = { Text("Например: было тяжело, но продуктивно", color = White.copy(alpha = 0.35f), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = White,
                        unfocusedBorderColor = White.copy(alpha = 0.4f),
                        cursorColor = White,
                        focusedTextColor = White,
                        unfocusedTextColor = White
                    )
                )
            }

            Spacer(Modifier.height(40.dp))

            // Кнопка ГОТОВО (белая на чёрном фоне)
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 220.dp, max = 320.dp)
                    .background(White, RoundedCornerShape(12.dp))
                    .clickable {
                        if (notes.isNotBlank()) viewModel.saveWorkoutNotes(notes)
                        onFinish()
                    }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ГОТОВО",
                    color = Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(
        Modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}