package com.filmroll.camera.notification

import com.filmroll.camera.resources.Res
import com.filmroll.camera.resources.notification_daily_content
import com.filmroll.camera.resources.notification_daily_title
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

private const val REQUEST_IDENTIFIER = "com.filmroll.camera.daily_reminder"

/** Local time of day the reminder fires. Evening, when people go through the day's photos. */
private const val REMINDER_HOUR = 19L

actual object DailyReminder {

    actual suspend fun setEnabled(enabled: Boolean): Boolean {
        val center = UNUserNotificationCenter.currentNotificationCenter()

        if (!enabled) {
            center.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_IDENTIFIER))
            return false
        }

        if (!requestAuthorization(center)) return false

        val content = UNMutableNotificationContent().apply {
            setTitle(getString(Res.string.notification_daily_title))
            setBody(getString(Res.string.notification_daily_content))
        }
        val dateComponents = NSDateComponents().apply {
            hour = REMINDER_HOUR
            minute = 0
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = REQUEST_IDENTIFIER,
            content = content,
            trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = dateComponents,
                repeats = true,
            ),
        )
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_IDENTIFIER))
        center.addNotificationRequest(request, null)
        return true
    }

    private suspend fun requestAuthorization(center: UNUserNotificationCenter): Boolean =
        suspendCancellableCoroutine { continuation ->
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { granted, _ ->
                continuation.resume(granted)
            }
        }
}
