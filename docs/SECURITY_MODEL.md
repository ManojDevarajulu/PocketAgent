# Security model

An agent that can read and operate a phone is powerful, so security and privacy are part of the product.
To report a vulnerability, follow [SECURITY.md](../SECURITY.md).

## Principles

1. **User-initiated only.** No task starts without a touch on the bubble. No remote channel, scheduling,
   background task or way for another app to start one.
2. **Least data.** Only the current step's screen text, and a screenshot only when the model asks, leaves the
   phone, and only to the provider the user configured. Nothing is kept after a task.
3. **Enforce in Kotlin, never in the prompt.** The model proposes; plain code decides.
4. **Show before you do.** A confirmation shows the exact content that will be sent.
5. **Fail closed.** When something is unknown, ask the user or stop.
6. **One door to the network.** Only the `llm` package talks to the network. No telemetry.
7. **State the limits.** Label matching can miss unusual wording, other languages and icon-only buttons.

## Threats and mitigations

| Threat | Mitigation |
| --- | --- |
| Prompt injection from screen content (messages, web pages, notifications, contact names) | Screen text is wrapped and labelled as data. Only the user's task and `ask_user` answers count as user input. Every rule is enforced in Kotlin |
| Injected data leaves through the agent's own actions | Confirmation cards show the destination and the exact typed text. There is no clipboard, URL or file tool |
| The screen changes between read and act | The target's label and bounds are re-checked before acting; a mismatch fails the action |
| The agent taps its own confirmation card | Coordinate taps inside Pocket Agent's windows are rejected |
| Another app draws over the card or bubble | Confirm buttons ignore obscured touches; the app name on the card comes from the gate, not the model |
| The agent opens Settings, an installer or system UI to disable itself | Those packages are on the block list; "install" is a risky label |
| API key theft or leakage | Keystore encryption, no backup, never logged, never in a URL (see below) |
| Bad endpoint (plain HTTP, redirects, oversized replies, look-alike URLs) | Provider validation and limits (see below) |
| Another app starts tasks or reads data | No exported component except the launcher activity; the service requires `BIND_ACCESSIBILITY_SERVICE`; no receivers, providers or deep links |
| Stalkerware use | User-initiated only, and a manifest test that no receiver or background service exists |
| Runaway cost or loops | 25 steps, per-call `max_tokens`, 90 s call timeout |
| Screen data lingering in logs, backups or memory | No screen text in logs, no crash reporter, backups disabled, saved tree dropped at task end |

## Safety gate rules

`SafetyGate` decides allow, block or confirm for every action:

- **Blocked apps.** The agent refuses to open or act in banking, payment, wallet, UPI and password-manager
  apps, and in system Settings, package installers, the permission controller and System UI. A task that
  lands in one stops. Over-blocking is accepted.
- **Risky actions need confirmation.** A tap is risky if its label or a nearby label matches terms such as
  pay, buy, place order, send, post, delete, remove, confirm, transfer, install or uninstall (English and Hindi).
- **The model can only raise risk.** `irreversible: true` forces a confirmation; omitting it never removes one.
- **Coordinate taps are checked.** A tap is matched to the node at that point. With no node, it needs
  confirmation whenever a risky label is visible. On a screen with no readable labels, every coordinate tap
  needs confirmation.
- **Generic buttons near money.** OK, Yes, Continue, Submit and similar need confirmation when the screen
  shows a currency symbol or words like total, order, pay or amount.
- **Password fields** are never read or typed into.
- **Confirmations time out.** No answer within 120 s counts as declining; declining returns a tool error.
- **`open_app` takes an app name only,** matched against installed launcher apps.

### Blocked-app list

Matching is by package, by app name, and by word-start patterns (`bank`, `pay`, `wallet`, `upi`, `password`)
on the name and on each part of the package. The built-in list lives in
`app/src/main/kotlin/dev/pocketagent/safety/BlockedApps.kt` and currently includes:

- **Payments and wallets:** Google Pay, Paytm, PhonePe, BHIM/UPI apps, Google Wallet
- **Passwords and authenticators:** Bitwarden, 1Password, LastPass, Google Authenticator
- **System screens:** Settings, System UI, package installers, permission controller

## API key storage

- The key is encrypted with **AES-256-GCM** using a non-exportable key in the **Android Keystore**. There is no
  plaintext fallback: if secure storage fails, saving fails.
- Only the ciphertext is stored. App backup is disabled and data-extraction rules exclude everything.
- The key is never logged, never placed in a URL, and shown masked after saving ("Key saved", no characters).
- It is sent only as a request header to the configured host.
- The setup screen sets `FLAG_SECURE` on non-debuggable builds, so it cannot be screenshotted.
- Clearing the app's data removes the key; setup then asks for it again.

## Provider hardening

- `https` is required. Plain `http` is allowed only for `localhost`, `*.local` and loopback or private
  ranges, with a visible warning. URLs with embedded credentials and other schemes are rejected.
- Redirects are disabled. Connect timeout 15 s, whole call 90 s, response body capped at 2 MB.
- Error messages never contain the key or screen text.
- A custom base URL shows a warning naming the real host that will receive screen text, and asks again if
  the host changes.

## Privacy

| Data | Goes to | Kept |
| --- | --- | --- |
| Task text and screen text | The configured provider, each step | Not by the app; the provider's policy applies |
| Screenshot (max 1280 px) | The configured provider, only if the model asks | Not by the app |
| Voice audio | The system speech recognizer, when the mic is tapped | Not by the app |
| API key | Request header to the configured host | Encrypted on the phone |
| Anything else | Nowhere | n/a |

**Permissions:** `INTERNET` and optional `RECORD_AUDIO` only. No contacts, SMS, storage, location, overlay
or `QUERY_ALL_PACKAGES`. The accessibility service subscribes to no events while idle.

## Known limits

- Label matching can miss unusual wording, other languages and icon-only buttons. Risky labels are English
  and Hindi only.
- A pending confirmation card can still appear on the lock screen if the phone locks while the agent is asking.
- The screen text goes to whichever provider the user configures, including a custom server they type in.
