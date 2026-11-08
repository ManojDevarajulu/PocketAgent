# Pocket Agent v0.1 Design

**Status:** approved
**Date:** 2026-11-04 (revised 2026-11-08)
**Scope:** Android only. iOS is a separate future project.

## 1. Goal

Pocket Agent is an open-source Android app. The user taps a floating bubble on whatever is on screen, says or types what to do with it, and an AI agent operates apps on the phone. It runs without a computer, `adb`, root or a backend.

The project's aim is GitHub stars and personal recognition, not revenue, so decisions favour:

1. A striking, truthful demo.
2. An easy first run.
3. Short, useful tasks that succeed reliably.
4. A small codebase that people can read and contribute to.
5. Public proof of quality that the author can be known for.

**Pitch:** "Tap the bubble on anything. Say what to do. Your phone does it."

**Category:** the open-source agent that lives on your phone, not on your laptop.

**Audience, in launch order:**

1. AI builders
2. Android developers
3. India's developer community

All of them are expected to be comfortable sideloading an APK and bringing their own LLM API key, or running a local model.

### Success criteria

v0.1 is ready to launch when all of these are true:

- A new user can go from APK install to a finished first task using only the in-app setup screen.
- Each of the three demo tasks (section 10) succeeds in at least 9 of 10 consecutive runs on the test phone.
- A risky action never runs without the user's confirmation in testing.
- The long-press stop halts the agent within 1 second.
- The README opens with a real, uncut demo of about 30 seconds.
- A task can use what was on screen when the bubble was tapped.
- At least five playbooks ship with the app, and a contributor guide explains how to add one.
- The public scoreboard (section 15) is published, failures included.

Stars are the outcome being measured, but no star count is promised or used as a release gate.

## 2. Competitive position

Figures were checked on 2026-11-04 through the GitHub API.

| Project | Stars | Runs on | Notes |
|---|---:|---|---|
| droidrun/mobilerun | 9,923 | Computer | LLM-agnostic mobile agent |
| phone-harness | 4,205 | Computer | Created 2026-08-07, driven by coding agents |
| minitap-ai/mobile-use | 3,361 | Computer | Android and iOS |
| orailnoor/private-agent | 420 | Phone | Flutter, OpenAI-compatible endpoints |
| tokenarc/open-jarvis | 107 | Phone | Voice, any LLM |
| 8crsk/openclaw-android | 20 | Phone | On-device OpenClaw gateway |
| j0taaa/android-use | 6 | Phone | Chat-first app, Anthropic support, signed APK |
| Fox-Islam/android-agent | 4 | Phone | Chat, floating card, rule-based permissions, MCP |

Gemini's own screen automation was reported in March 2026 as a beta on Galaxy S26 and Pixel 10 phones, in the US and Korea, for a short list of apps. This may have changed since then, so it must be rechecked before launch.

**What this means:**

- "No computer needed" is not a differentiator, because several projects already do it.
- On-device projects are still small, so there is no clear winner yet.
- Pocket Agent competes on three things that are harder to copy than a bubble: one-tap tasks on what is already on screen, a growing library of community playbooks, and published reliability results.
- Android-agent offers similar on-screen starting points through the share sheet and text selection. Pocket Agent's difference is a single tap on the bubble from any screen.
- Long tasks impress once but fail more often, so the product promise is short, useful tasks.
- Android-agent's permission model is the quality bar for safety.

**Position against alternatives:**

| Compared with | Pocket Agent |
|---|---|
| Gemini | Open source and not limited to a list of phones and apps |
| phone-harness, DroidRun | Made for phone users, with no computer involved |
| android-use, android-agent | Bubble-first and starts from what is on screen, instead of a chat app |

## 3. Scope

**In v0.1:**

- Floating bubble with in-app toggle in setup screen
- Voice and text task input
- On-screen tasks that use the screen visible when the bubble was tapped
- Agent loop with any model that supports tool calling, through two providers: Anthropic, and any OpenAI-compatible endpoint (OpenRouter, OpenAI, Gemini, DeepSeek, Groq, a local Ollama server and others)
- Community app playbooks bundled with the app (trusted first-party guidance)
- Live on-screen captions
- Safety gate with confirmations and an app block list
- One guided setup screen
- Public reliability scoreboard
- Signed APK on GitHub Releases
- MIT license

**Not in v0.1 (Deferred to v0.2+):**

- iOS or a Mac controller
- Quick Settings tile (first item for v0.2)
- A persistent notification (the accessibility service does not need one)
- Task history
- User-written permission rules
- MCP, share-sheet entry and scheduled tasks
- Hindi and Hinglish voice support (first priority after launch)
- Downloading playbooks at runtime
- A chat interface
- Accessibility claims, until tested with disabled users
- Play Store release
- Pausing and resuming a task

## 4. User flow

1. The user installs the APK and completes setup (section 8).
2. The user turns on the bubble from the setup screen.
3. In any app, the user taps the bubble. The app reads the current screen, then a translucent task screen opens over it.
4. The user speaks or types a task, and the task screen closes. Words like "this" refer to the saved screen.
5. The agent works. The bubble shows its state, and a one-line caption shows the current action.
6. Before a risky action, a confirmation card names the action, target and app.
7. A short summary card appears when the task ends.
8. A long press on the bubble stops the agent at any time.

### On-screen tasks

The UI tree must be read when the bubble is tapped, because the task screen covers the app afterwards. That tree becomes the first observation, using the same serialisation and password redaction as every other step. If the user's app is blocked (section 7), the task is refused before anything is sent to the model.

The saved tree stays in memory and is dropped when the task ends. A screenshot is taken only if the model asks for one, after the task screen has closed.

### Captions

The caption is a small line next to the bubble, such as "Tapping Add to cart". Kotlin builds it from the tool name and the target node's label, so the model cannot write misleading text into it. Long labels are shortened, and password fields are never named. The caption is hidden whenever the screen is read or an action runs, along with the bubble.

**Bubble states** use both colour and icon, so colour is not the only signal:

| State | Colour | Icon |
|---|---|---|
| Idle | Grey | Sparkle |
| Running | Blue | Animated ring |
| Waiting for user | Amber | Hand |
| Done | Green | Check, for 3 seconds |
| Error or stopped | Red | Cross, for 3 seconds |

## 5. Architecture

The project is one Gradle app module, written in Kotlin. Packages keep the agent logic free of Android types, so it can be unit-tested on a JVM.

```text
Bubble / task screen / setup
             │
             ▼
        AgentRunner  (starts, cancels and reports one task)
             │
             ▼
        AgentLoop ──► LlmClient ──► AnthropicClient / OpenAiCompatClient ──► model API
             │
             ├──► SafetyGate
             ├──► PlaybookStore  (bundled Markdown files)
             └──► DevicePort ◄── AccessibilityDevice
                                        │
                              PocketAccessibilityService
```

```text
app/src/main/
  assets/playbooks/   one Markdown file per app
  java/.../pocketagent/
    agent/     AgentLoop, AgentRunner, AgentState, StuckDetector, Tools
    llm/       LlmClient, AnthropicClient, OpenAiCompatClient, ProviderPresets
    safety/    SafetyGate, BlockedApps, RiskyLabels
    playbook/  Playbook, PlaybookStore
    device/    DevicePort, ScreenNode, ScreenSerializer
    android/   PocketAccessibilityService, AccessibilityDevice
    ui/        BubbleOverlay, Caption, ConfirmCard, TaskInputActivity, SetupActivity, KeyStore
benchmarks/           task list, results and scoreboard source
```

### Units

| Unit | Purpose | Depends on |
|---|---|---|
| `AgentLoop` | Runs observe, decide, check, act until the task ends | `LlmClient`, `SafetyGate`, `DevicePort` |
| `AgentRunner` | Allows one task at a time, owns cancellation, publishes state | `AgentLoop` |
| `LlmClient` | Takes the conversation and tools, returns the next action | Nothing |
| `AnthropicClient` | Implements `LlmClient` for the Anthropic Messages API | OkHttp |
| `OpenAiCompatClient` | Implements `LlmClient` for any OpenAI-compatible Chat Completions endpoint, using a configurable base URL, key and model | OkHttp |
| `SafetyGate` | Allows, blocks or requests confirmation for an action | Plain rules only |
| `StuckDetector` | Spots repeated actions that change nothing | Nothing |
| `PlaybookStore` | Finds and validates the playbook for an app package | Nothing |
| `DevicePort` | Reads the screen and performs actions | Nothing |
| `ScreenSerializer` | Turns screen nodes into compact text | Nothing |
| `AccessibilityDevice` | Implements `DevicePort` on the accessibility service | Android |

## 6. Agent loop

A task ends after 25 steps at most. The first observation is the screen saved when the bubble was tapped.

1. Wait until the screen settles: 300 ms without accessibility events, 3 seconds at most.
2. Hide the bubble, read the UI tree and show the bubble again.
3. Serialise visible nodes, leaving out password fields and Pocket Agent's own windows.
4. If an app with a playbook is in the foreground for the first time in this task, add its playbook.
5. Send the observation to the model.
6. Validate the returned tool call.
7. Pass the action through `SafetyGate`.
8. Hide the bubble, perform the action and show the bubble again.
9. Update `StuckDetector` and repeat.

Each node in an observation has a short ID, role, text or description, and flags such as clickable or editable. IDs are valid only for the observation that produced them, and a stale ID returns a tool error to the model.

### Tools

| Tool | Input | Behaviour |
|---|---|---|
| `tap` | `node_id`, or `x` and `y`; optional `irreversible` | Clicks the node, or sends a gesture as a fallback |
| `type` | `node_id`, `text` | Sets the field's text; refuses password fields |
| `scroll` | `direction`, optional `node_id` | Scrolls a container or the screen |
| `back` | None | Global back action |
| `home` | None | Global home action |
| `open_app` | `name` | Launches a matching installed app |
| `screenshot` | None | Returns a screenshot for this step |
| `wait` | `seconds` (1 to 5) | Waits for slow content |
| `ask_user` | `question` | Shows a question and returns the answer |
| `done` | `summary`, `success` | Ends the task |

### Playbooks

A playbook is a short Markdown file that tells the agent how an app works. Anyone can contribute one by pull request, without writing Kotlin.

```markdown
---
app: Swiggy
package: in.swiggy.android
tested_version: "4.0"
---

## Tips
- Search is the fastest route to a restaurant.
- Reorder lives on the Account screen.
```

Example only: the package, version and tips above are not verified.

Rules:

- One file per app package, 2 KB at most.
- Files ship inside the APK, so every playbook passes pull-request review and is never downloaded at runtime.
- `PlaybookStore` rejects a file with missing fields, an oversized body or a blocked package. A rejected file fails the build tests.
- Playbooks describe navigation only. They cannot change `SafetyGate`, which is enforced in Kotlin.
- The loop adds a playbook as a mid-conversation system message, which leaves the cached prefix and earlier history unchanged.
- **Trust:** because a system message carries operator authority, a bundled playbook is trusted first-party content, not user input and not screen data. Every playbook pull request is reviewed with the same care as a code change, and the contributor guide says so.
- Tips must be checked on the stated app version before merging.

### LLM integration

- **Model:** chosen by the user in setup from provider presets, or typed in freely. The README recommends whichever models score best on the scoreboard. The Anthropic client sends `low` effort, set explicitly because Opus 5.5 otherwise defaults to `medium`. Spike 4 compares Claude models with at least one free OpenRouter model and one other provider.
- **Providers:** two clients sit behind `LlmClient`. `AnthropicClient` speaks the Messages API. `OpenAiCompatClient` speaks the Chat Completions format with a configurable base URL, API key and model name, which covers OpenRouter, OpenAI, Gemini's OpenAI-compatible endpoint, DeepSeek, Groq and local servers such as Ollama. Both use OkHttp and kotlinx.serialization with no provider SDK, which keeps the APK small and the HTTP code in one place. Spike 3 confirms this on a real phone.
- **Model requirements:** a model must support tool calling, and setup verifies this with a test request. Image input is optional: a per-provider `supportsImages` flag decides whether the `screenshot` tool is offered at all.
- **Tool choice:** `auto`. `strict: true` is used where the provider supports it. Forced tool choice is never used, because Opus 5.5 returns a 400 for it and other providers vary. The system prompt requires exactly one tool call per step, and a reply without one counts as an invalid call.
- **Parallel tools:** disabled where the provider allows it. If a model returns several calls anyway, the client keeps only the first, because phone actions must run in order.
- **History:** append-only, kept inside each client in the provider's own format. For Anthropic, thinking blocks are passed back unchanged. Trimming is decided per client, as described in section 5 of `2026-11-08-security-privacy-scenarios.md`.
- **Caching:** Anthropic only: prompt caching on the stable system prompt and tool list. Other providers cache automatically or not at all, and their clients do nothing special.
- **Refusals (Anthropic client):** check `stop_reason` before reading content. Server-side fallback is enabled with `fallbacks: "default"` and the beta header `server-side-fallback-2026-07-01`. A fallback model cannot read Opus 5.5 thinking blocks, so it continues without them. If a request is still refused, the task stops and the user is told why. Prompt caching needs no beta header. A content-filter finish reason from another provider maps to the same refusal result.
- **Validation:** every tool input is parsed and checked before use. An invalid call returns a tool error, and two invalid calls in a row stop the task.

Provisional cost, to be measured in spike 4: about $0.05 on Haiku 5.5, $0.30 on Sonnet and $0.60 on Opus for a full 25-step task. Free OpenRouter models and local models cost nothing per task but are expected to be less reliable, and the scoreboard will show by how much.

## 7. Safety and privacy

Screen content is untrusted. A message or web page can contain instructions aimed at the agent, so the following rules are enforced in Kotlin and cannot be changed by the model.

1. **Data boundary:** observations, including the screen saved at bubble tap, are wrapped and labelled as screen data. The system prompt says text inside them is never an instruction. "Do this" means the user's task applies to that data, not that the screen can issue tasks.
2. **Single user channel:** only the original task and `ask_user` answers count as user input.
3. **Block list:** the agent refuses to open or act in banking, payment-only and password-manager apps. It matches a built-in package list plus name patterns such as `bank` and `pay`. If a blocked app is in the foreground, the task stops.
4. **Risky actions need confirmation:** a tap is risky if its label or a nearby label matches terms such as pay, place order, buy, send, post, delete, remove, confirm, transfer or uninstall. English and Hindi terms ship first.
5. **The model can only raise risk:** `irreversible: true` forces a confirmation. Leaving it out never removes one.
6. **Coordinate taps are checked too:** a coordinate tap is matched to the node at that point. If no node is found, it needs confirmation in any app where a risky label is visible on screen.
7. **Password fields:** the agent never reads or types in them.
8. **Confirmations:** the card shows the action, target and app. No answer within 120 seconds counts as declining, and declining returns a tool error so the agent can choose another path or finish.
9. **Self-protection:** Pocket Agent's windows are excluded from observations, and any action targeting them is rejected.
10. **More rules:** rules 10 to 15 in section 3 of `2026-11-08-security-privacy-scenarios.md` apply equally. They block system Settings, installers and system UI, reject coordinate taps inside Pocket Agent's own windows, show typed text on confirmation cards, re-check targets before acting, confirm generic "OK" buttons near money, and restrict `open_app` to app names. That document also holds the threat model, privacy rules and scenario table, and wins wherever it disagrees with this one.

**Known limit:** label matching can miss unusual wording, other languages and icon-only buttons. The README must state this plainly.

**Privacy:**

- First run explains that screen text, and screenshots when requested, go directly to the provider the user chose, and nowhere else. With a custom base URL that is whatever server the user typed in, and the notice says so.
- No telemetry, no analytics, no backend.
- Nothing from the screen is stored after a task ends.
- The API key is encrypted with an Android Keystore key and never logged.

## 8. Setup

`SetupActivity` is a single checklist that detects each step's state and updates when the user returns:

1. Read the privacy notice.
2. Choose a provider preset (Anthropic, OpenRouter, OpenAI, Gemini, DeepSeek, Groq or Ollama) or enter a custom base URL, then paste an API key if the provider needs one.
3. Enter a model name. A test request verifies that the model answers and makes a tool call, and the screen says clearly if it does not.
4. Enable accessibility. If the switch is greyed out, the screen shows the phone-specific **Allow restricted settings** steps and opens the app info page.
5. Grant microphone access (optional).
6. Turn on the floating bubble.
7. Run a test task.

The README also documents the install steps for Android's developer verification changes, which may add an advanced flow or `adb` install in some countries.

## 9. Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin with coroutines |
| Minimum Android | 11 (API 30) |
| Build | Gradle Kotlin DSL, version catalog |
| Screens | Platform Android `View`s for setup and the task screen. No Compose and no Material library, to keep the APK small (decided 2026-11-08) |
| Bubble and cards | Android `View`s in `TYPE_ACCESSIBILITY_OVERLAY` windows |
| Quick Settings | Deferred to v0.2 (In-app toggle in setup for v0.1) |
| Voice | Android `SpeechRecognizer`, in the task screen |
| Key storage | Android Keystore with an encrypted local value |
| Tests | JUnit, fake `DevicePort`, fake `LlmClient`, recorded UI trees |

## 10. Demo tasks

These three tasks are the reliability target and the source of the launch video.

1. **Message:** "Reply to Mom on WhatsApp that I'll be late." The agent drafts the reply, then waits for confirmation before sending.
2. **Food order:** "Order my usual coffee on Swiggy." The agent builds the cart, then stops for confirmation before paying.
3. **On-screen task:** while viewing an address in WhatsApp, the user taps the bubble and says "Take me there." The agent opens Google Maps with that address and starts directions. This needs no risky action and is the opening shot of the launch video.

Test accounts or the user's own accounts are used only with the user's approval. Swiggy can be replaced with another food app if the user's region requires it.

## 11. Error handling

| Situation | Behaviour |
|---|---|
| Same screen after 3 actions | Stop and explain |
| Step limit reached | Stop and summarise progress |
| Screenshot blocked by a secure window | Continue from the tree; ask the user if the tree is not enough |
| Empty or canvas-only UI tree | Take a screenshot and use coordinate taps |
| Network error, 429 or 5xx | Two retries with backoff, then stop |
| 401 or invalid key | Stop and link to setup |
| Accessibility service disabled mid-task | Stop and notify the user |
| Phone locks | Stop and summarise progress |
| User long-presses bubble or turns off in setup | Cancel the task within 1 second |
| Second task during a run | Refused; the running task continues |

Every ending shows a summary card, and no failure is silent.

## 12. Testing

**Spikes, done first and thrown away unless stated:**

1. On a real phone, complete restricted-settings setup, including Android 15+ if available.
2. Check that WhatsApp, Swiggy and Google Maps expose usable UI trees.
3. From the phone, send a tool-calling request with OkHttp to Anthropic and to an OpenAI-compatible endpoint. This confirms the two-client design in section 6.
4. Measure latency and cost per step for Opus 5.5, Sonnet 5.5, Haiku 5.5, a free OpenRouter model and one other provider on the demo tasks. This decides which models the README recommends.
5. Confirm that `TYPE_ACCESSIBILITY_OVERLAY` works for the bubble and cards, and that the task screen can open from it.

**Automated tests:**

- `AgentLoop`: completion, step cap, cancellation, invalid tool calls and refusals, using fakes
- `SafetyGate`: table-driven tests, including prompt-injection text inside observations and missing `irreversible` flags
- `ScreenSerializer`: recorded UI trees, including password redaction and removal of Pocket Agent's windows
- `StuckDetector`: repeated and changing screens
- `PlaybookStore`: every bundled playbook is valid, and bad files are rejected
- Captions: labels are shortened, and password fields are never named
- On-screen tasks: a blocked app is refused before any model call

**Manual release checks:**

- The demo tasks reach the 9-of-10 bar.
- Confirmation cards appear for every risky action in the demo tasks.
- The stop gesture works during an LLM call and during a gesture.
- A fresh install completes setup on at least one real phone.

## 13. Delivery order

1. Run spikes 1 to 3, and stop to rethink the design if a blocker appears.
2. Build `DevicePort`, the accessibility service and the serializer.
3. Build the agent loop, the two provider clients and the safety gate, with their tests.
4. Add the bubble, captions, confirmation cards and task screen, including the saved screen at bubble tap.
5. Add playbook support and the first five playbooks.
6. Add setup and voice input.
7. Run spike 4, then tune the three demo tasks to the reliability bar.
8. Run the benchmark and publish the scoreboard.
9. Prepare the launch in section 15.

## 14. Risks

| Risk | Mitigation |
|---|---|
| Setup friction stops people trying it | Guided setup; the demo video still works for those who do not install |
| Demo apps change or block accessibility | Three tasks across different apps, with a replacement allowed |
| Prompt injection | Rules in section 7, with the limits documented |
| Latency makes the demo feel slow | Spike 4; an uncut video stays honest, so speed must be real |
| Weak or non-tool-calling models fail tasks and make the app look bad | Setup test checks tool calling; the scoreboard scores each model separately; the README recommends models that did well |
| A competitor ships the same experience first | Launch early; keep v0.1 small |
| Developer verification reaches all users in 2027 | Decide whether to register before the global rollout |
| Banking or payment apps are misclassified | Block by pattern as well as by list, and accept some over-blocking |
| Playbooks go stale as apps update | `tested_version` field, and the scoreboard shows when results were last checked |
| A malicious playbook is submitted | Pull-request review, bundled files only, and no ability to change `SafetyGate` |
| Nobody contributes playbooks | Ship five good examples, a short guide and starter issues |
| The launch gets little attention | Build a small audience first, and keep shipping visible updates after launch |

## 15. Launch and recognition

The aim is for people to remember the work and its author, as well as to star the repo.

### Scoreboard

`benchmarks/` holds a fixed list of tasks and a results file, published in the README as a table. Each row shows the app, task, successes out of 10, phone, Android version, model, app version and test date. Failures stay visible, and each model gets its own rows so people can see which models work well. Runs are manual in v0.1, and each result follows the written method in `benchmarks/README.md`.

### Assets

- An uncut video of about 30 seconds, opening with the on-screen task
- A README with the video, the pitch, install steps, the scoreboard and a plain safety section
- A contributor guide for playbooks, with starter issues
- A technical post on stopping screen content from hijacking a phone agent
- Open credit to phone-harness and other work that informed the project

### Sequence

1. **Before launch:** share short clips of real progress and failures, and invite a few people to test it.
2. **Launch day:** publish the APK, the video and a Show HN post.
3. **Launch week:** post on Reddit (r/LocalLLaMA and r/androiddev), on X, and in Indian developer communities.
4. **One week later:** publish the technical post with real numbers.
5. **After launch:** ship visible updates, starting with Hindi and Hinglish voice and a second LLM provider, and thank playbook contributors by name.

### Rules

- Never buy stars or use star-exchange schemes.
- Every published demo must be real and uncut, with its speed unedited or clearly labelled.
- Every scoreboard number must come from a recorded run.
- Follow each community's self-promotion rules.
