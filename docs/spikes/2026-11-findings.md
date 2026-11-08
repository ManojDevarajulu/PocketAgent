# Feasibility spike findings (2026-11-08)

**Test phone:** Nothing phone, Android 16 (API 36), India region, with a work profile, a Private Space and DualApps installed.
**Spike app:** throwaway, kept out of the repo. Source lived in `spikes/spike-app/`.

## Verdict: Go, with three conditions

Accessibility can be enabled, two of the three demo apps expose usable screens (the third partly), and the bubble and task screen work. Conditions:

1. ~~Spike 3.~~ The OpenAI-compatible side passed. The Anthropic side and the first on-phone call are still to be checked, in Plan 2's integration test.
2. ~~Check Swiggy's cart.~~ Done: it is unreadable. Demo task 2 must change before it is built (see spike 2).
3. The install route must be tested before the launch docs are written (spike 1).

## Spike 1: restricted settings and installing

| Observation | Result |
|---|---|
| Install an APK that declares an accessibility service from the Google Files app | **Hard-blocked by Play Protect**, with no way past |
| Install the identical app without the service from the same app | Installs normally |
| Install the accessibility build with `adb install` | Works |
| Enabling the service after an `adb` install | Refused until "Allow restricted settings" is allowed in App info. `adb` does not skip this step |

**Cause:** consistent with Google's enhanced fraud protection pilot, which blocks sideloaded apps that request accessibility in India. One device and one run, so treat it as likely, not proven.

**Decisions:**
- The README cannot say "download the APK and install it". It needs a routes section: `adb install`, and an app-store route if one is exempt.
- The release spec (S6) tests F-Droid or IzzyOnDroid, and verified-developer registration, on this phone before promising either.
- The setup screen must walk users through "Allow restricted settings", with the phone-specific path.
- No Advanced Protection message was reported on this phone.

## Spike 2: screen trees

| App | Result |
|---|---|
| WhatsApp | Rich: about 150 nodes on the chat list and 47 in a chat, with labels and stable resource IDs. Message box is editable |
| Google Maps | Usable: 43 nodes, 16 labelled, including "Search here" and "Voice search". The map itself is unlabelled |
| Swiggy | Partial. Browsing and the item sheet are readable ("Add Item \| ₹249", "add one more"). The cart and checkout screen returned a single empty node with no labels on all three tries, so "Place order" is invisible to the agent |
| Settings | Readable, so blocking by package works |
| Pocket Agent's own windows | Captured too, so excluding them is mandatory |

**Decisions:**
- The serializer lets an unlabelled tappable node borrow the label of its nearest labelled ancestor, shown as `in "<label>"` (Plan 1, Task 4).
- Screen text can carry account details. Maps exposes the signed-in email, so the privacy notice says so.
- Swiggy's cart and checkout cannot be read (confirmed three times). Demo task 2 either stops at the cart so the user pays, or moves to another app. Zomato is installed on the test phone and is the first one to check.
- An unreadable screen means the safety gate cannot recognise a risky button. Every coordinate tap on a screen with no labelled nodes needs confirmation (security spec rule 16).

## Spike 3: model call from the phone

Run from the development PC with `curl`, not from the phone. That keeps the API key off the phone and tests the wire format, which was the open question. The first on-phone call becomes part of Plan 2's integration test.

**OpenAI-compatible side (OpenRouter, free model):** passed. HTTP 200 in 3.4 s for one step. The model picked `tap` with `node_id` `a1` for "open Settings", `finish_reason` was `tool_calls`, and the cost was 0. Quirks the client must handle:
- `tool_calls[].function.arguments` is a JSON **string** and must be parsed and validated.
- The reply may carry a `reasoning` field that is not part of the tool call.
- Each call has an `id`, which the tool-result message must echo.

**Anthropic side:** not tested live (no Anthropic key). The request and response shapes come from the current API documentation. The first live call is part of Plan 2's integration test.

## Spike 5: bubble and task screen

| Check | Result |
|---|---|
| `TYPE_ACCESSIBILITY_OVERLAY` bubble draws, drags and takes taps | Works |
| Hide, read tree, show | 0 to 5 ms each, far under the 50 ms target |
| Task screen opens translucent over another app | Works |

**Bug found and fixed:** the task screen shared the app's task, so a tap sometimes surfaced Accessibility settings. Giving it its own empty `taskAffinity` fixed it, confirmed over 10 taps.

**Decisions for the device-layer spec (S3):**
- The task screen must have its own task.
- Ignore bubble taps while a task is running or a task screen is open. A second tap stacked another screen.
- Do not rely on logcat for diagnosis: it rolls over within minutes on this phone.

## Scenarios to add

Work profile, Private Space and DualApps are all present on a normal phone. Act only in the user the service runs in.
