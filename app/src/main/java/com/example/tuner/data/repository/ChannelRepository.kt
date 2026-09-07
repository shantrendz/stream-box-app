package com.example.tuner.data.repository

import com.example.tuner.data.model.Channel
import com.example.tuner.data.model.CustomSource
import com.example.tuner.data.parser.M3UParser
import com.example.tuner.data.remote.PlaylistApi
import com.example.tuner.data.remote.PlaylistFetchResult
import com.example.tuner.domain.PlaylistSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Result of loading a [PlaylistSource.CustomSources] merge: channels plus per-source status. */
data class CustomSourcesLoadResult(
    val channels: List<Channel>,
    val succeededCount: Int,
    val totalCount: Int,
    val failures: List<Pair<CustomSource, String>>
)

sealed class SingleLoadResult {
    data class Success(val channels: List<Channel>) : SingleLoadResult()
    data class Failure(val message: String) : SingleLoadResult()
}

class ChannelRepository(
    private val api: PlaylistApi = PlaylistApi()
) {

    suspend fun loadRegion(region: PlaylistSource.Region): SingleLoadResult {
        val url = if (region.code == PlaylistSource.Region.ALL_CODE) {
            PlaylistApi.INDEX_URL
        } else {
            PlaylistApi.regionUrl(region.code)
        }
        val label = if (region.code == PlaylistSource.Region.ALL_CODE) {
            "All channels"
        } else {
            "Region: ${region.displayName}"
        }
        return loadAndParse(url, label)
    }

    /**
     * Region, Category, and Language combine as filters over one catalog rather than three
     * exclusive modes: Region picks the base playlist (or the full index for "All channels"),
     * then Category and Language — when not "All" — each fetch their own iptv-org playlist in
     * parallel and narrow the base list down to the intersection of stream URLs. A channel
     * absent from a filter's playlist is treated as not matching it.
     */
    suspend fun loadCatalog(
        region: PlaylistSource.Region,
        category: PlaylistSource.Category,
        language: PlaylistSource.Language
    ): SingleLoadResult = coroutineScope {
        val regionDeferred = async { loadRegion(region) }
        val categoryDeferred = if (category.slug != PlaylistSource.Category.ALL_SLUG) {
            async { loadAndParse(PlaylistApi.categoryUrl(category.slug), "Category: ${category.displayName}") }
        } else null
        val languageDeferred = if (language.code != PlaylistSource.Language.ALL_CODE) {
            async { loadAndParse(PlaylistApi.languageUrl(language.code), "Language: ${language.displayName}") }
        } else null

        var channels = when (val result = regionDeferred.await()) {
            is SingleLoadResult.Success -> result.channels
            is SingleLoadResult.Failure -> return@coroutineScope result
        }

        categoryDeferred?.await()?.let { result ->
            when (result) {
                is SingleLoadResult.Success -> {
                    val allowed = result.channels.mapTo(HashSet()) { it.streamUrl }
                    channels = channels.filter { it.streamUrl in allowed }
                }
                is SingleLoadResult.Failure -> return@coroutineScope SingleLoadResult.Failure("Category filter: ${result.message}")
            }
        }

        languageDeferred?.await()?.let { result ->
            when (result) {
                is SingleLoadResult.Success -> {
                    val allowed = result.channels.mapTo(HashSet()) { it.streamUrl }
                    channels = channels.filter { it.streamUrl in allowed }
                }
                is SingleLoadResult.Failure -> return@coroutineScope SingleLoadResult.Failure("Language filter: ${result.message}")
            }
        }

        SingleLoadResult.Success(channels)
    }

    /**
     * Fetches every saved custom source in parallel and merges the results. A single failed
     * or empty source never blocks the others — failures are reported alongside the merged
     * channel list so the UI can show "N of M sources loaded".
     */
    suspend fun loadCustomSources(sources: List<CustomSource>): CustomSourcesLoadResult = coroutineScope {
        if (sources.isEmpty()) {
            return@coroutineScope CustomSourcesLoadResult(emptyList(), 0, 0, emptyList())
        }

        val deferredResults = sources.map { source ->
            async { source to loadAndParse(source.url, source.label) }
        }

        val allChannels = mutableListOf<Channel>()
        val failures = mutableListOf<Pair<CustomSource, String>>()
        var succeeded = 0

        for (deferred in deferredResults) {
            val (source, result) = deferred.await()
            when (result) {
                is SingleLoadResult.Success -> {
                    succeeded++
                    allChannels += result.channels
                }
                is SingleLoadResult.Failure -> {
                    failures += source to result.message
                }
            }
        }

        CustomSourcesLoadResult(
            channels = allChannels,
            succeededCount = succeeded,
            totalCount = sources.size,
            failures = failures
        )
    }

    private suspend fun loadAndParse(url: String, label: String): SingleLoadResult {
        return when (val result = api.fetch(url)) {
            is PlaylistFetchResult.Success -> {
                val channels = M3UParser.parse(result.body, label)
                if (channels.isEmpty()) {
                    SingleLoadResult.Failure("Empty or unparseable playlist")
                } else {
                    SingleLoadResult.Success(channels)
                }
            }
            is PlaylistFetchResult.Failure -> SingleLoadResult.Failure(result.message)
        }
    }

    fun isWellFormedUrl(url: String): Boolean = api.isWellFormedUrl(url)
}
