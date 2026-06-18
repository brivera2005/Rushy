package com.streamvault.domain.model

data class ExternalRatingsLookup(
    val contentType: ContentType,
    val title: String,
    val releaseYear: String? = null,
    val tmdbId: Long? = null
) {
    init {
        require(contentType == ContentType.MOVIE || contentType == ContentType.SERIES) {
            "External ratings support only movie and series lookups"
        }
        require(title.isNotBlank()) { "title must not be blank" }
    }
}

data class ExternalRatings(
    val imdb: ExternalRatingValue = ExternalRatingValue.unavailable(),
    val rottenTomatoes: ExternalRatingValue = ExternalRatingValue.unavailable(),
    val metacritic: ExternalRatingValue = ExternalRatingValue.unavailable(),
    val tmdb: ExternalRatingValue = ExternalRatingValue.unavailable(),
    val lastUpdatedEpochMs: Long = 0L
) {
    companion object {
        fun unavailable(lastUpdatedEpochMs: Long = 0L): ExternalRatings = ExternalRatings(
            lastUpdatedEpochMs = lastUpdatedEpochMs
        )
    }
}

data class ExternalRatingValue(
    val displayValue: String,
    val available: Boolean
) {
    companion object {
        fun available(displayValue: String): ExternalRatingValue = ExternalRatingValue(
            displayValue = displayValue,
            available = true
        )

        fun unavailable(): ExternalRatingValue = ExternalRatingValue(
            displayValue = "N/A",
            available = false
        )
    }
}