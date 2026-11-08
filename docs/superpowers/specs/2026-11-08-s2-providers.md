# S2: Providers

**Date:** 2026-11-08
**Status:** Approved by the user on 2026-11-08, with the recommended answers to decisions 1 and 2. Anthropic facts verified against the API reference the same day.
**Builds on:** main spec §7 (LLM integration), security spec §5 (provider hardening) and rules T7, T8, T13, spike 3 findings.
**Implements the interfaces from Plan 1, Task 9:** `LlmClient`, `LlmSession`, `Turn`, `ToolResult`, `LlmResult`, `FailureKind`.

## 1. Scope

Two clients, one config type, one set of presets, and the setup test request. All of it lives in package `llm`, which is the only package allowed to import OkHttp (guarded by `ArchitectureTest`). No provider SDKs.

| Unit | Job |
|---|---|
| `ProviderConfig` | Kind, base URL, key, model, flags. `validate()` returns a list of problems |
| `ProviderPresets` | Fixed list used by the setup screen |
| `HttpTransport` | The one place OkHttp is used: timeouts, no redirects, size cap, header handling, error mapping |
| `AnthropicClient` | Messages API |
| `OpenAiCompatClient` | Chat Completions API |
| `ProviderCheck` | The setup test request: does the model answer and make a tool call |

## 2. `ProviderConfig`

```kotlin
enum class ProviderKind { ANTHROPIC, OPENAI_COMPAT }
data class ProviderConfig(
    val kind: ProviderKind, val baseUrl: String, val apiKey: String, val model: String,
    val supportsImages: Boolean, val contextTokens: Int = 128_000, val maxOutputTokens: Int = 4096,
    val effort: String? = null,
)
```

`validate()` rules (security spec §5):
- `https` only. Plain `http` is allowed for `localhost`, `*.local` and the private ranges 10/8, 172.16/12, 192.168/16 and 100.64/10, and the result carries a warning the UI must show.
- Reject credentials in the URL (`user:pass@host`), any other scheme, an empty host, and a query string or fragment on the base URL.
- Model name: 1 to 100 characters, no control characters. Key: no whitespace or control characters (a pasted newline is the usual cause of 401s). It may be empty only for a plain-`http` local server.
- `contextTokens` 4,000 to 2,000,000. `maxOutputTokens` 512 to 8,192 (T13); the default is 4,096 because current Claude models think before they call a tool, and 1,024 can cut a reply off. `effort`, when set, is one of `low`, `medium`, `high`, `xhigh`, `max`. The Anthropic preset sets `low`.

`toString()` of the config never contains the key.

## 3. `HttpTransport` rules

- `HttpTransport` is an interface (`suspend fun post(url, headers, body): HttpResponse`). `OkHttpTransport` is the only implementation and the only code that touches OkHttp; clients and retries are tested against a fake. One shared `OkHttpClient`. Redirects off (`followRedirects(false)`, `followSslRedirects(false)`): a 3xx is an error, so a key is never sent to a second host.
- Call timeout 90 s, connect timeout 15 s (T13).
- The key goes only in a header: `x-api-key` for Anthropic, `Authorization: Bearer` for OpenAI-compatible. Never in the URL.
- The response body is read with a 2 MB cap. A larger body is a `SERVER` failure.
- No logging interceptor. Nothing from a request or response is logged.
- Error messages are built from the status code and a fixed phrase. The provider's error body is never copied into a message, because it can echo the request.

## 4. Failure mapping and retries

| Condition | `FailureKind` | Retried |
|---|---|---|
| 401, 403 | `AUTH` | No |
| 429 | `RATE_LIMIT` | Yes |
| 5xx, 529 | `SERVER` | Yes |
| I/O error, timeout, TLS error | `NETWORK` | Yes |
| Other 4xx (400, 404, 413, 422), malformed JSON, 3xx | `OTHER` | No |

Two retries with backoff of 1 s then 3 s. A `Retry-After` header is honoured up to 10 s; a longer wait stops the task. The retries happen inside the client, so a `Failed` result means they are used up (Plan 1 contract). Cancellation is never swallowed: the call is cancelled with the coroutine.

## 5. Anthropic client

- `POST {baseUrl}/v1/messages`, header `anthropic-version: 2023-06-01`.
- `system` is one text block with `cache_control: ephemeral`. The tool list is sent unchanged so it caches too.
- Tools are `{name, description, input_schema, strict: true}`, taken straight from `ToolDefinition`.
- `tool_choice: {type: "auto", disable_parallel_tool_use: true}`. Forced choice is never used.
- Effort: `output_config: {"effort": "<config.effort>"}` when set. Verified: it goes inside `output_config`, and Opus 5.5 defaults to `medium` if it is left out. Thinking is never configured: on current models it cannot be turned off and omitting it is correct.
- Fallbacks: only when the base URL is `https://api.anthropic.com` and the model is one of `claude-fable-5-1`, `claude-opus-5-5`, `claude-opus-5`, `claude-sonnet-5-5`: send header `anthropic-beta: server-side-fallback-2026-07-01` and body `"fallbacks": "default"`.
- Optional fields: if the API answers 400 and the request carried `effort` or `fallbacks`, the client retries once without them and remembers that for the session. `anthropic-version` stays `2023-06-01`, which means no `temperature`, `top_p` or `top_k` are ever sent (they are rejected on the next version).
- Reply handling: read `stop_reason` first. `tool_use` gives `Call` with the first `tool_use` block. Extra blocks are not run, but each gets an error `tool_result` in the next request, because the API rejects a history with an unanswered tool call (the OpenAI-compatible client does the same with `tool` messages). `end_turn` or `max_tokens` with no tool block gives `NoCall`. `refusal` gives `Refused`. Thinking blocks are kept in history unchanged and never shown. The whole `content` array of every assistant reply is stored and replayed, because current models bind thinking blocks to the conversation. An empty text block is never sent (the API rejects it).
- A `ToolResult` becomes a `tool_result` block in the next user message. An image goes in the same message as an `image` block (base64 PNG), after the tool result. `Turn.text` follows as a text block, and is omitted when empty.

## 6. OpenAI-compatible client

- `POST {baseUrl}/chat/completions`, header `Authorization: Bearer <key>` (omitted when the key is empty).
- Tools are `{type: "function", function: {name, description, parameters}}`. `strict` is never sent: OpenAI's strict mode requires every property to be required, Plan 1's `tap` schema has optional fields, and many compatible servers reject the field. `Tools.parse` validates every call anyway. The output limit is `max_completion_tokens` for `api.openai.com` and `max_tokens` elsewhere.
- `tool_choice: "auto"`, `parallel_tool_calls: false` where supported. If the server rejects that field with a 400, the client retries once without it and remembers that for the session.
- Spike 3 quirks: `function.arguments` is a **JSON string**, parsed here and passed on as a `JsonObject`; a non-object or unparseable string becomes a `Call` with an empty input, so the loop answers that call id with a tool error and the history stays valid. A `reasoning` field is ignored. The call `id` is echoed in the `tool` message.
- `finish_reason`: `tool_calls` gives `Call` (first call only), `stop` or `length` with no call gives `NoCall`, `content_filter` gives `Refused`.
- A `ToolResult` becomes a `role: "tool"` message. A tool message cannot carry an image, so an image goes in a following `user` message as an `image_url` data URI.

## 7. `Turn.systemNote` (playbook text)

Both clients put it in its own text part placed **before** the screen text, wrapped in `<app_tips>...</app_tips>`. It is never merged into the screen data. A mid-conversation `system` role is not used, because several providers reject it. `SYSTEM_PROMPT` gains one sentence saying `<app_tips>` is guidance bundled with the app and is trusted, unlike `<screen_data>`. That is a small change to Plan 1's constant, and its stability test still applies.

## 8. History, tokens and cost

- Each session keeps its history in the provider's own format and only appends.
- Trimming follows security spec §5: the Anthropic client never edits earlier turns. The OpenAI-compatible client estimates tokens as characters ÷ 3 and, above 70% of `contextTokens`, replaces the text of older observations with `[earlier screen omitted]`, keeping tool-call and tool-result pairs intact. The most recent observation is never trimmed.
- `LlmSession` gains `val usage: TokenUsage` (`inputTokens`, `outputTokens`, `cachedInputTokens`, all running totals, zero when the provider omits them). `FakeLlm` returns zeros. The summary card shows the totals (T13).
- No cost in money is computed in v0.1, because prices change and are not in the API response.

## 9. Setup check and tests

- `ProviderCheck.run(config): CheckResult` sends one real request with a single trivial tool and the instruction "call it". Outcomes: `Works`, `NoToolCalls` ("this model answered but did not use tools"), `BadKey`, `Unreachable`, `Other`. It uses the same client, so a pass means the real path works.
- **JVM tests (CI):** no new dependency. Clients are tested against a fake `HttpTransport` that returns recorded responses; `OkHttpTransport` is tested against the JDK's built-in `com.sun.net.httpserver.HttpServer`. Recorded responses for each client: a tool call, a text-only reply, a refusal, each error status, a 3xx, an oversized body, a stringified-arguments call, multiple tool calls, 429 with `Retry-After`, and a cancelled call. Assertions on the request too: header names, key absent from the URL and from every error message, `strict` and `tool_choice` fields, image placement, history order, trimming. Config tests for every `validate()` rule, including `http://192.168.1.5`, `http://example.com`, `https://user:pw@host`, a look-alike `https://api.anthropic.com@evil.test` (rejected), and `https://api.anthropic.com.evil.test` (valid, but never treated as Anthropic's host).
- **Architecture test (extends A1):** only `llm` imports `okhttp3`; no `Log`; `ProviderConfig.toString()` hides the key.
- **Live check (manual, not CI):** a Gradle task or test enabled only when `POCKET_AGENT_LIVE_KEY` is set, run once per client against a real model, plus the first on-phone call. This closes spike 3's open Anthropic item. It needs an Anthropic key from the user.

## 10. Presets

Fixed in code. Base URLs are rechecked against each provider's docs when Plan 2 is written.

| Preset | Kind | Base URL | Images | Notes |
|---|---|---|---|---|
| Anthropic | ANTHROPIC | `https://api.anthropic.com` | yes | |
| OpenRouter | OPENAI_COMPAT | `https://openrouter.ai/api/v1` | per model | Free models exist but are less reliable |
| OpenAI | OPENAI_COMPAT | `https://api.openai.com/v1` | yes | `max_completion_tokens` |
| Gemini | OPENAI_COMPAT | `https://generativelanguage.googleapis.com/v1beta/openai/` | yes | |
| DeepSeek | OPENAI_COMPAT | `https://api.deepseek.com` | no | |
| Groq | OPENAI_COMPAT | `https://api.groq.com/openai/v1` | per model | |
| Ollama | OPENAI_COMPAT | `http://localhost:11434/v1` | per model | Plain-http warning; key optional |
| Custom | OPENAI_COMPAT | typed by the user | asked | |

No preset ships a default model name. The setup screen asks for it, so the code never goes stale when models change.

## 11. Out of scope

Streaming (the loop waits for whole replies), more than two wire formats, automatic model choice, cost in money, and storing the key (that is S4).

## 12. Decisions

1. **Add `usage` to `LlmSession`** (§8): yes.
2. **Edit `SYSTEM_PROMPT`** to describe `<app_tips>` (§7): yes. Also fixes the ambiguous "reply of mine" wording found in the Plan 1 review.
3. **Anthropic key for the live check** (§9): still open. Plan 2 builds the check; it is run when the user supplies a key. Until then the README says the Anthropic client is verified against recorded responses only.
