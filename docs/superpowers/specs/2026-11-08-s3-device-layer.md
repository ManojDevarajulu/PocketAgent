# S3: Device layer

**Date:** 2026-11-08
**Status:** Approved 2026-11-08 (the user confirmed the whole spec before Plan 3 was written).
**Builds on:** main spec §4 to §7 and §11, security spec §2 (T3 to T5, T9, T10, T14), §3 (rules 10 to 16) and §6, spike findings 1, 2 and 5.
**Implements:** `DevicePort` from Plan 1, Task 4, and `AgentUi` from Plan 1, Task 9, on a real phone.

## 1. Scope and exit gate

S3 turns the JVM-only agent into an app that runs on the phone: the accessibility service, the real `DevicePort`, the bubble, captions, the confirmation card, the summary card and the task screen.

**Exit gate:** demo task 3 runs end to end on the test phone (Nothing phone, Android 16) from a debug build. The user views an address in WhatsApp, taps the bubble and says "Take me there", and Google Maps starts directions to it. One clean run is enough here; the 9-of-10 bar belongs to S5. The device checklist in §10 must also pass.

**Not in S3:** voice input, the bubble on/off toggle, the setup screen, Keystore storage, notices and OEM guidance (all S4); playbooks, the Swiggy decision and benchmarks (S5); the Quick Settings tile (v0.2).

## 2. Units

The rule from Plan 1 holds: core packages import no Android types (`ArchitectureTest`). Every decision that can be made without Android lives in plain Kotlin in `device/` or `agent/` and is tested on the JVM. The Android packages only move data between the platform and those units.

**Pure Kotlin, `device/`**

| Unit | Job |
|---|---|
| `RawNode` | A plain copy of the fields read from an `AccessibilityNodeInfo`: class name, text, content description, clickable, editable, scrollable, password, visible-to-user, package, bounds, children |
| `TreeConverter` | `RawNode` to `ScreenNode`: drops nodes not visible to the user, maps class names to roles, assigns ids with `NodeIds`, caps depth at 60 and the total at 1,500 nodes |
| `WindowPicker` | Chooses which window to read from a list of window summaries, and collects Pocket Agent's own window bounds into `ownWindowBounds` |
| `Settler` | Waits for 300 ms without accessibility events, 3 s at most. Takes a clock and an event signal, so it runs on virtual time in tests |
| `Revalidator` | Decides whether a live node still matches the node the model chose (§5) |
| `GesturePlanner` | Tap points and scroll swipe paths, inside the target bounds or the screen and clear of the system-gesture edges |
| `AppMatcher` | Matches an `open_app` name against installed launcher apps: one match, none, or ambiguous |
| `DeviceUnavailable` | Exception thrown by a `DevicePort` when the phone cannot be used (§7) |

**Pure Kotlin, `agent/`**

| Unit | Job |
|---|---|
| `BubbleLogic` | The bubble's state machine: what a tap or long press does in each state, and when Done or Error return to Idle |

**Android, `android/`**

| Unit | Job |
|---|---|
| `PocketAccessibilityService` | Lifecycle only. Creates and closes `AgentSession`, forwards events to `Settler`, switches event subscription on and off |
| `AccessibilityDevice` | Implements `DevicePort` with `windows`, `rootInActiveWindow`, `performAction`, `performGlobalAction`, `dispatchGesture`, `takeScreenshot` and `startActivity`, using the pure units above |
| `AgentSession` | Owns the runner, the device and the overlays for the life of the service. `AgentSession.current` is the process-local holder, null when the service is off |
| `DevConfig` | Debug builds only: the provider config from `local.properties` (§8). Deleted in S4 |

**Views, `ui/`**

| Unit | Job |
|---|---|
| `BubbleOverlay` | Draws the bubble, handles drag, tap and long press, and delegates decisions to `BubbleLogic` |
| `CaptionView` | The one-line caption next to the bubble |
| `ConfirmCard` | The confirmation card |
| `SummaryCard` | The card shown when a task ends |
| `TaskInputActivity` | The task screen, in task mode and answer mode |
| `OverlayUi` | Implements `AgentUi` on top of the views above |
| `LauncherActivity` | One button that opens the accessibility settings. S4 replaces it with the setup screen |

**Ownership (decided):** the service owns everything. Service lifetime equals agent lifetime, so there is no binding, no broadcast and no exported component beyond the launcher (T9, T10).

**Flow:** bubble tap, then hide the bubble, read the screen and show the bubble, then open the task screen. On Go, `AgentSession` calls `runner.start(task, savedScreen)`, and the loop drives `AccessibilityDevice` and `OverlayUi`.

## 3. Service configuration

`res/xml/accessibility_config.xml`:

- `canRetrieveWindowContent`, `canPerformGestures` and `canTakeScreenshot`: true.
- `accessibilityFlags`: `flagRetrieveInteractiveWindows` only. Resource ids (`flagReportViewIds`) and not-important views (`flagIncludeNotImportantViews`) are not requested, to keep data to the minimum. Key-event filtering is never requested.
- `accessibilityEventTypes`: none in the XML. While idle the service subscribes to no events (security spec §4 and §7). When the runner leaves `Idle`, the service sets `serviceInfo.eventTypes` to window state changed, window content changed, windows changed and view scrolled. It sets them back to none when the runner reaches `Finished` or `Stopped`.
- `notificationTimeout`: 50 ms.
- The service is not declared an accessibility tool (security spec, decision 3). Its label and description say plainly that it reads the screen and acts on it when the user starts a task.

## 4. Reading the screen

**Window choice (`WindowPicker`):**

1. Every window whose package is Pocket Agent's goes into `ownWindowBounds` and is never read.
2. The **active** window is read. In split screen that is the half the user last touched. A dialog over an app is read as the dialog. Input-method windows are never chosen.
3. If the active window is system UI (notification shade, quick settings) its package is reported unchanged, so the gate blocks it under rule 10. The lock screen is handled earlier (§7).

`Screen.packageName` is the root node's package. `Screen.appLabel` comes from `PackageManager.getApplicationLabel`, never from screen content or the model.

**Conversion:** the live tree is copied into `RawNode`s on a background dispatcher, because each node read is a binder call. `TreeConverter` then builds the `ScreenNode` tree. Bounds always come from the current reading and are never cached between observations (rotation, split screen, display scaling).

**Settling:** `Settler` runs before every `readScreen`. The read at bubble tap is the only exception, because the screen is already still and the task screen is about to cover it.

**Hiding our own windows:** the bubble and caption are set invisible on the main thread for the read and shown again afterwards (0 to 5 ms each in spike 5). They are excluded from observations anyway; hiding them keeps them out of screenshots and gestures.

**The saved screen:** read at bubble tap, held in `AgentSession`, passed to `runner.start`, and dropped when the task ends or the task screen is dismissed.

**Serializer cap:** `AgentSession` builds `ScreenSerializer` with `maxChars = 12_000`, as security spec §5 requires. The class default of 24,000 is left alone for tests.

## 5. Acting

**Live node handles.** After each `readScreen`, `AccessibilityDevice` keeps a map from node id to the live `AccessibilityNodeInfo` for that observation only. The next read replaces the map. The session drops it when the task ends.

**Re-validation before every action (T3, rule 13).** Any failure returns `ActionResult.Failed("screen changed")` and does nothing. The loop then shows the model the fresh screen.

- Node actions: the live node is refreshed. It fails if the refresh fails, the node is no longer visible, its package is Pocket Agent's, or its window is no longer the active window. Then `Revalidator` requires the identical label and bounds within 4 px on every edge.
- Coordinate taps: the active window is read again, and the deepest node at the point must have the same label as in the observation. A point with no label must still have no label.

**Actions:**

| Action | Implementation |
|---|---|
| `Tap` | `ACTION_CLICK` on the node, or on its nearest clickable ancestor. If the click returns false or nothing is clickable, a 50 ms gesture tap at the centre of the node's bounds |
| `TapAt` | A 50 ms gesture tap at the point. The gate has already rejected points inside our own windows (rule 11) |
| `TypeText` | Refuses password fields, as a second check behind the gate. Then `ACTION_FOCUS` and `ACTION_SET_TEXT`. Failure returns `Failed("could not type here")` |
| `Scroll` with a node | `ACTION_SCROLL_UP`, `_DOWN`, `_LEFT` or `_RIGHT` on the node. If that is not supported, a swipe from `GesturePlanner` inside its bounds |
| `Scroll` without a node | The same, on the largest scrollable node in the window, or a swipe across the middle of the screen if there is none |
| `Back`, `Home` | `performGlobalAction` |
| `OpenApp` | See below |

**`open_app` (rule 15).** Launcher apps come from `PackageManager.queryIntentActivities` with the launcher intent, which the manifest's `<queries>` element makes visible. `QUERY_ALL_PACKAGES` is not used. Only apps of the user the service runs in are considered. `AppMatcher` compares names case-insensitively: an exact label match first, then a unique match on "contains".

- One match: the device checks `BlockedApps` on the resolved package, because only the device knows it, then starts the launch intent with `FLAG_ACTIVITY_NEW_TASK`. A blocked package gives `Failed("<name> is on the block list")`.
- No match: `Failed("no installed app named <name>")`.
- Several matches: `Failed("several apps match: A, B; ask the user which one")`, so the model uses `ask_user`.
- The launch throws: `Failed("could not open <name>")`.

**Gestures.** `dispatchGesture` is wrapped in `suspendCancellableCoroutine`. A gesture the platform cancels, usually because the user touched the screen, returns `Failed("gesture interrupted")`. The bubble and caption are hidden while the gesture runs and shown again in its callback.

**Screenshots.** `takeScreenshot(Display.DEFAULT_DISPLAY)` with the overlays hidden. The hardware bitmap is copied, scaled so the long side is 1,280 px at most, and encoded as PNG. A secure-window error returns null, which the loop already handles. If the platform's one-per-second limit is hit, the device waits one second and retries once, then returns null.

## 6. User interface

All overlays are `TYPE_ACCESSIBILITY_OVERLAY` windows, which need no overlay permission (spike 5). They belong to Pocket Agent's package, so they are excluded from every observation (rule 9).

**Bubble**

- Drag moves it. A tap is a touch that moves less than 12 px. A long press is 600 ms without moving. The position is kept in memory only.
- States and visuals follow the main spec §4 table, with colour and icon. Done and Error return to Idle after 3 s.
- In S3 the bubble is shown whenever the service is connected. S4 adds the toggle.

`BubbleLogic` rules (from spike 5):

| State | Tap | Long press |
|---|---|---|
| Idle, no task screen open | Hide, read the screen, show, open the task screen | Nothing |
| Task screen open | Ignored | Nothing |
| Running or waiting for the user | Ignored | `runner.cancel()`, which removes any card and closes any task screen |
| Done or Error (3 s) | As Idle | Nothing |

**Caption.** One line next to the bubble, with text from the existing `Captions` code only, so the model never writes it. The window is `FLAG_NOT_TOUCHABLE` and never takes a tap. It hides with the bubble and is cleared when the task ends.

**Confirmation card**

- A bottom-sheet overlay showing the action, the target and the app. When the task has typed text, it also shows "Will send:" with that text (rule 12, `ConfirmRequest.detail`).
- The app name comes from `PackageManager` through `Screen.appLabel`, never from the model.
- Two buttons: Allow and Decline. Both set `filterTouchesWhenObscured`, and a touch carrying `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` is also ignored (T5).
- **T4 is covered by the design.** While the card is shown, the loop is suspended inside `confirm()`, so the agent cannot send a gesture at it. Rule 11 is a second layer. The security spec's injected-touch flag check is therefore not needed and is not built.
- Cancelling the coroutine, by the 120 s timeout or a long press, removes the card.

**Task screen (`TaskInputActivity`)**

- Manifest: `taskAffinity=""` (spike 5 bug fix), `excludeFromRecents="true"`, a translucent theme, `exported="false"`. Accessibility services may start activities from the background; spike 5 confirmed this works.
- **Task mode:** a text box, Go and Cancel. Empty text is rejected and text is capped at 2,000 characters. Go passes the task and the saved screen to `runner.start` and closes the screen, so the user's app is in front again. Cancel, Back or a tap outside drops the saved screen.
- **Answer mode (`ask_user`):** the screen opens with the model's question above the text box. Submit returns the answer. Dismissing returns no answer. If the task is cancelled while it is open, the screen closes itself.
- `OverlayUi.ask` starts the screen in answer mode and suspends on a `CompletableDeferred` that the screen completes through `AgentSession`.

**Summary card.** Shown at every ending, because no failure is silent (main spec §11).

- The title is written by Kotlin from the ending: `Stopped` gives "Stopped by you", and each `EndReason` has a fixed title such as "Done", "Not finished", "Blocked app" or "Phone unavailable".
- The body is the outcome's summary as plain text, at most 300 characters, under the label "Agent's summary", followed by the token totals (T13).
- It closes on a tap or after 15 s.

## 7. Lifecycle and errors

**Lifecycle**

- `onServiceConnected`: create `AgentSession` with its own scope (`SupervisorJob()` plus `Dispatchers.Default`), load `DevConfig`, build the client with `Providers.create`, add the bubble, set `AgentSession.current`.
- `onUnbind` and `onDestroy`: `session.close()`. It cancels the runner and the scope, removes every overlay, drops the saved screen and the node map, and clears `AgentSession.current`.
- Nothing is persisted. After the system kills the app, the next start is idle.

**`DeviceUnavailable`.** `readScreen` and `perform` throw `DeviceUnavailable(reason)` when:

- The phone is locked (`KeyguardManager.isKeyguardLocked`, checked at every read and action; no broadcast receiver is needed). Reason: "The phone locked".
- The session is closed. Reason: "Pocket Agent's accessibility service was turned off".

**Changes to the core (Plan 1 code):**

- `EndReason` gains `DEVICE_UNAVAILABLE`. `AgentLoop` catches `DeviceUnavailable` around device calls and ends the task with that reason and the exception's reason as the summary. Cancellation is still never caught.
- `AgentLoop` applies its confirmation timeout (120 s) to `ask_user` as well. On timeout the answer is "(no answer)".

**Error table (S3 additions to main spec §11)**

| Situation | Behaviour |
|---|---|
| Phone locks during a task | `DeviceUnavailable`, summary card "Phone unavailable" |
| Accessibility turned off during a task | The session closes and the task is cancelled. Overlays are gone with the service, so a Toast says "Pocket Agent stopped: accessibility was turned off". No notification, because `POST_NOTIFICATIONS` is not on the permission allowlist |
| `rootInActiveWindow` is null or the tree is empty | An empty `Screen` is returned. The model can take a screenshot or ask. The stuck detector and step cap stop it otherwise |
| A gesture is cancelled by a user touch | `Failed("gesture interrupted")`, then the loop reads the screen again |
| Bubble tap with no valid provider config | No task starts. The summary card says "No model configured" and, in debug builds, names the `local.properties` keys |
| An app launch throws | `Failed("could not open <name>")` |

## 8. Debug-only provider config

S4 owns key storage. Until then:

- `local.properties`, already git-ignored, holds `pocketagent.kind`, `pocketagent.baseUrl`, `pocketagent.apiKey`, `pocketagent.model` and `pocketagent.supportsImages`.
- `buildFeatures.buildConfig` is enabled. The matching `buildConfigField`s are declared only in the `debug` build type.
- `DevConfig` lives in `src/debug/kotlin`. A stub with the same signature in `src/release/kotlin` always returns null, so release code never refers to the key fields.
- The loaded config goes through `ProviderConfig.validate()`. Problems show as "No model configured" (§7).
- The key is never logged and never shown.
- S4 deletes `DevConfig`, both source sets' copies, and the build fields.

## 9. Manifest

- `PocketAccessibilityService`: `exported="false"`, `permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`, the accessibility intent filter and the config from §3.
- `LauncherActivity`: the only exported component, with the launcher intent filter.
- `TaskInputActivity`: as in §6.
- `<queries>` with the `MAIN` and `LAUNCHER` intent, for `open_app`.
- No new permissions. `allowBackup="false"` and the data-extraction rules stay. The existing manifest tests must pass unchanged.
- Logging: none. The app has no `Log` wrapper, and S3 does not add one.

## 10. Testing

**JVM tests (CI).** No new test dependencies; Robolectric is not used.

| Unit | Cases |
|---|---|
| `TreeConverter` | Roles, invisible nodes dropped, depth and node caps, password flag kept, ids from `NodeIds`. Fixtures are hand-written trees shaped like the spike dumps. The dumps themselves are never committed, because they hold the user's real chats and account email |
| `WindowPicker` | One app, split screen, a dialog over an app, a keyboard showing, our own windows, the notification shade |
| `Settler` | Settles after 300 ms of quiet; a constant stream of events stops at 3 s; cancellation. Virtual time |
| `Revalidator` | Same node; label changed; bounds moved by 3 px (passes) and by 5 px (fails); node gone; window no longer active |
| `GesturePlanner` | Tap points inside bounds; swipes clear of the edges; all four directions |
| `AppMatcher` | Exact match, case-insensitive match, unique "contains" match, ambiguous, none, blocked package refused |
| `BubbleLogic` | Every row of the table in §6 |
| `AgentLoop` | `DeviceUnavailable` ends the task with `DEVICE_UNAVAILABLE` and its reason; `ask_user` times out to "(no answer)" |
| `ArchitectureTest` | Only the launcher is exported; the service requires the bind permission; the permission allowlist is unchanged; `apiKey` build fields appear only in the debug build type; the release `DevConfig` never refers to key fields |

**Device checklist** (added to `docs/TESTING.md`, run by hand on the test phone):

1. T3: change the target between read and act, for example by scrolling while the model is thinking. The action fails with "screen changed".
2. T5: turn on a screen-filter app over the confirmation card. Allow does not respond.
3. Long-press stop within 1 s during a model call, a gesture, a confirmation card and the answer screen.
4. Split screen, and rotation during a task.
5. Lock the phone during a task: summary card "Phone unavailable" after unlocking.
6. Turn off accessibility during a task: the Toast appears and nothing else runs.
7. Screenshot of a secure window returns the tool error.
8. `adb shell dumpsys accessibility` shows no event types for the service while idle.
9. The exit gate: demo task 3, end to end.

## 11. Open items

1. **Not-important views and Swiggy.** It is untested whether `flagIncludeNotImportantViews` makes Swiggy's empty cart readable. A one-line check during Plan 3 is worth doing; the result feeds S5's decision on demo task 2. The flag stays off unless the check shows a clear gain and the user agrees.
2. **Second phone.** The security spec asks for a second phone or an Android 11 emulator if available. S3 does not require it.

## 12. Decisions

1. **Provider config before S4:** debug-only `BuildConfig` from `local.properties`, with a release stub and a test (§8). Approved 2026-11-08.
2. **Ownership:** the accessibility service owns the session (§2). Approved 2026-11-08.
3. **`ask_user` reuses the task screen** in answer mode (§6). Approved 2026-11-08.
4. **`DeviceUnavailable` and `EndReason.DEVICE_UNAVAILABLE`** added to the core loop (§7). Approved 2026-11-08.
5. **`ask_user` timeout of 120 s** and **no injected-touch flag check** (§6, §7). Approved 2026-11-08.
