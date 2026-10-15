package ru.styrk.app.data.local.database

import androidx.room.migration.Migration
import ru.styrk.app.data.model.ExerciseRenames
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Переходы между версиями базы.
 *
 * ═══ ПОЧЕМУ ЭТО ВАЖНО ═══
 *
 * Приложение собирается БЕЗ fallbackToDestructiveMigration — это
 * сделано намеренно. С ним забытая миграция молча стирает всю историю
 * тренировок, и узнаём мы об этом от пользователя. Без него приложение
 * честно падает при первом же запуске на сборке, то есть ошибка
 * находится до того, как её кто-то увидит.
 *
 * Отсюда правило: изменили таблицу — сразу пишем сюда переход
 * и добавляем его в ALL_MIGRATIONS. Уже выпущенные переходы
 * не редактируются никогда, только добавляются новые.
 */

/**
 * 1 → 2: цели по подходам в плановых упражнениях.
 *
 * Три новых столбца, все допускают пустое значение. Существующие
 * строки получают NULL, то есть «цель не задана» — ровно то, что
 * и должно быть у планов, составленных до появления этой возможности.
 *
 * ALTER TABLE ADD COLUMN в SQLite не переписывает таблицу и не трогает
 * данные: операция мгновенная даже на большой истории.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetSets INTEGER")
        db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetReps INTEGER")
        db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetWeight REAL")
    }
}

/**
 * 2 → 3: свои тренировки-образцы.
 *
 * Две новые таблицы. Существующих данных переход не касается вовсе:
 * ничего не переписывается, ничего не удаляется — только добавляется
 * пустое место, куда человек положит свои наборы, если захочет.
 *
 * Столбцы описаны ровно так, как их объявляют сущности. Расхождение
 * здесь — самая коварная из возможных ошибок: приложение соберётся,
 * запустится и упадёт при первом обращении к таблице, уже у человека.
 * Поэтому имена и типы сверены по MyWorkoutEntity и
 * MyWorkoutExerciseEntity построчно.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS my_workouts (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                muscleGroup TEXT NOT NULL,
                uuid TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                dirty INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS my_workout_exercises (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                myWorkoutId INTEGER NOT NULL,
                exerciseName TEXT NOT NULL,
                position INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * 3 → 4: у черновика тренировки появилась заданная вручную длительность.
 *
 * Нужна для записи прошедшей тренировки: у обычной длительность
 * считается по часам, у прошлой её называет человек.
 *
 * Столбец допускает пустоту, и пусто — это «обычная тренировка».
 * Поэтому существующим черновикам ничего дописывать не нужно: они
 * и есть обычные.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE in_progress_workout ADD COLUMN manualDurationMinutes INTEGER")
    }
}

/**
 * 4 → 5: у своего упражнения появились описание и снимок.
 *
 * Два столбца, оба допускают пустоту. Пусто — это «как было раньше»,
 * поэтому существующим упражнениям ничего дописывать не нужно.
 *
 * В базе хранится ПУТЬ к снимку, а не сам снимок. Картинка в строке
 * таблицы утяжелила бы каждое чтение списка упражнений — а список
 * читается часто, снимок смотрят редко.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE custom_exercises ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE custom_exercises ADD COLUMN photoPath TEXT")
    }
}

/**
 * Версия 6: пол в профиле.
 *
 * Понадобился для разрядных таблиц: у мужчин и женщин они разные.
 * Колонка пустая у всех, кто уже пользуется приложением, — и это
 * нормально: пока пол не выбран, лестница разрядов не показывается,
 * а всё остальное работает как работало.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN sex TEXT")
    }
}

/**
 * Версия 7: переезд на новые названия упражнений.
 *
 * Что чинится. В справочнике потерялась «ё» («Подъем» вместо
 * «Подъём»), был падеж не в том месте («Пуловер с гантелей») и разнобой
 * в одинаковых семьях. Полный список — в ExerciseRenames.
 *
 * Почему миграцией, а не кодом при запуске. Название — это ключ,
 * по которому связаны история, рекорды, планы, цели и шаблоны.
 * Переносить их надо целиком или никак: остановись на полпути —
 * и половина записей будет ссылаться на имя, которого нет. Room
 * выполняет миграцию в одной транзакции и ровно один раз, а код
 * при запуске не даёт ни того ни другого.
 *
 * Свои упражнения человека (custom_exercises) НЕ трогаем: как он их
 * назвал, так они и называются.
 *
 * Отдельный случай — таблица рекордов: имя там первичный ключ,
 * и при сведении двойников две строки полезли бы в одну ячейку.
 * Поэтому там не простое переименование, а три шага (см. ниже).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ExerciseRenames.MAP.forEach { (old, new) ->
            val args = arrayOf<Any>(new, old)

            // ── записи, где имя не ключ: простое переименование ──
            db.execSQL(
                "UPDATE completed_workout_exercises SET exerciseName = ? WHERE exerciseName = ?", args)
            db.execSQL(
                "UPDATE completed_workout_sets SET exerciseName = ? WHERE exerciseName = ?", args)
            db.execSQL(
                "UPDATE planned_exercises SET exerciseName = ? WHERE exerciseName = ?", args)
            db.execSQL(
                "UPDATE my_workout_exercises SET exerciseName = ? WHERE exerciseName = ?", args)

            /* ── рекорды: имя здесь первичный ключ ──
               Шаг 1. Новая строка уже есть и она не хуже старой —
                      старая больше не нужна.
               Шаг 2. Старая уцелела, значит она была лучше, —
                      убираем новую, чтобы освободить ключ.
               Шаг 3. Переименовываем то, что осталось.
               Для упражнений без веса (повторы, время, расстояние)
               maxWeight у обоих нулевой, и шаг 1 оставляет новую —
               это верно: она свежее. */
            db.execSQL(
                "DELETE FROM exercise_history WHERE exerciseName = ? AND EXISTS (" +
                    "SELECT 1 FROM exercise_history h WHERE h.exerciseName = ? " +
                    "AND h.maxWeight >= (SELECT maxWeight FROM exercise_history " +
                    "WHERE exerciseName = ?))",
                arrayOf<Any>(old, new, old))
            db.execSQL(
                "DELETE FROM exercise_history WHERE exerciseName = ? AND EXISTS (" +
                    "SELECT 1 FROM exercise_history WHERE exerciseName = ?)",
                arrayOf<Any>(new, old))
            db.execSQL(
                "UPDATE exercise_history SET exerciseName = ? WHERE exerciseName = ?", args)

            /* ── незаконченная тренировка ──
               Она лежит одной строкой JSON, разбирать её здесь нечем.
               Меняем имя вместе с кавычками — так заменяется ровно
               целое название, а не кусок соседнего. */
            db.execSQL(
                "UPDATE in_progress_workout SET exercisesJson = " +
                    "replace(exercisesJson, '\"' || ? || '\"', '\"' || ? || '\"')",
                args.reversedArray())
        }

        /* Планы и шаблоны помечаем изменёнными.

           Их список упражнений отдельно не синхронизируется — он едет
           внутри самой записи. Не пометить значит оставить на сервере
           старые имена в планах: сервер считал бы их незатронутыми
           и при первом же обмене вернул бы обратно. */
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE planned_workouts SET updatedAt = ?, dirty = 1", arrayOf<Any>(now))
        db.execSQL("UPDATE my_workouts SET updatedAt = ?, dirty = 1", arrayOf<Any>(now))
    }
}

/**
 * Версия 8: план тренера по подходам.
 *
 * Тренер расписывает не «4 × 10 × 40», а каждый подход отдельно:
 * 40 × 10, 42,5 × 8, 45 × 6. Всё это лежит одной строкой JSON в новой
 * колонке — разбирать план на отдельную таблицу ради трёх чисел
 * на подход было бы лишним. У всех прежних планов колонка пустая:
 * у них плана по подходам и не было.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetPlan TEXT")
    }
}

/** Все переходы по порядку. Передаётся в Room при сборке базы. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8
)