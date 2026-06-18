package com.streamvault.app.di

import com.streamvault.app.BuildConfig
import com.streamvault.data.remote.gemini.GeminiNaturalLanguageSearchInterpreter
import com.streamvault.domain.search.NaturalLanguageSearchInterpreter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeminiModule {

    @Binds
    @Singleton
    abstract fun bindNaturalLanguageSearchInterpreter(
        impl: GeminiNaturalLanguageSearchInterpreter
    ): NaturalLanguageSearchInterpreter

    companion object {
        @Provides
        @Named("geminiApiKey")
        fun provideGeminiApiKey(): String = BuildConfig.GEMINI_API_KEY.trim()
    }
}
