package com.filmroll.camera.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.filmroll.camera.data.source.local.SettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin

/** Fires once a day from the AlarmManager and posts the reminder. */
class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                DailyReminder.showNotification(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot, so re-arm the reminder if the user still wants it. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = getKoin().get<SettingsStorage>().dailyReminderEnabled
                DailyReminder.rescheduleIfEnabled(enabled)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
