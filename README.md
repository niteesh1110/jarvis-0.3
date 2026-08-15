# Jarvis — Voice Assistant for Android

A "Hey Jarvis" style voice assistant: wake word → speech-to-text → Claude
for understanding → real phone actions (open apps, call, text, flashlight,
volume, web search, camera) + natural conversation.

## What it can and can't do

**It can:** open apps, make calls, send texts, control flashlight/volume,
search the web, open the camera, answer questions conversationally (via
Claude), and run in the background listening for "Hey Jarvis".

**It cannot bypass a secured lock screen.** If your phone has a PIN,
pattern, password, or biometric lock, Android will never let a third-party
app unlock it silently — that's a deliberate OS security boundary, not a
missing feature. If you ask "Jarvis, unlock my phone":
- If you have **no lock set**, it unlocks automatically.
- If you **do** have a lock, Jarvis surfaces the app over the lock screen
  and tells you to authenticate — you still do the PIN/fingerprint/face
  yourself. See the comment block on `ActionExecutor.requestUnlock()`.

## Setup — Option A: Cloud build (no Android Studio needed on your machine)

This repo includes `.github/workflows/build.yml`, which builds the APK on
GitHub's servers every time you push. You never need `adb` or a USB
connection.

1. Create a free GitHub account at github.com/join (skip if you have one).
2. Create a new repository (e.g. `jarvis-assistant`), public or private.
3. Install **GitHub Desktop** (desktop.github.com) — easiest way to publish
   a local folder without using git commands.
4. In GitHub Desktop: File > Add Local Repository > pick this
   `JarvisAssistant` folder > "Publish repository".
5. On GitHub.com, open your repo's **Actions** tab. A build run should
   already be in progress (triggered automatically by the push). Wait
   2-4 minutes for the green checkmark.
6. Click the completed run, scroll down to **Artifacts**, and download
   `jarvis-debug-apk` (a zip containing the APK). You can do this straight
   from your phone's browser too — no laptop needed at this step.
7. On your phone, extract the zip (any file manager or a free app like
   ZArchiver) to get `app-debug.apk`.
8. Tap the APK file to install. Your phone will ask to allow installs
   from that app (Files/Chrome/etc.) — allow it once, then install.
9. Open Jarvis, grant permissions, and add your Claude API key as before.

## Setup — Option B: Android Studio + USB (Hedgehog or newer): https://developer.android.com/studio
2. **Open this folder** (`JarvisAssistant/`) as a project — "Open" not "Import".
3. Let Gradle sync (it'll pull the dependencies listed in `app/build.gradle`).
4. Plug in your Android phone (enable Developer Options → USB Debugging) or
   use an emulator with a microphone enabled.
5. Hit Run ▶. On first launch, grant the permissions it asks for (mic,
   phone, SMS, notifications).
6. Tap **"Enter Claude API Key"** and paste a key from
   https://console.anthropic.com/settings/keys (this is stored only in the
   app's local SharedPreferences on-device, nowhere else).
7. Tap **"Tap to talk to Jarvis"** for one-shot commands, or **"Start
   always-listening"** to run the background wake-word service.

## How it works (architecture)

```
Mic audio
   │
   ▼
Android SpeechRecognizer  →  recognized text
   │
   ▼
ClaudeApiClient.interpret()  →  Claude decides: local action OR chat reply
   │
   ▼
CommandProcessor  →  routes to ActionExecutor (real device action)
   │
   ▼
TextToSpeech.speak()  →  Jarvis talks back
```

- `VoiceRecognitionManager.kt` — mic → text, and text → speech (TTS).
- `ClaudeApiClient.kt` — sends what you said to Claude with a system
  prompt that asks it to return either an action (JSON) or a spoken reply.
- `ActionExecutor.kt` — the actual device actions (open app, call, text,
  flashlight, volume, search, camera, best-effort unlock).
- `CommandProcessor.kt` — glues the above together.
- `JarvisForegroundService.kt` — background "Hey Jarvis" wake-word loop,
  using short repeated SpeechRecognizer bursts (free, no extra SDK).
- `MainActivity.kt` — UI, permissions, API key entry.

## Wake-word note

The always-listening mode currently re-triggers `SpeechRecognizer` in
short bursts and checks if the word "jarvis" appears in what it heard.
This is free and needs no extra service, but it's less instant and more
battery-hungry than a dedicated wake-word engine. If you want a snappier,
more battery-efficient hotword later, swap in **Picovoice Porcupine**
(free tier, custom "Hey Jarvis" model you train in their console) inside
`JarvisForegroundService` — everything else (Claude call, actions, TTS)
stays the same.

## Extending it

Add new actions in two places:
1. Add the action name to the JSON schema in `ClaudeApiClient.SYSTEM_PROMPT`.
2. Add a `when` branch for it in `CommandProcessor.handle()` calling a new
   method in `ActionExecutor`.

Ideas: smart home control (needs a hub API), calendar events, music
playback control, reading notifications aloud (the `NotificationReaderService`
stub is already wired up — just needs you to enable Notification Access in
system settings, since Android won't grant that one via a permission popup).
