package com.jarvis.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional: lets Jarvis see incoming notifications so you could later add
 * "Jarvis, read my notifications" or "Jarvis, any new messages?".
 * Requires the user to manually grant Notification Access in
 * Settings > Apps > Special access > Notification access (Android does not
 * allow this permission via a runtime dialog).
 */
class NotificationReaderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        // Hook point: store the latest notification so CommandProcessor can
        // answer "what's my last notification" type queries.
        latestNotification = "$title: $text"
    }

    companion object {
        var latestNotification: String = ""
    }
}
