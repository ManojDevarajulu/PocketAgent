# Testing

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

## Device checklist (S3)

Run by hand on the test phone (Nothing phone, Android 16) from a debug build. Tick each box only after you
have seen the expected result.

### Provider config for the debug build

The debug build reads its provider from `local.properties` in the repository root. Add these five keys, then
rebuild:

```properties
pocketagent.kind=ANTHROPIC
pocketagent.baseUrl=https://api.anthropic.com
pocketagent.apiKey=<your key>
pocketagent.model=<model id>
pocketagent.supportsImages=true
```

- `pocketagent.kind` is `ANTHROPIC` or `OPENAI_COMPAT` (for OpenRouter use `OPENAI_COMPAT` and
  `https://openrouter.ai/api/v1`).
- `pocketagent.supportsImages` is `true` or `false`.
- If any value is blank or the kind is unknown, the debug build has no provider config.
- `local.properties` is git-ignored. Never commit it or share it.
- A debug APK embeds the key in plaintext. Never distribute a debug APK.

### Checklist

- [ ] **1. Screen changed (T3).** Change the target between read and act, for example by scrolling while the
  model is thinking. Expected: the action fails with "screen changed".
- [ ] **2. Screen-filter app (T5).** Turn on a screen-filter app over the confirmation card. Expected: Allow
  does not respond.
- [ ] **3. Long-press stop.** Long-press stop during a model call, a gesture, a confirmation card and the
  answer screen. Expected: the task stops within 1 s each time.
- [ ] **4. Split screen and rotation.** Use split screen, and rotate the phone during a task. Expected: the
  task keeps working; no wrong-window or crash behaviour.
- [ ] **5. Lock during a task.** Lock the phone during a task. Expected: after unlocking, the summary card
  says "Phone unavailable".
- [ ] **6. Accessibility off during a task.** Turn off accessibility for the service during a task. Expected:
  the Toast appears and nothing else runs.
- [ ] **7. Secure-window screenshot.** Ask for a screenshot of a secure window. Expected: it returns the tool
  error.
- [ ] **8. Idle event types.** While the service is idle, run `adb shell dumpsys accessibility`. Expected: no
  event types are listed for the service.
- [ ] **9. Exit gate.** Demo task 3, end to end: view an address in WhatsApp, tap the bubble and say "Take me
  there". Expected: Google Maps starts directions to the address.
