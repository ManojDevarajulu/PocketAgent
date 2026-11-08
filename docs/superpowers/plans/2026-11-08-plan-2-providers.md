# Pocket Agent Plan 2: Providers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the two model clients behind `LlmClient` (Anthropic Messages and OpenAI-compatible Chat Completions), their config validation, presets, HTTP layer and the setup check, all unit-tested on the JVM without a network.

**Architecture:** Everything lives in package `llm`, the only package that may import OkHttp. Clients talk to an `HttpTransport` interface. Tests use a fake transport with recorded responses, and the one OkHttp class is tested against the JDK's built-in `com.sun.net.httpserver.HttpServer`, so no new dependency is needed. A shared `HttpCaller` owns retries, backoff and error mapping.

**Tech Stack:** Kotlin, coroutines, kotlinx.serialization JSON, OkHttp (runtime, new in this plan), JUnit 4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-08-s2-providers.md`. Security rules: `docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md` (T7, T8, T13, section 5). Contract being implemented: Plan 1, Task 9 (`LlmClient`, `LlmSession`, `Turn`, `ToolResult`, `LlmResult`, `FailureKind`).

## Global Constraints

- Carried from Plan 1: Kotlin only; packages `agent`, `llm`, `safety`, `playbook`, `device` import no `android.*`; no telemetry; the API key is never logged or committed; a task ends after 25 steps; commits are small, imperative, and end with the `Co-Authored-By` line; run `git status` before each commit.
- Runtime dependencies stay exactly: `kotlinx-coroutines-android`, `kotlinx-serialization-json`, `okhttp`, `androidx-core-ktx`. Test dependencies stay `junit`, `kotlin-test`, `kotlinx-coroutines-test`. `dependenciesMatchAllowlist` must keep passing. No provider SDKs.
- Only package `llm` imports `okhttp3`. Only `OkHttpTransport` uses it.
- HTTPS only, except plain `http` for `localhost`, `*.local` and 10/8, 172.16/12, 192.168/16, 100.64/10, with a warning. No credentials in URLs. Redirects off. Key only in a header, never in a URL, error message, `toString()` or log.
- Call timeout 90 s, connect timeout 15 s, response body cap 2 MB, `maxOutputTokens` 512 to 8,192 (default 4,096).
- Retries: two, after 1 s then 3 s, only for 429, 5xx, 529 and I/O errors. `Retry-After` honoured up to 10 s. Never retry 401 or 403. Cancellation is never swallowed.
- Anthropic: `anthropic-version: 2023-06-01`; never send `temperature`, `top_p`, `top_k`, `thinking`, or a forced `tool_choice`; never send an empty text block; store and replay each assistant reply's whole `content` array unchanged.

## Review Focus

1. **The key appearing anywhere but the auth header** (URL, error text, `toString()`, exception messages, a provider error body that echoes it). Tested in Tasks 2, 5 and 6.
2. **A redirect to another host.** The key must not follow it. Tested in Task 4.
3. **Malformed model output:** `arguments` that is not valid JSON or not an object, several tool calls in one reply, a reply with no call, an unknown stop reason. Each must become a defined `LlmResult`, never a crash, and the next request must still be valid: both APIs reject a history where a tool call has no matching result, which would end the task with an error on the following step. Tested in Tasks 6 and 7.
4. **Huge or slow responses.** A body over 2 MB and a stalled connection must fail cleanly. Tested in Task 4.
5. **Cancelling during a request or during backoff.** It must stop at once and must not retry. Tested in Task 5.

---

### Task 1: Plan 1 amendments (token usage, prompt wording)

**Files:**
- Modify: `llm/LlmClient.kt`, `agent/TaskOutcome.kt`, `agent/AgentLoop.kt`, `playbook/PlaybookStore.kt`, `src/test/.../fakes/Fakes.kt`, `agent/AgentLoopTest.kt`, `agent/AgentRunnerTest.kt` (its `HangingLlm` session must implement `usage`), `playbook/PlaybookStoreTest.kt`
- Create: `llm/TokenUsage.kt`

**Interfaces:**
- Produces:
```kotlin
data class TokenUsage(val inputTokens: Long = 0, val outputTokens: Long = 0, val cachedInputTokens: Long = 0) {
    operator fun plus(other: TokenUsage): TokenUsage
}
interface LlmSession { suspend fun send(turn: Turn): LlmResult; val usage: TokenUsage }   // running totals, no default getter
data class TaskOutcome(val reason: EndReason, val success: Boolean, val summary: String, val usage: TokenUsage = TokenUsage())
```
The loop fills `usage` from `session.usage` on every outcome it returns after the session exists (`BLOCKED_APP` before the session keeps the default). This is what the summary card shows (T13).

- [ ] **Step 1: Write failing tests** in `AgentLoopTest`: `systemPromptDescribesAppTipsAsTrusted` (`SYSTEM_PROMPT` contains `<app_tips>` and `<screen_data>`, and the `<app_tips>` sentence says it comes from the app, not the screen) and `systemPromptDoesNotSayReplyOfMine` (does not contain `reply of mine`). Add `TokenUsageTest`: `plusAddsEachField`. Add `AgentLoopTest.outcomeCarriesTheSessionUsage` (a `FakeLlm` whose session reports `TokenUsage(10, 5, 2)`; the `DONE` and `STEP_LIMIT` outcomes carry it). Add `PlaybookStoreTest.bodyMentioningAppTipsIsRejected` (a tip must not be able to close its own wrapper).
- [ ] **Step 2: Run to fail** — `--tests "*AgentLoopTest" --tests "*TokenUsageTest" --tests "*PlaybookStoreTest"` → FAIL.
- [ ] **Step 3: Implement.** Add `TokenUsage` and `usage` on `LlmSession`; `FakeLlm` takes an optional `usage: TokenUsage = TokenUsage()` and its session reports it. `PlaybookStore` rejects a body containing `app_tips`, the same way it rejects `screen_data`. In `SYSTEM_PROMPT` change "Each reply of mine shows" to "Each user message shows", and add one sentence: text inside `<app_tips>` is guidance bundled with this app and may be trusted, unlike `<screen_data>`. Keep the prompt free of changing content.
- [ ] **Step 4: Run to pass**, then the full suite — `./gradlew :app:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add token usage to LlmSession and describe app tips in the system prompt"`

---

### Task 2: Provider config and validation

**Files:**
- Create: `llm/ProviderConfig.kt`
- Test: `llm/ProviderConfigTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class ProviderKind { ANTHROPIC, OPENAI_COMPAT }
data class ProviderConfig(
    val kind: ProviderKind, val baseUrl: String, val apiKey: String, val model: String,
    val supportsImages: Boolean, val contextTokens: Int = 128_000, val maxOutputTokens: Int = 4096,
    val effort: String? = null,
) { fun validate(): ConfigCheck; val host: String }   // host: parsed and lower-cased, for exact comparisons
data class ConfigCheck(val errors: List<String>, val warnings: List<String>) { val ok: Boolean }
```

- [ ] **Step 1: Write failing tests** (one assertion each, error text checked by substring):
```kotlin
@Test fun httpsUrlIsValid()
@Test fun httpToPublicHostIsRejected()                 // http://example.com
@Test fun httpToLocalhostAndPrivateRangesIsAllowedWithWarning() // localhost, foo.local, 10.1.2.3, 172.16.0.1, 172.31.255.1, 192.168.1.5, 100.64.0.1 -> ok + 1 warning
@Test fun httpToJustOutsideThePrivateRangesIsRejected() // 172.32.0.1, 100.128.0.1, 192.169.0.1
@Test fun credentialsInUrlAreRejected()                // https://user:pw@host
@Test fun userinfoLookAlikeIsRejected()                // https://api.anthropic.com@evil.test (a credentials part) is rejected
@Test fun hostIsParsedNotPrefixMatched()               // host of https://api.anthropic.com.evil.test is "api.anthropic.com.evil.test"
@Test fun ipv6LiteralsOverPlainHttpAreRejected()       // http://[::1] is rejected in v0.1; only IPv4 literals are classified
@Test fun otherSchemesQueriesAndFragmentsAreRejected() // ftp://, file://, https://h/?k=1, https://h/#x, empty host
@Test fun modelNameLimits()                            // blank, 101 chars, control char rejected
@Test fun keyWithWhitespaceOrNewlineIsRejected()
@Test fun emptyKeyAllowedOnlyForPlainHttpLocal()
@Test fun numericLimitsAreChecked()                    // contextTokens 3999/2_000_001, maxOutputTokens 511/8193, effort "ultra"
@Test fun toStringNeverContainsTheKey()                // key "sk-secret-123" absent from toString() and from every error message
```
- [ ] **Step 2: Run to fail** — `--tests "*ProviderConfigTest"` → FAIL.
- [ ] **Step 3: Implement** with `java.net.URI` parsing (no `android.*`). Private-range test parses the host as an IPv4 literal; hostnames other than `localhost` and `*.local` are public. Override `toString()` to print `apiKey=***`. Error strings never include the key.
- [ ] **Step 4: Run to pass**, then the full suite → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add provider config with URL, key and limit validation"`

---

### Task 3: Provider presets

**Files:**
- Create: `llm/ProviderPresets.kt`
- Test: `llm/ProviderPresetsTest.kt`

**Interfaces:**
- Consumes: `ProviderConfig` (Task 2).
- Produces:
```kotlin
data class ProviderPreset(
    val id: String, val label: String, val kind: ProviderKind, val baseUrl: String,
    val imagesByDefault: Boolean, val needsKey: Boolean, val effort: String?,
)
object ProviderPresets {
    val all: List<ProviderPreset>                       // the seven named rows of spec section 10, in order; Custom is the setup screen's free-entry path, not a preset
    fun byId(id: String): ProviderPreset?
    fun configFor(preset: ProviderPreset, apiKey: String, model: String, supportsImages: Boolean = preset.imagesByDefault): ProviderConfig
}
```

- [ ] **Step 1: Write failing tests:** `everyPresetPassesValidationWithAKeyAndModel` (Ollama with an empty key), `idsAreUnique`, `onlyAnthropicSendsEffortLow`, `imagesByDefaultMatchTheSpec` (Anthropic, OpenAI and Gemini true; OpenRouter, DeepSeek, Groq and Ollama false, because "per model" means the user turns it on), `anthropicPresetIsAnthropicKind`, `ollamaIsPlainHttpLocalAndNeedsNoKey`, `noPresetCarriesAModelName` (the type has no model field).
- [ ] **Step 2: Run to fail** → FAIL.
- [ ] **Step 3: Implement** the seven rows as constants. Base URLs are rechecked against each provider's current docs and any difference is recorded in the commit message.
- [ ] **Step 4: Run to pass**, then the full suite → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add provider presets"`

---

### Task 4: HTTP transport

**Files:**
- Create: `llm/HttpTransport.kt` (interface, `HttpResponse`, `BodyTooLargeException`), `llm/OkHttpTransport.kt`
- Modify: `gradle/libs.versions.toml` (add `okhttp`), `app/build.gradle.kts` (`implementation(libs.okhttp)`)
- Test: `llm/OkHttpTransportTest.kt` (local `HttpServer` on port 0, `http://127.0.0.1:<port>`)

**Interfaces:**
- Produces:
```kotlin
data class HttpResponse(val status: Int, val headers: Map<String, String>, val body: String)  // header names lower-case
interface HttpTransport { suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse }
class BodyTooLargeException : java.io.IOException()
class OkHttpTransport(callTimeoutMs: Long = 90_000, connectTimeoutMs: Long = 15_000, maxBodyBytes: Long = 2_000_000) : HttpTransport
```
`post` throws `java.io.IOException` for network failures and `BodyTooLargeException` for an oversized body. Non-2xx statuses are returned, not thrown.

- [ ] **Step 1: Write failing tests:** `sendsPostWithHeadersAndBody`, `returnsStatusHeadersAndBodyOnNon2xx` (500 with a body), `redirectIsNotFollowedAndSecondHostNeverSeesTheKey` (server A answers 302 to server B; B records requests; the response is the 302 and B got none), `bodyOverTheCapFailsWithBodyTooLarge` (cap set small in the test), `stalledServerTimesOutAsIOException` (short timeout), `cancellingTheCoroutineEndsThePostPromptly` (the server handler blocks for 30 s; cancel the job after the request arrives; the job completes within 1 s of real time), `noLoggingOrKeyInExceptionMessages`.
- [ ] **Step 2: Run to fail** → FAIL.
- [ ] **Step 3: Implement** one shared `OkHttpClient` (`followRedirects(false)`, `followSslRedirects(false)`, `retryOnConnectionFailure(false)`), `suspendCancellableCoroutine` around `Call.enqueue` with `invokeOnCancellation { call.cancel() }`, and a streamed body read that stops at `maxBodyBytes`. No interceptors that log. Add the catalog entry and dependency.
- [ ] **Step 4: Run to pass**, then the full suite (`ArchitectureTest` must still pass) → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add OkHttp transport with no redirects, body cap and cancellation"`

---

### Task 5: HTTP caller (retries and failure mapping)

**Files:**
- Create: `llm/HttpCaller.kt`
- Test: `llm/HttpCallerTest.kt` (fake `HttpTransport`, `runTest` virtual time)

**Interfaces:**
- Consumes: `HttpTransport`, `HttpResponse` (Task 4), `FailureKind` (Plan 1).
- Produces:
```kotlin
sealed interface HttpOutcome {
    data class Success(val body: String) : HttpOutcome
    data class Failure(val kind: FailureKind, val message: String, val status: Int?) : HttpOutcome
}
class HttpCaller(private val transport: HttpTransport) {
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpOutcome
}
```
Messages are fixed phrases plus the status code (for example `"The provider rejected the API key (401)"`). The provider's response body and the headers are never copied into a message.

- [ ] **Step 1: Write failing tests:**
```kotlin
@Test fun status200IsSuccess()
@Test fun statusMapping()                       // 401,403 AUTH; 429 RATE_LIMIT; 500,502,529 SERVER; 400,404,413,422 OTHER; 302 OTHER
@Test fun retriesTwiceWithBackoffThenFails()    // 500,500,500: three posts, virtual time 4_000 ms (1 s + 3 s), Failure(SERVER)
@Test fun succeedsOnTheSecondTry()
@Test fun doesNotRetryAuthOrOtherClientErrors() // exactly one post
@Test fun retryAfterIsHonouredUpTo10Seconds()   // Retry-After: 5 -> waits 5_000 not 1_000
@Test fun retryAfterThatIsNotWholeSecondsIsIgnored() // an HTTP date or "abc" -> the normal 1 s backoff
@Test fun retryAfterOver10SecondsStopsWithoutWaiting() // Failure(RATE_LIMIT) after one post
@Test fun ioErrorIsNetworkAndRetried()
@Test fun bodyTooLargeIsServerAndNotRetried()
@Test fun cancellationDuringBackoffStopsAtOnce() // cancel while delaying; no further post; CancellationException propagates
@Test fun messagesNeverContainTheKeyOrTheProviderBody() // key in header, provider body echoes it; assert absent from Failure.message
```
- [ ] **Step 2: Run to fail** → FAIL.
- [ ] **Step 3: Implement** the status table from spec section 4. Use `kotlinx.coroutines.delay` for backoff. Catch `IOException` only; `CancellationException` is never caught.
- [ ] **Step 4: Run to pass**, then the full suite → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add HTTP caller with retries, backoff and failure mapping"`

---

### Task 6: Anthropic client

**Files:**
- Create: `llm/AnthropicClient.kt`
- Test: `llm/AnthropicClientTest.kt`, with recorded replies in `src/test/resources/llm/anthropic/*.json`

**Interfaces:**
- Consumes: `ProviderConfig`, `HttpCaller`, `HttpOutcome`, `ToolDefinition`, `Turn`, `LlmResult`, `TokenUsage`.
- Produces: `class AnthropicClient(private val config: ProviderConfig, private val caller: HttpCaller) : LlmClient` (`supportsImages = config.supportsImages`). Each `newSession` keeps its own append-only message list and `usage`.

Request shape: `POST {baseUrl}/v1/messages` (a trailing `/` on the base URL is ignored); headers `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`; body has `model`, `max_tokens = config.maxOutputTokens`, `system` as one text block with `cache_control: {"type":"ephemeral"}`, `tools` (name, description, `input_schema`, `strict: true`), `tool_choice: {"type":"auto","disable_parallel_tool_use":true}`, `messages`, top-level `cache_control: {"type":"ephemeral"}` so the growing history is cached too (every step resends all earlier observations, so this is the main cost saving, T13), and `output_config: {"effort": …}` when `config.effort` is set. When `config.host` equals `api.anthropic.com` exactly (never a prefix match) and the model is `claude-fable-5-1`, `claude-opus-5-5`, `claude-opus-5` or `claude-sonnet-5-5`, also header `anthropic-beta: server-side-fallback-2026-07-01` and body `"fallbacks": "default"`.

- [ ] **Step 1: Write failing tests** (each builds a fake `HttpTransport` that records the request and returns a recorded reply):
```kotlin
@Test fun requestHasTheRightUrlHeadersAndFields()      // url, x-api-key, anthropic-version, system cache_control, strict tools, tool_choice auto + disable_parallel, no temperature/top_p/top_k/thinking
@Test fun keyIsOnlyInTheHeader()                       // absent from url and body
@Test fun effortIsSentOnlyWhenConfigured()
@Test fun fallbacksOnlyForListedModelsOnTheAnthropicHost()
@Test fun fallbacksNotSentToALookAlikeHost()           // https://api.anthropic.com.evil.test with claude-opus-5-5
@Test fun a400AfterEffortOrFallbacksRetriesOnceWithoutThemAndRemembers() // remembered only if the retry succeeds; later sends omit them
@Test fun a400ThatPersistsWithoutTheOptionalFieldsForgetsNothing()        // both attempts 400: Failed(OTHER); the next send still includes them
@Test fun toolUseReplyBecomesCall()                    // stop_reason tool_use -> Call(id, name, input JsonObject)
@Test fun onlyTheFirstToolUseIsKept()
@Test fun droppedToolUsesAreAnsweredInTheNextRequest() // each extra tool_use id gets a tool_result with is_error true and "ignored: one action per step", or the API rejects the history
@Test fun textOnlyReplyIsNoCall()                      // end_turn; and max_tokens with no tool block
@Test fun refusalStopReasonIsRefusedWithExplanationOrNull() // explanation from stop_details.explanation; null when absent
@Test fun unknownStopReasonWithNoToolIsNoCall()
@Test fun toolResultAndTextAreSentInOneUserMessage()   // tool_result first; Turn.text as a text block after it; empty text omitted entirely
@Test fun imageGoesAfterTheToolResultAsBase64Png()
@Test fun systemNoteIsItsOwnAppTipsBlockBeforeTheScreenText() // <app_tips>…</app_tips>, never merged into the screen block
@Test fun assistantContentIncludingThinkingBlocksIsReplayedUnchanged() // second request contains the first reply's content array byte-for-byte
@Test fun historyIsAppendOnly()                        // earlier messages are identical in every later request
@Test fun usageAccumulatesInputOutputAndCachedTokens()
@Test fun automaticCachingIsRequested()                 // top-level cache_control present
@Test fun httpFailuresMapToFailed()                    // 401 -> Failed(AUTH, …); message has no key
@Test fun aRetryDoesNotDuplicateTheUserMessage()        // the optional-field retry resends the same history, not the user turn twice
@Test fun malformedJsonReplyIsFailedOther()
@Test fun cancellationPropagates()
```
- [ ] **Step 2: Run to fail** → FAIL.
- [ ] **Step 3: Implement.** Build requests with `buildJsonObject`; parse replies with `Json.parseToJsonElement`; keep `content` as the raw `JsonArray` for replay. Use `config.supportsImages` for the image block; a `Turn.image` on a client with `supportsImages = false` is dropped. Map failures from `HttpOutcome.Failure` unchanged. The user message joins the session history only when its request succeeds, so a retry or a failure never leaves two user turns in a row. Tests run through a real `HttpCaller` over the fake transport, under `runTest`, so backoff uses virtual time.
- [ ] **Step 4: Run to pass**, then the full suite → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add Anthropic Messages client"`

---

### Task 7: OpenAI-compatible client

**Files:**
- Create: `llm/OpenAiCompatClient.kt`
- Test: `llm/OpenAiCompatClientTest.kt`, with recorded replies in `src/test/resources/llm/openai/*.json` (the spike 3 response was not saved, so write one by hand from the quirks in `docs/spikes/2026-11-findings.md`: `arguments` as a string, a `reasoning` field, a call `id`)

**Interfaces:**
- Consumes: as Task 6.
- Produces: `class OpenAiCompatClient(private val config: ProviderConfig, private val caller: HttpCaller) : LlmClient`.

Request shape: `POST {baseUrl}/chat/completions` (the base URL may end with or without `/`); `Authorization: Bearer <key>` omitted when the key is empty; body has `model`, the output limit, `messages` (a `system` message first), `tools` as `{type:"function", function:{name, description, parameters}}`, `tool_choice: "auto"`, `parallel_tool_calls: false`. The output limit is `max_completion_tokens` when `config.host` is `api.openai.com` (its newer models reject `max_tokens`) and `max_tokens` everywhere else. `strict` is never sent: OpenAI's strict mode requires every property to be required, and Plan 1's `tap` schema has optional fields, so every request would be rejected. `Tools.parse` already validates every call.

Message layout per turn: a `ToolResult` becomes a `role: "tool"` message with `tool_call_id` and `content = toolResult.text`. Then one `user` message whose parts are, in order, the `<app_tips>` part (if any), `Turn.text` (if not empty) and the image (if any). If none of those exist, no user message is sent.

- [ ] **Step 1: Write failing tests:**
```kotlin
@Test fun requestHasTheRightUrlHeadersAndFields()
@Test fun noAuthHeaderWhenKeyIsEmpty()
@Test fun strictIsNeverSent()
@Test fun openAiHostUsesMaxCompletionTokens()          // api.openai.com -> max_completion_tokens; openrouter.ai -> max_tokens
@Test fun a400AfterParallelToolCallsRetriesOnceWithoutItAndRemembers()
@Test fun toolCallsReplyBecomesCall()                  // arguments is a JSON string, parsed to JsonObject; id kept
@Test fun reasoningFieldIsIgnored()                    // from the spike 3 recording
@Test fun onlyTheFirstToolCallIsKept()
@Test fun droppedToolCallsAreAnsweredInTheNextRequest() // each extra id gets a tool message "ignored: one action per step"
@Test fun argumentsThatAreNotJsonOrNotAnObjectBecomeACallWithEmptyInput() // "not json", "[1]": Call(id, name, {}), so the loop answers that id with a tool error and the history stays valid
@Test fun stopWithoutCallAndLengthAreNoCall()
@Test fun contentFilterIsRefused()
@Test fun toolResultIsARoleToolMessageWithTheCallId()
@Test fun imageGoesInAFollowingUserMessageAsImageUrlDataUri()
@Test fun systemNoteIsAnAppTipsPartBeforeTheScreenText()
@Test fun emptyTurnTextIsOmitted()
@Test fun assistantMessageWithToolCallsIsReplayedInHistory()
@Test fun olderObservationsAreStubbedAbove70PercentOfContext() // in older user messages the <screen_data …>…</screen_data> span becomes "[earlier screen omitted]" and images are removed; the task line, tool_call/tool pairs and the latest observation are untouched
@Test fun noTrimmingBelowTheThreshold()
@Test fun usageAccumulates()
@Test fun httpFailuresMapToFailedWithoutTheKey()
@Test fun cancellationPropagates()
```
- [ ] **Step 2: Run to fail** → FAIL.
- [ ] **Step 3: Implement.** Token estimate is characters ÷ 3 over the serialized messages. Trimming replaces only the `<screen_data` … `</screen_data>` span and image parts in `user` messages before the last one; nothing else is edited.
- [ ] **Step 4: Run to pass**, then the full suite → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add OpenAI-compatible client with history trimming"`

---

### Task 8: Setup check and client factory

**Files:**
- Create: `llm/ProviderCheck.kt`, `llm/Providers.kt`
- Test: `llm/ProviderCheckTest.kt`

**Interfaces:**
- Consumes: Tasks 2 to 7.
- Produces:
```kotlin
sealed interface CheckResult { data object Works; data object NoToolCalls; data object BadKey; data object Unreachable; data class Other(val message: String) }
object ProviderCheck { suspend fun run(client: LlmClient): CheckResult }
object Providers { fun create(config: ProviderConfig, caller: HttpCaller): LlmClient }   // by kind; throws IllegalArgumentException if validate() has errors
```
`run` opens a session with one trivial tool named `ping` (no fields) and the system prompt "Call the ping tool.", sends one turn, and maps the result: `Call` named `ping` → `Works`; `NoCall` or `Call` of another name → `NoToolCalls`; `Failed(AUTH)` → `BadKey`; `Failed(NETWORK)` → `Unreachable`; `Refused` and other failures → `Other` with a fixed message.

- [ ] **Step 1: Write failing tests** with `FakeLlm`: one per outcome above, plus `createRefusesAnInvalidConfig`, `createPicksTheClientByKind`.
- [ ] **Step 2: Run to fail** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run to pass**, then the full suite → PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add provider setup check and client factory"`

---

### Task 9: Guards and live check

**Files:**
- Modify: `ArchitectureTest.kt`
- Create: `src/test/kotlin/dev/pocketagent/llm/LiveProviderCheckTest.kt`, `docs/TESTING.md` section "Live provider check"

- [ ] **Step 1: Write failing tests** in `ArchitectureTest`: `onlyOkHttpTransportImportsOkHttp` (only `llm/OkHttpTransport.kt` has `import okhttp3`), `noProviderCodeLogsOrPrintsTheKey` (existing logging guard covers `llm`), `noHardcodedKeysInMainSources` (no match for `sk-[A-Za-z0-9_-]{20,}` in `src/main`; the pattern must also catch Anthropic's `sk-ant-…` form; test sources are excluded because they hold fake keys).
- [ ] **Step 2: Prove each guard can fail.** Guards pass on correct code, so temporarily add `import okhttp3.OkHttpClient` to `AnthropicClient.kt` and a fake `sk-ant-` key string to `ProviderPresets.kt`, run, see both fail, then remove both.
- [ ] **Step 3: Add `LiveProviderCheckTest`**, skipped with `Assume.assumeTrue` unless `POCKET_AGENT_LIVE_KEY`, `POCKET_AGENT_LIVE_KIND`, `POCKET_AGENT_LIVE_BASE_URL` and `POCKET_AGENT_LIVE_MODEL` are set. It runs `ProviderCheck.run` through `OkHttpTransport` and asserts `Works`. Document the commands for the Anthropic run and an OpenRouter run in `docs/TESTING.md`, and say that CI never sets these.
- [ ] **Step 4: Run to pass** — `./gradlew :app:testDebugUnitTest` → PASS, with the live test reported as skipped.
- [ ] **Step 5: Commit** — `git commit -m "Add provider architecture guards and an opt-in live check"`

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes locally and in CI, with the live check skipped.
- The live check has passed once against an OpenAI-compatible model. The Anthropic live run happens when the user supplies a key; until then the README says that client is verified against recorded replies only.
- `git status` is clean and `git log` shows one focused commit per task.
- Next: write S3 (device layer) and its plan.
