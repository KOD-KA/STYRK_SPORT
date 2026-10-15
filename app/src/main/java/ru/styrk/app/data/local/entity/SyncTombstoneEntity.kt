package ru.styrk.app.data.local.entity

import androidx.room.Entity

/**
 * Надгробие: отметка о том, что запись была удалена.
 *
 * Зачем это нужно. Если просто убрать строку из таблицы, то при следующем
 * обмене с сервером она вернётся обратно — с другого устройства, где про
 * удаление никто не знает. Поэтому факт удаления сам является изменением
 * и должен доехать до сервера.
 *
 * Такой подход выбран вместо колонки deleted в основных таблицах
 * осознанно: иначе каждый запрос в приложении пришлось бы дописывать
 * условием «и не удалённые», а это десятки мест и столько же шансов
 * забыть одно из них.
 *
 * После успешной отправки на сервер надгробие удаляется — оно больше
 * не нужно.
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "uuid"])
data class SyncTombstoneEntity(
    val kind: String,
    val uuid: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Виды записей, которые участвуют в обмене с сервером. */
object SyncKind {
    const val WORKOUT = "workout"
    const val PLANNED = "planned"
    const val CUSTOM_EXERCISE = "custom_exercise"
    const val BODY_METRIC = "body_metric"
    const val PROFILE = "profile"

    /**
     * Своя тренировка-образец.
     *
     * Название вида в точности такое же, как в браузерной версии
     * и в мини-приложении: my_workout. Разойдись оно хоть на знак —
     * и один и тот же образец существовал бы на сервере в двух видах,
     * причём каждое устройство видело бы только свой.
     */
    const val MY_WORKOUT = "my_workout"
}