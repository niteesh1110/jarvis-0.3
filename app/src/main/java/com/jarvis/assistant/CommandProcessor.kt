package com.jarvis.assistant

import android.app.Activity
import android.content.Context
import org.json.JSONObject

class CommandProcessor(private val context: Context, apiKey: String) {

    private val claude = ClaudeApiClient(apiKey)
    private val actions = ActionExecutor(context)

    /**
     * Full pipeline: recognized text -> Claude decision -> executed action.
     * Returns the spoken reply to hand to TextToSpeech.
     */
    suspend fun handle(userText: String, activity: Activity?): String {
        val decision: JSONObject = try {
            claude.interpret(userText)
        } catch (e: Exception) {
            return "Sorry, I had trouble reaching my brain just now."
        }

        val action = decision.optString("action", "none")
        val target = decision.optString("target", "")
        val message = decision.optString("message", "")
        val reply = decision.optString("reply", "")

        val actionResult = when (action) {
            "open_app" -> actions.openApp(target)
            "call" -> actions.call(target)
            "sms" -> actions.sendSms(target, message)
            "flashlight_on" -> actions.setFlashlight(true)
            "flashlight_off" -> actions.setFlashlight(false)
            "set_volume" -> actions.setVolume(target.toIntOrNull() ?: 50)
            "web_search" -> actions.webSearch(target)
            "open_camera" -> actions.openCamera()
            "unlock_screen" -> actions.requestUnlock(activity)
            else -> null
        }

        // Prefer Claude's natural spoken reply; fall back to the action's
        // own status message if Claude didn't provide one.
        return if (reply.isNotBlank()) reply else (actionResult ?: "Done.")
    }
}
