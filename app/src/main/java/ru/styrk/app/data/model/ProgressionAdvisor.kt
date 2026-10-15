package ru.styrk.app.data.model

/**
 * Рекомендации по прогрессии для силовых упражнений (п4).
 *
 * Метод — двойная прогрессия (double progression): сначала доводим
 * повторы до верха рабочего диапазона на текущем весе, затем прибавляем
 * вес и возвращаемся к нижней границе. Это самый устойчивый способ
 * прогрессировать без перегруза техники.
 *
 * Дополнительно: тренд по последним тренировкам, уровень относительно
 * силовых нормативов (см. StrengthStandards) и подсказки по технике.
 */
data class ProgressionAdvice(
    val nextTarget: String,
    val method: String,
    val trendLine: String,
    val levelLine: String?,
    val technique: List<String>
)

object ProgressionAdvisor {

    private const val REP_BOTTOM = 8
    private const val REP_TOP = 12

    // Упражнения ног/спины — крупные группы, прибавка веса крупнее (5 кг),
    // остальное (руки/плечи/грудь-изоляция) — 2.5 кг
    private val BIG_INCREMENT = setOf(
        "Приседания со штангой", "Становая тяга", "Румынская тяга",
        "Жим ногами", "Фронтальные приседания", "Гакк-приседания"
    )

    private val GENERIC_TECHNIQUE = listOf(
        "Контролируйте вес на опускании (2–3 сек), без рывков",
        "Полная амплитуда важнее большого веса",
        "Дышите: выдох на усилии, вдох на опускании"
    )

    private val TECHNIQUE = mapOf(
        "Жим штанги лёжа" to listOf(
            "Лопатки сведены и прижаты, лёгкий прогиб в пояснице",
            "Гриф опускать к низу груди, локти под ~45° к корпусу",
            "Ноги в упор в пол, таз на скамье"
        ),
        "Жим штанги на наклонной скамье" to listOf(
            "Угол скамьи 30–45°, лопатки сведены",
            "Гриф — к верхней части груди",
            "Не отрывайте таз от скамьи"
        ),
        "Жим узким хватом" to listOf(
            "Хват чуть уже плеч, локти вдоль корпуса",
            "Опускать к нижней части груди",
            "Работает трицепс — не разводите локти в стороны"
        ),
        "Приседания со штангой" to listOf(
            "Гриф на трапеции, кор напряжён",
            "Колени по направлению носков, таз назад",
            "Глубина минимум до параллели бедра с полом"
        ),
        "Становая тяга" to listOf(
            "Гриф над серединой стопы, спина ровная (не круглить)",
            "Штанга скользит близко к телу",
            "Толчок ногами от пола, затем разгибание таза"
        ),
        "Жим штанги стоя" to listOf(
            "Кор и ягодицы напряжены, не прогибать поясницу",
            "Гриф от груди по прямой над головой",
            "В верхней точке голова «проходит» под гриф"
        ),
        "Подъём штанги на бицепс" to listOf(
            "Локти зафиксированы у корпуса",
            "Без раскачки корпусом — поднимает бицепс, не спина",
            "Полное разгибание внизу"
        ),
        "Молотки с гантелями" to listOf(
            "Нейтральный хват (ладони друг к другу)",
            "Локти у корпуса, без рывков",
            "Работает бицепс и плечевая мышца"
        ),
        "Французский жим" to listOf(
            "Локти неподвижны и направлены вверх",
            "Опускать ко лбу или за голову",
            "Движение только в локтевом суставе"
        ),
        "Разгибание рук на блоке" to listOf(
            "Локти прижаты к корпусу",
            "Двигается только предплечье, до полного разгибания",
            "Без наклона корпуса вперёд"
        ),
        "Горизонтальная тяга блока" to listOf(
            "Спина прямая, тянуть к животу",
            "Сводить лопатки в конце движения",
            "Без рывков корпусом"
        ),
        "Тяга верхнего блока" to listOf(
            "Тянуть к верху груди, грудь вперёд",
            "Локти идут вниз, не заваливаться назад",
            "Полное растяжение вверху"
        ),
        "Болгарские выпады" to listOf(
            "Задняя нога на возвышении, вес на передней",
            "Колено передней ноги по направлению носка",
            "Небольшой наклон корпуса вперёд"
        )
    )

    fun advise(
        name: String,
        history: ExerciseHistory,
        lastTopSet: WorkoutSet?,
        recentMainPoints: List<Float>,
        bodyWeight: Float?
    ): ProgressionAdvice {
        val increment = if (name in BIG_INCREMENT) 5f else 2.5f

        val nextTarget = if (lastTopSet == null || lastTopSet.weight <= 0f) {
            "Сделайте пару рабочих подходов — появится цель на следующую тренировку"
        } else {
            val w = lastTopSet.weight
            val r = lastTopSet.reps
            if (r >= REP_TOP) {
                "Следующая цель: ${fmt(w + increment)} кг × $REP_BOTTOM — прибавьте вес"
            } else {
                "Следующая цель: ${fmt(w)} кг × ${r + 1} — прибавьте повтор"
            }
        }

        val method =
            "Двойная прогрессия: доведите повторы до $REP_TOP на текущем весе во " +
                    "всех рабочих подходах, затем прибавьте ${fmt(increment)} кг и вернитесь к $REP_BOTTOM повторам. " +
                    "Если застой 2–3 тренировки — сделайте лёгкую (разгрузочную) неделю −10% веса."

        val trendLine = when {
            recentMainPoints.size < 2 -> "Тренд: мало данных — нужно больше тренировок"
            recentMainPoints.last() > recentMainPoints[recentMainPoints.size - 2] -> "Тренд: рост ↗ — так держать"
            recentMainPoints.last() < recentMainPoints[recentMainPoints.size - 2] -> "Тренд: спад ↘ — проверьте восстановление и сон"
            else -> "Тренд: застой → пора менять схему или дать отдых"
        }

        val std = StrengthStandards.standards.find { it.exerciseName == name }
        val levelLine: String? = if (std != null && bodyWeight != null && bodyWeight > 0f && history.maxWeight > 0f) {
            val orm = StrengthStandards.estimate1RM(history.maxWeight, history.maxWeightReps)
            val ratio = orm / bodyWeight
            val level = StrengthStandards.levelName(ratio, std)
            val nextRatio = listOf(std.beginner, std.intermediate, std.advanced, std.master).firstOrNull { it > ratio }
            val nextPart = nextRatio?.let { " · до след. уровня ≈ ${fmt(it * bodyWeight)} кг (1ПМ)" } ?: " · максимальный уровень"
            "Уровень: $level (ваш 1ПМ ≈ ${fmt(orm)} кг)$nextPart"
        } else null

        val technique = TECHNIQUE[name] ?: GENERIC_TECHNIQUE

        return ProgressionAdvice(nextTarget, method, trendLine, levelLine, technique)
    }

    private fun fmt(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
}