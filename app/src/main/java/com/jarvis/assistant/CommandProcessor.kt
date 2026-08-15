package com.jarvis.assistant

import android.app.Activity
import android.content.Context

/**
 * 100% FREE, LOCAL command processor. No API calls, no billing, no
 * internet required. Uses broad keyword matching (not exact phrases) so
 * natural speech like "turn on the flash" or "can you open camera" works,
 * not just rigid patterns.
 */
class CommandProcessor(private val context: Context, @Suppress("UNUSED_PARAMETER") apiKey: String) {

    private val actions = ActionExecutor(context)

    suspend fun handle(userText: String, activity: Activity?): String {
        val text = userText.trim().lowercase()
        val isOff = Regex("""\boff\b""").containsMatchIn(text)

        return when {
            text.contains("flash") || text.contains("torch") ->
                actions.setFlashlight(!isOff)

            text.contains("camera") || text.contains("photo") || text.contains("picture") ->
                actions.openCamera()

            text.contains("volume") || text.contains("loud") -> {
                val number = Regex("""\d+""").find(text)?.value?.toIntOrNull()
                if (number != null) actions.setVolume(number)
                else "Say a number, like: set volume to 50."
            }

            text.contains("unlock") -> actions.requestUnlock(activity)

            text.contains("call ") || text.contains("dial ") || text.contains("phone ") -> {
                val target = extractAfterAny(text, listOf("call ", "dial ", "phone "))
                if (target.isNotBlank()) actions.call(target)
                else "Who do you want to call?"
            }

            text.contains("text ") || text.contains("message ") || text.contains("sms ") -> {
                val after = extractAfterAny(text, listOf("text ", "message ", "sms "))
                val parts = after.split(" saying ", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    actions.sendSms(parts[0].trim(), parts[1].trim())
                } else {
                    "Say it like: text 9876543210 saying I'm on my way."
                }
            }

            text.contains("search for ") -> actions.webSearch(text.substringAfter("search for ").trim())
            text.contains("search ") -> actions.webSearch(text.substringAfter("search ").trim())
            text.contains("google ") -> actions.webSearch(text.substringAfter("google ").trim())
            text.contains("look up ") -> actions.webSearch(text.substringAfter("look up ").trim())

            text.contains("open ") || text.contains("launch ") || text.contains("start ") -> {
                val target = extractAfterAny(text, listOf("open ", "launch ", "start "))
                if (target.isNotBlank()) actions.openApp(target)
                else "Which app do you want to open?"
            }

            else -> "I didn't understand that. Try: turn on flash, open camera, call mom, " +
                    "text 9876543210 saying hello, search for pizza near me, or unlock."
        }
    }

    private fun extractAfterAny(text: String, triggers: List<String>): String {
        for (trigger in triggers) {
            if (text.contains(trigger)) {
                return text.substringAfter(trigger).trim()
            }
        }
        return ""
    }
}
