package ru.styrk.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.styrk.app.data.local.dao.*
import ru.styrk.app.data.local.entity.*

/**
 * База данных STYRK — РЕЛИЗНАЯ версия 1.
 *
 * Это не продолжение прежней базы, а новая: файл называется иначе
 * (styrk_database вместо vka_sport_database), и версия начинается с
 * единицы. История версий 1–17 и миграции к ним относились к периоду
 * разработки и выброшены целиком — вместе с ними ушли и три таблицы,
 * в которые приложение никогда ничего не писало.
 *
 * Перенос данных из старого приложения делается файлом бэкапа:
 * BackupManager читает и новые файлы (STYRK), и старые (VKA_SPORT).
 *
 * ═══ ПРАВИЛА ИЗМЕНЕНИЯ СХЕМЫ (данные пользователей не сбрасываются) ═══
 *
 * 1. Изменили entity — повысили version на +1.
 * 2. СРАЗУ же написали миграцию в Migrations.kt и добавили её
 *    в ALL_MIGRATIONS. Без миграции приложение упадёт при первом
 *    запуске после обновления.
 * 3. Старые миграции никогда не редактируются, только добавляются новые.
 * 4. Папку app/schemas не удалять — по этим снимкам пишутся и проверяются
 *    миграции.
 *
 * Теперь, когда приложение выходит в релиз, эти правила становятся
 * обязательными: сбросить базу «как в разработке» больше нельзя,
 * на другом конце живые люди со своей историей тренировок.
 */
@Database(
    entities = [
        // ── журнал тренировок ──
        CompletedWorkoutEntity::class,
        CompletedWorkoutExerciseEntity::class,
        CompletedWorkoutSetEntity::class,
        // ── рекорды (считаются из журнала, живут только на устройстве) ──
        ExerciseHistoryEntity::class,
        // ── планирование ──
        PlannedWorkoutEntity::class,
        PlannedExerciseEntity::class,
        // ── свои упражнения ──
        CustomExerciseEntity::class,
        // ── свои тренировки-образцы ──
        MyWorkoutEntity::class,
        MyWorkoutExerciseEntity::class,
        // ── незавершённая тренировка (черновик, не синхронизируется) ──
        InProgressWorkoutEntity::class,
        // ── тело и профиль ──
        BodyMetricEntity::class,
        UserProfileEntity::class,
        // ── служебное: отметки об удалении для синхронизации ──
        SyncTombstoneEntity::class
    ],
    // Версия 3: свои тренировки-образцы. Переход описан в Migrations.kt.
    //
    // Версия 2: у плановых упражнений появились цели (подходы,
    // повторы, вес).
    // Версия 7: переезд на новые названия упражнений (ExerciseRenames).
    // Версия 6: пол в профиле — для разрядных таблиц ЕВСК.
    // Версия 8: план тренера по подходам (planned_exercises.targetPlan).
    // Версия 9: названия подогнаны под ролики (ExerciseRenames.MAP_9),
    // схема та же — переносятся только записи.
    version = 9,
    exportSchema = true
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutHistoryDao(): WorkoutHistoryDao
    abstract fun exerciseHistoryDao(): ExerciseHistoryDao
    abstract fun completedWorkoutExerciseDao(): CompletedWorkoutExerciseDao
    abstract fun completedWorkoutSetDao(): CompletedWorkoutSetDao
    abstract fun plannedWorkoutDao(): PlannedWorkoutDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun myWorkoutDao(): MyWorkoutDao
    abstract fun inProgressWorkoutDao(): InProgressWorkoutDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
}