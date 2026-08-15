package com.jarvis.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to Anthropic's Claude API to interpret free-form voice commands.
 * The system prompt asks Claude to classify the request as one of Jarvis's
 * known local actions (JSON) OR just answer conversationally.
 */
class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-4-6"

        private val SYSTEM_PROMPT = """
            You are Jarvis, a voice assistant running on the user's Android phone.
            For every user request, decide whether it maps to one of these local
            device actions. If it does, reply with ONLY a JSON object (no prose,
            no markdown fences) in this exact shape:

            {"action": "<one of: open_app, call, sms, flashlight_on, flashlight_off,
              set_volume, set_brightness, web_search, open_camera, unlock_screen,
              none>", "target": "<app name, phone number, or search text if relevant>",
              "message": "<sms body if relevant>", "reply": "<short spoken reply
              to say back to the user>"}

            If the request is just conversation, general knowledge, or doesn't map
            to a device action, set "action" to "none" and put your natural spoken
            answer in "reply". Keep "reply" short (1-2 sentences) since it will be
            spoken aloud via text-to-speech.
        """.trimIndent()
    }

    /**
     * Sends the recognized speech text to Claude and returns the parsed JSON
     * decision (action + reply). Runs on IO dispatcher.
     */
    suspend fun interpret(userText: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 300)
            put("system", SYSTEM_PROMPT)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                }
            ))
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                return@withContext JSONObject().apply {
                    put("action", "none")
                    put("reply", "Sorry, I couldn't reach my brain. Error ${response.code}.")
                }
            }
            val json = JSONObject(raw)
            val contentArray = json.optJSONArray("content") ?: JSONArray()
            val textBlock = StringBuilder()
            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                if (block.optString("type") == "text") {
                    textBlock.append(block.optString("text"))
                }
            }
            parseModelOutput(textBlock.toString())
        }
    }

    /** Claude is asked to return raw JSON; strip fences defensively and parse. */
    private fun parseModelOutput(text: String): JSONObject {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            // Model replied in plain prose instead of JSON -> treat as conversational answer
            JSONObject().apply {
                put("action", "none")
                put("reply", text.trim())
            }
        }
    }
}
