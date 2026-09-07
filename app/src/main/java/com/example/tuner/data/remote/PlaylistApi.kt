package com.example.tuner.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class PlaylistFetchResult {
    data class Success(val body: String) : PlaylistFetchResult()
    data class Failure(val message: String) : PlaylistFetchResult()
}

/**
 * Thin OkHttp wrapper for fetching static M3U playlist text over HTTPS. No auth, no proxy
 * needed for playlist fetching itself (GitHub Pages serves permissive CORS/no-auth) — a
 * relay/proxy is only relevant for individual stream *playback*, handled separately.
 */
class PlaylistApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val INDEX_URL = "https://iptv-org.github.io/iptv/index.m3u"

        fun regionUrl(code: String) = "https://iptv-org.github.io/iptv/countries/$code.m3u"
        fun categoryUrl(slug: String) = "https://iptv-org.github.io/iptv/categories/$slug.m3u"
        fun languageUrl(code: String) = "https://iptv-org.github.io/iptv/languages/$code.m3u"
    }

    /** True if [url] is a syntactically valid HTTP(S) URL — used both here and for save-time validation. */
    fun isWellFormedUrl(url: String): Boolean = url.toHttpUrlOrNull() != null

    suspend fun fetch(url: String): PlaylistFetchResult = withContext(Dispatchers.IO) {
        val parsedUrl = url.toHttpUrlOrNull()
            ?: return@withContext PlaylistFetchResult.Failure("Invalid URL")

        val request = Request.Builder().url(parsedUrl).get().build()

        try {
            suspendCoroutine<PlaylistFetchResult> { cont ->
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        cont.resume(PlaylistFetchResult.Failure(e.message ?: "Network error"))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            if (!resp.isSuccessful) {
                                cont.resume(PlaylistFetchResult.Failure("HTTP ${resp.code}"))
                                return
                            }
                            val text = resp.body?.string()
                            if (text.isNullOrBlank()) {
                                cont.resume(PlaylistFetchResult.Failure("Empty response"))
                            } else {
                                cont.resume(PlaylistFetchResult.Success(text))
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            PlaylistFetchResult.Failure(e.message ?: "Unknown error")
        }
    }
}
