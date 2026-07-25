package com.filmroll.camera.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.filmroll.camera.R
import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.notification_channel_daily
import com.filmroll.camera.resources.notification_daily_content
import com.filmroll.camera.resources.notification_daily_title
import com.filmroll.camera.util.AppContext
import org.jetbrains.compose.resources.getString
import java.util.Calendar

private const val ALARM_REQUEST_CODE = 2001
private const val LAUNCH_REQUEST_CODE = 2002
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "daily_reminder"

/** Local time of day the reminder fires. Evening, when people go through the day's photos. */
private const val REMINDER_HOUR = 19

actual object DailyReminder {

    actual suspend fun setEnabled(enabled: Boolean): Boolean {
        val context = AppContext.get() ?: return false
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false

        if (!enabled) {
            buildAlarmIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
            return false
        }

        // Ask before scheduling: an alarm that fires into a blocked channel is worse than
        // an honest "off" switch.
        if (!NotificationPermission.request(context)) return false

        createChannel(context)
        val pendingIntent = buildAlarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        // Inexact is deliberate: a reminder does not justify the exact-alarm permission prompt.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTriggerAtMillis(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent,
        )
        return true
    }

    /** Called from [DailyReminderReceiver] once the alarm fires. */
    internal suspend fun showNotification(context: Context) {
        if (!NotificationPermission.isGranted(context)) return
        createChannel(context)

        val title = getString(Res.string.notification_daily_title)
        val content = getString(Res.string.notification_daily_content)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(buildLaunchIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Re-arms the alarm after a reboot, which clears everything the AlarmManager held. */
    internal suspend fun rescheduleIfEnabled(enabled: Boolean) {
        if (enabled) setEnabled(true)
    }

    private suspend fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(Res.string.notification_channel_daily),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun nextTriggerAtMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun buildAlarmIntent(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, DailyReminderReceiver::class.java),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildLaunchIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            LAUNCH_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
