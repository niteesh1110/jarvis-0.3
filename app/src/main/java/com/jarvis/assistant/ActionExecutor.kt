package com.jarvis.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log

/**
 * Executes the concrete actions Claude decides on. Each function fails
 * gracefully (returns a spoken message) rather than crashing if a
 * permission is missing.
 */
class ActionExecutor(private val context: Context) {

    private val TAG = "ActionExecutor"

    fun openApp(appName: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }
        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "Opening $appName."
            } else {
                "I found $appName but couldn't launch it."
            }
        } else {
            "I couldn't find an app called $appName."
        }
    }

  fun call(rawTarget: String): String {
        // If it looks like a phone number already (mostly digits), dial it directly.
        val looksLikeNumber = rawTarget.count { it.isDigit() } >= 5
        val number = if (looksLikeNumber) rawTarget else resolveContactNumber(rawTarget)

        if (number == null) {
            return "I couldn't find a contact named $rawTarget."
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            if (looksLikeNumber) "Calling $number." else "Calling $rawTarget."
        } catch (e: SecurityException) {
            "I need call permission to do that."
        }
    }

    /** Looks up a phone number from the phone's contacts by display name. */
    private fun resolveContactNumber(name: String): String? {
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        return try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    cursor.getString(numberIndex)
                } else null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    fun sendSms(number: String, message: String): String {
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(number, null, message, null, null)
            "Text sent to $number."
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
            "I couldn't send that text. Check SMS permission."
        }
    }

    fun setFlashlight(on: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, on)
            if (on) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) {
            "I couldn't control the flashlight on this device."
        }
    }

    fun setVolume(level: Int): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (level.coerceIn(0, 100) * max) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return "Volume set."
    }

    fun webSearch(query: String): String {
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
        intent.putExtra("query", query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Searching for $query."
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
            "Searching for $query."
        }
    }

    fun openCamera(): String {
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Opening camera."
        } catch (e: Exception) {
            "I couldn't open the camera."
        }
    }

    /**
     * IMPORTANT LIMITATION:
     * Android does not allow third-party apps to bypass a secure lock
     * screen (PIN / pattern / password / biometric). That restriction is
     * intentional — allowing voice-unlock would let anyone near the phone
     * unlock it by speaking a command.
     *
     * What this CAN do:
     *  - If the device has no lock set (KeyguardManager.isKeyguardSecure()
     *    is false), we can dismiss the keyguard directly.
     *  - If a secure lock IS set, the best we can do is bring the app to
     *    the foreground over the lock screen (see MainActivity's
     *    showWhenLocked/turnScreenOn) and trigger the system's own
     *    biometric/PIN prompt for the user to complete themselves.
     */
    fun requestUnlock(activity: Activity?): String {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (!keyguardManager.isKeyguardLocked) {
            return "Your phone is already unlocked."
        }

        if (!keyguardManager.isKeyguardSecure) {
            // No PIN/pattern/password set - safe to dismiss programmatically.
            if (activity != null) {
                keyguardManager.requestDismissKeyguard(activity, null)
                return "Unlocking."
            }
            return "Bring the app to the foreground and I can unlock it — no lock is set."
        }

        // A secure lock is set: we deliberately do NOT attempt to bypass it.
        return "Your phone has a PIN or biometric lock set, so you'll need to " +
               "authenticate that yourself — I can't bypass it, by design."
    }
}
