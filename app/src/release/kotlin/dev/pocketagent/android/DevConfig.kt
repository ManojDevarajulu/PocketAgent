package dev.pocketagent.android

import dev.pocketagent.llm.ProviderConfig

/** Release builds carry no developer provider settings: there is nothing to load. */
object DevConfig {
    fun load(): ProviderConfig? = null
}
