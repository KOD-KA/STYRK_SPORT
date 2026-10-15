package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Одна запись замеров тела. Все значения опциональны — можно записать
 * только вес, только талию и т.д. Размеры в сантиметрах, вес в килограммах.
 */
@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,              // LocalDate.toEpochDay()
    val weight: Float? = null,   // вес, кг
    val chest: Float? = null,    // грудь, см
    val waist: Float? = null,    // талия, см
    val hips: Float? = null,     // бёдра, см
    val biceps: Float? = null,   // бицепс, см
    val forearm: Float? = null,  // предплечье, см
    val thigh: Float? = null,    // бедро, см
    val calf: Float? = null,     // икра, см
    val neck: Float? = null,     // шея, см
    val shoulders: Float? = null, // плечи, см

    // ═══ ПОЛЯ ДЛЯ СИНХРОНИЗАЦИИ (добавлены в релизной базе v1) ═══
    // uuid — постоянный опознавательный знак записи. Номер строки (id)
    //   на каждом устройстве свой, поэтому для обмена он не годится.
    //   Значение по умолчанию вычисляется при создании объекта, поэтому
    //   все существующие места создания записей продолжают работать
    //   без изменений и сразу получают свой uuid.
    // updatedAt — когда запись менялась последний раз.
    // dirty — 1 = изменение ещё не отправлено на сервер. Пока аккаунта
    //   нет, поле просто лежит и никому не мешает.
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Int = 1
)
