# Pocket Agent

**Tap the bubble on anything. Say what to do. Your phone does it.**

---

## What is Pocket Agent?

Most mobile AI agents require a computer running Python, `adb`, and a USB cable connected to your phone. 

**Pocket Agent lives directly on your phone.** It is a standalone, open-source Android app that operates your phone autonomously through a floating overlay bubble—with **no computer, no `adb`, no root, and no cloud backend**.

You bring your own API key (Anthropic Claude, OpenRouter, OpenAI, Groq, DeepSeek, or local Ollama). Pocket Agent observes the active screen, decides what actions to take, and performs gestures natively through an Android `AccessibilityService`.

---

## Key Differentiators

* 🎯 **Contextual Trigger over Chat**: You don't leave your current app to open a chat window. Tap the bubble on whatever is already on screen, speak or type your instruction, and the agent acts immediately.
* ⚡ **100% On-Device & Zero-PC**: No tethered laptops, no background servers, no telemetry. Direct client-side HTTP calls to your chosen AI provider.
* 🛡️ **Hardware-Backed Safety Gate**: Deterministic Kotlin rules enforce security before any action runs:
  * Sensitive apps (banking, payment, password managers, system settings) are **strictly blocked**.
  * Risky actions (*"Pay"*, *"Send"*, *"Delete"*, *"Order"*) pause execution and require explicit user confirmation.
  * Screen text is wrapped in non-executable data boundaries to prevent adversarial prompt injection.
  * API keys are encrypted locally using **Android Keystore AES-256 GCM** with `FLAG_SECURE` window protection.
* 📖 **Community Playbooks**: App navigation heuristics are written as simple 15-line Markdown files bundled inside the APK. Anyone can contribute a playbook for their favorite app via a GitHub Pull Request.
* 🎨 **Bespoke 60-30-10 Brand Identity**: Designed with a sleek, AMOLED-friendly dark palette:
  * **60% Preto Meia-Noite (`#090A10`)**: Deep obsidian canvas and glass cards.
  * **30% Azul Eléctrico (`#1E6FFF`)**: Pulsing active energy rings and structural controls.
  * **10% Laranja Cantalupo (`#FF8544`)**: High-contrast focal points, AI aperture sparkle, and confirmation alerts.

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
[ AI Provider (Claude / OpenRouter / OpenAI) ]
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

Pocket Agent communicates directly with provider endpoints using a hardened OkHttp transport (redirects disabled, 90s timeout, 2MB body cap, key sanitization):

| Provider | Models Supported | Notes |
|---|---|---|
| **Anthropic** | Claude 3.5 Sonnet, Claude 3.5 Haiku, Claude 3 Opus | Full prompt caching & thinking block preservation |
| **OpenRouter** | Any tool-calling model (Llama 3.3, Mistral, Qwen, etc.) | OpenAI-compatible endpoint with automatic context trimming |
| **OpenAI** | GPT-4o, GPT-4o-mini | Standard Chat Completions API |
| **Groq / DeepSeek** | Fast tool-calling models | Ultra-low step turnaround latency |
| **Local / Custom** | Local Ollama, vLLM, or private servers | Allowed over local network (`localhost`, `10.x`, `192.168.x`) |

---

## Project Structure & Architecture

Pocket Agent is built as a single Gradle module with clean architectural decoupling:

```text
app/src/main/
  assets/playbooks/        Bundled Markdown navigation guides per app package
  kotlin/dev/pocketagent/
    agent/                 AgentLoop, AgentRunner, StuckDetector, Tools, Captions
    device/                DevicePort, ScreenNode, ScreenSerializer, Settler, Revalidator
    llm/                   AnthropicClient, OpenAiCompatClient, OkHttpTransport, ProviderConfig
    safety/                SafetyGate, BlockedApps, RiskyLabels
    playbook/              Playbook, PlaybookStore
    setup/                 SetupModel, ConfigRepository
    android/               PocketAccessibilityService, AccessibilityDevice, ScreenReader
    ui/                    BubbleOverlay, CaptionView, ConfirmCard, SummaryCard, SetupActivity
```

> **Architecture Guarantee**: Packages `agent`, `device`, `llm`, `safety`, and `playbook` contain **zero Android framework imports**. All decision logic is 100% unit-tested on the JVM with fakes.

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
   * Tap **"Test Model"** to verify tool calling.
   * Enable the **Accessibility Service** *(if Android 13–16 shows "Restricted setting", tap the 3-dot menu in App Info $\rightarrow$ "Allow restricted settings")*.
   * Grant optional microphone access for voice tasks.
   * Toggle the **Floating Bubble** ON.

---

## Contributing App Playbooks

Playbooks are simple Markdown files that teach Pocket Agent how to navigate a specific app effectively.

To contribute a playbook for an app:
1. Create a file in `app/src/main/assets/playbooks/<package_name>.md` (e.g. `com.google.android.youtube.md`).
2. Add metadata frontmatter and concise navigation tips:

```markdown
---
app: YouTube
package: com.google.android.youtube
tested_version: "19.0"
---

## Tips
- Search button is in the top right header.
- The first video result starts playback immediately upon tapping.
```

3. Keep it under 2 KB, test it on your device, and submit a Pull Request!

---

## Documentation

* [Product Design Spec](docs/superpowers/specs/2026-11-04-pocket-agent-design.md)
* [Security, Privacy & Threat Model](docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md)
* [Master Roadmap](docs/superpowers/plans/00-roadmap.md)
* [Android 16 Feasibility Spike Findings](docs/spikes/2026-11-findings.md)
* [Device Checklist & Testing Guide](docs/TESTING.md)

---

## Licence

Licensed under the [MIT Licence](LICENSE).  
Copyright (c) 2026 Pocket Agent contributors.
