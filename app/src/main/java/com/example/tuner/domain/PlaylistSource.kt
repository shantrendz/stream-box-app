package com.example.tuner.domain

import com.example.tuner.data.model.CustomSource

/**
 * Region, Category, and Language are combinable filters (each defaults to "All") applied
 * together against the catalog — see [ChannelListViewModel]. CustomSources / Favorites /
 * History are separate, mutually-exclusive top-level modes with their own data source.
 */
sealed class PlaylistSource {

    data class Region(val code: String, val displayName: String) : PlaylistSource() {
        companion object {
            const val ALL_CODE = "__all__"
        }
    }

    data class Category(val slug: String, val displayName: String) : PlaylistSource() {
        companion object {
            const val ALL_SLUG = "__all__"
        }
    }

    /** ISO 639-2 code, e.g. "eng" — matches iptv-org's languages/<code>.m3u playlists. */
    data class Language(val code: String, val displayName: String) : PlaylistSource() {
        companion object {
            const val ALL_CODE = "__all__"
        }
    }

    data class CustomSources(val sources: List<CustomSource>) : PlaylistSource()

    /** Starred channels, read from local storage — no network fetch. */
    data object Favorites : PlaylistSource()

    /** Recently-played channels, read from local storage — no network fetch. */
    data object History : PlaylistSource()

    companion object {
        val REGIONS: List<Region> = listOf(
            Region(Region.ALL_CODE, "All channels"),
            Region("us", "United States"),
            Region("uk", "United Kingdom"),
            Region("ca", "Canada"),
            Region("de", "Germany"),
            Region("fr", "France"),
            Region("in", "India"),
            Region("ae", "United Arab Emirates"),
            Region("br", "Brazil"),
            Region("jp", "Japan"),
            Region("au", "Australia")
        )

        val CATEGORIES: List<Category> = listOf(Category(Category.ALL_SLUG, "All categories")) + listOf(
            "animation", "auto", "business", "classic", "comedy", "cooking", "culture",
            "documentary", "education", "entertainment", "family", "general", "kids",
            "legal", "lifestyle", "movies", "music", "news", "outdoor", "relax",
            "religious", "science", "series", "shop", "sports", "travel", "weather"
        ).map { slug -> Category(slug, slug.replaceFirstChar { it.uppercase() }) }

        val LANGUAGES: List<Language> = listOf(
            Language(Language.ALL_CODE, "All languages"),
            Language("eng", "English"),
            Language("spa", "Spanish"),
            Language("fra", "French"),
            Language("deu", "German"),
            Language("por", "Portuguese"),
            Language("ara", "Arabic"),
            Language("hin", "Hindi"),
            Language("rus", "Russian"),
            Language("zho", "Chinese"),
            Language("jpn", "Japanese"),
            Language("ita", "Italian"),
            Language("tur", "Turkish")
        )
    }
}
