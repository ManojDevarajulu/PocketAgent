# Changelog

All notable changes to Pocket Agent are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0] - November 2026

### Added
* **Computer Link (MCP Bridge)**: Opt-in local HTTP MCP server on your Wi-Fi (off by default, pairing code required), allowing desktop coding agents (Claude Code, Cursor) to dispatch tasks to the phone with on-device approval.
* **Record My Routine**: Manual tap recorder that drafts a playbook from the buttons you tap, without typed text, passwords or personal-looking labels. It is saved privately on the phone and copied so you can review it before sharing.
* **One-Step Intent Shortcuts**: Instant direct dispatch for maps directions, phone calls, SMS composition, web searches, alarms, timers, URLs, and calendar events.
* **Pixel-Grid Visual Grounding**: Real-pixel grid overlay for vision models when accessibility trees report sparse (<3) nodes on custom Canvas or Flutter screens.
* **Quick Settings Pull-Down Tile**: Access Pocket Agent instantly from Android's notification pull-down shade.
* **Lighter Mode (experimental, off by default)**: Sends the AI only what changed on the screen each step.
* **Android 17 Compatibility**: Tested and verified on Android 16 and Android 17 emulators/devices.

### Changed
* Upgraded project license to **GNU Affero General Public License v3.0 (AGPL-3.0)**.
* Established community **Safety & Ethical Guidelines** ([ACCEPTABLE_USE.md](ACCEPTABLE_USE.md)).
* Added **Third-Party Notices** ([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)) covering bundled fonts and dependencies.

---

## [0.1.0] - November 2026

### Added
* **Autonomous Floating Bubble**: On-device overlay that executes accessibility actions over any Android app with zero PC and zero ADB.
* **Bring-Your-Own-Key (BYOK) Engine**: Support for Anthropic Claude, OpenAI GPT-4o, OpenRouter, Gemini, DeepSeek, Groq, and Ollama running on your own machine.
* **Deterministic Safety Gate**: Android Keystore AES-256 GCM key encryption, blocked banking/auth apps, and mandatory human confirmation for irreversible actions (*Pay*, *Send*, *Delete*, *Order*).
* **Hands-Free Voice Loop**: Speech-to-text input, spoken action captions, and voice confirmations with *Talk back*.
* **Private Notification Listener**: On-device OTP lookup with automatic 6-digit code masking (`••••••`) and safe notification replies.
* **Bundled App Playbooks**: Heuristic guides for WhatsApp, Google Maps, YouTube, Gmail, and Chrome.
