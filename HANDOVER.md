# Handover: Scam Radar

Snapshot of where the project stands, how to pick it up, and what is still
open. Written on 2026-11-04 at the end of the work on branch `android-app`.

## In one paragraph

Scam Radar is an offline Android app that warns people (especially older
people) when an SMS or WhatsApp message looks like a scam, in Urdu and
English. A fine-tuned `google/gemma-3-270m-it` labels each message as
`label|scam_type|flags`. A rule layer runs before it, and all advice shown to
the user is pre-written. This repo holds the Python data/training/eval
pipeline and, since this branch, the Android app in `app/`.

**Status: prototype.** The app builds and its logic is tested, but:

- the model has **not been trained or exported yet**;
- the app has **never run on a phone or emulator**;
- it is **not on the Play Store**.

The app runs in "demo mode" (a keyword stand-in) until a `.litertlm` file is
pushed to the phone.

## Where things are

- **Branch:** `android-app` (on top of `main`; `git log main..android-app` lists the work).
- **Pull request:** [JehanzebAli11/scam-radar#1](https://github.com/JehanzebAli11/scam-radar/pull/1),
  open, not merged.
- **CI:** the Python job (`tests.yml`, 3.11/3.12/3.13) passes. The Android job
  (`android.yml`) failed on its first run because `setup-android` tried to
  install the removed `tools` package. That is fixed in the last commit; check
  the PR to confirm it now passes.
- **Ground rules:** read `AGENTS.md` (or `CLAUDE.md`) before changing anything.
  In short:
  - Training data and labels come only from a local open-weight teacher or
    consented real messages. No Claude/OpenAI/Gemini APIs.
  - `eval/locked/` is the real test set. Never write to it; only the eval
    harness reads it.
  - No real personal data in the repo.

## Repo map (what this branch added)

```
app/                        Android app (Gradle; open this folder in Android Studio)
  core/                     pure Kotlin, no Android: testable with just a JDK
    Schema.kt, Prompt.kt    mirror of scamradar/schema.py and prompting.py
    rules/                  BISP 8171 allowlist, link extraction, "asks for a code" detector
    classify/               MessageClassifier interface + FakeClassifier (demo mode)
    verdict/                rules + model answer -> green/amber/red Verdict; ScamChecker pipeline
    text/                   AdviceText (all user-facing Urdu/English text), ShareText
  android/                  the app
    llm/                    LiteRtClassifier (LiteRT-LM), ClassifierHolder (lazy load, idle unload), ModelLocator
    notify/                 ScamNotificationListener, AlertNotifier, ProtectionLog, NotificationMessage
    ui/                     Compose screens: Home (protection panel + manual check), ResultScreen,
                            ResultCard, Settings, Disclosure, ReportDialog, theme/components
    share/                  "Send to family" and report intents (never sends by itself)
    src/test/               JVM unit tests + Robolectric DemoVideoTest (records the demo video)
    src/androidTest/        instrumented Compose tests (compile only, never run)
  tools/make_demo_video.*   turns DemoVideoTest frames into a captioned MP4
  README.md                 build on a Mac, push the model, verified/unverified list
scamradar/baselines/litertlm_predictor.py   run an exported .litertlm the way the app does; size/RAM/latency stats
scamradar/export_compare.py                 `scamradar compare-exports`: accuracy + cost per export
export/README.md (section 4)                int8 vs int4 / cache 4096 vs 512 experiment
export/make_results_chart.py                LinkedIn-ready chart/clip of that experiment
tests/fixtures/schema_vectors.jsonl         shared parser vectors (Python and Kotlin both test against them)
.github/workflows/android.yml               Android CI
```

## How it works (app)

1. **Notification arrives.** `ScamNotificationListener.onNotificationPosted`
   drops anything not from an SMS or WhatsApp app. It extracts sender and text
   and skips duplicates.
2. **Rules first.** `RuleEngine`:
   - Sender exactly `8171`, no link and no code request: green, and the model
     is skipped.
   - Links are extracted; shorteners, IPs, punycode and risky TLDs are
     flagged.
   - The "asks for a code" detector ignores "do not share" OTPs and courier
     codes.
3. **Model.** `ClassifierHolder` loads the model only when needed and unloads
   it after 5 idle minutes. `LiteRtClassifier` sends exactly
   `Classify this message:\n<text>` in a fresh conversation, with greedy
   decoding, `maxNumTokens = 512` and at most 48 output tokens.
4. **Verdict.** The lenient parser reads the compact line. Then:
   - scam: red;
   - safe or promo, but the rules found a code request, a suspicious link, or
     a BISP mention from a non-8171 sender: amber;
   - unreadable output: amber, never green.
5. **Warning.** For amber or red, `AlertNotifier` posts a bilingual
   notification. Tapping it opens `ResultScreen`. `ProtectionLog` keeps counts
   and recent warnings in memory only.
6. **Text.** Every word on a card comes from `AdviceText`; the model only picks
   the label, type and flags.

The app has **no INTERNET permission**; the manifest strips it even if a
library adds it.

## Running the checks

```bash
pip install -e ".[dev]"
```

```bash
ruff check . && ruff format --check . && mypy scamradar && pytest
```

```bash
cd app && ./gradlew :core:test :android:testDebugUnitTest :android:lintDebug :android:assembleDebug :android:assembleDebugAndroidTest
```

Results at handover:

- **Python:** 162 tests pass.
- **Kotlin:** core 32, Android unit 12. The demo-video test is skipped unless
  asked for.
- **Lint and builds:** lint is clean; the debug APK and test APK build.

The toolchain:

- AGP 9.5.0 (built-in Kotlin), Kotlin 2.4.21, Gradle 9.8.2 (wrapper).
- compile/target SDK 37, minSdk 26, JDK 21.
- `litertlm-android` 0.19.0, Compose BOM 2026.09.00, Robolectric 4.17.

**Gotchas seen in the cloud sandbox** (probably not on a Mac):

- Maven Central sometimes answered 429. A local, uncommitted Gradle init script
  pointed `repo.maven.apache.org` at
  `https://maven-central.storage-download.googleapis.com/maven2/`.
- Robolectric downloads its runtime from Maven Central itself. Pass
  `-Drobolectric.dependency.repo.url=<mirror>`; the build forwards it.
- No KVM, so no emulator. The demo video is rendered with Robolectric instead.

The cloud environment "Dgx" now has a setup script that installs the Python
dev dependencies and the Android SDK for this repo. It is unproven until the
next fresh session.

## Verified vs not verified

**Verified:**

- **Builds:** the app builds against the real LiteRT-LM AAR.
- **Logic tests:**
  - the parser matches `schema.py` on the shared vectors;
  - the code detector agrees with every seed's `asks_for_code` flag;
  - verdict combining, the idle unload, and notification parsing are tested.
- **End-to-end on the JVM:**
  - the real listener warns about a scam SMS and a WhatsApp scam;
  - it stays quiet for a genuine OTP and an 8171 BISP message (asserted in
    `DemoVideoTest`).
- **Python predictor:** `predict-litertlm` ran with the real `litert-lm-api`
  0.19.0 on Google's tiny test model.

**Not verified:**

- Building in Android Studio on macOS.
- **Anything on a device:**
  - the UI and the share target;
  - the notification-access flow;
  - real SMS/WhatsApp notification formats;
  - the instrumented tests.
- **Real model inference:**
  - whether the exported chat template wraps the prompt as training did;
  - whether the output is exactly the compact line;
  - load time, latency, and memory on a phone.
- **Memory and battery numbers.** Everything quoted so far is an estimate.
- `adb push` into `Android/data` on a given phone (`run-as` is the fallback).
- **Urdu wording.** It needs a native speaker's review.
- **The report short code.** `ShareIntents.REPORT_SMS_NUMBER` is `null` on
  purpose.

## Decisions made, and why

- **BISP allowlist is by sender, not text.** Scammers write "8171" in the
  text. A message from 8171 that has a link or asks for a code still goes to
  the model, because sender IDs can be spoofed.
- **No model-written text reaches the user.** Wrong advice to a vulnerable
  person is worse than terse advice.
- **"Report this scam" is opt-in and only prepares text.** The recipient is
  left empty until the official number is confirmed.
- **Automatic protection is the main feature**, behind a bilingual disclosure
  screen. Manual paste is secondary.
- **Event-driven, not a background service.** The listener does nothing until
  a notification arrives. The model loads on demand and unloads after 5 idle
  minutes. Opening the app warms it up for paste checks.
- **The KV cache must be shrunk at export.** `litert-torch` 0.10.0 bakes a
  fixed 4096-token cache into the file by default, and LiteRT-LM uses the
  file's limit. Export with `--cache_length=512`; the app's 512 cap
  (`-PscamradarMaxTokens`) is a safety net.

## Next steps, in order

1. **Train** on the Spark (`scamradar train`). Then serve the checkpoint and
   run `scamradar eval --strict`. Only continue if it beats the TF-IDF
   baseline.
2. **Export and run the experiment** in `export/README.md` section 4 (int8 /
   int8+int4 embeddings / int4, cache 4096 vs 512). Use
   `scamradar predict-litertlm`, then `scamradar compare-exports`. Ship the
   smallest export that still passes `--strict`, and check the per-type
   numbers.
3. **Test on a phone.** Build in Android Studio (`app/README.md`), push
   `scamradar.litertlm`, then measure:
   - TOTAL PSS with `adb shell dumpsys meminfo io.github.jehanzebali11.scamradar`
     while loaded and after the idle unload;
   - the time of the first check.

   Put the numbers in `runs/exp/<name>/device.json`.
4. **Real notifications.** Test with Google Messages, Samsung Messages and
   WhatsApp. WhatsApp's format is the most likely to need fixes in
   `NotificationMessage.extract`.
5. **Urdu review** of `AdviceText.kt`, the disclosure screen and
   notifications.
6. **Before release:**
   - Play Store `NotificationListenerService` policy and its declaration;
   - the Gemma Terms of Use notice;
   - turn on minify and re-test;
   - split by ABI (drops ~half the APK size);
   - consider bundling a Nastaliq Urdu font.

## Questions for the owner

- What is the official scam-report short code (PTA or other)? It goes in
  `ShareIntents.REPORT_SMS_NUMBER`.
- Keep the package name `io.github.jehanzebali11.scamradar`? Change it before
  the first public release, if ever.
- Bundle Noto Nastaliq Urdu (~1 MB, needs extra line spacing)?

## Demo video and charts

The generated files are not committed (binaries). Regenerate them:

```bash
cd app && tools/make_demo_video.sh build/scamradar-demo.mp4
```

```bash
python export/make_results_chart.py runs/exp/* --ship <chosen-export> --device "<phone>" --png runs/exp/results.png --mp4 runs/exp/results.mp4
```

- **The demo video** shows real app screens rendered by Robolectric (not a
  phone recording), in demo mode with fictional messages, and says so on
  screen.
- **The chart script** uses phone memory when every export has a
  `device.json`. `--sample` stamps made-up numbers "SAMPLE DATA".

## LinkedIn plan

Rules for every post:

- no numbers that were not measured;
- no messages from `eval/locked/`;
- nothing real without consent;
- say "prototype" until it is downloadable.

**Can post now:**

1. Launch, with the demo video. The draft is below.
2. "8 scams our parents fall for, and how to spot each": a PDF carousel made
   from the app's red cards.
3. "An app that can't leak your messages": no internet permission.
4. "Why I don't let the AI write the advice."
5. "The hard part isn't catching scams, it's not crying wolf": OTP and 8171
   messages stay quiet.
6. "Training an AI without a cloud API": the local teacher and the locked
   test set.
7. "I built and tested an Android app without a phone": Robolectric.

**After training and the phone test:**

8. The 270M model vs the TF-IDF baseline.
9. "How small can it get?": the export experiment. The draft is below.
10. "Roman Urdu breaks most AI", with fictional examples only.
11. Does it work on a budget phone?
12. A week on my parents' phones (with consent).
13. Asking for testers, Urdu reviewers and consented scam messages.
14. The Play Store review story.

### Draft: launch post (attach the demo video)

> "Ammi, this is my new number. I've had an accident, please send 15,000 now."
> "BISP has approved your 25,000. Send a 1,000 registration fee to claim it."
> "My code went to your number by mistake, please send it back."
>
> Most of us spot these in a second. Many of our parents and grandparents
> don't, and scammers know exactly who to target.
>
> So I'm building **Scam Radar**: an Android app that checks every new SMS and
> WhatsApp message *automatically* and warns you before you reply. In Urdu
> and English.
>
> 🔴 Scam? A clear warning: what kind of scam it is, and what to do.
> 🟢 A genuine bank OTP, or a real BISP message from 8171? It stays quiet.
> 👨‍👩‍👧 One tap to warn the rest of the family.
> 🔒 Fully offline. The app has no internet permission. Messages never leave
> the phone and are never stored.
> 🔋 It doesn't run in the background. It wakes up only when a message
> arrives, checks it, and goes back to sleep.
>
> Under the hood is a small AI model (Google's Gemma 3, 270M parameters),
> fine-tuned to read Roman Urdu, Urdu script, English and the mix we actually
> text in. A few simple rules sit in front of it for the classic tricks.
>
> This is an early prototype. The video shows the real app screens in demo
> mode, with fictional messages. Next: training the model and testing on
> real phones.
>
> Next post: how small can an AI model get and still catch scams? I'm
> testing four versions of it.
>
> If you'd like to try it with a parent or grandparent when it's ready,
> comment or DM me. 🙏
>
> #ScamAwareness #Pakistan #OnDeviceAI #Android #DigitalSafety #AI

### Draft: follow-up (attach `results.mp4` or `results.png`; fill every `[ ]`)

> How small can an AI model get and still catch scams? I tested it.
>
> Scam Radar runs its AI on the phone itself, so it has to fit on the budget
> phones many parents use. I exported the same fine-tuned model four ways and
> measured each one on a [phone model].
>
> What I changed:
> 1️⃣ **How the numbers are stored.** int8, int4, or a mix (int4 for the word
> table, which is ~63% of this model).
> 2️⃣ **How much "working memory" it reserves.** The export tool defaults to
> room for 4,096 tokens. A scam check needs under 512.
>
> Results:
> 📉 Memory while checking: [default] MB → [shipped] MB ([X]% less)
> 💾 File size: [default] MB → [shipped] MB
> 🎯 Scams caught: [default]% → [shipped]%
> 🚫 False alarms on genuine messages: [default]% → [shipped]%
>
> The surprise: [your real finding].
>
> The lesson: the default settings are built for chatbots. A classifier that
> reads one text message doesn't need a chatbot's memory. Measure before you
> assume you need a bigger phone or a cloud API.
>
> All scored on a held-out, human-labelled test set the model never saw
> during training.
>
> That's about [one / two] Chrome tabs' worth of memory, on a phone, offline,
> only while a message is being checked.
>
> #OnDeviceAI #EdgeAI #MachineLearning #Gemma #ScamAwareness #Pakistan

Before posting: make the opening scam texts true to your family (or say "the
kind of messages our parents get"). In the follow-up, use only measured
numbers, and a Chrome-tab comparison only if the measured memory supports it
(very roughly 100–300 MB per tab).
