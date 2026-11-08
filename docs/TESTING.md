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
