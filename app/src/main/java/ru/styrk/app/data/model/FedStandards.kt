package ru.styrk.app.data.model

/**
 * Нормативы ФЕДЕРАЦИЙ — не ЕВСК.
 *
 * По этим дисциплинам проводят соревнования и присваивают разряды,
 * но это разряды федерации, а не звание от Минспорта: «МС ISF» —
 * не то же самое, что «мастер спорта России». Поэтому они лежат
 * отдельно от Standards и показываются в отдельном свёрнутом списке.
 *
 * Откуда числа (переписаны 30.09.2026, без правок, даже там, где
 * ступени в таблице идут неровно — так опубликовано):
 *   подтягивания, брусья, двоеборье — ISF-Россия (Федерация
 *     стритлифтинга России), streetlifting.ru/docs/standards;
 *   строгий подъём на бицепс — WRPF 2026, без допинг-контроля.
 *
 * Те же таблицы лежат в дневнике (FED в app.html); обе копии
 * собираются одной программой из одного файла (TOOLS/data/fed_norms.py).
 *
 * Ступени по возрастанию: 3 юн., 2 юн., 1 юн., III, II, I, КМС, МС,
 * МСМК, Элита. null — ступени в этой категории нет.
 */
object FedStandards {

    class Table(val classes: DoubleArray, val rows: Array<Array<Double?>>)

    val SHORT = arrayOf("3юн", "2юн", "1юн", "III", "II", "I", "КМС", "МС", "МСМК", "Элита")
    val LONG = arrayOf(
        "III юношеский", "II юношеский", "I юношеский", "III разряд", "II разряд",
        "I разряд", "КМС", "мастер спорта", "МСМК", "Элита"
    )
    val GENIT = arrayOf(
        "III юношеского", "II юношеского", "I юношеского", "III разряда", "II разряда",
        "I разряда", "КМС", "мастера спорта", "МСМК", "Элиты"
    )

    /**
     * Дисциплина: ключ, подпись, откуда норма и какие упражнения
     * справочника в неё идут. [bodyweight] — результат считается как
     * ДОПОЛНИТЕЛЬНЫЙ вес: подтягиваются и отжимаются с весом тела,
     * а в протокол пишут то, что подвешено на пояс.
     */
    data class Disc(
        val key: String,
        val cap: String,
        val names: List<String>,
        val bodyweight: Boolean,
        val source: String,
        val note: String
    )

    val DISCS = listOf(
        Disc("pull", "Подтягивания с весом", listOf("Подтягивания"), true,
            "ISF-Россия (стритлифтинг)",
            "В зачёт — дополнительный вес на поясе. Подбородок выше перекладины, " +
                "внизу руки выпрямлены полностью, без рывка и раскачки."),
        Disc("dip", "Брусья с весом", listOf("Отжимания на брусьях с весом", "Отжимания на брусьях"), true,
            "ISF-Россия (стритлифтинг)",
            "В зачёт — дополнительный вес на поясе. Внизу плечо ниже локтя, " +
                "наверху руки выпрямлены полностью."),
        Disc("curl", "Строгий подъём на бицепс", listOf("Подъём штанги на бицепс"), false,
            "WRPF, без допинг-контроля",
            "Спиной к стене или стойке, корпус не помогает. С раскачкой выходит " +
                "заметно больше — такой результат сюда не годится.")
    )

    /** Двоеборье стритлифтинга: подтягивания + брусья. */
    const val STREET_CAP = "Стритлифтинг: двоеборье"

    private val PULL_M = Table(
        doubleArrayOf(52.0, 56.0, 60.0, 67.5, 75.0, 82.5, 90.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(10.0, 12.5, 15.0, 20.0, 25.0, 35.0, 40.0, 52.5, null, null),
            arrayOf<Double?>(12.5, 17.5, 22.5, 27.5, 35.0, 45.0, 50.0, 62.5, 70.0, 82.5),
            arrayOf<Double?>(15.0, 20.0, 25.0, 35.0, 45.0, 52.5, 55.0, 65.0, 72.5, 87.5),
            arrayOf<Double?>(17.5, 22.5, 27.5, 37.5, 47.5, 55.0, 60.0, 67.5, 77.5, 91.25),
            arrayOf<Double?>(20.0, 25.0, 30.0, 40.0, 51.25, 57.5, 65.0, 72.5, 82.5, 95.0),
            arrayOf<Double?>(17.5, 23.75, 31.25, 38.75, 53.75, 60.0, 67.5, 75.0, 85.0, 98.75),
            arrayOf<Double?>(15.0, 21.25, 27.5, 37.5, 55.0, 62.5, 70.0, 80.0, 90.0, 100.0),
            arrayOf<Double?>(12.5, 20.0, 25.0, 35.0, 53.75, 65.0, 75.0, 82.5, 92.5, 101.25)
        )
    )
    private val PULL_F = Table(
        doubleArrayOf(44.0, 48.0, 52.0, 56.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(null, null, 5.0, 7.5, 10.0, 12.5, 15.0, 18.75, null, null),
            arrayOf<Double?>(null, null, 7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 25.0, 32.5),
            arrayOf<Double?>(null, 5.0, 10.0, 12.5, 15.0, 17.5, 20.0, 22.5, 27.5, 35.0),
            arrayOf<Double?>(null, 7.5, 12.5, 15.0, 17.5, 20.0, 22.5, 25.0, 32.5, 40.0),
            arrayOf<Double?>(null, 10.0, 15.0, 17.5, 20.0, 22.5, 25.0, 27.5, 35.0, 41.25)
        )
    )
    private val DIP_M = Table(
        doubleArrayOf(52.0, 56.0, 60.0, 67.5, 75.0, 82.5, 90.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(15.0, 17.5, 22.5, 30.0, 40.0, 50.0, 57.5, 62.5, null, null),
            arrayOf<Double?>(17.5, 20.0, 25.0, 35.0, 45.0, 55.0, 62.5, 72.5, 85.0, 105.0),
            arrayOf<Double?>(20.0, 25.0, 30.0, 40.0, 50.0, 65.0, 75.0, 85.0, 102.5, 120.0),
            arrayOf<Double?>(25.0, 30.0, 35.0, 45.0, 55.0, 75.0, 85.0, 100.0, 112.5, 130.0),
            arrayOf<Double?>(30.0, 35.0, 40.0, 50.0, 60.0, 80.0, 90.0, 105.0, 117.5, 140.0),
            arrayOf<Double?>(32.5, 40.0, 45.0, 55.0, 65.0, 85.0, 95.0, 110.0, 122.5, 147.5),
            arrayOf<Double?>(30.0, 42.5, 42.5, 60.0, 72.5, 90.0, 100.0, 115.0, 127.5, 150.0),
            arrayOf<Double?>(25.0, 37.5, 40.0, 65.0, 77.5, 95.0, 102.5, 120.0, 135.0, 152.5)
        )
    )
    private val DIP_F = Table(
        doubleArrayOf(44.0, 48.0, 52.0, 56.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 22.5, 27.5, null, null),
            arrayOf<Double?>(10.0, 12.5, 15.0, 17.5, 21.25, 23.75, 26.25, 31.25, 37.5, 42.5),
            arrayOf<Double?>(12.5, 15.0, 17.5, 22.5, 25.0, 27.5, 30.0, 35.0, 40.0, 45.0),
            arrayOf<Double?>(10.0, 12.5, 16.25, 25.0, 27.5, 30.0, 32.5, 37.5, 42.5, 47.5),
            arrayOf<Double?>(7.5, 10.0, 15.0, 23.75, 30.0, 32.5, 35.0, 40.0, 45.0, 50.0)
        )
    )
    private val STREET_M = Table(
        doubleArrayOf(52.0, 56.0, 60.0, 67.5, 75.0, 82.5, 90.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(25.0, 30.0, 37.5, 50.0, 65.0, 80.0, 97.5, 115.0, null, null),
            arrayOf<Double?>(30.0, 35.0, 47.5, 65.0, 82.5, 97.5, 112.5, 127.5, 160.0, 175.0),
            arrayOf<Double?>(35.0, 45.0, 55.0, 72.5, 90.0, 105.0, 122.5, 137.5, 170.0, 190.0),
            arrayOf<Double?>(42.5, 52.5, 62.5, 80.0, 97.5, 112.5, 135.0, 152.5, 180.0, 205.0),
            arrayOf<Double?>(50.0, 60.0, 70.0, 90.0, 105.0, 122.5, 145.0, 165.0, 190.0, 215.0),
            arrayOf<Double?>(42.5, 50.0, 65.0, 85.0, 110.0, 130.0, 150.0, 175.0, 200.0, 222.5),
            arrayOf<Double?>(35.0, 42.5, 55.0, 82.5, 115.0, 135.0, 160.0, 185.0, 210.0, 230.0),
            arrayOf<Double?>(30.0, 35.0, 50.0, 80.0, 120.0, 140.0, 165.0, 195.0, 217.5, 235.0)
        )
    )
    private val STREET_F = Table(
        doubleArrayOf(44.0, 48.0, 52.0, 56.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(12.5, 15.0, 17.5, 25.0, 27.5, 32.5, 37.5, 45.0, null, null),
            arrayOf<Double?>(15.0, 20.0, 22.5, 30.0, 32.5, 37.5, 42.5, 52.5, 62.5, 77.5),
            arrayOf<Double?>(17.5, 22.5, 27.5, 32.5, 37.5, 45.0, 47.5, 57.5, 72.5, 87.5),
            arrayOf<Double?>(15.0, 20.0, 25.0, 37.5, 42.5, 50.0, 52.5, 67.5, 77.5, 95.0),
            arrayOf<Double?>(12.5, 17.5, 22.5, 35.0, 45.0, 52.5, 55.0, 72.5, 82.5, 97.5)
        )
    )
    private val CURL_M = Table(
        doubleArrayOf(52.0, 56.0, 60.0, 67.5, 75.0, 82.5, 90.0, 100.0, 110.0, 125.0, 140.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(null, null, null, 25.0, 30.0, 35.0, 40.0, 47.5, 55.0, null),
            arrayOf<Double?>(null, null, null, 27.5, 32.5, 37.5, 42.5, 50.0, 57.5, null),
            arrayOf<Double?>(null, null, null, 30.0, 35.0, 40.0, 45.0, 52.5, 62.5, null),
            arrayOf<Double?>(null, null, null, 32.5, 37.5, 45.0, 52.5, 60.0, 70.0, null),
            arrayOf<Double?>(null, null, null, 35.0, 42.5, 50.0, 57.5, 65.0, 75.0, null),
            arrayOf<Double?>(null, null, null, 40.0, 47.5, 55.0, 62.5, 70.0, 80.0, null),
            arrayOf<Double?>(null, null, null, 45.0, 52.5, 60.0, 67.5, 75.0, 85.0, null),
            arrayOf<Double?>(null, null, null, 50.0, 57.5, 65.0, 72.5, 80.0, 90.0, null),
            arrayOf<Double?>(null, null, null, 55.0, 62.5, 70.0, 77.5, 85.0, 95.0, null),
            arrayOf<Double?>(null, null, null, 60.0, 67.5, 75.0, 82.5, 90.0, 100.0, null),
            arrayOf<Double?>(null, null, null, 65.0, 72.5, 80.0, 87.5, 95.0, 105.0, null),
            arrayOf<Double?>(null, null, null, 70.0, 77.5, 85.0, 92.5, 102.5, 112.5, null)
        )
    )
    private val CURL_F = Table(
        doubleArrayOf(44.0, 48.0, 52.0, 56.0, 60.0, 67.5, 75.0, 82.5, 90.0, Double.MAX_VALUE),
        arrayOf(
            arrayOf<Double?>(null, null, null, 17.5, 20.0, 22.5, 25.0, 30.0, 35.0, null),
            arrayOf<Double?>(null, null, null, 20.0, 22.5, 25.0, 27.5, 32.5, 37.5, null),
            arrayOf<Double?>(null, null, null, 22.5, 25.0, 27.5, 30.0, 35.0, 40.0, null),
            arrayOf<Double?>(null, null, null, 25.0, 27.5, 30.0, 32.5, 37.5, 42.5, null),
            arrayOf<Double?>(null, null, null, 27.5, 30.0, 32.5, 35.0, 40.0, 45.0, null),
            arrayOf<Double?>(null, null, null, 30.0, 32.5, 35.0, 37.5, 42.5, 47.5, null),
            arrayOf<Double?>(null, null, null, 32.5, 35.0, 37.5, 40.0, 45.0, 50.0, null),
            arrayOf<Double?>(null, null, null, 35.0, 37.5, 40.0, 42.5, 47.5, 52.5, null),
            arrayOf<Double?>(null, null, null, 37.5, 40.0, 42.5, 47.5, 52.5, 57.5, null),
            arrayOf<Double?>(null, null, null, 40.0, 42.5, 47.5, 52.5, 57.5, 62.5, null)
        )
    )
    private fun table(key: String, male: Boolean): Table? = when (key) {
        "pull" -> if (male) PULL_M else PULL_F
        "dip" -> if (male) DIP_M else DIP_F
        "street" -> if (male) STREET_M else STREET_F
        "curl" -> if (male) CURL_M else CURL_F
        else -> null
    }

    /** Ступени для веса тела. null — такой дисциплины нет. */
    fun steps(key: String, male: Boolean, bodyWeight: Double): Array<Double?>? {
        val t = table(key, male) ?: return null
        var i = t.classes.size - 1
        for (n in t.classes.indices) if (bodyWeight <= t.classes[n]) { i = n; break }
        return t.rows[i].copyOf()
    }

    /** «до 82,5 кг» или «свыше 90 кг» — категория в таблице этой дисциплины. */
    fun classWord(key: String, male: Boolean, bodyWeight: Double): String {
        val t = table(key, male) ?: return ""
        for (n in t.classes.indices) {
            if (bodyWeight <= t.classes[n]) {
                return if (t.classes[n] == Double.MAX_VALUE)
                    "свыше " + fmt(t.classes[n - 1]) + " кг" else "до " + fmt(t.classes[n]) + " кг"
            }
        }
        return ""
    }

    /**
     * Сколько подняли бы на один раз — в том, что пишут в протокол.
     *
     * У штанги это обычная формула Эпли. У подтягиваний и брусьев
     * поднимается и собственный вес, поэтому Эпли считается от ВСЕГО
     * веса (тело + груз), а из результата тело вычитается. Иначе
     * «20 кг × 8» давало бы жалкие 25 кг, хотя на деле это около
     * 45. Формула — Бжицки, повторов не больше десяти: как у штанги
     * (см. Standards.oneRM).
     */
    fun oneRM(weight: Double, reps: Int, bodyweight: Boolean, bodyWeightKg: Double): Double {
        if (weight <= 0.0) return 0.0
        if (reps <= 1) return weight
        val r = minOf(reps, 10)
        val k = 36.0 / (37.0 - r)
        val est = if (bodyweight) (bodyWeightKg + weight) * k - bodyWeightKg
                  else weight * k
        return Math.round(est * 10.0) / 10.0
    }

    data class Place(val idx: Int, val next: Int?, val need: Double?)

    fun place(kg: Double, st: Array<Double?>): Place {
        var idx = -1
        for (i in st.indices) { val v = st[i]; if (v != null && kg >= v) idx = i }
        var next: Int? = null
        for (i in (idx + 1) until st.size) if (st[i] != null) { next = i; break }
        return Place(idx, next, next?.let { st[it] })
    }

    fun fmt(v: Double): String {
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r)) r.toLong().toString()
        else r.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }
}
