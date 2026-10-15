package ru.styrk.app.data.model

/**
 * РАЗРЯДЫ ЕВСК — те же таблицы, что в веб-дневнике.
 *
 * Здесь лежит приказ Минспорта России и ничего сверх него: девять
 * ступеней (три юношеских и шесть взрослых), девять весовых категорий,
 * четыре таблицы — сумма троеборья и жим, каждая в классике
 * и в экипировке.
 *
 * Числа перенесены сюда НЕ РУКАМИ: файл собран из того же источника,
 * что и дневник в браузере. Переписывать полторы тысячи чисел с экрана
 * — верный способ получить два разных приложения, и однажды это уже
 * ловилось: в таблице на сайте стояло 2800 там, где должно быть 280.
 *
 * Чего здесь нет и не будет: возраста. Юношеские разряды присваивают
 * с двенадцати лет, но спрашивать у человека год рождения ради строчки
 * в дневнике — плохой обмен. Юношеские ступени просто исчезают, как
 * только взят III взрослый.
 *
 * И главное, что стоит помнить, читая эти числа: разряд присваивают
 * на соревнованиях, по правилам, с судьями. Здесь линейка, а не
 * протокол.
 */
object Standards {

    /** Верхние границы весовых категорий. Последняя — «свыше». */
    private val CLASSES_M = floatArrayOf(53f, 59f, 66f, 74f, 83f, 93f, 105f, 120f, Float.MAX_VALUE)
    private val CLASSES_F = floatArrayOf(43f, 47f, 52f, 57f, 63f, 69f, 76f, 84f, Float.MAX_VALUE)

    val RANKS = listOf(
        "III юношеский", "II юношеский", "I юношеский",
        "III разряд", "II разряд", "I разряд", "КМС", "МС", "МСМК"
    )

    /** Родительный падеж — для «до … ещё столько-то». Аббревиатуры не склоняются. */
    val GENIT = listOf(
        "III юношеского", "II юношеского", "I юношеского",
        "III разряда", "II разряда", "I разряда", "КМС", "МС", "МСМК"
    )

    val SHORT = listOf("3ю", "2ю", "1ю", "III", "II", "I", "КМС", "МС", "МСМК")

    /**
     * Сколько ступеней внизу — юношеские.
     *
     * Первая взрослая идёт под этим номером; всё, что ниже, срезается,
     * как только взято. Взрослому человеку «II юношеский» не говорит
     * ничего, а тому, кто до III разряда ещё не дотянулся, — говорит
     * многое: без юношеских внизу лестницы пустота и одно слово
     * «нет разряда», а до первой настоящей ступени может быть
     * полсотни килограммов.
     */
    const val YOUTH = 3

    // ── Троеборье классическое ──────────────────────────────────────
    private val TOTAL_M = arrayOf(
        arrayOf<Float?>(185f, 200f, 215f, 240f, 265f, 300f, 340f, null, null),
        arrayOf<Float?>(205f, 225f, 245f, 275f, 300f, 350f, 390f, 472.5f, 550f),
        arrayOf<Float?>(215f, 245f, 270f, 305f, 335f, 390f, 435f, 545f, 640f),
        arrayOf<Float?>(230f, 260f, 295f, 325f, 365f, 425f, 475f, 600f, 705f),
        arrayOf<Float?>(255f, 290f, 320f, 350f, 400f, 465f, 515f, 665f, 770f),
        arrayOf<Float?>(275f, 315f, 345f, 385f, 430f, 490f, 550f, 710f, 810f),
        arrayOf<Float?>(300f, 330f, 370f, 415f, 460f, 515f, 600f, 740f, 850f),
        arrayOf<Float?>(325f, 355f, 395f, 455f, 505f, 560f, 640f, 775f, 880f),
        arrayOf<Float?>(345f, 370f, 425f, 485f, 525f, 590f, 690f, 820f, 930f)
    )
    private val TOTAL_F = arrayOf(
        arrayOf<Float?>(90f, 97.5f, 105f, 115f, 125f, 150f, 180f, null, null),
        arrayOf<Float?>(97.5f, 105f, 115f, 125f, 145f, 175f, 220f, 280f, 350f),
        arrayOf<Float?>(105f, 115f, 125f, 145f, 170f, 200f, 255f, 315f, 385f),
        arrayOf<Float?>(115f, 125f, 145f, 165f, 185f, 215f, 285f, 340f, 410f),
        arrayOf<Float?>(125f, 140f, 160f, 180f, 200f, 240f, 315f, 365f, 440f),
        arrayOf<Float?>(137.5f, 150f, 170f, 190f, 222.5f, 260f, 330f, 380f, 460f),
        arrayOf<Float?>(150f, 170f, 190f, 210f, 242.5f, 285f, 345f, 395f, 480f),
        arrayOf<Float?>(160f, 180f, 200f, 220f, 255f, 300f, 355f, 405f, 500f),
        arrayOf<Float?>(180f, 200f, 220f, 250f, 285f, 320f, 375f, 420f, 550f)
    )

    /**
     * Жим классический.
     *
     * МСМК в этой дисциплине не присваивают — выше МС ступеней нет,
     * и это не пропуск в таблице. Пустая строка (мужчины до 53,
     * женщины до 43) значит, что нормы нет вовсе: там ориентир берётся
     * долей от суммы.
     */
    private val BENCH_M = arrayOf(
        null,
        arrayOf<Float?>(57.5f, 65f, 75f, 85f, 90f, 105f, 115f, 135f, null),
        arrayOf<Float?>(62.5f, 75f, 85f, 90f, 95f, 115f, 137.5f, 155f, null),
        arrayOf<Float?>(70f, 80f, 90f, 100f, 110f, 127.5f, 140f, 170f, null),
        arrayOf<Float?>(75f, 85f, 95f, 115f, 125f, 140f, 160f, 190f, null),
        arrayOf<Float?>(80f, 95f, 105f, 122.5f, 135f, 150f, 172.5f, 197.5f, null),
        arrayOf<Float?>(95f, 105f, 115f, 130f, 145f, 160f, 180f, 210f, null),
        arrayOf<Float?>(100f, 110f, 120f, 135f, 155f, 175f, 195f, 222.5f, null),
        arrayOf<Float?>(105f, 112.5f, 125f, 140f, 160f, 190f, 200f, 230f, null)
    )
    private val BENCH_F = arrayOf(
        null,
        arrayOf<Float?>(25f, 30f, 35f, 40f, 45f, 50f, 62.5f, 75f, null),
        arrayOf<Float?>(30f, 35f, 40f, 45f, 50f, 55f, 67.5f, 82.5f, null),
        arrayOf<Float?>(35f, 37.5f, 42.5f, 50f, 55f, 60f, 75f, 90f, null),
        arrayOf<Float?>(40f, 45f, 50f, 55f, 60f, 67.5f, 85f, 97.5f, null),
        arrayOf<Float?>(45f, 50f, 55f, 60f, 65f, 72.5f, 90f, 105f, null),
        arrayOf<Float?>(50f, 55f, 60f, 65f, 70f, 80f, 95f, 110f, null),
        arrayOf<Float?>(65f, 70f, 75f, 80f, 85f, 90f, 107.5f, 125f, null),
        arrayOf<Float?>(65f, 70f, 75f, 80f, 85f, 90f, 107.5f, 125f, null)
    )

    /**
     * Те же две дисциплины, но В ЭКИПИРОВКЕ — комбинезон, жимовая
     * рубашка, бинты. Это другой спорт внутри того же спорта:
     * снаряжение возвращает часть усилия, и нормы поэтому выше.
     * В зале так никто не тренируется, и главной лестницей это быть
     * не должно — но спросить «а сколько нужно там?» человек вправе.
     */
    private val TOTAL_EQ_M = arrayOf(
        arrayOf<Float?>(195f, 215f, 232.5f, 260f, 282.5f, 330f, 400f, null, null),
        arrayOf<Float?>(212.5f, 240f, 260f, 290f, 315f, 360f, 450f, 540f, 615f),
        arrayOf<Float?>(227.5f, 257.5f, 287.5f, 320f, 350f, 400f, 505f, 590f, 710f),
        arrayOf<Float?>(247.5f, 280f, 317.5f, 352.5f, 385f, 440f, 540f, 660f, 770f),
        arrayOf<Float?>(277.5f, 307.5f, 352.5f, 387.5f, 422.5f, 480f, 575f, 760f, 845f),
        arrayOf<Float?>(307.5f, 340f, 382.5f, 412.5f, 465f, 515f, 600f, 790f, 925f),
        arrayOf<Float?>(330f, 355f, 397.5f, 460f, 500f, 555f, 640f, 840f, 965f),
        arrayOf<Float?>(347.5f, 372.5f, 422.5f, 497.5f, 530f, 600f, 680f, 870f, 1015f),
        arrayOf<Float?>(372.5f, 390f, 455f, 510f, 545f, 625f, 740f, 890f, 1045f)
    )
    private val TOTAL_EQ_F = arrayOf(
        arrayOf<Float?>(97.5f, 112.5f, 122.5f, 137.5f, 150f, 180f, 245f, null, null),
        arrayOf<Float?>(105f, 122.5f, 135f, 150f, 165f, 195f, 270f, 320f, 395f),
        arrayOf<Float?>(117.5f, 135f, 147.5f, 167.5f, 182.5f, 215f, 295f, 370f, 440f),
        arrayOf<Float?>(127.5f, 147.5f, 162.5f, 182.5f, 200f, 230f, 320f, 400f, 490f),
        arrayOf<Float?>(142.5f, 162.5f, 180f, 202.5f, 220f, 255f, 340f, 420f, 535f),
        arrayOf<Float?>(152.5f, 175f, 190f, 215f, 237.5f, 280f, 360f, 435f, 555f),
        arrayOf<Float?>(167.5f, 190f, 205f, 235f, 265f, 305f, 380f, 450f, 575f),
        arrayOf<Float?>(177.5f, 205f, 220f, 260f, 285f, 330f, 405f, 465f, 595f),
        arrayOf<Float?>(192.5f, 217.5f, 235f, 285f, 320f, 355f, 425f, 480f, 615f)
    )
    private val BENCH_EQ_M = arrayOf(
        arrayOf<Float?>(52.5f, 57.5f, 62.5f, 72.5f, 80f, 92.5f, 107.5f, null, null),
        arrayOf<Float?>(57.5f, 65f, 72.5f, 85f, 95f, 105f, 122.5f, 147.5f, 180f),
        arrayOf<Float?>(65f, 72.5f, 80f, 92.5f, 105f, 122.5f, 140f, 182.5f, 215f),
        arrayOf<Float?>(77.5f, 85f, 95f, 112.5f, 120f, 137.5f, 160f, 207.5f, 240f),
        arrayOf<Float?>(85f, 95f, 105f, 122.5f, 135f, 155f, 180f, 230f, 270f),
        arrayOf<Float?>(92.5f, 102.5f, 115f, 135f, 150f, 170f, 200f, 250f, 297.5f),
        arrayOf<Float?>(100f, 110f, 122.5f, 145f, 160f, 185f, 215f, 270f, 315f),
        arrayOf<Float?>(107.5f, 120f, 132.5f, 155f, 170f, 195f, 230f, 280f, 330f),
        arrayOf<Float?>(115f, 127.5f, 140f, 165f, 180f, 210f, 245f, 295f, 345f)
    )
    private val BENCH_EQ_F = arrayOf(
        arrayOf<Float?>(25f, 30f, 35f, 40f, 45f, 50f, 57.5f, null, null),
        arrayOf<Float?>(30f, 35f, 40f, 45f, 50f, 57.5f, 67.5f, 87.5f, 100f),
        arrayOf<Float?>(35f, 40f, 45f, 50f, 55f, 62.5f, 77.5f, 97.5f, 112.5f),
        arrayOf<Float?>(40f, 45f, 50f, 55f, 60f, 70f, 85f, 107.5f, 122.5f),
        arrayOf<Float?>(45f, 50f, 55f, 60f, 67.5f, 77.5f, 95f, 115f, 132.5f),
        arrayOf<Float?>(50f, 55f, 60f, 65f, 72.5f, 85f, 100f, 125f, 140f),
        arrayOf<Float?>(52.5f, 57.5f, 62.5f, 67.5f, 75f, 90f, 105f, 132.5f, 150f),
        arrayOf<Float?>(60f, 65f, 70f, 77.5f, 85f, 100f, 117.5f, 145f, 172.5f),
        arrayOf<Float?>(60f, 65f, 70f, 77.5f, 85f, 100f, 117.5f, 145f, 172.5f)
    )

    /**
     * Доли суммы для приседа и тяги.
     *
     * Своих разрядных таблиц у них нет — есть только сумма. Доля
     * не выдумана: это обычное соотношение движений в троеборье.
     */
    private val SHARE = mapOf(
        "squat" to 0.35, "bench" to 0.28, "dead" to 0.37
    )

    /**
     * Ориентиры относительно веса тела — для движений без разрядной
     * таблицы. Это НЕ нормативы: ни одна федерация не присваивает
     * разряд обычному человеку.
     */
    private val ORIENT_M = mapOf(
        "press" to doubleArrayOf(0.35, 0.65, 0.95),
        "curl" to doubleArrayOf(0.25, 0.45, 0.7)
    )
    private val ORIENT_F = mapOf(
        "press" to doubleArrayOf(0.2, 0.4, 0.6),
        "curl" to doubleArrayOf(0.15, 0.28, 0.45)
    )
    val ORIENT_SHORT = listOf("обычный", "зал", "сильно")
    val ORIENT_LONG = listOf("как у обычного", "как в зале", "сильно")
    val ORIENT_GENIT = listOf("обычного уровня", "уровня зала", "сильного уровня")

    /**
     * Коэффициенты IPF GL (классика, версия 2020). Сто очков —
     * примерно уровень мирового рекорда. Нужны ровно для одного:
     * сравнить людей разного веса не на глазок.
     */
    private const val GL_M_A = 1199.72839
    private const val GL_M_B = 1025.18162
    private const val GL_M_C = 0.00921
    private const val GL_F_A = 610.32796
    private const val GL_F_B = 1045.59282
    private const val GL_F_C = 0.03048

    /** Три движения троеборья: ключ, имя в справочнике, короткая подпись. */
    data class Lift(val key: String, val name: String, val cap: String, val note: String = "")

    val LIFTS = listOf(
        Lift("squat", "Приседания со штангой", "Присед"),
        Lift("bench", "Жим штанги лёжа", "Жим лёжа"),
        Lift("dead", "Становая тяга", "Тяга")
    )

    /**
     * Движения без открытой разрядной таблицы.
     *
     * Разряды по жиму стоя есть у WRPF, но их нормы лежат в файлах
     * федерации, а не в открытой таблице, и выдумывать числа вместо
     * них мы не станем. Подъём на бицепс отсюда ушёл: у него теперь
     * настоящая таблица WRPF (см. FedStandards).
     */
    val EXTRA = listOf(
        Lift(
            "press", "Жим штанги стоя", "Жим стоя",
            "Стоя, без подседа и без раскачки. Разряды по жиму стоя есть у WRPF; " +
                "здесь — ориентир по весу тела."
        )
    )

    // ═══════════════════ СЧЁТ ═══════════════════

    /**
     * Одноповторный максимум по рабочему подходу.
     *
     * Формула Бжицки, а не Эпли, и не больше десяти повторов.
     *
     * Эпли на малых повторах щедрее: «130 × 4» давало 147,3, Бжицки —
     * 141,8; на десяти повторах они сходятся. А без потолка повторов
     * «60 × 20» превращалось в сотню — хотя двадцать повторов говорят
     * о выносливости, а не о максимуме. Больше десяти считаем как
     * десять. Оценка всё равно остаётся оценкой: в зале приседают
     * не всегда на соревновательную глубину и жмут без паузы на груди,
     * а на помосте так нельзя.
     *
     * Считается во Float ровно потому же, почему в браузере нарочно
     * зажат Math.fround: там числа двойной точности, здесь одинарной,
     * и без этого выходило «до след. уровня 27,1 кг» тут против «27,2»
     * там. Мелочь, но человек видит два экрана одного приложения
     * и вправе ждать одинаковых цифр.
     */
    fun oneRM(weight: Float, reps: Int): Float =
        if (reps <= 1) weight else weight * 36f / (37f - minOf(reps, 10))

    private fun classes(male: Boolean) = if (male) CLASSES_M else CLASSES_F

    private fun classIdx(male: Boolean, bodyWeight: Float): Int {
        val cs = classes(male)
        for (i in cs.indices) if (bodyWeight <= cs[i]) return i
        return cs.size - 1
    }

    /** «до 83 кг» или «свыше 120 кг». */
    fun classWord(male: Boolean, bodyWeight: Float): String {
        val cs = classes(male)
        val i = classIdx(male, bodyWeight)
        return if (cs[i] == Float.MAX_VALUE) "свыше " + fmt(cs[i - 1]) + " кг"
        else "до " + fmt(cs[i]) + " кг"
    }

    /**
     * Ступени для одного движения, в килограммах. null в ячейке —
     * звание в этой категории не присваивается.
     */
    fun steps(male: Boolean, bodyWeight: Float, key: String, equipped: Boolean): Array<Float?> {
        val i = classIdx(male, bodyWeight)
        val total = when {
            equipped && male -> TOTAL_EQ_M[i]
            equipped -> TOTAL_EQ_F[i]
            male -> TOTAL_M[i]
            else -> TOTAL_F[i]
        }

        if (key == "total") return total.copyOf()

        if (key == "bench") {
            val row = when {
                equipped && male -> BENCH_EQ_M[i]
                equipped -> BENCH_EQ_F[i]
                male -> BENCH_M[i]
                else -> BENCH_F[i]
            }
            if (row != null) return row.copyOf()
        }

        // До половины килограмма: блины меньше 1,25 кг в залах редкость,
        // а дробь в четыре знака выглядит как ошибка счёта.
        /* Доли считаются в ДВОЙНОЙ точности, а не в одинарной.

           Это не придирка к знакам после запятой: 365 × 0,35 в двойной
           точности даёт 127,7499…, в одинарной — 127,75 ровно. После
           округления до половины килограмма получается 127,5 против
           128 — и человек видит разные цифры на телефоне и в браузере
           в одной и той же строке. Дневник считает в двойной,
           значит и здесь двойная.

           Одноповторный максимум, наоборот, считается в одинарной —
           см. oneRM. Там дневник нарочно зажат под телефон. */
        val share = SHARE[key] ?: SHARE.getValue("bench")
        return Array(total.size) { n ->
            total[n]?.let { v -> (Math.round(v.toDouble() * share * 2.0) / 2.0).toFloat() }
        }
    }

    /** Три ориентира по весу тела для движений без разрядной таблицы. */
    fun orient(male: Boolean, bodyWeight: Float, key: String): FloatArray? {
        val p = (if (male) ORIENT_M else ORIENT_F)[key] ?: return null
        return FloatArray(p.size) { n ->
            (Math.round(p[n] * bodyWeight.toDouble() * 2.0) / 2.0).toFloat()
        }
    }

    /**
     * Где стоит результат: достигнутая ступень и следующая.
     *
     * [idx] = -1 значит «ниже первой ступени»; [next] = null значит
     * «выше уже ничего нет».
     */
    data class Place(val idx: Int, val next: Int?, val have: Float, val need: Float?, val top: Float)

    fun place(kg: Float, st: Array<Float?>): Place {
        var idx = -1
        for (i in st.indices) {
            val v = st[i]
            if (v != null && kg >= v) idx = i
        }
        var next: Int? = null
        for (i in (idx + 1) until st.size) if (st[i] != null) { next = i; break }
        var top = 0f
        st.forEach { v -> if (v != null) top = v }
        return Place(
            idx = idx,
            next = next,
            have = if (idx < 0) 0f else (st[idx] ?: 0f),
            need = next?.let { st[it] },
            top = top
        )
    }

    /** Название ступени словами. */
    fun word(idx: Int): String = if (idx < 0) "нет разряда" else RANKS[idx]

    fun glPoints(male: Boolean, bodyWeight: Float, total: Float): Double? {
        if (bodyWeight <= 0f || total <= 0f) return null
        // Тоже в двойной точности: формула официальная, и ломать её
        // округлением ради экономии, которой никто не заметит, незачем.
        val a = if (male) GL_M_A else GL_F_A
        val b = if (male) GL_M_B else GL_F_B
        val c = if (male) GL_M_C else GL_F_C
        val d = a - b * Math.exp(-c * bodyWeight.toDouble())
        // Наружу отдаём Double: во Float последние знаки съезжают,
        // и число переставало сходиться с дневником до запятой.
        return if (d > 0.0) total.toDouble() * 100.0 / d else null
    }

    /** Число без лишних нулей: 83 → «83», 82.5 → «82,5». */
    fun fmt(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else "%.1f".format(v)
}
