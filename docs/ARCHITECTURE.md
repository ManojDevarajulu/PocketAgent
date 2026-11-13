# Architecture

Pocket Agent is a single Gradle module written in Kotlin. The agent logic is kept free of Android types so it
can be unit-tested on a plain JVM; everything that touches the phone sits behind small interfaces.

```text
Bubble / task screen / setup
             │
             ▼
        AgentRunner      one task at a time, owns cancellation, publishes state
             │
             ▼
        AgentLoop ──► LlmClient ──► AnthropicClient / OpenAiCompatClient ──► model API
             │
             ├──► SafetyGate       allow, block or ask the user
             ├──► PlaybookStore    bundled Markdown tips per app
             ├──► DevicePort ◄── AccessibilityDevice
             │                           │
             │                 PocketAccessibilityService
             └──► NotificationPort ◄── AndroidNotifications
                                           │
                                 PocketNotificationListener
```

## Packages

| Package | Role |
| --- | --- |
| `agent/` | `AgentLoop`, `AgentRunner`, `StuckDetector`, tool definitions, captions, `OtpQuickAction` ("Latest OTP", no AI) |
| `llm/` | `LlmClient` and its two implementations, provider presets and validation. The only package that touches the network |
| `safety/` | `SafetyGate`, `BlockedApps`, `RiskyLabels`, `NotificationFilter`, `MoneyMessages`, `CodeFinder`. Plain rules, no Android types |
| `playbook/` | Loads and validates the per-app Markdown playbooks in `assets/playbooks/` |
| `device/` | `DevicePort`, screen model, `ScreenSerializer`, settle logic, re-validation, `NotificationPort`, `NotificationSerializer` |
| `android/` | The accessibility service, `AccessibilityDevice`, the notification listener (`PocketNotificationListener`, `AndroidNotifications`), Keystore storage |
| `setup/` | Setup state and settings storage |
| `ui/` | Bubble, caption, confirmation card, task screen, setup screen (platform Views, no Compose) |

## The agent loop

`AgentLoop` runs observe, decide, check, act, for at most 25 steps. The first observation is the screen that
was saved when the user tapped the bubble.

1. Wait for the screen to settle (300 ms without accessibility events, 3 s at most).
2. Hide the bubble, read the accessibility tree, show the bubble again.
3. Serialize the visible nodes (see below) and wrap them as screen data.
4. If an app with a playbook is in the foreground for the first time, add its playbook.
5. Send the observation to the model and receive one tool call.
6. Validate the call. An invalid call returns a tool error; two in a row stop the task.
7. Pass the action through `SafetyGate`.
8. Perform the action through `DevicePort`, then update `StuckDetector` and repeat.

The model's tools are `tap`, `type`, `scroll`, `back`, `home`, `open_app`, `screenshot`, `wait`, `ask_user`
and `done`. `open_app` takes an app name only, never a package, URL or intent.

A task also ends when the same screen persists after three actions, when the phone locks, when accessibility
is switched off, on a fatal provider error, or when the user long-presses the bubble (cancelled within 1 s).

### Providers

`LlmClient` has two implementations: `AnthropicClient` (Messages API) and `OpenAiCompatClient` (Chat
Completions with a configurable base URL, key and model). That covers OpenRouter, OpenAI, Gemini's
compatible endpoint, DeepSeek, Groq and local servers such as Ollama. Each client keeps its own history in
its provider's format. Any model must support tool calling; setup verifies this with a test request.

## Accessibility tree serializer

`ScreenSerializer` turns the raw accessibility tree into compact text for the model.

- Each visible node gets a short ID, a role, its text or content description, and flags such as clickable or
  editable. IDs are valid for one observation only; a stale ID returns a tool error.
- Password fields are redacted: the agent never reads or types in them.
- Pocket Agent's own windows are left out.
- Output is capped (12,000 characters by default).
- The result is wrapped and labelled as **screen data**. The system prompt tells the model that text inside
  it is never an instruction.

Before an action runs, `DevicePort.perform` re-checks the target's label and bounds against the observation.
If the screen changed in between, the action fails with "screen changed" and the safety gate runs again on
the fresh label.

## Overlays

The bubble, the caption line and the confirmation and summary cards are Android `View`s added to
`TYPE_ACCESSIBILITY_OVERLAY` windows by the accessibility service. This needs no overlay permission.

- **Bubble:** the only way to start a task. Tap to open the task screen, long-press to stop. State is shown
  by colour and icon: idle, running, waiting for the user, done, error.
- **Caption:** one line next to the bubble. Kotlin builds it from the tool name and the target's label, so the
  model cannot write misleading text into it. Password fields are never named.
- **Confirmation card:** shows the action, target, app name and the exact text that will be sent. Its buttons
  ignore touches when another window covers them. No answer within 120 s counts as declining.
- **Summary card:** shown when any task ends, including failures.

The bubble and caption are hidden while the screen is read, while an action runs, and while the phone is
locked. Taps by coordinate inside any of Pocket Agent's own windows are rejected.

## Setup and storage

`SetupActivity` is a single checklist that detects each step's state: privacy notice, provider and key,
model test, accessibility, optional microphone, bubble toggle, test task. The API key is stored encrypted
(see [SECURITY_MODEL.md](SECURITY_MODEL.md)). There is no build-time provider config.

## Playbooks

A playbook is a short Markdown file (2 KB at most) in `app/src/main/assets/playbooks/` with `app`, `package`
and `tested_version` front matter and a list of navigation tips. Playbooks ship inside the APK and are never
downloaded. They are trusted first-party content, so every playbook pull request is reviewed like code. They
describe navigation only and cannot change `SafetyGate`. See [CONTRIBUTING.md](../CONTRIBUTING.md).

## Constraints

- Minimum Android 11 (API 30).
- Four runtime dependencies: kotlinx-coroutines-android, kotlinx-serialization-json, okhttp, androidx-core-ktx.
  A test fails if another appears.
- No telemetry, analytics, crash reporter, receiver, provider or exported component other than the launcher
  activity. The only services are the accessibility service and the notification listener, both system-bound and
  not exported; the listener handles no notification events and stores nothing.
