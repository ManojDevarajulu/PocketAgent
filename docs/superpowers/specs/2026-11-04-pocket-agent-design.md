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

All of them are expected to be comfortable sideloading an APK and bringing their own Claude API key.

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
- Agent loop with Claude (Haiku, Sonnet, Opus)
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
- Gemini or other LLM providers (the `LlmClient` interface leaves room for them)
- Task history
- User-written permission rules
- MCP, share-sheet entry and scheduled tasks
- Hindi and Hinglish voice support (first priority after launch, with a second LLM provider)
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
        AgentLoop ──► LlmClient ──► ClaudeClient ──► Claude API
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
    llm/       LlmClient, ClaudeClient
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
| `ClaudeClient` | Implements `LlmClient` for the Claude API | Claude SDK or HTTP |
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
5. Send the observation to Claude.
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

- **Model:** `claude-opus-5-5` at `low` effort by default. Setup offers `claude-sonnet-5-5` and `claude-haiku-5-5` as cheaper, faster options. Spike 4 will evaluate Haiku as a primary candidate for near-zero cost and fast step latency.
- **API access:** use the official Java SDK if it builds and runs on Android 11. If spike 3 in section 12 fails, call the Messages API directly with OkHttp + Kotlinx.Serialization.
- **Tool choice:** `auto` with `strict: true` tools. Forced tool choice returns a 400 on this model, so the system prompt requires exactly one tool call per step.
- **Parallel tools:** disabled, because phone actions must run in order.
- **History:** append-only. Thinking blocks are passed back unchanged. The 25-step cap keeps the history small, so v0.1 does not trim it.
- **Caching:** prompt caching on the stable system prompt and tool list.
- **Refusals:** check `stop_reason` before reading content. Server-side fallback is enabled with `fallbacks: "default"` and the beta header `server-side-fallback-2026-07-01`. A fallback model cannot read Opus 5.5 thinking blocks, so it continues without them. If a request is still refused, the task stops and the user is told why. Prompt caching needs no beta header.
- **Validation:** every tool input is parsed and checked before use. An invalid call returns a tool error, and two invalid calls in a row stop the task.

Provisional cost, to be measured in spike 4: about $0.05 on Haiku 5.5, $0.30 on Sonnet and $0.60 on Opus for a full 25-step task.

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

**Known limit:** label matching can miss unusual wording, other languages and icon-only buttons. The README must state this plainly.

**Privacy:**

- First run explains that screen text, and screenshots when requested, go directly to Anthropic.
- No telemetry, no analytics, no backend.
- Nothing from the screen is stored after a task ends.
- The API key is encrypted with an Android Keystore key and never logged.

## 8. Setup

`SetupActivity` is a single checklist that detects each step's state and updates when the user returns:

1. Read the privacy notice.
2. Paste an API key, which is verified with one small request.
3. Choose a model.
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
| Screens | Jetpack Compose, for setup and the task screen |
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
3. Run the Java SDK on Android 11. This decides the API access in section 6.
4. Measure latency and cost per step for Opus 5.5, Sonnet 5.5 and Haiku 5.5 on the demo tasks. This confirms the default model.
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
3. Build the agent loop, Claude client and safety gate, with their tests.
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
| Claude-only support limits adoption | `LlmClient` interface; a second provider is the first post-launch item |
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

`benchmarks/` holds a fixed list of tasks and a results file, published in the README as a table. Each row shows the app, task, successes out of 10, phone, Android version, model, app version and test date. Failures stay visible. Runs are manual in v0.1, and each result follows the written method in `benchmarks/README.md`.

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
