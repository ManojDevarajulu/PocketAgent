# Pocket Agent: Security, Privacy and Scenarios

**Status:** approved 2026-11-08
**Date:** 2026-11-08
**Applies to:** the v0.1 design (`2026-11-04-pocket-agent-design.md`). Where this document disagrees with it, this document wins. Section 10 lists every change to existing documents.

An agent that can read and operate a phone is a powerful thing to install. The project's reputation depends on being the one people can trust, so this document treats security and privacy as the product, not as a checklist.

## 1. Principles

1. **User-initiated only.** No task starts without a touch on the bubble. There is no remote channel, no scheduling, no background task, no hidden mode, and no way for another app to start one.
2. **Least data.** Only the current step's screen text, and a screenshot only when asked for, leaves the phone, and only to the provider the user configured. Nothing is stored after a task.
3. **Enforce in Kotlin, never in the prompt.** The model proposes. Plain code decides.
4. **Show before you do.** A confirmation shows the exact content that will be sent, not just the word "Send".
5. **Fail closed.** When something is unknown, ask the user or stop.
6. **Few dependencies, one door to the network.** Only the `llm` package touches the network. No telemetry, analytics or crash reporter.
7. **Say the limits plainly.** The README lists what the safety rules cannot catch.

## 2. Threats and mitigations

| ID | Threat | Mitigation | Verified by |
|---|---|---|---|
| T1 | **Prompt injection** from screen content: messages, web pages, notifications, contact names, content descriptions, text inside screenshots | Data wrapper with tag escaping, single user channel, every rule enforced in Kotlin (spec §7) | JVM: a corpus of injection strings run through the serializer and `SafetyGate` |
| T2 | **Injected data leaves through the agent's own actions** (it types an OTP or a private message into a "send" box) | Every send, post or pay confirmation shows the destination and the exact text typed. v0.1 has no clipboard, URL or file tool | JVM: confirm request carries the typed text |
| T3 | **Screen changes between read and act** ("Add to cart" becomes "Pay") | `DevicePort.perform` re-checks the target's label and bounds against the observation. A mismatch fails the action and the gate runs again on the fresh label | JVM with fakes; device spike |
| T4 | **The agent taps its own confirmation card** by coordinates | A tap inside any Pocket Agent window's bounds is rejected. Confirm buttons also ignore touches the platform flags as injected by an accessibility gesture, if spike 5 shows the flag is available | JVM; spike 5 |
| T5 | **Another app draws over the card or bubble** | `filterTouchesWhenObscured` on confirm buttons. The app name on the card comes from the gate, never from the model | Manual |
| T6 | **The agent opens Settings, an installer, a permission dialog or system UI** to disable its own service, grant permissions or install apps | Those packages are on the block list. "install" is a risky label | JVM table test |
| T7 | **API key theft or leakage** | Keystore AES-GCM, no backup, never logged, masked after saving, `FLAG_SECURE` on the key screen, sent only as a header to the configured host, never in a URL | JVM; manifest test |
| T8 | **Bad endpoint:** plain HTTP over the internet, redirect to another host, oversized response, look-alike URL with embedded credentials | Provider validation and limits (section 5) | JVM |
| T9 | **Another app starts tasks or reads data** | No exported component except the launcher activity. The service requires `BIND_ACCESSIBILITY_SERVICE`. No receivers, providers or deep links | JVM manifest test |
| T10 | **Covert use as stalkerware** | Principle 1, plus a manifest test that no receiver or background service exists | JVM manifest test |
| T11 | **Supply chain:** dependencies, CI, release signing, malicious playbook pull request | Section 8 | CI |
| T12 | **Voice audio sent to a third party** | Prefer on-device recognition (`EXTRA_PREFER_OFFLINE`). The notice says the system recognizer may use the cloud | Manual |
| T13 | **Runaway cost or loops** | 25 steps, per-call `max_tokens`, 90 s call timeout, token use shown on the summary card | JVM |
| T14 | **Screen data lingers** in logs, crash dumps, recents thumbnails, backups or memory | No screen text in logs, a `Log` wrapper that does nothing in release builds, no crash reporter, saved tree dropped at task end, backups disabled | JVM architecture test |

## 3. Safety rules added to spec §7

10. **Sensitive system apps are blocked** like banking apps: system Settings, package installers, the permission controller, system UI (notification shade and quick settings) and device-admin screens. A task that lands there stops.
11. **A coordinate tap inside Pocket Agent's own windows is rejected**, using bounds the screen model keeps for those windows.
12. **Confirmation shows the content.** `ConfirmRequest` gains a `detail` field holding the last text typed in this task (200 characters at most). It lives in memory and is dropped at task end.
13. **Re-validate before acting** (T3). A stale or changed target returns `ActionResult.Failed("screen changed")`.
14. **Affirmative buttons near money need confirmation.** If a tapped label is a generic affirmative (OK, Yes, Continue, Proceed, Done, Submit) and the screen shows a currency symbol or the words total, order, pay, amount, the tap needs confirmation.
15. **`open_app` takes an app name only.** It is matched against the installed launcher apps. The model cannot supply package names, URLs or intents. An ambiguous name triggers `ask_user`.

## 4. Privacy

**Data flow**

| Data | Goes to | When | Kept |
|---|---|---|---|
| Task text, screen text | The configured provider | Each step | Not by the app. The provider's own policy applies |
| Screenshot (shrunk to 1280 px on the long side) | The configured provider | Only if the model asks and supports images | Not by the app |
| Voice audio | The system speech recognizer | When the user taps the mic | Not by the app |
| API key | Request header to the configured host | Each request | Encrypted on the phone |
| Anything else | Nowhere | Never | Not applicable |

**Permissions allowlist:** `INTERNET`, and `RECORD_AUDIO` (optional, requested at runtime). No contacts, SMS, storage, location, overlay permission or `QUERY_ALL_PACKAGES`. App names come from a `<queries>` launcher intent.

**Manifest:** `allowBackup="false"` plus data-extraction rules that exclude everything. No exported component except the launcher activity. The accessibility service requests only window content, gestures and screenshots. It never asks to filter key events, and it subscribes to no events while idle.

**Notices:** first run explains where data goes. Choosing a custom base URL shows who will receive the screen text. The voice step explains the system recognizer.

**Logging:** release builds log nothing from the screen, the model, or the key. Debug logging is off by default and never prints the key.

## 5. Provider hardening

- **URL rules** (`ProviderConfig.validate`): `https` required. Plain `http` only for `localhost`, `*.local` and private ranges (10/8, 172.16/12, 192.168/16, 100.64/10), with a visible warning. Reject URLs with embedded credentials and any other scheme.
- **Auth:** the key goes only in a header, to the configured host. No query-string keys. Redirects are disabled.
- **Limits:** connect timeout 15 s, whole call 90 s, response body capped at 2 MB, `max_tokens` capped per call.
- **Errors:** messages never contain the key or screen text.
- **History:** each client owns its history. A client for a small-context provider replaces older observations with a one-line stub when the estimate passes 70% of the configured context size. The Anthropic client does not edit earlier turns, because thinking blocks must stay valid, and its context is large enough for 25 steps. The serializer cap is configurable and defaults to 12,000 characters. This replaces the spec's "v0.1 does not trim".

## 6. Scenarios

**Device and OS**

| Scenario | Behaviour |
|---|---|
| Android 11 to 16, OEM skins that kill background services | Detect service disconnect. Setup shows battery and autostart steps per OEM |
| Advanced Protection or restricted settings blocks the service | Setup shows the cause and the fix, or says plainly that the app cannot run in that mode |
| Rotation, split screen, PiP, foldables, font and display scaling, dark mode, gesture or 3-button navigation | Always read a fresh tree and use its bounds, never coordinates cached across observations |
| Work profile, second user, cloned apps | Act only in the user the service runs in. Block list matches by package |
| Phone locks, a call arrives, notification shade opens | Lock: stop with a summary. Shade: blocked foreground (rule 10) |

**Apps**

| Scenario | Behaviour |
|---|---|
| Slow loading, spinners, skeleton screens | Settle wait, `wait` tool, stuck detector |
| WebView, Flutter or canvas apps with sparse trees | Screenshot and coordinate taps if the model supports images, otherwise ask the user or stop |
| App refuses to run with accessibility on | Empty tree: stop with a plain message |
| Login walls, CAPTCHA, OTP prompts | `ask_user`. The agent never tries to solve a CAPTCHA |
| Crash, app update changes the UI | Stuck detector and step cap; playbook `tested_version` flags stale tips |
| Two contacts or items with the same name | `ask_user` |
| App language differs from the user's | The model translates. Risky labels are English and Hindi only, which the README states |
| Icon-only buttons | Content descriptions are used. With no label, a coordinate tap needs confirmation if any risky label is visible |

**Model and network**

| Scenario | Behaviour |
|---|---|
| Offline, captive portal, DNS failure | Two retries, then stop with "check your connection" |
| 401 or 403 | Stop and link to setup |
| 429 | Honour `Retry-After` up to 30 s, then stop |
| 5xx | Two retries with backoff, then stop |
| No tool call, unknown tool, invented node ID, bad arguments | Invalid-call counter; two in a row stop the task |
| Model without tool calling | Setup test catches it. At runtime, stop with an explanation |
| Context length exceeded | Observation cap and history stubs (section 5). If still exceeded, stop |
| Refusal or content filter | Stop with the explanation |
| Reply cut off by `max_tokens` | Counts as an invalid call |
| Huge or malformed response | Size cap, parse error ends the task with `LLM_ERROR` |

**User**

| Scenario | Behaviour |
|---|---|
| User touches the phone while the agent works | The agent re-reads each step. Long-press stops it. Touch is never blocked |
| Stop during a model call, a gesture or a confirmation | Cancel within 1 s. A pending action never runs |
| Empty or very long task text | Empty rejected, capped at 2,000 characters |
| Confirmation unanswered | Declined after 120 s |
| System kills the app mid-task | Nothing resumes. The next start is idle |
| Microphone permission denied | Text input still works |
| Part of a multi-step task done, then declined | The summary lists exactly what was done |

## 7. Staying lite

| Budget | Target |
|---|---|
| Release APK | 3 MB or less with platform Views, 8 MB or less if Compose stays. CI fails the build over budget |
| Runtime dependencies | Four: kotlinx-coroutines-android, kotlinx-serialization-json, okhttp, androidx-core-ktx. A test fails if another appears |
| Native code, Google Play services, analytics, crash SDKs | None |
| Background work | None: no foreground service, WorkManager or receivers |
| Idle cost | The service subscribes to no events until a task starts |
| Memory | Saved tree and history dropped at task end |
| Codebase | One module. Core packages stay free of Android types |

## 8. Open-source standards

**Repository:** README (demo, install, safety, privacy, scoreboard), LICENSE (MIT), `SECURITY.md` with private reporting, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `docs/PLAYBOOKS.md`, `docs/ARCHITECTURE.md`, `docs/THREAT_MODEL.md` (a public version of sections 2 and 3), `docs/PRIVACY.md`, `CHANGELOG.md`, issue and pull-request templates, `CODEOWNERS` covering `safety/` and `assets/playbooks/`.

**CI:** unit tests, Android Lint, APK size check, dependency allowlist, manifest allowlist. GitHub Actions are pinned by commit SHA, with read-only default permissions and no secrets for forks.

**Dependencies:** pinned in the version catalog, Gradle dependency verification enabled, Dependabot on.

**Release:** a signed APK, its SHA-256, and build provenance attestation, published on GitHub Releases. The signing key never enters the repo. Its fingerprint is in the README so users can check it with `apksigner`. The key is backed up in two places, because losing it means users can never update.

**Process:** small commits in the imperative mood, SemVer 0.x, protected `main`, reviewed pull requests, and starter issues labelled for newcomers.

## 9. Verification

- **JVM tests:** every rule in sections 3 and 5, an injection corpus, the manifest and dependency allowlists, and architecture guards: only `llm` may import the network library, and no direct `Log` or `println` calls.
- **Device checklist** (`docs/TESTING.md`): each T-row marked "manual" or "spike", on the Android 16 test phone, plus a second phone or emulator on Android 11 if available.
- **Before release:** run a security review of the whole code base, and publish the scoreboard with failures.

## 10. Changes to existing documents

**Main spec:** §7 gains rules 10 to 15. §6 drops "v0.1 does not trim" in favour of section 5. §9 changes if the Compose decision below goes ahead. The privacy notice follows section 4.

**Plan 1 amendments** (applied after approval):

- **A1, Task 3:** extend `ArchitectureTest` with the network, logging, manifest and dependency guards.
- **A2, Task 4:** `Screen` gains `ownWindowBounds: List<Bounds>`. `DevicePort.perform` documents the re-validation contract.
- **A3, Task 5:** block list gains the system packages. "install" joins the risky labels. Gate gains rules 11 and 14.
- **A4, Task 8:** `open_app` stays name-only, with a test.
- **A5, Task 9:** `ConfirmRequest` gains `detail`, with a test that it carries the last typed text.

**Decisions (all three approved 2026-11-08, recommendations accepted)**

1. **Plain Views or Compose.** I recommend plain Views. The app has two simple screens and the bubble already needs Views, so Compose would add 4 to 5 MB for little gain. Compose is friendlier to some contributors, so say if you want it.
2. **Block Settings, system UI and installers outright.** I recommend yes. The cost is that the agent cannot toggle Wi-Fi or change a setting for you, which is the safer default for v0.1.
3. **Do not declare the service an accessibility tool.** Honest, but Android 16 Advanced Protection may then refuse to run it. Spike 1 will show whether that applies, and the README will say so.
