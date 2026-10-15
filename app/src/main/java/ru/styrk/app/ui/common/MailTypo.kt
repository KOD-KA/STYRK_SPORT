package ru.styrk.app.ui.common

/**
 * Опечатка в адресе почты: «gmil.com» вместо «gmail.com».
 *
 * С такой опечаткой письмо для восстановления пароля уходит в никуда,
 * а при регистрации получается аккаунт, пароль к которому потом
 * не восстановить никак. Сервер здесь не подскажет: «забыли пароль»
 * нарочно отвечает одинаково, есть такой адрес или нет.
 *
 * Поэтому под полем — «Возможно, вы имели в виду …?» с кнопкой
 * «Исправить». Не запрет: редкий настоящий адрес может совпасть
 * с опечаткой, решает человек.
 *
 * Список тот же, что в веб-дневнике (MAIL_FIX в app.html).
 */
object MailTypo {

    private val FIX = mapOf(
        "gmil.com" to "gmail.com", "gmal.com" to "gmail.com", "gmai.com" to "gmail.com",
        "gamil.com" to "gmail.com", "gmial.com" to "gmail.com", "gnail.com" to "gmail.com",
        "gmaill.com" to "gmail.com", "gmaul.com" to "gmail.com", "gmsil.com" to "gmail.com",
        "hmail.com" to "gmail.com", "gmail.co" to "gmail.com", "gmail.cm" to "gmail.com",
        "gmail.con" to "gmail.com", "gmail.om" to "gmail.com", "gmail.ru" to "gmail.com",
        "yandex.ry" to "yandex.ru", "yandx.ru" to "yandex.ru", "yndex.ru" to "yandex.ru",
        "yanex.ru" to "yandex.ru", "yadex.ru" to "yandex.ru", "yamdex.ru" to "yandex.ru",
        "yandex.r" to "yandex.ru", "ya.ry" to "ya.ru",
        "mail.ry" to "mail.ru", "mal.ru" to "mail.ru", "mai.ru" to "mail.ru", "maill.ru" to "mail.ru",
        "mial.ru" to "mail.ru", "meil.ru" to "mail.ru", "mail.r" to "mail.ru",
        "inbox.ry" to "inbox.ru", "list.ry" to "list.ru", "bk.ry" to "bk.ru",
        "rambler.ry" to "rambler.ru", "rambeler.ru" to "rambler.ru", "ramber.ru" to "rambler.ru",
        "hotmal.com" to "hotmail.com", "hotmial.com" to "hotmail.com", "hotmail.co" to "hotmail.com",
        "outlok.com" to "outlook.com", "outlook.co" to "outlook.com",
        "iclod.com" to "icloud.com", "icoud.com" to "icloud.com", "icloud.co" to "icloud.com"
    )

    private val SHAPE = Regex("^([^@\\s]+)@([^@\\s]+)$")

    /** Исправленный адрес — или null, если опечатки не видно. */
    fun suggest(email: String): String? {
        val m = SHAPE.find(email.trim()) ?: return null
        val fix = FIX[m.groupValues[2].lowercase()] ?: return null
        return m.groupValues[1] + "@" + fix
    }
}
