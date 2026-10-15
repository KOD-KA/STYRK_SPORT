package ru.styrk.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Единая точка планирования напоминаний.
 *
 * init(applicationContext) вызывается один раз в MainActivity.onCreate —
 * после этого ViewModel может планировать/отменять напоминания, не имея
 * собственного Context (хранится application context, утечки нет).
 *
 * Времена напоминаний о запланированной тренировке:
 *  - утром в 9:00 в день тренировки
 *  - за 2 часа до времени тренировки
 * Моменты, которые уже в прошлом, просто пропускаются.
 */
object ReminderScheduler {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun schedulePlanned(id: Long, date: LocalDate, hour: Int, minute: Int, muscleGroup: String) {
        val context = appContext ?: return
        val now = LocalDateTime.now()
        val timeText = "%02d:%02d".format(hour, minute)

        fun enqueue(at: LocalDateTime, type: String) {
            val delay = Duration.between(now, at)
            if (delay.isNegative || delay.isZero) return // момент уже прошёл
            val request = OneTimeWorkRequestBuilder<PlannedWorkoutReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "type" to type,
                        "plannedId" to id,
                        "muscleGroup" to muscleGroup,
                        "timeText" to timeText
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "planned_${id}_$type",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        enqueue(date.atTime(LocalTime.of(9, 0)), "morning")
        enqueue(date.atTime(hour, minute).minusHours(2), "soon")
    }

    fun cancelPlanned(id: Long) {
        val context = appContext ?: return
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("planned_${id}_morning")
        wm.cancelUniqueWork("planned_${id}_soon")
    }

    /**
     * Ежедневная проверка неактивности (~19:00). KEEP — если задача уже
     * стоит в очереди, повторный вызов при каждом запуске ничего не ломает.
     */
    fun ensureDailyInactivityCheck() {
        val context = appContext ?: return

        val now = LocalDateTime.now()
        var firstRun = now.toLocalDate().atTime(19, 0)
        if (!firstRun.isAfter(now)) firstRun = firstRun.plusDays(1)
        val initialDelay = Duration.between(now, firstRun)

        val request = PeriodicWorkRequestBuilder<InactivityCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "inactivity_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}