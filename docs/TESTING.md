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

When you report a failure, include the phone model, Android version, app version and provider. Never paste an
API key.
