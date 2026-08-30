package com.gitofy.ai.provider.registry

import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §10, §11 — Centralized Provider Registry.
 *
 * Delegates to [BuiltInProviders] so there is exactly ONE list of provider
 * definitions in the codebase (shared with ApiProviderClient).
 *
 * Additional providers can be registered at runtime without rewriting the
 * UI (PRD §11).  The list does NOT claim to be exhaustive.
 */
@Singleton
class ProviderRegistryData @Inject constructor() {

    /** The built-in Custom / OpenAI-compatible definition, always available. */
    val customDefinition: ProviderDefinition
        get() = BuiltInProviders.all.first { it.id == ProviderRegistryData.CUSTOM_ID }

    /** All built-in provider definitions, in display order. */
    fun allDefinitions(): List<ProviderDefinition> = BuiltInProviders.all

    /** Look up a definition by its [id]. */
    fun getDefinition(id: String): ProviderDefinition? = BuiltInProviders.all.find { it.id == id }

    /** The two default providers seeded on fresh install — PRD §7. */
    fun defaultProviderIds(): List<String> = listOf("gemini", "openrouter")

    companion object {
        const val CUSTOM_ID = "custom"
    }
}
