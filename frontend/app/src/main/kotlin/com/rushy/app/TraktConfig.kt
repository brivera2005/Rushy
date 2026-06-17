package com.rushy.app

/**
 * Public Trakt OAuth client identifier — safe to commit (standard for mobile/TV apps).
 * Client secret is injected at build time via trakt.properties / local.properties only.
 */
object TraktConfig {
    const val CLIENT_ID = "512f6bfe24c45d58153d3dd6814ace2037927045d3ab2ad61468348aba92876f"
}
