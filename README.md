# Pocket Agent

**Tap the bubble on anything. Say what to do. Your phone does it.**

---

## What is Pocket Agent?

Most mobile AI agents require a computer running Python, `adb`, and a USB cable connected to your phone.

**Pocket Agent lives directly on your phone.** It is a standalone, open-source Android app that operates your phone autonomously through a floating overlay bubble—with **no computer, no `adb`, no root, and no cloud backend**.

You bring your own API key (Anthropic, OpenRouter, OpenAI, Gemini, DeepSeek, Groq, or a local Ollama server) and type the model name you want to use. Pocket Agent observes the active screen, decides what actions to take, and performs gestures natively through an Android `AccessibilityService`.

---

## Key Differentiators

* 🎯 **Contextual Trigger over Chat**: You don't leave your current app to open a chat window. Tap the bubble on whatever is already on screen, speak or type your instruction, and the agent acts immediately.
* ⚡ **Zero-PC, No Backend**: No tethered laptops, no background servers, no telemetry. The app runs on your phone and calls your chosen AI provider directly. Screen content is sent to that provider, so choose one you trust.
* 🛡️ **Deterministic Safety Gate**: Deterministic Kotlin rules enforce security before any action runs:
  * Sensitive apps (banking, payment, password managers, system settings) are **strictly blocked**.
  * Risky actions (*"Pay"*, *"Send"*, *"Delete"*, *"Order"*) pause execution and require explicit user confirmation.
  * Screen text is wrapped in non-executable data boundaries to prevent adversarial prompt injection.
  * API keys are encrypted locally using **Android Keystore AES-256 GCM** with `FLAG_SECURE` window protection.
* 📖 **Community Playbooks**: App navigation heuristics are written as simple 15-line Markdown files bundled inside the APK. Anyone can contribute a playbook for their favorite app via a GitHub Pull Request.

---

## Notifications (optional)

Turn on notification access and the agent can catch you up on messages and reply without opening apps. Off by default; without it everything works the slower way, through the apps.

* **What it reads:** the notifications currently in your shade, only while a task runs or when you tap **Latest OTP**. It has no background handler and keeps nothing.
* **What it skips:** banking and payment apps, bank and payment messages, its own and ongoing notifications, and anything older than 24 hours.
* **Codes stay on the phone:** verification codes are masked before the AI sees anything. **Latest OTP** shows the newest code from the last 10 minutes with no AI call.
* **Nothing is stored:** history shows "Summary hidden (it used your notifications)" for these tasks.
* **Replies are always confirmed:** you see the exact text first, and only a notification's reply action is ever used. Replies are limited to 200 characters on one line.
* **Turn it off:** Settings > Notification access.

Limits: bank detection is word-list based and can miss unusual wording; code masking needs a word like OTP or code nearby; codes visible on screen still go to the AI; emoji joined with zero-width joiners and Persian or Urdu text with zero-width non-joiners are refused in replies; and a draft may reply to your own last line in a chat. See the [Security Model](docs/SECURITY_MODEL.md).

---

## How It Works

```
[ Active Screen ] 
       │
       ▼ (Tap Floating Bubble)
[ Screen Node Capture & Sanitizer ] ──► Password & system UI redacted
       │
       ▼ (Mid-conversation injection)
[ System Prompt + Bundled Playbook ] 
       │
       ▼
[ AI Provider (Anthropic / OpenAI-compatible) ]
       │
       ▼ (Strict Single-Tool Call)
[ Deterministic Safety Gate ] ───────► Blocked app? -> Abort
       │                               Risky action? -> User Confirmation Card
       ▼ (Approved)
[ Native Accessibility Action ] ─────► Click / Scroll / Type / Navigate
       │
       ▼
[ Screen Settle & Loop Repeat ] ─────► Max 25 steps | Stuck detection
```

---

## Supported Providers (BYOK)

Pocket Agent communicates directly with provider endpoints using a hardened OkHttp transport (redirects disabled, 90s call timeout, 2MB response cap, API key validation and masking):

| Provider             | Type                       | Notes                                          |
| -------------------- | -------------------------- | ---------------------------------------------- |
| **Anthropic**  | Messages API               | Prompt caching and thinking-block preservation |
| **OpenRouter** | OpenAI-compatible          | Text-only by default                           |
| **OpenAI**     | OpenAI-compatible          | Image input on by default                      |
| **Gemini**     | OpenAI-compatible endpoint | Image input on by default                      |
| **DeepSeek**   | OpenAI-compatible          | Text-only by default                           |
| **Groq**       | OpenAI-compatible          | Text-only by default                           |
| **Ollama**     | OpenAI-compatible, local   | No API key needed; plain `http`              |
| **Custom**     | OpenAI-compatible          | Any base URL you enter                         |

**Choosing a model:** Pocket Agent does not ship a fixed model list. You type the exact model name from your provider during setup, and the model must support **tool calling**. Tap **"Test the model"** to check it with one small request.

Plain `http` is only accepted for `localhost`, `.local` names and private network addresses (`10.x`, `172.16–31.x`, `192.168.x`, `127.x`, `100.64–127.x`). Every other provider must use `https`.

---

## Project Structure & Architecture

Pocket Agent is built as a single Gradle module with clean architectural decoupling:

```text
app/src/main/
  assets/playbooks/        Bundled Markdown navigation guides per app package
  kotlin/dev/pocketagent/
    agent/                 AgentLoop, AgentRunner, StuckDetector, Tools, Captions
    device/                DevicePort, ScreenNode, ScreenSerializer, Settler, Revalidator
    llm/                   AnthropicClient, OpenAiCompatClient, OkHttpTransport, ProviderConfig, ProviderPresets
    safety/                SafetyGate, BlockedApps, RiskyLabels
    playbook/              Playbook, PlaybookStore
    setup/                 SetupModel, StoredConfigRepository
    android/               PocketAccessibilityService, AccessibilityDevice, ScreenReader
    ui/                    BubbleOverlay, CaptionView, ConfirmCard, SummaryCard, SetupActivity
```

> **Architecture Guarantee**: Packages `agent`, `device`, `llm`, `safety`, `playbook`, and `setup` contain **zero Android framework imports**. All decision logic is 100% unit-tested on the JVM with fakes.

---

## Quick Start: Building & Running

### Prerequisites

* Android phone running **Android 11 or newer** (API 30+). Tested on Android 16.
* Java 17+ / Android Studio SDK with Build-Tools.

### 1. Clone the repository

```bash
git clone https://github.com/ManojDevarajulu/PocketAgent.git
cd PocketAgent
```

### 2. Run the test suite

```bash
./gradlew testDebugUnitTest
```

### 3. Build & Install Debug APK

Connect your phone with USB debugging enabled:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Complete Onboarding Setup

1. Open **Pocket Agent** from your app launcher.
2. Follow the guided setup checklist:
   * Read the privacy notice.
   * Select your AI provider and paste your API key.
   * Type your model name and tap **"Test the model"** to verify tool calling.
   * Enable the **Accessibility Service** *(if Android 13–16 shows "Restricted setting", tap the 3-dot menu in App Info $\rightarrow$ "Allow restricted settings")*.
   * Grant optional microphone access for voice tasks.
   * Optionally turn on notification access (Android settings opens; switch Pocket Agent on).
   * Toggle the **Floating Bubble** ON.

---

## Contributing

Contributions are welcome, including app playbooks (short Markdown navigation guides). See [CONTRIBUTING.md](CONTRIBUTING.md) for the build steps, playbook format and rules. Report security issues privately as described in [SECURITY.md](SECURITY.md).

---

## Documentation

* [Architecture](docs/ARCHITECTURE.md): agent loop, accessibility tree serializer and overlays
* [Security Model](docs/SECURITY_MODEL.md): threat model, blocked apps and key storage
* [Testing](docs/TESTING.md): unit tests and the physical-device checklist

---

## Licence

Licensed under the [MIT Licence](LICENSE).
Copyright (c) 2026 Pocket Agent contributors.
