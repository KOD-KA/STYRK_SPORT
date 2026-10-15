package ru.styrk.app.ui.common

/**
 * Слово при числе по-русски: «1 день», «3 дня», «5 дней».
 *
 * Три формы и одно исключение. Одиннадцать–четырнадцать берут третью
 * форму («12 дней»), хотя кончаются на 1–4, — поэтому сначала смотрим
 * на две последние цифры, потом на одну.
 *
 * Без этого экран говорил «3 дней без тренировки» и «4 упражнений» —
 * мелочь, которую человек замечает раньше любой другой.
 */
fun ruPlural(n: Long, one: String, few: String, many: String): String {
    val a = kotlin.math.abs(n) % 100
    if (a in 11..14) return many
    return when (a % 10) {
        1L -> one
        2L, 3L, 4L -> few
        else -> many
    }
}

fun ruPlural(n: Int, one: String, few: String, many: String): String =
    ruPlural(n.toLong(), one, few, many)
