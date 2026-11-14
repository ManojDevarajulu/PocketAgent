# Changelog

All notable changes to Pocket Agent are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0] - November 2026

### Added
* **Computer Link (MCP Bridge)**: Opt-in local WebSocket MCP server over Wi-Fi, allowing desktop coding agents (Claude Code, Cursor) to dispatch tasks to the phone with on-device approval.
* **Record My Routine**: Manual tap recorder that drafts a clean, stripped markdown playbook (`assets/playbooks/<app>.md`) without typed text or personal data.
* **One-Step Intent Shortcuts**: Instant direct dispatch for maps directions, phone calls, SMS composition, web searches, alarms, timers, URLs, and calendar events.
* **Pixel-Grid Visual Grounding**: Real-pixel grid overlay for vision models when accessibility trees report sparse (<3) nodes on custom Canvas or Flutter screens.
* **Quick Settings Pull-Down Tile**: Access Pocket Agent instantly from Android's notification pull-down shade.
* **Save Tokens Mode**: Differential screen updates that send only changed or newly appeared nodes, reducing LLM token consumption.
* **Android 17 Compatibility**: Tested and verified on Android 16 and Android 17 emulators/devices.

### Changed
* Upgraded project license to **GNU Affero General Public License v3.0 (AGPL-3.0)**.
* Established community **Safety & Ethical Guidelines** ([ACCEPTABLE_USE.md](ACCEPTABLE_USE.md)).
* Added **Third-Party Notices** ([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)) covering bundled fonts and dependencies.

---

## [0.1.0] - November 2026

### Added
* **Autonomous Floating Bubble**: On-device overlay that executes accessibility actions over any Android app with zero PC and zero ADB.
* **Bring-Your-Own-Key (BYOK) Engine**: Support for Anthropic Claude, OpenAI GPT-4o, OpenRouter, Gemini, DeepSeek, Groq, and local offline Ollama endpoints.
* **Deterministic Safety Gate**: Hardware-backed Android Keystore AES-256 GCM key encryption, blocked banking/auth apps, and mandatory human confirmation for irreversible actions (*Pay*, *Send*, *Delete*, *Order*).
* **Hands-Free Voice Loop**: Speech-to-text input, spoken action captions, and voice confirmations with *Talk back*.
* **Private Notification Listener**: On-device OTP lookup with automatic 6-digit code masking (`••••••`) and safe notification replies.
* **Bundled App Playbooks**: Heuristic guides for WhatsApp, Google Maps, YouTube, Gmail, and Chrome.
