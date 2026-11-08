# Pocket Agent Roadmap

**Date:** 2026-11-08
**Method:** each sub-project goes spec, then review, then plan, then build. No sub-project is built before its spec is approved. The security rules in `specs/2026-11-08-security-privacy-scenarios.md` apply to all of them.

## Sub-projects

| # | Sub-project | Spec | Plan | Depends on | Exit gate |
|---|---|---|---|---|---|
| 0 | Toolchain and feasibility spikes | Done (main spec §12) | Plan 1, Tasks 1 to 2 | none | Findings say Go on Android 16 |
| 1 | Core agent: screen model, serializer, safety gate, loop, runner | Done, plus amendments A1 to A5 | Plan 1, Tasks 3 to 10 | 0 | JVM suite green in CI |
| 2 | Providers: Anthropic and OpenAI-compatible clients, presets, validation, history | **S2, to write** | Plan 2 | 1, spike 3 | Recorded-response tests pass for both formats; setup tool-call test works against a real model |
| 3 | Device layer: accessibility service, `DevicePort`, bubble, captions, confirmation card, task screen | **S3, to write** | Plan 3 | 1, spikes 1, 2, 5 | Demo task 3 (address to Maps) runs end to end on the test phone |
| 4 | Setup, key storage, notices, OEM guidance, voice | **S4, to write** | Plan 4 | 2, 3 | A fresh install reaches a finished first task using only the setup screen |
| 5 | Playbooks and benchmarks: contributor guide, CI checks, method, scoreboard | **S5, to write** | Plan 5 | 3, 4 | Five playbooks valid; three demo tasks at 9 of 10 or the failures published |
| 6 | Release and open source: repo standards, signing, pipeline, README, launch | **S6, to write** | Plan 6 | all | Security review done; signed release published |

## Spec tasks

Each spec is short and covers what the main spec leaves open.

- **S2 Providers:** wire formats for tool calling in both APIs, `ProviderConfig` validation, capability flags, presets, retry and error mapping, history policy, token accounting, and test strategy (MockWebServer with recorded responses).
- **S3 Device layer:** service configuration, tree conversion, window selection, settle logic, gestures, screenshots, overlay windows, re-validation (T3), tapjacking (T4, T5), service lifecycle, and multi-window behaviour.
- **S4 Setup:** the setup state machine, Keystore storage, notices, restricted-settings and OEM guidance, and voice.
- **S5 Playbooks and benchmarks:** the contributor guide, review checklist, benchmark method, and scoreboard format.
- **S6 Release:** repo files, CI, signing and key backup, provenance, README structure, demo recording, and launch checklist.

## Order of work

1. Plan 1 Task 1 (done) and Task 2 (spikes). Spike 3 waits for an API key.
2. Apply amendments A1 to A5, then Plan 1 Tasks 3 to 10.
3. Write S2 and S3 using the spike findings, review, then plans 2 and 3.
4. S4, then Plan 4. S5 and S6 can be written in parallel with plan 4.
5. Security review, scoreboard runs, release.
