package ru.styrk.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.styrk.app.MainActivity
import ru.styrk.app.R

/**
 * Уведомление-таймер отдыха в шторке.
 *
 * Использует системный chronometer (setUsesChronometer) — секунды в
 * уведомлении обновляет сама система, приложению не нужно ничего
 * перерисовывать. Мы лишь пересоздаём уведомление с новым startMillis
 * после каждой записи подхода (это делает LaunchedEffect в
 * TrainingFlowScreen.kt).
 *
 * Без foreground-сервиса — осознанно: уведомление живёт, пока жив процесс
 * приложения, для сценария «телефон в кармане между подходами» этого
 * достаточно и не требует разрешений на сервисы (строже с API 34).
 */
object RestTimerNotification {

    private const val CHANNEL_ID = "rest_timer"
    private const val NOTIFICATION_ID = 1001

    private fun ensureChannel(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Таймер отдыха",
                    NotificationManager.IMPORTANCE_LOW // без звука и вибрации
                ).apply {
                    description = "Время отдыха с последнего подхода во время тренировки"
                    setShowBadge(false)
                }
            )
        }
    }

    fun show(context: Context, startMillis: Long) {
        // Без разрешения (Android 13+) молча не показываем — не крашимся
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                // Возвращаемся в СУЩЕСТВУЮЩУЮ тренировку (singleTop), не создаём
                // новый экземпляр поверх — иначе таймер "пропадал" под новым экраном
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_dumbbell)
            .setContentTitle("Отдых после подхода")
            .setContentText("VKA SPORT — тренировка идёт")
            .setWhen(startMillis)
            .setUsesChronometer(true) // системный секундомер, тикает сам
            .setOngoing(true)         // нельзя смахнуть случайно
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(contentIntent)
            .build()

        // Разрешение проверено выше, но система может отозвать его в любой
        // момент — ловим SecurityException явно, чтобы приложение не падало
        // (и чтобы линтер не ругался на «call requires permission»).
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // нет разрешения на уведомления — просто ничего не показываем
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}