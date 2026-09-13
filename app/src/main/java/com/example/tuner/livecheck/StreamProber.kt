package com.example.tuner.livecheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cheap "would this actually play?" check that mimics the player instead of just pinging the
 * URL: many streams serve their .m3u8 fine but block the video segments (geo/referrer rules),
 * so an HLS stream only counts as WORKING once its first media segment downloads.
 */
class StreamProber(
    private val client: OkHttpClient = defaultClient(),
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : StreamProbe {

    companion object {
        const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
        private const val DEFAULT_TIMEOUT_MILLIS = 8_000L
        private const val MAX_PLAYLIST_BYTES = 64L * 1024
        private const val HLS_HEADER = "#EXTM3U"
        private const val VARIANT_TAG = "#EXT-X-STREAM-INF"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private class Fetched(val text: String, val finalUrl: HttpUrl, val byteCount: Long)

    /**
     * Tracks this probe's OkHttp calls so a timeout can cancel them — a blocked socket read
     * ignores thread interruption, so cancelling the Call is the only way to stop it early.
     */
    private inner class ProbeCalls {
        private val cancelled = AtomicBoolean(false)
        private val calls = java.util.concurrent.CopyOnWriteArrayList<Call>()

        fun execute(request: Request): Response {
            if (cancelled.get()) throw IOException("Probe cancelled")
            val call = client.newCall(request)
            calls += call
            if (cancelled.get()) call.cancel()
            return call.execute()
        }

        fun cancelAll() {
            cancelled.set(true)
            calls.forEach { it.cancel() }
        }
    }

    override suspend fun probe(url: String): LiveStatus = try {
        withTimeoutOrNull(timeoutMillis) {
            coroutineScope {
                val calls = ProbeCalls()
                val result = async(Dispatchers.IO) { probeBlocking(url, calls) }
                try {
                    result.await()
                } finally {
                    calls.cancelAll()
                }
            }
        } ?: LiveStatus.NOT_WORKING
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LiveStatus.NOT_WORKING
    }

    private fun probeBlocking(url: String, calls: ProbeCalls): LiveStatus {
        val startUrl = url.trim().toHttpUrlOrNull() ?: return LiveStatus.NOT_WORKING
        val first = fetchText(startUrl, calls) ?: return LiveStatus.NOT_WORKING

        if (!first.text.trimStart().startsWith(HLS_HEADER)) {
            // Direct stream (.ts/.mp4/DASH…): reachable with real bytes is as far as we check.
            return if (first.byteCount > 0) LiveStatus.WORKING else LiveStatus.NOT_WORKING
        }

        var media = first
        if (first.text.contains(VARIANT_TAG)) {
            val variantUri = firstUri(first.text, afterTag = VARIANT_TAG) ?: return LiveStatus.NOT_WORKING
            val variantUrl = first.finalUrl.resolve(variantUri) ?: return LiveStatus.NOT_WORKING
            media = fetchText(variantUrl, calls) ?: return LiveStatus.NOT_WORKING
        }

        val segmentUri = firstUri(media.text) ?: return LiveStatus.NOT_WORKING
        val segmentUrl = media.finalUrl.resolve(segmentUri) ?: return LiveStatus.NOT_WORKING
        return if (segmentHasBytes(segmentUrl, calls)) LiveStatus.WORKING else LiveStatus.NOT_WORKING
    }

    private fun fetchText(url: HttpUrl, calls: ProbeCalls): Fetched? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        calls.execute(request).use { response ->
            if (!response.isSuccessful) return null
            val source = response.body?.source() ?: return null
            source.request(MAX_PLAYLIST_BYTES)
            val byteCount = minOf(source.buffer.size, MAX_PLAYLIST_BYTES)
            return Fetched(source.buffer.readUtf8(byteCount), response.request.url, byteCount)
        }
    }

    private fun segmentHasBytes(url: HttpUrl, calls: ProbeCalls): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-1023")
            .get()
            .build()
        calls.execute(request).use { response ->
            if (response.code != 200 && response.code != 206) return false
            return response.body?.source()?.request(1) == true
        }
    }

    /** First URI line of a playlist, or — with [afterTag] — the first URI line following that tag. */
    private fun firstUri(playlist: String, afterTag: String? = null): String? {
        var seenTag = afterTag == null
        for (raw in playlist.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> continue
                line.startsWith("#") -> if (afterTag != null && line.startsWith(afterTag)) seenTag = true
                seenTag -> return line
            }
        }
        return null
    }
}
