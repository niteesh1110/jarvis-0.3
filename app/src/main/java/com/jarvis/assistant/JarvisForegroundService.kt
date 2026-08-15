package com.jarvis.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Keeps a short listen->check->relisten loop running in the background so
 * the phone reacts to "Hey Jarvis, ...". This uses the free built-in
 * SpeechRecognizer in short bursts rather than a dedicated wake-word
 * engine, so it's not as instant or battery-light as Porcupine/Picovoice,
 * but it requires no extra SDK or API key. Swap WakeWordDetector's
 * implementation later if you want a true always-on hotword.
 */
class JarvisForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var voiceManager: VoiceRecognitionManager? = null
    private var commandProcessor: CommandProcessor? = null
    private var listening = true

    companion object {
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIFICATION_ID = 1
        const val WAKE_WORD = "jarvis"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Listening for \"Hey Jarvis\"..."))

        val apiKey = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("claude_api_key", "") ?: ""
        commandProcessor = CommandProcessor(applicationContext, apiKey)

        voiceManager = VoiceRecognitionManager(
            context = applicationContext,
            onResult = { text -> handleHeard(text) },
            onError = { relisten() }
        )
        voiceManager?.startListening()
    }

    private fun handleHeard(text: String) {
        val lower = text.lowercase()
        if (lower.contains(WAKE_WORD)) {
            // Strip the wake word out, take whatever follows as the command.
            val command = lower.substringAfter(WAKE_WORD).trim()
            if (command.isNotBlank()) {
                serviceScope.launch {
                    val reply = commandProcessor?.handle(command, null) ?: "Okay."
                    voiceManager?.speak(reply)
                    updateNotification(reply)
                    delay(1500) // give TTS a moment before we start listening again
                    relisten()
                }
            } else {
                voiceManager?.speak("Yes?")
                relisten()
            }
        } else {
            relisten()
        }
    }

    private fun relisten() {
        if (listening) voiceManager?.startListening()
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        listening = false
        voiceManager?.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
