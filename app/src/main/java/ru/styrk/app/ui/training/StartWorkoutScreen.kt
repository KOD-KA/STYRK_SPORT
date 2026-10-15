package ru.styrk.app.ui.training

import ru.styrk.app.ui.common.ruPlural
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.todaysPlan
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.White
import java.time.format.DateTimeFormatter

@Composable
fun StartWorkoutScreen(
    viewModel: TrainingSessionViewModel,
    onStart: () -> Unit
) {
    val workouts by viewModel.completedWorkouts.collectAsState()
    val lastWorkout = workouts.firstOrNull()
    val daysSince = viewModel.getDaysSinceLastWorkout()

    // План на сегодня. Список планов подтягиваем сами: этот экран
    // открывается первым, и календарь мог ещё ни разу не открываться.
    val planned by viewModel.plannedWorkouts.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPlannedWorkouts() }

    val workoutsByDate = remember(workouts) { workouts.groupBy { it.dateTime.toLocalDate() } }
    val plan = remember(planned, workoutsByDate) { todaysPlan(planned, workoutsByDate) }

    // Экран умеет прокручиваться, если содержимое не помещается.
    //
    // Раньше он был жёстким: Column во весь экран, без прокрутки.
    // При обычном шрифте всё влезало, и это выглядело как решение.
    // При крупном содержимое становилось выше экрана — и просто
    // обрезалось снизу. Обрезалась при этом кнопка «Начать
    // тренировку»: главное действие приложения оказывалось
    // недоступным ровно у тех, кому крупный шрифт нужен.
    //
    // heightIn(min = maxHeight) вместе с Arrangement.Bottom сохраняет
    // прежний вид, когда содержимое короче экрана: столбец всё равно
    // растягивается на всю высоту и прижимает содержимое к низу —
    // ровно так, как это делал распорный Spacer. А когда содержимое
    // выше — столбец растёт, и появляется прокрутка.
    //
    // Распорный Spacer(weight) при этом пришлось убрать: внутри
    // прокручиваемого столбца высота не ограничена, и делить нечего.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
    // Высоту экрана забираем ЗДЕСЬ, в теле BoxWithConstraints,
    // и дальше пользуемся уже обычной переменной.
    //
    // Внутри вложенного Column обратиться к maxHeight напрямую нельзя:
    // Compose намеренно закрывает внешний контекст разметки, чтобы
    // не путались области. Компилятор на это ругается прямо —
    // «cannot be called in this context with an implicit receiver», —
    // и правильно делает: иначе легко взять размер не того, что думал.
    val screenHeight = maxHeight

    // Просветы — доля высоты экрана, а не вес.
    //
    // Вес делил бы остаток, а внутри прокручиваемого столбца остатка
    // нет: высота там не ограничена. Доля от известной высоты экрана
    // даёт то же на глаз и работает при любом содержимом.
    //
    // Покажется, что содержимое стоит слишком высоко или низко, —
    // это одно число, 0.18.
    val gap = (screenHeight * 0.18f).coerceIn(24.dp, 170.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = screenHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {

        Spacer(Modifier.height(gap))

        // ===== ЛОГОТИП =====
        Text(
            text = "STYRK",
            color = White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        // ===== СЧЁТЧИК ДНЕЙ =====
        val daysLabel = when (daysSince) {
            null -> "Первая тренировка"
            0L   -> "Сегодня уже тренировался 💪"
            else -> "$daysSince " + ruPlural(daysSince ?: 0L, "день", "дня", "дней") + " без тренировки"
        }

        // Отступы по краям и выравнивание по центру.
        //
        // Раньше их не было: строка «Сегодня уже тренировался» короткая
        // и при обычном шрифте помещалась целиком, так что упереться
        // в край было негде. При крупном она переносится на две строки
        // и начинает липнуть к самому краю экрана — будто её обрезали.
        Text(
            text = daysLabel,
            color = White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(gap))

        // ===== ПЛАН НА СЕГОДНЯ =====
        // Если на сегодня что-то запланировано и ещё не сделано, это
        // и есть главное на экране. Карточку прошлой тренировки в таком
        // случае не показываем: два блока подряд спорили бы за внимание,
        // а прошлое сегодня уже не важно.
        if (plan != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("СЕГОДНЯ ПО ПЛАНУ", color = Gold, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    Text("%02d:%02d".format(plan.hour, plan.minute),
                        color = White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(plan.muscleGroup, color = White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                if (plan.exercises.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        plan.exercises.take(3).joinToString(" · ") +
                                if (plan.exercises.size > 3) " · ещё ${plan.exercises.size - 3}" else "",
                        color = White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Главная кнопка — начать по плану. Упражнения подставятся сами.
            Box(
                modifier = Modifier
                    // heightIn, а не height: кнопка растёт под текст.
                    // С жёсткой высотой надпись при крупном шрифте
                    // обрезалась на половине буквы.
                    .heightIn(min = 56.dp)
                    .widthIn(min = 240.dp, max = 340.dp)
                    .background(White, RoundedCornerShape(14.dp))
                    .clickable { viewModel.startPlannedWorkout(plan) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("НАЧАТЬ ПО ПЛАНУ", color = Black, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(12.dp))

            // Своя тренировка никуда не девается: план — предложение,
            // а не обязательство. Пришёл и передумал — обычное дело.
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 240.dp, max = 340.dp)
                    .clickable { onStart() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Начать свою", color = White.copy(alpha = 0.75f), fontSize = 15.sp,
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(56.dp))
            return@Column
        }

        // ===== КАРТОЧКА ПОСЛЕДНЕЙ ТРЕНИРОВКИ =====
        if (lastWorkout != null) {

            val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy")
            val hours   = lastWorkout.durationMinutes / 60
            val mins    = lastWorkout.durationMinutes % 60
            val durText = if (hours > 0) "$hours ч $mins мин" else "$mins мин"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(
                        White.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "Последняя тренировка",
                    color = White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = lastWorkout.dateTime.format(dateFmt),
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = lastWorkout.muscleGroup,
                    color = White.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(12.dp))

                // Статистика: упражнения / подходы / время / вес.
                //
                // Равные доли вместо «развести по краям». При SpaceBetween
                // ширина колонки зависела от её содержимого: «1 мин» шире
                // цифры «5», и подписи снизу переставали стоять под своими
                // числами — на крупном шрифте особенно заметно.
                //
                // Здесь доли поровну уместны, в отличие от нижней панели:
                // колонок три-четыре, а не пять, и содержимое у них
                // короткое. Места хватает всем.
                Row(modifier = Modifier.fillMaxWidth()) {
                    LastWorkoutStat(
                        value = "${lastWorkout.exercises.size}",
                        label = "упр.",
                        modifier = Modifier.weight(1f)
                    )
                    LastWorkoutStat(
                        value = "${lastWorkout.exercises.sumOf { it.sets.size }}",
                        label = "под.",
                        modifier = Modifier.weight(1f)
                    )
                    // Раньше подпись здесь была пустой, и в ряду зияла
                    // дыра: три числа и две подписи. «Время» — не украшение,
                    // а недостающее слово.
                    LastWorkoutStat(
                        value = durText,
                        label = "время",
                        modifier = Modifier.weight(1f)
                    )
                    lastWorkout.athleteWeight?.let { w ->
                        LastWorkoutStat(
                            value = "%.1f".format(w),
                            label = "кг",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        } else {
            Spacer(Modifier.height(32.dp))
        }

        // ===== КНОПКА СТАРТ =====
        Box(
            modifier = Modifier
                // Та самая кнопка, которая обрезалась на снимке.
                // Отступ по бокам ужат с 32 до 20: надпись длинная,
                // и лишние 24 точки ширины ей нужнее пустых полей.
                .heightIn(min = 56.dp)
                .widthIn(min = 240.dp, max = 340.dp)
                .background(White, RoundedCornerShape(14.dp))
                .clickable { onStart() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "НАЧАТЬ ТРЕНИРОВКУ",
                color = Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(56.dp))
    }
    }
}

@Composable
private fun LastWorkoutStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}