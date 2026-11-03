# Scam Radar for Android

An offline Android app that checks SMS and WhatsApp messages with the
fine-tuned Gemma 3 270M model, exported as `.litertlm` (see
[`../export/README.md`](../export/README.md)) and run on the phone with
[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). The app has **no
INTERNET permission** (the manifest strips it even if a library tries to add
it), so messages cannot leave the phone.

## What it does

- **Paste box.** Paste or type a message, tap *Check*.
- **"Share to Scam Radar".** Long-press a message in SMS/WhatsApp, Share,
  pick Scam Radar. The app opens and checks it right away.
- **Notification checking (opt-in).** A `NotificationListenerService` reads
  new notifications from SMS and WhatsApp apps (plus whatever the default SMS
  app is) and posts a warning for amber and red results. A disclosure screen,
  in Urdu and English, explains what is read and what happens to it *before*
  the app opens Android's notification-access settings. An in-app switch turns
  it off again. Message text is never stored.
- **Rule layer, run before the model** (`core/.../rules/`):
  - **BISP 8171 allowlist.** If the sender id is exactly `8171` and the
    message has no link and does not ask for a code, it is green without
    running the model. A message that mentions BISP but comes from another
    (known) sender is at least amber. Pasted text has no sender, so it is never
    allowlisted.
  - **Link extraction.** `http(s)://`, `www.` and bare domains (`example.pk/x`).
    Shorteners, bare IPs, punycode and throwaway TLDs count as suspicious.
  - **"Asks for a code" detector.** English, Roman Urdu and Urdu script.
    Ignores "do not share this code" OTP messages and courier "show the code
    to the rider" messages. It agrees with the `asks_for_code` flag on every
    fictional seed in `data/seeds/seeds.example.jsonl` (checked by a test).
- **Parser** for the compact `label|scam_type|flags` line, a Kotlin copy of
  `scamradar/schema.py` (strict and lenient modes). Both parsers are checked
  against the same vectors, `tests/fixtures/schema_vectors.jsonl`.
- **Result cards.** Large green / amber / red cards. Every word on them is
  pre-written in `core/.../text/AdviceText.kt`, in Urdu and English, per scam
  type, per flag and per caution reason. The model only chooses the label,
  type and flags; nothing it writes is shown as advice.
- **"Send to family".** Opens the share sheet with a pre-written bilingual
  warning and the quoted message. You choose who to send it to.
- **"Report this scam" (opt-in, off by default).** Turn it on in Settings.
  It prepares a report for you to copy or share; it never sends anything.
- **Demo mode.** With no model file, the app uses `FakeClassifier`, a keyword
  stand-in, and says so on every card.

How a verdict is made (`core/.../verdict/`):

| Model answer | Rule findings | Card |
|---|---|---|
| `scam\|<type>\|...` | anything | red, with that type's text |
| `safe` / `promo` | asks for a code, suspicious link, or BISP not from 8171 | amber |
| unreadable output or model error | anything | amber ("could not check") |
| `safe` / `promo` | nothing | green |
| (model skipped) | sender is 8171, no link, no code request | green (BISP text) |

## Layout

```
app/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  core/      pure Kotlin/JVM, no Android: Schema.kt, Prompt.kt, rules/, classify/,
             verdict/, text/ (all user-facing text). Tests: core/src/test
  android/   the app: LiteRT-LM classifier, Compose UI, notification listener,
             share/report intents. Unit tests: android/src/test,
             instrumented UI tests: android/src/androidTest
```

The classifier sits behind `MessageClassifier` (`core/.../classify/`).
`LiteRtClassifier` (android) is the real model; `FakeClassifier` (core) and
scripted classifiers in the tests let the rules, verdicts, text and UI be
tested without a model.

## Build on a Mac with Android Studio

Tested toolchain (see "What was verified" below): AGP 9.5.0, Kotlin 2.4.21,
Gradle 9.8.2 (wrapper), compileSdk/targetSdk 37, minSdk 26, JDK 21,
LiteRT-LM `litertlm-android` 0.19.0, Compose BOM 2026.09.00.

### 1. Install

1. Install the current Android Studio from
   <https://developer.android.com/studio> (Apple silicon or Intel build to
   match your Mac). It bundles a JDK 21, which is what Gradle should use.
2. In Android Studio: **Settings → Languages & Frameworks → Android SDK**
   - *SDK Platforms*: tick **Android 37.0** (API 37). The Compose libraries
     need compileSdk 37.
   - *SDK Tools*: **Android SDK Build-Tools**, **Android SDK Platform-Tools**
     (gives you `adb`).
3. Optional, for `adb` in Terminal: add
   `export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"` to `~/.zshrc`.

### 2. Open and build

1. **File → Open…** and choose the **`app/` folder** of this repo (not the
   repo root; the Python project lives there).
2. Let Gradle sync. Studio writes `app/local.properties` with your SDK path
   (`sdk.dir=/Users/<you>/Library/Android/sdk`); it is git-ignored.
   - If sync says the Android Gradle plugin version is not supported, update
     Android Studio (**Android Studio → Check for Updates**). As a fallback,
     lower `agp` in `gradle/libs.versions.toml` to the newest 9.x your Studio
     supports. AGP 9 is required: the build uses its built-in Kotlin support.
     *(Unverified: I could not check which Studio release first supports
     AGP 9.5.)*
   - If Gradle complains about the JDK, set **Settings → Build, Execution,
     Deployment → Build Tools → Gradle → Gradle JDK** to the bundled
     "jbr-21".
3. Plug in an Android phone with USB debugging on (Settings → About phone →
   tap *Build number* 7 times → Developer options → USB debugging), choose the
   **android** run configuration and press **Run**.

Or from Terminal:

```bash
cd app
```

```bash
./gradlew :core:test :android:testDebugUnitTest :android:lintDebug :android:assembleDebug
```

```bash
adb install -r android/build/outputs/apk/debug/android-debug.apk
```

`:core:test` needs only a JDK, no Android SDK. Instrumented UI tests run on a
connected phone or emulator:

```bash
./gradlew :android:connectedDebugAndroidTest
```

### 3. Try it without the model

Launch the app: a "Demo mode" banner shows, and the keyword stand-in answers.
Paste a fictional seed, for example
`Assalam o alaikum bhai, ghalti se mera 6 digit code aap ke number par chala gaya hai. Please woh code mujhe wapis bhej dein, bohat zaroori hai.`
and you should get a red "Someone wants your code" card.

### 4. Push the model

Export as in [`../export/README.md`](../export/README.md), then copy the file to
the phone under the exact name `scamradar.litertlm`. The model is never
bundled in the APK and never downloaded by the app.

Open the app once first (so Android creates its storage folder), then:

```bash
adb push runs/v1/litertlm/<exported>.litertlm /sdcard/Android/data/io.github.jehanzebali11.scamradar/files/scamradar.litertlm
```

If that path is not writable on your phone (some Android 11+ builds block
`adb` from `Android/data`), use the app's internal storage instead (works for
debug builds):

```bash
adb push runs/v1/litertlm/<exported>.litertlm /data/local/tmp/scamradar.litertlm
```

```bash
adb shell run-as io.github.jehanzebali11.scamradar cp /data/local/tmp/scamradar.litertlm files/scamradar.litertlm
```

```bash
adb shell rm /data/local/tmp/scamradar.litertlm
```

Then in the app: **Settings → Look for the model again**. Settings shows
"On-device model loaded (CPU)" when it worked, and the demo banner disappears.
Settings also lists the exact paths the app looks in. The engine loads on CPU
by default (`LiteRtClassifier.load(preferGpu = false)`); GPU, then CPU, is
tried when `preferGpu = true`.

Check the result against `scamradar validate`: each card shows the parsed
compact line in small print (`model: scam|code_theft|asks_for_code,urgency`).

### 5. Notification checking

Settings → *Check SMS and WhatsApp notifications* → **Set up…** → read the
disclosure → *I understand, continue* → allow Scam Radar notifications
(Android 13+) → turn on Scam Radar in **Notification access**. Send yourself a
fictional scam SMS from another phone; a "Likely scam" notification should
appear, and tapping it opens the full card.

## What was verified, and what was not

Verified in a Linux (x86_64) sandbox with no device:

- `./gradlew :core:test` — 32 tests pass (parser vs `schema.py` vectors, every
  seed's expected line, link extraction, code-request detector vs every seed,
  BISP allowlist, verdict combining, model-failure handling, text completeness
  in both languages, share/report text).
- `./gradlew :android:testDebugUnitTest` — 10 tests pass (ViewModel flow with
  the fake classifier, notification parsing, dedupe).
- `:android:lintDebug` clean; `:android:assembleDebug` and
  `:android:assembleDebugAndroidTest` build. The debug APK contains
  `liblitertlm_jni.so` for arm64-v8a and x86_64 (about 63 MB, mostly those
  native libraries). The merged manifest has no INTERNET permission.
- `LiteRtClassifier` compiles against the real `litertlm-android` 0.19.0 AAR,
  so the API calls (`EngineConfig`, `Engine.initialize`, `createConversation`,
  `sendMessage`, `SamplerConfig`) exist with these signatures.
- `pytest` (including `tests/test_schema_vectors.py`), `ruff` and `mypy` pass.

**Not verified (needs your Mac and a phone):**

- Building in Android Studio on macOS. Only the Gradle command line on Linux
  was run.
- **Anything on a device or emulator**: the UI, the share target, the
  disclosure → notification-access flow, the listener seeing real SMS/WhatsApp
  notifications (WhatsApp's notification format in particular), the alert
  notification, and the instrumented tests in `android/src/androidTest`. No
  emulator could run here (no KVM).
- **Real model inference.** No exported `.litertlm` existed to test with. In
  particular unverified: that the chat template inside the exported file wraps
  `Classify this message:\n<text>` the way training did, that greedy decoding
  (`topK = 1`) returns just the compact line, load time and latency on a
  phone, and GPU backend behaviour.
- Whether `adb push` into `/sdcard/Android/data/<package>/files` works on
  your particular phone (the `run-as` route is the fallback).
- **Urdu wording.** Written carefully but should be read by a native speaker
  before release.
- The **report destination.** `ShareIntents.REPORT_SMS_NUMBER` is `null` on
  purpose: confirm the official complaint short code before filling it in.
  Until then, the report is only offered through Copy and the share sheet.
- The new `.github/workflows/android.yml` job has not run yet.

## Before a public release

- Google Play restricts `NotificationListenerService`; check the current
  policy and the permissions declaration form. The in-app disclosure is
  there, but the Play listing needs its own.
- Ship the Gemma Terms of Use notice with the model (see the repo README).
- Turn on minify for release and re-test (the ProGuard rule keeps LiteRT-LM's
  classes).
- Consider splitting the APK by ABI to drop the x86_64 library on phones.
