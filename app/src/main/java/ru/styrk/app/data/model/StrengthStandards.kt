package ru.styrk.app.data.model

/**
 * Ориентиры силовых нормативов относительно веса тела (мужчины).
 * Значения — множители к весу тела для оценочного 1ПМ (одноповторного
 * максимума), по мотивам общепринятых силовых стандартов.
 *
 * «Мастер» здесь — уровень людей, тренирующихся на постоянной основе
 * 5+ лет (НЕ официальное звание «мастер спорта»!). Это ориентир для
 * мотивации, а не спортивная классификация.
 */
data class StrengthStandard(
    val exerciseName: String,   // должно совпадать с именем в ExerciseLibrary
    val beginner: Float,        // новичок (первые месяцы)
    val intermediate: Float,    // любитель со стажем ~1-2 года
    val advanced: Float,        // продвинутый ~3-5 лет
    val master: Float           // 5+ лет постоянных тренировок
)

object StrengthStandards {

    val standards = listOf(
        StrengthStandard("Жим штанги лёжа",       0.50f, 0.85f, 1.25f, 1.75f),
        StrengthStandard("Приседания со штангой", 0.75f, 1.15f, 1.60f, 2.25f),
        StrengthStandard("Становая тяга",         1.00f, 1.50f, 2.00f, 2.50f),
        StrengthStandard("Жим штанги стоя",       0.35f, 0.55f, 0.80f, 1.05f)
    )

    /**
     * Оценка одноповторного максимума по формуле Эпли:
     * 1ПМ = вес × (1 + повторы/30)
     */
    fun estimate1RM(weight: Float, reps: Int): Float =
        if (reps <= 1) weight else weight * (1f + reps / 30f)

    fun levelName(ratio: Float, s: StrengthStandard): String = when {
        ratio >= s.master       -> "Мастер (5+ лет)"
        ratio >= s.advanced     -> "Продвинутый"
        ratio >= s.intermediate -> "Любитель со стажем"
        ratio >= s.beginner     -> "Новичок+"
        else                    -> "Начало пути"
    }
}