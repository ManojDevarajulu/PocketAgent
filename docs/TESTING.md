# Testing

## Unit tests

The JVM suite covers the agent loop, safety gate, serializer, providers, setup logic and architecture
guards (for example: only the `llm` package may use the network, and the manifest and dependency allowlists).
It needs no phone and no API key.

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:lintDebug           # Android Lint
./gradlew assembleDebug            # build the debug APK
```

On Windows use `gradlew.bat` in cmd, or `./gradlew` in Git Bash or PowerShell. CI runs the same checks on every
pull request.

## Live provider check

`LiveProviderCheckTest` sends one real request to a real model and passes only if the model answers with a
tool call. It costs one small request (a few hundred tokens). It is skipped unless all four variables below
are set and non-blank, and CI never sets them, so CI and a plain `./gradlew :app:testDebugUnitTest` skip it.

| Variable | Value |
| --- | --- |
| `POCKET_AGENT_LIVE_KEY` | your API key |
| `POCKET_AGENT_LIVE_KIND` | `ANTHROPIC` or `OPENAI_COMPAT` |
| `POCKET_AGENT_LIVE_BASE_URL` | for example `https://api.anthropic.com` |
| `POCKET_AGENT_LIVE_MODEL` | the model id |

Set the key in the current shell only; never put it in a file, a script or the repository. The test never
prints it.

### Anthropic

Git Bash:

```bash
export POCKET_AGENT_LIVE_KEY='<your key>'
export POCKET_AGENT_LIVE_KIND=ANTHROPIC
export POCKET_AGENT_LIVE_BASE_URL=https://api.anthropic.com
export POCKET_AGENT_LIVE_MODEL='<model id>'
./gradlew --no-daemon :app:testDebugUnitTest --tests "*LiveProviderCheckTest"
```

PowerShell:

```powershell
$env:POCKET_AGENT_LIVE_KEY = '<your key>'
$env:POCKET_AGENT_LIVE_KIND = 'ANTHROPIC'
$env:POCKET_AGENT_LIVE_BASE_URL = 'https://api.anthropic.com'
$env:POCKET_AGENT_LIVE_MODEL = '<model id>'
./gradlew --no-daemon :app:testDebugUnitTest --tests "*LiveProviderCheckTest"
```

### OpenRouter (OpenAI-compatible)

Git Bash:

```bash
export POCKET_AGENT_LIVE_KEY='<your key>'
export POCKET_AGENT_LIVE_KIND=OPENAI_COMPAT
export POCKET_AGENT_LIVE_BASE_URL=https://openrouter.ai/api/v1
export POCKET_AGENT_LIVE_MODEL='<model id>'
./gradlew --no-daemon :app:testDebugUnitTest --tests "*LiveProviderCheckTest"
```

PowerShell:

```powershell
$env:POCKET_AGENT_LIVE_KEY = '<your key>'
$env:POCKET_AGENT_LIVE_KIND = 'OPENAI_COMPAT'
$env:POCKET_AGENT_LIVE_BASE_URL = 'https://openrouter.ai/api/v1'
$env:POCKET_AGENT_LIVE_MODEL = '<model id>'
./gradlew --no-daemon :app:testDebugUnitTest --tests "*LiveProviderCheckTest"
```

### Afterwards

Remove the variables from your shell (or just close it).

Git Bash: `unset POCKET_AGENT_LIVE_KEY POCKET_AGENT_LIVE_KIND POCKET_AGENT_LIVE_BASE_URL POCKET_AGENT_LIVE_MODEL`

PowerShell: `Remove-Item Env:POCKET_AGENT_LIVE_KEY, Env:POCKET_AGENT_LIVE_KIND, Env:POCKET_AGENT_LIVE_BASE_URL, Env:POCKET_AGENT_LIVE_MODEL`

If the Gradle daemon is running it keeps the environment it started with, so use `--no-daemon` as above.

## Device checklist

The JVM suite cannot see Android-only behaviour (Keystore, speech recognition, permissions, overlays, lock
screen). Run this list by hand on a physical phone (Android 11 or newer; Android 16 is the reference) from a
debug build, and tick an item only after you have seen the expected result.

Install and set up:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The provider and key are entered in the app's setup screen. There is no build-time provider config.

### Setup

- [ ] **Fresh install.** Uninstall any earlier build, install fresh. Using only the setup screen: accept the
  notice, choose a provider and paste the key, enter a model and pass the test, allow restricted settings and
  enable accessibility, grant or skip the microphone, switch the bubble on, run the test task. Expected: the
  checklist shows "You're all set".
- [ ] **Key storage.** Kill and reopen the app. Expected: "Key saved", no characters shown. Clear the app's
  data. Expected: setup asks for the provider and key again.
- [ ] **Restricted settings.** On a fresh install the accessibility switch may be greyed out. Expected: the
  guidance text and the app info button lead to "Allow restricted settings", and the switch then works.
- [ ] **Check messages.** Test with a wrong key, a model without tool calls, an unreachable server and a rate
  limit. Expected: each shows a clear message; the rate limit shows the provider's own message.
- [ ] **Microphone.** Granted, denied and offline. Expected: granted fills the box with the spoken words and
  nothing starts until Go; denied shows a "turn it on in setup" hint; offline shows a connection message or
  on-device recognition works.
- [ ] **Secure screen.** Needs a release (non-debuggable) build, because `FLAG_SECURE` is only set there.
  Screenshot the setup screen while the key field is shown. Expected: blocked or blank.
- [ ] **Accessibility toggle.** Switch accessibility off and return to the app. Expected: it shows as the
  current step again; switching it back on marks it done.
- [ ] **Custom URL.** Enter a custom address. Expected: the warning names the real host, and changing the host
  asks again.
- [ ] **Palette.** Open every screen and card in light and dark. Expected: text is readable, and the Allow and
  Decline buttons look identical.
- [ ] **Notification access row.** Tap "Open notification access" in setup. Expected: Android's notification
  access screen opens; after switching Pocket Agent on and returning, the Notifications step shows On.
- [ ] **Listener label.** Open Settings > Notification access. Expected: the listener is listed with a readable
  name ("Pocket Agent").
- [ ] **OTP tile without access.** With access off, tap Latest OTP, then "Turn on notification access in setup
  to use this." Expected: setup opens in its own task, not on top of the task sheet's stack.

### Agent behaviour

- [ ] **Screen changed.** Change the target between read and act, for example by scrolling while the model is
  thinking. Expected: the action fails with "screen changed".
- [ ] **Screen-filter app.** Turn on a screen-filter app over the confirmation card. Expected: the filter
  cannot cover the card; if something does, Allow does not respond.
- [ ] **Long-press stop.** Long-press the bubble during a model call, a gesture, a confirmation card and the
  answer screen. Expected: the task stops within 1 s each time.
- [ ] **Split screen and rotation.** Use split screen and rotate the phone during a task. Expected: the task
  keeps working, with no wrong-window behaviour or crash.
- [ ] **Lock during a task.** Lock the phone during a task. Expected: after unlocking, the summary card says
  "Phone unavailable"; the bubble and caption are hidden while locked.
- [ ] **Accessibility off during a task.** Turn it off mid-task. Expected: a Toast appears and nothing else
  runs.
- [ ] **Secure-window screenshot.** Ask for a screenshot of a secure window (for example a password manager).
  Expected: the tool returns an error.
- [ ] **Idle event types.** While the service is idle, run `adb shell dumpsys accessibility`. Expected: no
  event types are listed for the service.
- [ ] **Blocked apps.** Ask the agent to open Settings or a payment app. Expected: the task stops before
  acting.

### Notifications

- [ ] **Grant access.** Grant access in setup. Expected: the step shows Done.
- [ ] **Catch me up.** Get a WhatsApp message and run "Catch me up". Expected: a summary in 1 to 2 model calls,
  no app opened.
- [ ] **Reply.** Run "Reply to {sender}: on my way". Expected: the confirmation card shows the text; Approve
  sends it; Cancel sends nothing.
- [ ] **Latest OTP.** Get an SMS OTP and tap "Latest OTP". Expected: the code shows in about a second. Check
  the provider never got it: a "Catch me up" run shows the model "••••••".
- [ ] **Blocked and bank.** Get a notification from a blocked app and a bank debit SMS. Expected: "Catch me up"
  never mentions them.
- [ ] **Revoke access.** Revoke access. Expected: the agent opens WhatsApp the old way, and "Latest OTP" shows
  the setup link.
- [ ] **Timing.** Compare the timing line on the summary card for "Catch me up" with before.
- [ ] **Long reply.** Ask for a reply longer than 200 characters. Expected: it is refused; the agent shortens
  the text and retries or tells you.
- [ ] **Hidden characters.** Ask for a reply containing an emoji joined by a zero-width joiner (for example a
  family emoji). Expected: it is refused; the agent rewrites without it or tells you.
- [ ] **Card matches send.** Approve a reply. Expected: the text on the card is exactly the text that arrives.
- [ ] **Name matching.** Get notifications from a contact named "Bankey" and from "HDFC Bank". Expected:
  "Catch me up" includes Bankey and drops HDFC Bank.
- [ ] **Latest OTP on Android 15+.** On Android 15 or newer, get an SMS OTP and tap "Latest OTP". Expected: the
  code shows. The platform may hide sensitive notification content from third-party listeners; if it does, note
  the phone model and Android version in the report.
- [ ] **Listener binds while not exported.** Open Settings > Notification access. Expected: Pocket Agent is
  listed, and after switching it on "Catch me up" reads notifications (the listener is `exported="false"`).
- [ ] **Three replies in one task.** Get messages from three different people and ask the agent to reply to
  all three. Expected: three confirmation cards, three replies sent, and the task does not end as stuck.
- [ ] **Own messages.** In a chat where your own message is the last line, run "Catch me up". Expected: the
  summary does not treat your own line as a new message to you.
- [ ] **Catch me up without access.** With access off, run "Catch me up". Expected: the agent opens your
  messaging apps instead of reporting a notification access error.

### Voice loop

Needs Talk back switched on (Settings screen, Bubble section). All items are still to be run on a device.

- [ ] **Bluetooth headphones.** Run the WhatsApp reply task from the mic. Expected: it finishes without touching
  the screen after the mic.
- [ ] **Read it.** Say "Reply to Mom I'm on my way". Expected: the confirmation does not speak the text; "read
  it" does; the end line is just "Done."
- [ ] **Unclear then silence.** Say "maybe" at a confirmation, then nothing. Expected: it declines after the
  re-ask.
- [ ] **Card beats voice.** Tap Approve while the prompt is still speaking. Expected: it approves at once and
  speech stops.
- [ ] **Cancel mid-sentence.** Cancel from the bubble while it speaks. Expected: silence immediately.
- [ ] **Hindi.** Set the phone language to Hindi. Expected: prompts are spoken by the Hindi voice and "haan"
  approves.
- [ ] **Typed task.** Type a task. Expected: it stays silent.
- [ ] **Talk back off.** Turn Talk back off and run a mic task. Expected: it stays silent.
- [ ] **Phone call.** Take a call mid-sentence. Expected: speech stops and the task carries on; a confirmation
  during a call declines or waits for the card; it must never approve.

Known device-pass items:

- [ ] **Recognizer from the service (gate).** A `SpeechRecognizer` created from the accessibility service must
  produce a result while another app is in front. If Android blocks it, confirmations fall back to the card only
  and the setup copy must say so.
- [ ] **TTS warm-up.** Text-to-speech may be unavailable in the first second after the bubble tap, so a task
  sent immediately runs silent. Check how often this happens.

Open questions from the design, not settled without a device:

- **`RecognitionService` query.** Whether Android 11+ needs a `<queries>` entry for
  `android.speech.RecognitionService`. It has not been added because it is unverified; the current mic works
  without it. Add it (and extend `ArchitectureTest`) only if a device shows the recognizer is not found.
- **Start tone.** The kept tone is `ToneGenerator` `TONE_PROP_BEEP` for 100 ms, as in the code. It is not yet
  judged on a device.

When you report a failure, include the phone model, Android version, app version and provider. Never paste an
API key.
