package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planned_workouts")
data class PlannedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,        // LocalDate.toEpochDay()
    val hour: Int,
    val minute: Int,
    val muscleGroup: String,

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
