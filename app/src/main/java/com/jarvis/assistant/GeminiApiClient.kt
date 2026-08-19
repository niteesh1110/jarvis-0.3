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
 * Free-tier Google Gemini client. Used only as a fallback for anything
 * that doesn't match a known local command - real conversation, general
 * knowledge questions, and suggestions. Sends recent conversation history
 * along with each request so Gemini has short-term memory of the chat.
 */
class GeminiApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val MODEL = "gemini-flash-latest"
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        private const val SYSTEM_PROMPT =
            "You are Jarvis, a friendly voice assistant living on the user's phone. " +
            "Keep replies short - 1 to 3 sentences - since they'll be spoken aloud by " +
            "text-to-speech. Chat naturally, answer questions, and offer helpful " +
            "suggestions when relevant. Use the conversation history you're given to " +
            "remember context and follow-ups."
    }

    /** history: list of (role, text) pairs, role is "user" or "model". */
    suspend fun chat(history: List<Pair<String, String>>, newMessage: String): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            for ((role, text) in history) {
                contents.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                })
            }
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", newMessage)))
            })

            val body = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                })
                put("contents", contents)
            }

            val url = "$ENDPOINT?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    return@withContext "Sorry, I couldn't reach my brain. Error ${response.code}."
                }
                try {
                    val json = JSONObject(raw)
                    val candidates = json.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text")
                    text?.trim().takeUnless { it.isNullOrBlank() }
                        ?: "I'm not sure how to respond to that."
                } catch (e: Exception) {
                    "Sorry, I had trouble understanding that response."
                }
            }
        }
}
