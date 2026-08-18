package com.jarvis.assistant

import android.app.Activity
import android.content.Context

/**
 * Hybrid processor: fixed device actions run 100% locally and free (open
 * app, call, flashlight, etc). Anything that doesn't match a known
 * command pattern falls through to Gemini's free tier for real
 * conversation, memory of the chat, and suggestions.
 */
class CommandProcessor(private val context: Context, private val apiKey: String) {

    private val actions = ActionExecutor(context)
    private val gemini = GeminiApiClient(apiKey)

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val maxHistoryTurns = 20

    suspend fun handle(userText: String, activity: Activity?): String {
        val text = userText.trim().lowercase()
        val isOff = Regex("""\boff\b""").containsMatchIn(text)

        val localResult: String? = when {
            text.contains("flash") || text.contains("torch") ->
                actions.setFlashlight(!isOff)

            text.contains("camera") || text.contains("photo") || text.contains("picture") ->
                actions.openCamera()

            text.contains("volume") || text.contains("loud") -> {
                val number = Regex("""\d+""").find(text)?.value?.toIntOrNull()
                if (number != null) actions.setVolume(number) else null
            }

            text.contains("unlock") -> actions.requestUnlock(activity)

            text.contains("call ") || text.contains("dial ") || text.contains("phone ") -> {
                val target = extractAfterAny(text, listOf("call ", "dial ", "phone "))
                if (target.isNotBlank()) actions.call(target) else null
            }

            text.contains("text ") || text.contains("message ") || text.contains("sms ") -> {
                val after = extractAfterAny(text, listOf("text ", "message ", "sms "))
                val parts = after.split(" saying ", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    actions.sendSms(parts[0].trim(), parts[1].trim())
                } else null
            }

            text.contains("search for ") -> actions.webSearch(text.substringAfter("search for ").trim())
            text.contains("google ") -> actions.webSearch(text.substringAfter("google ").trim())
            text.contains("look up ") -> actions.webSearch(text.substringAfter("look up ").trim())

            text.contains("close") || text.contains("go home") || text.contains("exit") ->
                actions.goHome()

            text.contains("open ") || text.contains("launch ") || text.contains("start ") -> {
                val target = extractAfterAny(text, listOf("open ", "launch ", "start "))
                if (target.isNotBlank()) actions.openApp(target) else null
            }

            else -> null
        }

        if (localResult != null) return localResult

        if (apiKey.isBlank()) {
            return "I didn't understand that as a command, and no Gemini key is set for " +
                    "conversation. Try a command like open camera or flashlight on, or add " +
                    "a free Gemini key in settings."
        }

        return try {
            val reply = gemini.chat(conversationHistory.toList(), userText)
            conversationHistory.add("user" to userText)
            conversationHistory.add("model" to reply)
            while (conversationHistory.size > maxHistoryTurns * 2) {
                conversationHistory.removeAt(0)
            }
            reply
        } catch (e: Exception) {
            "Sorry, I couldn't reach my brain just now."
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
