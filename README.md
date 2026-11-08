# Pocket Agent

Tap the bubble on anything. Say what to do. Your phone does it.

Pocket Agent is an open-source Android app: an AI agent that lives on your phone and operates your apps for you, with no computer, no root and no backend. You bring your own model and API key (Anthropic, OpenRouter, OpenAI, Gemini, DeepSeek, Groq, or a local server).

> **Status: work in progress.** There is nothing to install yet. This repository currently holds the design and the tested core of the agent.

The Anthropic client is verified against recorded replies only, until the live check has run with a real key (see [docs/TESTING.md](docs/TESTING.md)).

## Principles

- **You start every task.** No remote control, no scheduling, no background tasks.
- **Your screen goes only to the model you choose.** No telemetry, no analytics, nothing stored after a task.
- **Risky actions need your OK.** Paying, sending, posting and deleting always ask first, and banking apps are off limits.
- **Small and readable.** One module, a handful of dependencies, no native code.

The limits of these rules are written down in [the security and privacy design](docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md).

## Documents

- [Design](docs/superpowers/specs/2026-11-04-pocket-agent-design.md)
- [Security, privacy and scenarios](docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md)
- [Roadmap](docs/superpowers/plans/00-roadmap.md)
- [Feasibility spike findings](docs/spikes/2026-11-findings.md)

## Licence

[MIT](LICENSE)
