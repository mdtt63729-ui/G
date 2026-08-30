package com.gitofy.di

import com.gitofy.ai.provider.GeminiProvider
import com.gitofy.ai.provider.NvidiaNimProvider
import com.gitofy.ai.provider.OpenRouterProvider
import com.gitofy.ai.provider.OpenCodeZenProvider
import com.gitofy.ai.provider.CustomProvider
import com.gitofy.ai.provider.ProviderRegistry
import com.gitofy.ai.provider.OpenAiProvider
import com.gitofy.ai.provider.SarvamProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PRD §55: AI Provider Architecture — all 6 mandatory providers + custom.
 *
 * ModelRegistry and ExtendedModelRegistry are now consolidated into
 * AIModelCatalog (single source of truth) — PRD §3, §47.
 * AIModelCatalog has its own @Inject constructor, so no @Provides needed here.
 */
@Module
@InstallIn(SingletonComponent::class)
object AIProviderModule {

    @Provides
    @Singleton
    fun provideProviderRegistry(
        geminiProvider: GeminiProvider,
        nvidiaNimProvider: NvidiaNimProvider,
        openRouterProvider: OpenRouterProvider,
        openCodeZenProvider: OpenCodeZenProvider,
        openAiProvider: OpenAiProvider,
        sarvamProvider: SarvamProvider,
        customProvider: CustomProvider
    ): ProviderRegistry {
        val registry = ProviderRegistry()
        registry.register(geminiProvider)
        registry.register(nvidiaNimProvider)
        registry.register(openRouterProvider)
        registry.register(openCodeZenProvider)
        registry.register(openAiProvider)
        registry.register(sarvamProvider)
        registry.register(customProvider)
        return registry
    }
}
