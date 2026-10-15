package ru.styrk.app.data.model

import kotlin.math.roundToInt

/**
 * Оценка сожжённых калорий за тренировку.
 *
 * ПЕРЕРАБОТАНО (v1.5.1, 2-я итерация). Теперь учитывается ТЯЖЕСТЬ работы,
 * а не только время:
 *
 *  1. МЕХАНИЧЕСКАЯ РАБОТА (главный вклад «тяжести»):
 *     работа = поднятая масса × g × высота подъёма × повторы.
 *     – поднятая масса: вес снаряда, а для упражнений с собственным весом
 *       (отжимания, подтягивания, брусья…) — доля веса атлета;
 *     – высота подъёма зависит от упражнения И ОТ РОСТА атлета
 *       (у высокого амплитуда больше → больше работы);
 *     – КПД мышц ≈ 22%, остальное уходит в тепло: ккал = работа / 0.22.
 *
 *  2. МЕТАБОЛИЧЕСКИЙ ФОН (MET × вес атлета × время): кардио по своим MET,
 *     статика (планка), активная силовая работа и отдых между подходами.
 *
 * Итого: тяжёлые подходы дают больше калорий, чем лёгкие при том же
 * времени; рост и вес атлета влияют на результат.
 *
 * Это оценка, а не измерение: реальный расход зависит от интенсивности,
 * пульса и обмена веществ.
 */
object CalorieCalculator {

    private const val DEFAULT_BODY_WEIGHT = 75f
    private const val DEFAULT_HEIGHT = 175f
    private const val G = 9.81f
    private const val EFFICIENCY = 0.22f      // КПД мышечной работы
    private const val J_PER_KCAL = 4184f

    private const val SECONDS_PER_REP = 3f
    private const val MIN_SECONDS_PER_SET = 30f
    private const val ISOMETRIC_MET = 4.0f
    private const val DEFAULT_CARDIO_MET = 7f
    // Интенсивность силовой работы влияет на MET: лёгкие веса — низкий
    // расход, тяжёлые — высокий (и дыхание дольше восстанавливается,
    // поэтому отдых между тяжёлыми подходами тоже «дороже»)
    private const val STRENGTH_MET_MIN = 3.5f
    private const val STRENGTH_MET_RANGE = 4.5f
    private const val REST_MET_MIN = 1.5f
    private const val REST_MET_RANGE = 1.5f

    // Базовая высота подъёма снаряда (м) при росте 175 см
    private val LIFT_HEIGHT = mapOf(
        "Приседания со штангой" to 0.55f,
        "Фронтальные приседания" to 0.55f,
        "Гакк-приседания" to 0.50f,
        "Жим ногами" to 0.45f,
        "Становая тяга" to 0.60f,
        "Румынская тяга" to 0.45f,
        "Выпады с гантелями" to 0.45f,
        "Болгарские выпады" to 0.45f,
        "Жим штанги лёжа" to 0.40f,
        "Жим гантелей лёжа" to 0.40f,
        "Жим штанги на наклонной скамье" to 0.40f,
        "Жим штанги стоя" to 0.50f,
        "Жим гантелей сидя" to 0.50f,
        "Подтягивания" to 0.55f,
        "Отжимания на брусьях" to 0.45f,
        "Отжимания" to 0.35f,
        "Тяга верхнего блока" to 0.45f,
        "Горизонтальная тяга блока" to 0.45f,
        "Тяга штанги в наклоне" to 0.40f,
        "Подъём штанги на бицепс" to 0.35f,
        "Подъём гантелей на бицепс" to 0.35f,
        "Молотки с гантелями" to 0.35f,
        "Французский жим" to 0.35f,
        "Разгибание рук на блоке" to 0.30f
    )
    private const val DEFAULT_LIFT_HEIGHT = 0.40f

    // Доля веса тела, которую реально поднимаешь в упражнениях без снаряда
    private val BODYWEIGHT_RATIO = mapOf(
        "Отжимания" to 0.65f,
        "Отжимания на брусьях" to 0.95f,
        "Подтягивания" to 0.95f,
        "Австралийские подтягивания" to 0.55f,
        "Приседания без веса" to 0.75f,
        "Выпады на месте" to 0.75f,
        "Присед на одной ноге («пистолетик»)" to 0.85f,
        "Берпи" to 0.70f,
        "Обратные отжимания от стула" to 0.55f,
        "Подъёмы корпуса" to 0.40f,
        "Скручивания" to 0.35f,
        "Подъём ног в висе" to 0.35f,
        "Ягодичный мостик" to 0.50f
    )
    private const val DEFAULT_BODYWEIGHT_RATIO = 0.55f

    private val CARDIO_MET = mapOf(
        "Беговая дорожка"  to 9.0f,
        "Бег на улице"     to 9.8f,
        "Велотренажёр"     to 7.0f,
        "Велопрогулка"     to 6.8f,
        "Эллипсоид"        to 5.5f,
        "Гребной тренажёр" to 7.0f,
        "Степпер"          to 8.5f,
        "Ходьба в горку"   to 6.0f,
        "Скакалка"         to 11.0f,
        "Лыжи"             to 9.0f,
        "Плавание"         to 7.0f
    )

    fun estimateKcal(
        exercises: List<WorkoutExercise>,
        durationMinutes: Long,
        athleteWeight: Float?,
        heightCm: Float? = null
    ): Int {
        val weight = athleteWeight ?: DEFAULT_BODY_WEIGHT
        val height = heightCm ?: DEFAULT_HEIGHT
        // Амплитуда пропорциональна росту: у 190 см она больше, чем у 165
        val heightFactor = (height / DEFAULT_HEIGHT).coerceIn(0.8f, 1.25f)

        var cardioKcal = 0f
        var cardioSeconds = 0f
        var isoKcal = 0f
        var isoSeconds = 0f
        var workJoules = 0f          // механическая работа силовой части
        var strengthReps = 0
        var strengthSets = 0
        var loadSum = 0f             // сумма поднимаемых масс (для интенсивности)
        var loadCount = 0

        exercises.forEach { ex ->
            val isCardioByName = CARDIO_MET.containsKey(ex.name)
            when {
                // ── Кардио и дистанция ────────────────────────────────
                ex.measureType == MeasureType.CARDIO ||
                        ex.measureType == MeasureType.DISTANCE ||
                        isCardioByName -> {
                    val met = CARDIO_MET[ex.name] ?: DEFAULT_CARDIO_MET
                    ex.sets.forEach { s ->
                        var sec = (s.seconds ?: 0).toFloat()
                        if (sec == 0f && (s.distanceKm ?: 0f) > 0f) {
                            sec = (s.distanceKm!! / 9f) * 3600f
                        }
                        cardioSeconds += sec
                        cardioKcal += met * weight * (sec / 3600f)
                    }
                }
                // ── Статика (планка, вис, удержание) ─────────────────
                ex.measureType == MeasureType.TIME -> {
                    ex.sets.forEach { s ->
                        val sec = (s.seconds ?: 0).toFloat()
                        isoSeconds += sec
                        isoKcal += ISOMETRIC_MET * weight * (sec / 3600f)
                    }
                }
                // ── Силовые и «только повторы» ───────────────────────
                else -> {
                    val liftHeight = (LIFT_HEIGHT[ex.name] ?: DEFAULT_LIFT_HEIGHT) * heightFactor
                    ex.sets.forEach { s ->
                        // Поднимаемая масса: снаряд, либо доля веса тела
                        val movedMass = if (s.weight > 0f) s.weight
                        else weight * (BODYWEIGHT_RATIO[ex.name] ?: DEFAULT_BODYWEIGHT_RATIO)
                        // Работа за повтор: подъём + вклад опускания (эксцентрика ~1/3)
                        workJoules += movedMass * G * liftHeight * s.reps * 1.33f
                        strengthReps += s.reps
                        strengthSets += 1
                        loadSum += movedMass
                        loadCount++
                    }
                }
            }
        }

        // Механическая работа → калории (с учётом КПД мышц)
        val workKcal = workJoules / EFFICIENCY / J_PER_KCAL

        // ИНТЕНСИВНОСТЬ: средняя поднимаемая масса относительно веса атлета.
        // 0 — совсем лёгкие веса, 1.0 и выше — работа с весом уровня своего
        // тела и тяжелее. От неё зависят MET силовой работы и отдыха.
        val avgLoad = if (loadCount > 0) loadSum / loadCount else 0f
        val intensity = (avgLoad / weight).coerceIn(0f, 1.2f) / 1.2f
        val strengthMet = STRENGTH_MET_MIN + STRENGTH_MET_RANGE * intensity
        val restMet = REST_MET_MIN + REST_MET_RANGE * intensity

        // Метаболический фон силовой части и отдыха
        val strengthActiveSeconds = maxOf(strengthReps * SECONDS_PER_REP, strengthSets * MIN_SECONDS_PER_SET)
        val strengthBaseKcal = strengthMet * weight * (strengthActiveSeconds / 3600f)

        val totalSeconds = durationMinutes * 60f
        val activeSeconds = cardioSeconds + isoSeconds + strengthActiveSeconds
        val restSeconds = (totalSeconds - activeSeconds).coerceAtLeast(0f)
        val restKcal = restMet * weight * (restSeconds / 3600f)

        return (cardioKcal + isoKcal + workKcal + strengthBaseKcal + restKcal).roundToInt()
    }
}