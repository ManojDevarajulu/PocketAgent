package dev.pocketagent.setup

import dev.pocketagent.llm.CheckResult

/** Plain-language results of the model check. None of them can contain the API key. */
object CheckMessages {
    fun messageFor(result: CheckResult): String = when (result) {
        CheckResult.Works -> "The model answers and can use tools. You can go on."
        CheckResult.NoToolCalls ->
            "This model answered but cannot use tools, so the agent cannot run on it. Pick another model."
        CheckResult.BadKey -> "The provider rejected the key. Check that it is correct and still active."
        CheckResult.Unreachable ->
            "Could not reach the provider. Check the address and your internet connection."
        is CheckResult.Other -> result.message.trim().ifEmpty { "The test failed for an unknown reason. Try again." }
    }
}
