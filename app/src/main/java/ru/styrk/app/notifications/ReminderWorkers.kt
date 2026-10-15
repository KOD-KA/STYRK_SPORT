package ru.styrk.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.styrk.app.data.local.database.AppDatabaseProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Фоновые задачи WorkManager. WorkManager сам переживает перезагрузку
 * телефона и закрытие приложения — отдельный BroadcastReceiver на
 * BOOT_COMPLETED не нужен.
 */

/**
 * Разовое напоминание о запланированной тренировке.
 * inputData: "type" ("morning" | "soon"), "plannedId", "muscleGroup", "timeText".
 */
class PlannedWorkoutReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val plannedId = inputData.getLong("plannedId", -1L)
        val type = inputData.getString("type") ?: return Result.success()
        val muscleGroup = inputData.getString("muscleGroup") ?: ""
        val timeText = inputData.getString("timeText") ?: ""

        // Страховка: если тренировку уже удалили из плана —
        // молча выходим (даже если cancel по какой-то причине не сработал)
        val db = AppDatabaseProvider.getDatabase(applicationContext)
        val stillPlanned = db.plannedWorkoutDao().getAll().any { it.id == plannedId }
        if (!stillPlanned) return Result.success()

        /* Уже тренируется — начал чуть раньше назначенного — или только
           что закончил: «скоро тренировка» ему ни к чему. Незавершённая
           тренировка лежит черновиком в базе; законченная — в архиве,
           и считаем ту, что закончилась за последние три часа. */
        // Черновик старше шести часов — брошенный, а не идущий.
        val draft = db.inProgressWorkoutDao().get()
        if (draft != null && System.currentTimeMillis() - draft.workoutStartTime < 6 * 3_600_000L) {
            return Result.success()
        }
        val recent = db.workoutHistoryDao().getAllWorkouts().firstOrNull()
        if (recent != null && System.currentTimeMillis() - recent.date < 3 * 3_600_000L) {
            return Result.success()
        }

        when (type) {
            "morning" -> WorkoutReminders.showPlannedMorning(
                applicationContext, muscleGroup, timeText, plannedId
            )
            "soon" -> WorkoutReminders.showPlannedSoon(
                applicationContext, muscleGroup, timeText, plannedId
            )
        }
        return Result.success()
    }
}

/**
 * Ежедневная проверка: если с последней завершённой тренировки прошло
 * БОЛЬШЕ 2 дней — напоминаем. Если тренировок ещё не было вообще —
 * не навязываемся.
 */
class InactivityCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabaseProvider.getDatabase(applicationContext)

        // getAllWorkouts() отсортирован по date DESC — первый и есть последний
        val latest = db.workoutHistoryDao().getAllWorkouts().firstOrNull()
            ?: return Result.success()

        val lastDate = Instant.ofEpochMilli(latest.date)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val days = ChronoUnit.DAYS.between(lastDate, LocalDate.now())

        if (days > 2) {
            WorkoutReminders.showInactivity(applicationContext, days)
        }
        return Result.success()
    }
}