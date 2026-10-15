package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_exercises")
data class CustomExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // Хранится как MuscleGroup.name (стабильный ключ enum), не как
    // локализованный title, чтобы не сломаться при смене текста.
    val muscleGroup: String,
    // MeasureType.name. Nullable СПЕЦИАЛЬНО (null = WEIGHT_REPS).
    val measureType: String? = null,

    /**
     * Своё описание: чем оно отличается, на что смотреть, какая
     * настройка тренажёра. Пусто — обычное дело, поле необязательное.
     */
    val description: String? = null,

    /**
     * Путь к снимку во внутренней памяти приложения.
     *
     * Именно путь, а не сама картинка: в базе ей не место, она утяжелит
     * каждое чтение списка упражнений. По сети и в файл бэкапа едет
     * содержимое, но это забота обмена, а не базы.
     */
    val photoPath: String? = null,

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
