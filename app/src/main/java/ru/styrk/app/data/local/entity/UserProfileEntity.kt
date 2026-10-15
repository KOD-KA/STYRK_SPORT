package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Профиль пользователя — одна строка (id всегда 1).
 *
 * photoPath — абсолютный путь к скопированному во внутреннюю память файлу
 * фото. В бэкап путь НЕ попадает: он привязан к устройству (само фото
 * переносится картинкой внутри файла бэкапа).
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String? = null,
    val photoPath: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,

    /**
     * Пол: "m", "f" или null, пока не выбран.
     *
     * Нужен ровно для одного — разрядных таблиц: у мужчин и женщин
     * они разные, и показать женщине мужскую лестницу значит соврать
     * ей в лицо числом. Ни на что другое в приложении он не влияет
     * и нигде, кроме этой строки, не спрашивается.
     *
     * null — законное состояние, а не «ещё не заполнили»: человек
     * вправе не отвечать, и тогда разряды просто не показываются.
     */
    val sex: String? = null,

    // ═══ ПОЛЯ ДЛЯ СИНХРОНИЗАЦИИ ═══
    // Профиль всегда один, поэтому uuid у него постоянный — "me".
    // Случайный здесь был бы вреден: при обмене профиль каждый раз
    // выглядел бы новой записью и размножался.
    val uuid: String = "me",
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Int = 1
)
