# Pocket Agent Plan 3: Device Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the JVM-only agent into an app that runs on the phone: accessibility service, real `DevicePort`, bubble, caption, confirmation card, summary card and task screen, ending with demo task 3 (address in WhatsApp to Google Maps directions) running on the test phone.

**Architecture:** Every decision that can be made without Android lives in plain Kotlin in `device/` or `agent/` and is tested on the JVM (Tasks 1 to 9). The Android code in `android/` and `ui/` only moves data between the platform and those units (Tasks 10 to 15) and is verified by compiling, by the architecture tests, and by the device checklist. The accessibility service owns the whole session (`AgentSession`); there is no binding, broadcast or exported component beyond the launcher.

**Tech Stack:** Kotlin, coroutines, Android framework Views and `AccessibilityService` APIs only (no new dependencies, no Robolectric), JUnit 4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-08-s3-device-layer.md`. Security rules: `docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md` (T3 to T5, T9, T10, T14, rules 10 to 16). Contracts being implemented: Plan 1 `DevicePort`, `AgentUi`, `AgentRunner`, `AgentLoop`.

## Global Constraints

- Carried from Plans 1 and 2: Kotlin only; packages `agent`, `llm`, `safety`, `playbook`, `device` import no `android.*` or `androidx.*` (`ArchitectureTest`); no telemetry; the API key is never logged, shown or committed; a task ends after 25 steps; commits are small, imperative, and end with the `Co-Authored-By` line; run `git status` before each commit.
- Runtime and test dependencies stay exactly as allowed by `dependenciesMatchAllowlist`. Add none. Use platform APIs only (no `androidx.*`).
- Permissions stay `INTERNET` and `RECORD_AUDIO` only. No `QUERY_ALL_PACKAGES`, no overlay permission. `allowBackup="false"` and the data-extraction rules stay. The existing `manifestMatchesAllowlist` test must pass unchanged. The only exported component is `LauncherActivity`.
- Logging: none. No `Log`, `println` or wrapper is added in S3.
- Service: `canRetrieveWindowContent`, `canPerformGestures`, `canTakeScreenshot` true; `accessibilityFlags` is `flagRetrieveInteractiveWindows` only (no `flagReportViewIds`, no `flagIncludeNotImportantViews`, no key-event filtering); no event types in the XML; `notificationTimeout` 50 ms; not declared an accessibility tool. While idle it subscribes to no events; while a task runs it subscribes to window state changed, window content changed, windows changed and view scrolled.
- Reading: tree capped at depth 60 and 1,500 nodes; `ScreenSerializer(maxChars = 12_000)` in `AgentSession`; settle = 300 ms without events, 3 s at most; re-validation bounds tolerance 4 px per edge; screenshots 1,280 px long side at most, PNG; one retry after 1 s on the platform rate limit.
- Overlays are all `TYPE_ACCESSIBILITY_OVERLAY`. Bubble: tap = under 12 px of movement, long press = 600 ms without moving, Done and Error return to Idle after 3 s. Confirmation and `ask_user` time out after 120 s (answer `(no answer)`). Task text is capped at 2,000 characters, empty rejected. Summary body at most 300 characters, card closes after 15 s.
- Captions come from `Captions` only. App names on cards come from `Screen.appLabel` (PackageManager), never from the model.
- Nothing is persisted. Everything the session holds (saved screen, live node map, last typed text) is dropped at task end.

## Review Focus

1. **The screen changes between read and act** (scroll or a new dialog while the model thinks): the action must fail with `screen changed` and do nothing. Tested in Tasks 5 and 12; device checklist item 1.
2. **Our own windows leaking into what the model sees or acts on** (bubble, caption, card, task screen in the tree, in a screenshot, or under a coordinate tap). Tested in Tasks 3, 4 and 12.
3. **Tapjacking and a stray touch on the confirmation card** (a screen-filter app over Allow). Checked in Task 11 and device item 2.
4. **The phone locks, accessibility is switched off, or the user cancels mid-action.** The task must end with a clear card or Toast, and a pending action must never run. Tested in Tasks 1 and 13; device items 3, 5, 6.
5. **Odd installs and screens:** an `open_app` name that matches two apps, matches nothing, or is on the block list; a null or empty tree; a secure window blocking screenshots; the 1-per-second screenshot limit. Tested in Tasks 2, 7 and 12.

---

### Task 1: Device unavailable and the `ask_user` timeout in the core loop

**Files:**
- Create: `device/DeviceUnavailable.kt`
- Modify: `agent/TaskOutcome.kt`, `agent/AgentLoop.kt`, `src/test/.../fakes/Fakes.kt`, `agent/AgentLoopTest.kt`

**Interfaces:**
- Produces:
```kotlin
class DeviceUnavailable(val reason: String) : Exception(reason)     // package dev.pocketagent.device
enum class EndReason { DONE, STEP_LIMIT, STUCK, INVALID_CALLS, REFUSED, AUTH_ERROR, LLM_ERROR, BLOCKED_APP, DEVICE_UNAVAILABLE }
// FakeDevice gains: failReadsFrom: Int? = null  -> readScreen() throws DeviceUnavailable("The phone locked") once reads >= that value
```

- [ ] **Step 1: Write failing tests** in `AgentLoopTest`: `deviceUnavailableOnReadEndsTheTask` (a `FakeDevice(failReadsFrom = 1)` after a tap: outcome reason `DEVICE_UNAVAILABLE`, `success` false, summary `"The phone locked"`); `deviceUnavailableOnActionEndsTheTask` (`resultFor` throws `DeviceUnavailable("Pocket Agent's accessibility service was turned off")`: same reason, that summary); `cancellationIsNotTurnedIntoAnOutcome` (a `CancellationException` thrown from the device still propagates); `askUserTimesOutToNoAnswer` (virtual time, `confirmTimeoutMs = 1_000`, `onAsk` suspends forever: the next turn's tool result text is `"(no answer)"` and `waiting` ends `false`).
- [ ] **Step 2: Run to fail** — `./gradlew :app:testDebugUnitTest --tests "*AgentLoopTest"` → FAIL (compile error: `DeviceUnavailable`).
- [ ] **Step 3: Implement.** In `AgentLoop.runSteps`, wrap the `handle(run, result)` call in `try { ... } catch (e: DeviceUnavailable) { return end(EndReason.DEVICE_UNAVAILABLE, e.reason) }` (never catch `CancellationException`). In `askUser`, wrap `ui.ask` in `withTimeoutOrNull(confirmTimeoutMs)`. Add the `FakeDevice.failReadsFrom` parameter.
- [ ] **Step 4: Run to pass**, then the whole suite — `./gradlew :app:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "End a task when the device becomes unavailable and time out ask_user"`

---

### Task 2: `RawNode` and `TreeConverter`

**Files:**
- Create: `device/RawNode.kt`, `device/TreeConverter.kt`
- Test: `device/TreeConverterTest.kt`

**Interfaces:**
- Consumes: `ScreenNode`, `Bounds`, `NodeIds`.
- Produces:
```kotlin
data class RawNode(
    val className: String?, val text: String?, val description: String?,
    val clickable: Boolean, val editable: Boolean, val scrollable: Boolean, val isPassword: Boolean,
    val visibleToUser: Boolean, val packageName: String, val bounds: Bounds, val children: List<RawNode>,
)
class TreeConverter(private val maxDepth: Int = 60, private val maxNodes: Int = 1_500) {
    /** Returns the root (null when [raw] itself is not visible) and a map from each kept node's id to its [RawNode], so the caller can find the live node behind an id. */
    fun convert(raw: RawNode, observation: Int): ConvertedTree
}
class ConvertedTree(val root: ScreenNode?, val rawById: Map<String, RawNode>)
```
Ids are `NodeIds.idFor(observation, index)` with `index` in depth-first order from 0. Roles are the existing set: `button`, `image`, `text`, `input`, `checkbox`, `list`, `group`. Class-name mapping: `*Button` or `*ImageButton` to `button`; `*ImageView` to `image`; `*TextView` to `text`; `*EditText` or `editable` to `input`; `*CheckBox`, `*Switch`, `*RadioButton` to `checkbox`; `*RecyclerView`, `*ListView`, `*ScrollView`, `*GridView` or `scrollable` to `list`; anything else `group`.

- [ ] **Step 1: Write failing tests** in `TreeConverterTest`, hand-written `RawNode` trees shaped like the spike dumps (no real captures): `invisibleNodesAndTheirSubtreesAreDropped`; `rolesFollowTheClassNameTable` (one case per row above, including `editable` winning over a `TextView` class); `passwordFlagIsKept`; `idsComeFromNodeIdsInDepthFirstOrder` (ids `a0`, `a1`... for observation 0, `b0`... for 1); `depthIsCappedAt60` (a 100-deep chain yields 61 nodes); `totalIsCappedAt1500` (a root with 2,000 children yields 1,500 nodes, in order); `nullClassNameIsAGroup`; `rawByIdMapsEveryKeptNode`; `invisibleRootGivesNullRoot`.
- [ ] **Step 2: Run to fail** — `--tests "*TreeConverterTest"` → FAIL.
- [ ] **Step 3: Implement** `convert` with an explicit stack or recursion bounded by `maxDepth` (no stack overflow at 60). Counting stops expanding at `maxNodes`; siblings after the cap are dropped.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Convert raw accessibility trees into screen nodes"`

---

### Task 3: `WindowPicker`

**Files:**
- Create: `device/WindowPicker.kt`
- Test: `device/WindowPickerTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class WindowKind { APPLICATION, INPUT_METHOD, SYSTEM, ACCESSIBILITY_OVERLAY, OTHER }
data class WindowSummary(val id: Int, val packageName: String?, val kind: WindowKind, val active: Boolean, val bounds: Bounds)
data class WindowChoice(val windowId: Int?, val ownWindowBounds: List<Bounds>)
class WindowPicker(private val ownPackage: String) { fun pick(windows: List<WindowSummary>): WindowChoice }
```
Rules: every window whose package is `ownPackage` goes into `ownWindowBounds` and is never chosen; otherwise the **active** window is chosen unless its kind is `INPUT_METHOD`; if no window qualifies, `windowId` is null. System UI windows (notification shade, quick settings) are chosen as-is so the gate blocks them.

- [ ] **Step 1: Write failing tests**: `oneAppWindowIsChosen`; `splitScreenChoosesTheActiveHalf`; `dialogOverAnAppIsChosenWhenActive`; `keyboardIsNeverChosen` (active input-method window with an app window behind: the app window is chosen, so the choice is the active non-IME window); `ownWindowsAreCollectedAndNeverChosen` (bubble and task screen both ours, even when one is active); `notificationShadeIsChosenUnchanged` (package `com.android.systemui`); `noQualifyingWindowGivesNull`.
- [ ] **Step 2: Run to fail** — `--tests "*WindowPickerTest"` → FAIL.
- [ ] **Step 3: Implement** `pick`. When the active window is an IME, fall back to the first `APPLICATION` window that is not ours.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Choose the window to read and collect our own window bounds"`

---

### Task 4: `Settler`

**Files:**
- Create: `device/Settler.kt`
- Test: `device/SettlerTest.kt`

**Interfaces:**
- Produces:
```kotlin
class Settler(private val now: () -> Long, private val quietMs: Long = 300, private val maxMs: Long = 3_000) {
    fun onEvent()                      // records now()
    suspend fun settle()               // returns after quietMs without events, measured from the later of the last event and the call; never later than maxMs after the call
}
```
`now` is milliseconds; production passes `SystemClock::uptimeMillis`, tests pass `{ testScheduler.currentTime }`. Waiting uses `delay`, so cancellation works.

- [ ] **Step 1: Write failing tests** with `runTest`: `settlesAfter300msOfQuiet` (no events: `currentTime == 300` on return); `anEventAt200msMovesItTo500ms`; `aConstantStreamStopsAt3s` (an event every 100 ms: returns at 3,000); `cancellationStopsTheWait` (cancel the job at 100 ms: no further delay, `isCancelled`); `eventsBeforeTheCallDoNotDelay` (event at t=0, `settle()` called at t=1,000: returns at 1,300).
- [ ] **Step 2: Run to fail** — `--tests "*SettlerTest"` → FAIL.
- [ ] **Step 3: Implement** a loop that delays `min(quietMs - quietSoFar, maxMs - elapsed)` until quiet or the cap.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Wait for the screen to settle before reading it"`

---

### Task 5: `Revalidator`

**Files:**
- Create: `device/Revalidator.kt`
- Test: `device/RevalidatorTest.kt`

**Interfaces:**
- Produces:
```kotlin
/** What the device saw when it refreshed the live node. Null instead of this means the refresh failed. */
data class LiveNodeState(val label: String?, val bounds: Bounds, val visibleToUser: Boolean, val packageName: String, val inActiveWindow: Boolean)
class Revalidator(private val ownPackage: String, private val tolerancePx: Int = 4) {
    fun nodeStillMatches(observed: ScreenNode, live: LiveNodeState?): Boolean
    /** [liveLabel] is the label of the deepest live node at the point; a point with no label must still have none. */
    fun pointStillMatches(observed: Screen, x: Int, y: Int, liveLabel: String?): Boolean
}
```
`nodeStillMatches` is false when `live` is null, not visible, in our package, not in the active window, has a different label (`ScreenNode.label()` equality, null equals null), or any edge differs by more than 4 px.

- [ ] **Step 1: Write failing tests**: `sameNodeMatches`; `labelChangedFails`; `boundsMovedBy3pxPasses`; `boundsMovedBy5pxFails` (test each of the four edges); `refreshFailureFails` (null); `invisibleFails`; `ownPackageFails`; `windowNoLongerActiveFails`; `pointWithSameLabelMatches`; `pointLabelChangedFails`; `unlabelledPointMustStayUnlabelled` (observed no label, live has one: false).
- [ ] **Step 2: Run to fail** — `--tests "*RevalidatorTest"` → FAIL.
- [ ] **Step 3: Implement.** `pointStillMatches` compares `observed.deepestAt(x, y)?.label()` with `liveLabel`.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Decide whether a live node still matches the one the model chose"`

---

### Task 6: `GesturePlanner`

**Files:**
- Create: `device/GesturePlanner.kt`
- Test: `device/GesturePlannerTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class Point(val x: Int, val y: Int)
data class SwipePath(val from: Point, val to: Point)
class GesturePlanner(private val screen: Bounds, private val edgeInsetPx: Int) {
    fun tapPoint(target: Bounds): Point                       // centre of target ∩ safe area
    fun swipe(direction: Direction, within: Bounds?): SwipePath   // null = across the middle of the screen
}
```
Safe area = `screen` shrunk by `edgeInsetPx` on every side (keeps taps and swipes off the system-gesture edges). `swipe` runs inside `within ∩ safe area`, along its centre line, from 75% to 25% of the length. `Direction` is the way the content scrolls, as in the model's `scroll` tool: `DOWN` shows what is below, so the finger moves **up**; `UP` moves the finger down; `LEFT` and `RIGHT` likewise.

- [ ] **Step 1: Write failing tests**: `tapPointIsTheCentre`; `tapPointOfATargetPartlyOffScreenIsInsideTheSafeArea`; `tapPointOfATargetOnTheEdgeIsPulledIn`; `swipeDownMovesTheFingerUp` and the three other directions; `swipeStaysInsideTheTargetBounds`; `swipeAvoidsTheEdges` (target as large as the screen); `nullBoundsSwipesAcrossTheMiddle`.
- [ ] **Step 2: Run to fail** — `--tests "*GesturePlannerTest"` → FAIL.
- [ ] **Step 3: Implement.** Intersect with the safe area first; if the intersection is empty, fall back to the safe area.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Plan tap points and scroll swipes clear of the screen edges"`

---

### Task 7: `AppMatcher`

**Files:**
- Create: `device/AppMatcher.kt`
- Test: `device/AppMatcherTest.kt`

**Interfaces:**
- Consumes: `safety.BlockedApps` (`isBlocked(packageName, label)`).
- Produces:
```kotlin
data class LauncherApp(val label: String, val packageName: String)
sealed interface AppResolution {
    data class Launch(val app: LauncherApp) : AppResolution
    data class Refused(val reason: String) : AppResolution
}
class AppMatcher(private val blocked: BlockedApps) { fun resolve(name: String, apps: List<LauncherApp>): AppResolution }
```
Matching is case-insensitive on trimmed text: an exact label match wins; otherwise a unique "contains" match; otherwise refuse. Exact reasons: no match `"no installed app named <name>"`; several `"several apps match: A, B; ask the user which one"` (labels sorted, at most 5); blocked `"<label> is on the block list"` (checked on the resolved package and label). Two apps with identical labels are ambiguous even on an exact match.

- [ ] **Step 1: Write failing tests**: `exactMatchLaunches`; `caseInsensitiveMatchLaunches`; `uniqueContainsMatchLaunches` ("maps" finds "Google Maps"); `exactMatchBeatsContains` ("Maps" with "Maps" and "Google Maps"); `ambiguousContainsRefusesAndListsLabels`; `twoAppsWithTheSameLabelAreAmbiguous`; `noMatchRefuses`; `blockedPackageIsRefused` (package `com.phonepe.app`); `blankNameRefuses`.
- [ ] **Step 2: Run to fail** — `--tests "*AppMatcherTest"` → FAIL.
- [ ] **Step 3: Implement** `resolve`.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Match open_app names against launcher apps"`

---

### Task 8: `BubbleLogic` and `SummaryText`

**Files:**
- Create: `agent/BubbleLogic.kt`, `agent/SummaryText.kt`
- Test: `agent/BubbleLogicTest.kt`, `agent/SummaryTextTest.kt`

**Interfaces:**
- Consumes: `AgentState`, `TaskOutcome`, `EndReason`, `TokenUsage`.
- Produces:
```kotlin
enum class BubbleMode { IDLE, RUNNING, WAITING, DONE, ERROR }
enum class BubbleCommand { NONE, OPEN_TASK_SCREEN, CANCEL }
class BubbleLogic {
    var taskScreenOpen: Boolean                       // set by the UI
    val mode: BubbleMode
    fun onState(state: AgentState)                    // Idle->IDLE, Running->RUNNING, WaitingForUser->WAITING, Finished(success)->DONE, Finished(!success)->ERROR, Stopped->ERROR
    fun onTap(): BubbleCommand
    fun onLongPress(): BubbleCommand
    fun returnToIdle()                                // the overlay calls it 3 s after DONE or ERROR
}
const val BUBBLE_RESULT_MS = 3_000L
object SummaryText {
    fun title(state: AgentState.Finished): String
    fun stoppedTitle(): String                        // "Stopped by you"
    fun body(outcome: TaskOutcome): String            // see below
}
```
`onTap` is `OPEN_TASK_SCREEN` in IDLE, DONE or ERROR with `taskScreenOpen` false, else `NONE`; `onLongPress` is `CANCEL` in RUNNING or WAITING, else `NONE`. Titles: `DONE` with success "Done", `DONE` without success "Not finished", `STEP_LIMIT` "Not finished", `STUCK` "Not finished", `INVALID_CALLS` "Not finished", `REFUSED` "Declined by the model", `AUTH_ERROR` "Key rejected", `LLM_ERROR` "Model error", `BLOCKED_APP` "Blocked app", `DEVICE_UNAVAILABLE` "Phone unavailable". `body` is `"Agent's summary: <summary cut to 300 chars>"` followed by a line `"Tokens: <in> in, <out> out"` (cached tokens appended as `", <n> cached"` when above zero); control characters in the summary become spaces; an empty summary gives only the tokens line.

- [ ] **Step 1: Write failing tests**: one `BubbleLogicTest` case per row of the spec §6 table (Idle, task screen open, Running, Waiting, Done, Error, each for tap and long press); `stateMappingCoversEveryAgentState`; `returnToIdleOnlyLeavesDoneAndError`. `SummaryTextTest`: `everyEndReasonHasATitle` (iterate `EndReason.entries`), `doneNotSuccessfulIsNotFinished`, `bodyCutsTheSummaryAt300Characters`, `bodyReplacesLineBreaks`, `bodyShowsTokenTotals`, `bodyOmitsEmptySummary`.
- [ ] **Step 2: Run to fail** — `--tests "*BubbleLogicTest" --tests "*SummaryTextTest"` → FAIL.
- [ ] **Step 3: Implement** both objects.
- [ ] **Step 4: Run to pass**, then the whole suite.
- [ ] **Step 5: Commit** — `git commit -m "Add the bubble state machine and summary card text"`

---

### Task 9: Debug-only provider config

**Files:**
- Modify: `app/build.gradle.kts`, `ArchitectureTest.kt`
- Create: `src/debug/kotlin/dev/pocketagent/android/DevConfig.kt`, `src/release/kotlin/dev/pocketagent/android/DevConfig.kt`, `src/testDebug/kotlin/dev/pocketagent/android/DevConfigTest.kt`

**Interfaces:**
- Consumes: `ProviderConfig`, `ProviderKind` (`validate()`).
- Produces (identical in both source sets):
```kotlin
object DevConfig { fun load(): ProviderConfig? }
```
Debug also has `internal fun fromValues(kind: String, baseUrl: String, apiKey: String, model: String, supportsImages: String): ProviderConfig?`, which returns null for an unknown kind, a blank field, or a config that fails `validate()`. The release `load()` always returns null and mentions no key field.

- [ ] **Step 1: Write failing tests**: `DevConfigTest` (debug): `validValuesBuildAConfig`; `unknownKindGivesNull`; `blankModelGivesNull`; `plainHttpToTheInternetGivesNull`; `supportsImagesParsesTrueAndFalse`. `ArchitectureTest`: `apiKeyBuildFieldsAppearOnlyInTheDebugBuildType` (in `build.gradle.kts`, `buildConfigField` lines mentioning `pocketagent.apiKey` or `API_KEY` occur only inside the `debug { }` block); `releaseDevConfigNeverMentionsKeyFields` (the release file contains neither `BuildConfig` nor `apiKey`).
- [ ] **Step 2: Run to fail** — `./gradlew :app:testDebugUnitTest --tests "*DevConfigTest" --tests "*ArchitectureTest"` → FAIL.
- [ ] **Step 3: Implement.** In `build.gradle.kts` enable `buildFeatures { buildConfig = true }`, read `local.properties` with `java.util.Properties` and add `buildConfigField("String", ...)` in the `debug` build type for `pocketagent.kind`, `.baseUrl`, `.apiKey`, `.model`, `.supportsImages` (empty string when unset). Never print the values in the build log.
- [ ] **Step 4: Run to pass**, then `./gradlew :app:testDebugUnitTest :app:compileReleaseKotlin` → PASS (proves the release stub compiles with no key fields).
- [ ] **Step 5: Commit** — `git commit -m "Load the provider config from local.properties in debug builds only"`

---

### Task 10: Overlay views: bubble, caption, confirmation card, summary card

**Files:**
- Create: `ui/BubbleOverlay.kt`, `ui/CaptionView.kt`, `ui/ConfirmCard.kt`, `ui/SummaryCard.kt`, `ui/Overlays.kt` (shared helper: add or remove a view with `TYPE_ACCESSIBILITY_OVERLAY` parameters), `res/values/strings.xml`, `res/values/colors.xml`

**Interfaces:**
- Consumes: `BubbleLogic`, `BubbleMode`, `BubbleCommand`, `SummaryText`, `ConfirmRequest`.
- Produces (all constructed with the service `Context`, used on the main thread only):
```kotlin
class BubbleOverlay(context: Context, logic: BubbleLogic, onCommand: (BubbleCommand) -> Unit) {
    fun show(); fun hide(); fun setVisible(visible: Boolean); fun render(mode: BubbleMode); fun close()
    val bounds: Rect?
}
class CaptionView(context: Context) { fun setText(text: String?); fun setVisible(visible: Boolean); fun close() }
class ConfirmCard(context: Context) { fun show(request: ConfirmRequest, onResult: (Boolean) -> Unit); fun close() }
class SummaryCard(context: Context) { fun show(title: String, body: String); fun close() }
```
Behaviour (spec §6): bubble drag moves it (position kept in memory only), a tap moves under 12 px, a long press is 600 ms still, and the mode is shown by colour **and** icon (grey sparkle, blue ring, amber hand, green check, red cross); `CaptionView` is `FLAG_NOT_TOUCHABLE`; `ConfirmCard` is a bottom sheet with the action, target, app name, and `Will send:` plus `request.detail` when present, and two buttons (Allow, Decline) with `filterTouchesWhenObscured = true` and a touch handler that ignores events carrying `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`; `SummaryCard` closes on tap or after 15 s. Draw icons with `Canvas` or platform vector drawables, no libraries.

- [ ] **Step 1: Implement the four views and the helper** to the behaviour above. These are Android views with no JVM test (no Robolectric); the pure decisions they rely on were tested in Task 8.
- [ ] **Step 2: Compile** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Guard tests** — run `./gradlew :app:testDebugUnitTest` → PASS (`corePackagesDoNotImportAndroid` still passes because the new classes are in `ui/`).
- [ ] **Step 4: Commit** — `git commit -m "Add the bubble, caption, confirmation card and summary card overlays"`

---

### Task 11: Task screen and `OverlayUi`

**Files:**
- Create: `ui/TaskInputActivity.kt`, `ui/OverlayUi.kt`, `res/values/themes.xml`
- Modify: `AndroidManifest.xml`

**Interfaces:**
- Consumes: `AgentUi`, `ConfirmRequest`, `ConfirmCard`, `CaptionView`, `BubbleOverlay`.
- Produces:
```kotlin
class OverlayUi(
    private val confirmCard: ConfirmCard, private val caption: CaptionView,
    private val openAnswerScreen: (question: String) -> Unit,
) : AgentUi {
    fun completeAnswer(answer: String?)          // called by the task screen through AgentSession
    fun closeAll()                               // removes any card, clears the caption, completes a pending answer with null
}
class TaskInputActivity : Activity()             // extras: EXTRA_QUESTION (String?) -> answer mode when present
/** What the task screen needs from the session. `AgentSession` (Task 13) implements it and sets [current] while the service is on; this is the spec's process-local `AgentSession.current` holder. */
interface TaskScreenHost {
    fun startTask(text: String)
    fun dropSavedScreen()
    fun completeAnswer(answer: String?)
    companion object { @Volatile var current: TaskScreenHost? = null }
}
```
`OverlayUi.confirm` shows the card and suspends in `suspendCancellableCoroutine`, removing the card in `invokeOnCancellation`. `ask` calls `openAnswerScreen(question)` and awaits a `CompletableDeferred<String?>` that `completeAnswer` completes; cancellation also closes the answer screen. Task screen: task mode has a text box, Go and Cancel; empty rejected, text capped at 2,000 characters via an `InputFilter`; Go calls `TaskScreenHost.current?.startTask(text)` then `finish()`; Cancel, Back and an outside tap call `TaskScreenHost.current?.dropSavedScreen()`. Answer mode shows the question above the box; Submit calls `TaskScreenHost.current?.completeAnswer(text)`, dismissing calls `completeAnswer(null)`; the session closes it on cancel. Manifest entry: `exported="false"`, `taskAffinity=""`, `excludeFromRecents="true"`, translucent theme.

- [ ] **Step 1: Implement** `OverlayUi` and `TaskInputActivity`, adding the activity to the manifest.
- [ ] **Step 2: Compile** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.- [ ] **Step 3: Run the whole suite** — `./gradlew :app:testDebugUnitTest` → PASS (`manifestMatchesAllowlist` unchanged).
- [ ] **Step 4: Commit** — `git commit -m "Add the task screen and the overlay implementation of AgentUi"`

---

### Task 12: `AccessibilityDevice`

**Files:**
- Create: `android/AccessibilityDevice.kt`, `android/ScreenReader.kt`

**Interfaces:**
- Consumes: `DevicePort`, `WindowPicker`, `TreeConverter`, `Settler`, `Revalidator`, `GesturePlanner`, `AppMatcher`, `BlockedApps`, `DeviceUnavailable`, `LauncherApp`.
- Produces:
```kotlin
class AccessibilityDevice(
    private val service: AccessibilityService, private val settler: Settler,
    private val hideOverlays: suspend (hidden: Boolean) -> Unit,        // main-thread hide/show of bubble and caption
    private val isClosed: () -> Boolean,
) : DevicePort {
    suspend fun readScreenNow(): Screen            // no settle; used once at bubble tap
    fun dropHandles()                              // clears the node map
}
```
Implements spec §4 and §5: `ScreenReader` copies the live tree into `RawNode`s on `Dispatchers.Default` (each node read is a binder call), picks the window with `WindowPicker`, builds the `Screen` with `TreeConverter` (`appLabel` from `PackageManager.getApplicationLabel`, `ownWindowBounds` from the picker, observation counter incremented per read) and keeps the observation's id-to-live-node map. `readScreen` = `settler.settle()` then hide overlays, read, show overlays. `perform` first throws `DeviceUnavailable("The phone locked")` when `KeyguardManager.isKeyguardLocked`, and `DeviceUnavailable("Pocket Agent's accessibility service was turned off")` when `isClosed()`; the same two checks run in `readScreen` and `screenshot`. Then each action as in the spec §5 table, with re-validation first (`Failed("screen changed")` on any mismatch, `nodeStillMatches` for node actions, `pointStillMatches` for `TapAt` after re-reading the active window); `TypeText` refuses password fields and returns `Failed("could not type here")` on failure; `Scroll` tries the node action then a `GesturePlanner` swipe; `OpenApp` uses `queryIntentActivities` for the launcher intent (user of the service only), `AppMatcher.resolve`, then `startActivity` with `FLAG_ACTIVITY_NEW_TASK`, mapping a throw to `Failed("could not open <name>")`. `dispatchGesture` is wrapped in `suspendCancellableCoroutine`; a cancelled gesture gives `Failed("gesture interrupted")`; overlays are hidden for the gesture. `screenshot()` hides overlays, calls `takeScreenshot(Display.DEFAULT_DISPLAY, ...)`, copies the hardware bitmap, scales so the long side is at most 1,280 px, encodes PNG, returns null on a secure-window error, and on the rate limit waits 1 s and retries once, then returns null. A null or empty root gives an empty `Screen` (a root group with no children, current package).

- [ ] **Step 1: Implement** `ScreenReader` and `AccessibilityDevice` to the behaviour above, using only the pure units for decisions.
- [ ] **Step 2: Compile** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Run the whole suite** — `./gradlew :app:testDebugUnitTest` → PASS.
- [ ] **Step 4: Commit** — `git commit -m "Implement DevicePort on the accessibility service"`

---

### Task 13: `AgentSession`, the service, the launcher and the manifest

**Files:**
- Create: `android/AgentSession.kt`, `android/PocketAccessibilityService.kt`, `ui/LauncherActivity.kt`, `res/xml/accessibility_config.xml`
- Modify: `AndroidManifest.xml`, `ArchitectureTest.kt`

**Interfaces:**
- Consumes: everything above, `Providers.create`, `OkHttpTransport`, `HttpCaller`, `AgentRunner`, `AgentLoop`, `SafetyGate`, `BlockedApps`, `PlaybookStore`, `ScreenSerializer`, `DevConfig`.
- Produces:
```kotlin
class AgentSession(service: PocketAccessibilityService) : TaskScreenHost {
    // sets TaskScreenHost.current when created and clears it in close()
    fun startBubbleTap()                       // hide bubble, readScreenNow(), show bubble, remember the screen, open TaskInputActivity
    override fun startTask(text: String)       // validates, then runner.start(text, savedScreen)
    override fun dropSavedScreen()
    override fun completeAnswer(answer: String?)
    fun close()                                // cancels runner and scope, removes overlays, drops screen and node map, clears TaskScreenHost.current
}
```
Session wiring (spec §2, §4, §7): scope `SupervisorJob() + Dispatchers.Default`; `ScreenSerializer(ownPackage, maxChars = 12_000)`; `SafetyGate(BlockedApps(), ownPackage)`; the client from `Providers.create(DevConfig.load() ?: no model, HttpCaller(OkHttpTransport()))`. With no valid config, a bubble tap starts no task and the summary card says "No model configured" plus, in debug builds, the five `local.properties` keys. A collector on `runner.state` drives `BubbleLogic.onState`, `bubble.render`, the Done/Error 3 s return to idle (`BUBBLE_RESULT_MS`), the summary card (`SummaryText`, including `Stopped by you` for `Stopped`), `onCancel` cleanup (`OverlayUi.closeAll()`, close any task screen), and switching `serviceInfo.eventTypes` (four types while not `Idle`, none otherwise; the service forwards every accessibility event to `Settler.onEvent`). Bubble commands: `OPEN_TASK_SCREEN` runs `startBubbleTap`, `CANCEL` calls `runner.cancel()`. The service's `onServiceConnected` creates the session and shows the bubble; `onUnbind` and `onDestroy` call `close()`; if the service stops during a task, show a `Toast` "Pocket Agent stopped: accessibility was turned off". `LauncherActivity` has one button opening `Settings.ACTION_ACCESSIBILITY_SETTINGS`. Manifest: the service (`exported="false"`, `permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`, the `android.accessibilityservice.AccessibilityService` intent filter, `@xml/accessibility_config`), the launcher (the only `exported="true"`), and `<queries>` with the `MAIN`/`LAUNCHER` intent. The service label and description say it reads the screen and acts on it when the user starts a task.

- [ ] **Step 1: Write failing architecture tests** in `ArchitectureTest`: `onlyTheLauncherIsExported`; `serviceRequiresTheBindPermission`; `serviceConfigRequestsNoEventsAndTheMinimumFlags` (parses `accessibility_config.xml`: no `accessibilityEventTypes` attribute, `accessibilityFlags` is exactly `flagRetrieveInteractiveWindows`, `canRetrieveWindowContent`, `canPerformGestures` and `canTakeScreenshot` are `true`, no `canRequestFilterKeyEvents`); `manifestDeclaresLauncherQueriesOnly` (a `<queries>` element, no `QUERY_ALL_PACKAGES`); `taskScreenHasItsOwnTask` (`taskAffinity=""`, `excludeFromRecents="true"`, `exported="false"`).
- [ ] **Step 2: Run to fail** — `--tests "*ArchitectureTest"` → FAIL.
- [ ] **Step 3: Implement** the session, service, launcher, config XML and manifest entries; replace any Task 11 stub.
- [ ] **Step 4: Run to pass** — `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin` → PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -m "Run the agent inside the accessibility service"`

---

### Task 14: Device checklist and CI compile check

**Files:**
- Modify: `docs/TESTING.md`, `.github/workflows/ci.yml`

- [ ] **Step 1: Add a "Device checklist (S3)" section to `docs/TESTING.md`** with the nine items from spec §10 verbatim in intent (T3 screen-change, T5 screen-filter app, long-press stop within 1 s during a model call/gesture/confirmation/answer screen, split screen and rotation, lock during a task, accessibility off during a task, secure-window screenshot, `adb shell dumpsys accessibility` shows no event types while idle, the exit gate), each with the expected result and a tick box; plus how to set the five `local.properties` keys (file is git-ignored, never share it).
- [ ] **Step 2: Extend CI** so the one step becomes `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin`, keeping every action pinned by SHA.
- [ ] **Step 3: Verify locally** — run the same command → PASS. Say plainly that CI itself has not run until the branch is pushed.
- [ ] **Step 4: Commit** — `git commit -m "Add the device checklist and compile the Android code in CI"`

---

### Task 15: Run on the phone (exit gate)

**Files:**
- Modify: `docs/superpowers/specs/2026-11-08-s3-device-layer.md` (status and open item 1), `docs/superpowers/plans/00-roadmap.md` if the gate result changes it

This task is done by hand with the user, not by an agent alone. Ask before installing anything on the phone.

- [ ] **Step 1: Install** the debug build with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, then allow restricted settings and enable the service as in the spike 1 findings.
- [ ] **Step 2: One-line Swiggy check (open item 1).** Temporarily add `flagIncludeNotImportantViews`, reinstall, open Swiggy's empty cart, and record whether labels appear. Revert the flag (the `serviceConfigRequestsNoEventsAndTheMinimumFlags` test fails if it stays). Write the result in the spec's open item 1 for S5.
- [ ] **Step 3: Run checklist items 1 to 8** and tick them in `docs/TESTING.md` with the date. Any failure is fixed or recorded as an open item before Step 4.
- [ ] **Step 4: Exit gate.** View an address in WhatsApp, tap the bubble, say "Take me there"; Google Maps must start directions. One clean run.
- [ ] **Step 5: Commit** the spec and testing updates — `git commit -m "Record the device checklist and exit gate results"` — and mark the spec Status as approved with the date.

---

## Self-review

**Spec coverage:** §1 exit gate and checklist: Tasks 14, 15. §2 units: `RawNode`/`TreeConverter` T2, `WindowPicker` T3, `Settler` T4, `Revalidator` T5, `GesturePlanner` T6, `AppMatcher` T7, `DeviceUnavailable` T1, `BubbleLogic` T8, `PocketAccessibilityService`/`AgentSession` T13, `AccessibilityDevice` T12, `DevConfig` T9, views T10, `TaskInputActivity`/`OverlayUi` T11, `LauncherActivity` T13. §3 service config: T13 (guard test). §4 reading: T2, T3, T4, T12, `maxChars` in T13. §5 acting and `open_app`, gestures, screenshots: T5 to T7, T12. §6 UI: T8 (logic and summary text), T10, T11. §7 lifecycle, errors, core changes: T1, T12, T13. §8 debug config: T9. §9 manifest: T11, T13. §10 testing: tests are in each task, device checklist in T14. §11 open items: Swiggy check in T15; second phone left optional. §12 decisions: reflected in T1, T9, T11, T13.

**Step scan and types:** `ConvertedTree.rawById` is consumed by `ScreenReader` in Task 12; the unused `indexOf` mention in the Task 2 doc comment is covered by `rawById`. `Direction` meaning is defined once (Task 6) and used by Task 12's swipe. `BUBBLE_RESULT_MS` is defined in Task 8 and used in Task 13. The task screen reaches the session only through `TaskScreenHost` (Task 11), which `AgentSession` implements in Task 13, so Task 11 compiles on its own.

**Known limits:** Tasks 10 to 13 are Android code that the JVM suite cannot exercise; they are checked by compiling, by the architecture tests, and by the device checklist in Task 15. CI cannot run the device checklist.
