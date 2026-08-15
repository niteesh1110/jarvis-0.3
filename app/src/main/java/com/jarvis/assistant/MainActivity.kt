package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var conversationLog: TextView
    private lateinit var voiceManager: VoiceRecognitionManager
    private var commandProcessor: CommandProcessor? = null
    private var serviceRunning = false
    private var bubbleRunning = false

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled implicitly; features degrade gracefully without them */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        conversationLog = findViewById(R.id.conversationLog)
        val micButton = findViewById<Button>(R.id.micButton)
        val serviceToggleButton = findViewById<Button>(R.id.serviceToggleButton)
        val bubbleToggleButton = findViewById<Button>(R.id.bubbleToggleButton)
        val settingsButton = findViewById<Button>(R.id.settingsButton)

        requestNeededPermissions()

        val apiKey = getApiKey()
        commandProcessor = CommandProcessor(applicationContext, apiKey)

        voiceManager = VoiceRecognitionManager(
            context = this,
            onResult = { text -> onHeard(text) },
            onError = { statusText.text = "Didn't catch that — tap to try again." }
        )

        micButton.setOnClickListener {
            statusText.text = "Listening..."
            voiceManager.startListening()
        }

        serviceToggleButton.setOnClickListener {
            serviceRunning = !serviceRunning
            if (serviceRunning) {
                val intent = Intent(this, JarvisForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                serviceToggleButton.text = "Stop always-listening"
            } else {
                stopService(Intent(this, JarvisForegroundService::class.java))
                serviceToggleButton.text = "Start always-listening (Hey Jarvis)"
            }
        }

        bubbleToggleButton.setOnClickListener {
            if (!bubbleRunning) {
                // Need "Draw over other apps" permission first.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        "Allow 'Display over other apps' for Jarvis, then tap this button again.",
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    return@setOnClickListener
                }
                startService(Intent(this, BubbleOverlayService::class.java))
                bubbleRunning = true
                bubbleToggleButton.text = "Disable Floating Bubble"
            } else {
                stopService(Intent(this, BubbleOverlayService::class.java))
                bubbleRunning = false
                bubbleToggleButton.text = "Enable Floating Bubble"
            }
        }

        settingsButton.setOnClickListener { showApiKeyDialog() }
    }

    private fun onHeard(text: String) {
        appendLog("You: $text")
        statusText.text = "Thinking..."
        CoroutineScope(Dispatchers.Main).launch {
            val reply = commandProcessor?.handle(text, this@MainActivity) ?: "Sorry, something went wrong."
            appendLog("Jarvis: $reply")
            statusText.text = "Idle"
            voiceManager.speak(reply)
        }
    }

    private fun appendLog(line: String) {
        conversationLog.append("$line\n\n")
    }

    private fun requestNeededPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun getApiKey(): String {
        return getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("claude_api_key", "") ?: ""
    }

    private fun showApiKeyDialog() {
        val input = EditText(this)
        input.hint = "sk-ant-..."
        AlertDialog.Builder(this)
            .setTitle("Claude API Key")
            .setMessage("Get one from console.anthropic.com. It's stored only on this device.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
                    .edit().putString("claude_api_key", input.text.toString().trim()).apply()
                commandProcessor = CommandProcessor(applicationContext, getApiKey())
                statusText.text = "API key saved."
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        voiceManager.destroy()
        super.onDestroy()
    }
}
