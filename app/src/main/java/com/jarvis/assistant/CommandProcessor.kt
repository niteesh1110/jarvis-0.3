package com.jarvis.assistant

import android.app.Activity
import android.content.Context

/**
 * 100% FREE, LOCAL command processor. No API calls, no billing, no
 * internet required. Understands fixed phrase patterns and maps them
 * straight to device actions. Trade-off: it can't hold open-ended
 * conversations, only these specific commands.
 */
class CommandProcessor(private val context: Context, @Suppress("UNUSED_PARAMETER") apiKey: String) {

    private val actions = ActionExecutor(context)

    suspend fun handle(userText: String, activity: Activity?): String {
        val text = userText.trim().lowercase()

        return when {
            text.startsWith("open ") -> {
                val target = text.removePrefix("open ").trim()
                if (target == "camera") actions.openCamera() else actions.openApp(target)
            }
            text.startsWith("launch ") -> actions.openApp(text.removePrefix("launch ").trim())

            text.startsWith("call ") -> actions.call(text.removePrefix("call ").trim())
            text.startsWith("dial ") -> actions.call(text.removePrefix("dial ").trim())

            text.startsWith("text ") || text.startsWith("message ") -> {
                val body = text.substringAfter(" ", "").trim()
                val parts = body.split(" saying ", limit = 2)
                if (parts.size == 2) {
                    actions.sendSms(parts[0].trim(), parts[1].trim())
                } else {
                    "Say it like: text 9876543210 saying I'm on my way."
                }
            }

            text.contains("flashlight") || text.contains("torch") -> {
                if (text.contains("off")) actions.setFlashlight(false) else actions.setFlashlight(true)
            }

            text.contains("camera") -> actions.openCamera()

            text.contains("volume") -> {
                val number = Regex("""\d+""").find(text)?.value?.toIntOrNull()
                if (number != null) actions.setVolume(number) else "Say a number, like: set volume to 50."
            }

            text.startsWith("search for ") -> actions.webSearch(text.removePrefix("search for ").trim())
            text.startsWith("search ") -> actions.webSearch(text.removePrefix("search ").trim())
            text.startsWith("google ") -> actions.webSearch(text.removePrefix("google ").trim())

            text.contains("unlock") -> actions.requestUnlock(activity)

            else -> "I didn't understand that. Try: open camera, call mom, flashlight on, " +
                    "text 9876543210 saying hello, search for pizza near me, or unlock."
        }
    }
}
