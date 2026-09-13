# Live Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a live working/not-working status dot on every channel and let the user filter to working channels only ("Live only").

**Architecture:** A `StreamProber` does a player-like HLS probe (playlist → variant → first segment) over OkHttp. A process-scoped `LiveCheckRepository` runs up to 4 probes at a time from a replaceable queue, caches results in memory for 30 minutes, and exposes a `StateFlow<Map<url, LiveStatus>>`. The channel list reports which channels are on screen; the ViewModel queues them (or the whole list while "Live only" is on) and filters on the results. The player feeds real playback outcomes into the same cache.

**Tech Stack:** Kotlin 2.0.21, Coroutines 1.9.0, OkHttp 4.12.0 (+ MockWebServer for tests), Jetpack Compose Material3, DataStore Preferences, JUnit 4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-13-live-check-design.md`

**Prerequisite:** Execute `docs/superpowers/plans/2026-09-14-kids-mode-parental-control.md` first. This plan edits code that plan creates (`ChannelListUiState.baseChannels`, the `parentalControlRepository` factory argument, `shieldState` parameters on `ChannelRow`/`ChannelGridTile`, the Parental Control section in Settings).

## Global Constraints

- Probe User-Agent: `VLC/3.0.20 LibVLC/3.0.20` (same as `PlayerViewModel`).
- Probe: connect timeout 5 s, whole probe 8,000 ms, read at most 64 KB of any playlist, segment request `Range: bytes=0-1023`, 200/206 with ≥ 1 byte = working.
- Max 4 concurrent probes. Results cached in memory 30 minutes, keyed by stream URL. Nothing persisted except the "Live only" toggle.
- Status dot colors: UNCHECKED hollow ring, CHECKING pulsing grey, WORKING green, NOT_WORKING red.
- Visible-item reports debounced 300 ms; status updates to the UI sampled every 300 ms.
- New dependency allowed: `com.squareup.okhttp3:mockwebserver` (test only, version `okhttp` = 4.12.0).
- Unit test command: `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"` (PowerShell, repo root). Build check: `.\gradlew.bat :app:assembleDebug`.
- Prober tests use `runBlocking`, not `runTest` — `runTest` virtual time would fire `withTimeoutOrNull` instantly while real IO runs.

## File Structure

| File | Responsibility |
|---|---|
| Create `app/src/main/java/com/example/tuner/livecheck/LiveStatus.kt` | Status enum + `StreamProbe` interface |
| Create `app/src/main/java/com/example/tuner/livecheck/StreamProber.kt` | HLS/direct stream probe over OkHttp |
| Create `app/src/main/java/com/example/tuner/livecheck/LiveCheckRepository.kt` | Queue, concurrency cap, TTL cache, status flow |
| Create `app/src/main/java/com/example/tuner/ui/components/LiveStatusDot.kt` | Dot composable |
| Modify `gradle/libs.versions.toml`, `app/build.gradle.kts` | MockWebServer test dependency |
| Modify `app/src/main/java/com/example/tuner/ui/theme/Color.kt` | `green` palette entry + `TunerGreen` |
| Modify `app/src/main/java/com/example/tuner/ui/components/ChannelRow.kt` | Dot replaces the cyan dot |
| Modify `app/src/main/java/com/example/tuner/ui/components/ChannelGridTile.kt` | Dot next to the name |
| Modify `app/src/main/java/com/example/tuner/data/repository/AppStateRepository.kt` | Persist "Live only" |
| Modify `app/src/main/java/com/example/tuner/ui/channels/ChannelListViewModel.kt` | Live state, filtering, requests |
| Modify `app/src/main/java/com/example/tuner/TunerApplication.kt`, `MainActivity.kt` | Wiring |
| Modify `app/src/main/java/com/example/tuner/ui/player/PlayerViewModel.kt` | Report playback results |
| Modify `app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt` | Visible reporting, toggle, progress, empty states |
| Modify `app/src/main/java/com/example/tuner/ui/settings/SettingsScreen.kt` | "Clear check results" |
| Tests in `app/src/test/java/com/example/tuner/livecheck/` | Prober + repository tests |

---

### Task 1: Stream prober

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/tuner/livecheck/LiveStatus.kt`
- Create: `app/src/main/java/com/example/tuner/livecheck/StreamProber.kt`
- Test: `app/src/test/java/com/example/tuner/livecheck/StreamProberTest.kt`

**Interfaces:**
- Produces:
  - `enum class LiveStatus { UNCHECKED, CHECKING, WORKING, NOT_WORKING }`
  - `interface StreamProbe { suspend fun probe(url: String): LiveStatus }`
  - `class StreamProber(client: OkHttpClient = StreamProber.defaultClient(), timeoutMillis: Long = 8_000L) : StreamProbe` with `companion object { const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"; fun defaultClient(): OkHttpClient }`

- [ ] **Step 1: Add the test dependency**

In `gradle/libs.versions.toml` under `[libraries]`, after the `okhttp = ...` line add:

```toml
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

In `app/build.gradle.kts`, after `testImplementation(libs.kotlinx.coroutines.test)` add:

```kotlin
    testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 2: Create `LiveStatus.kt`**

```kotlin
package com.example.tuner.livecheck

enum class LiveStatus { UNCHECKED, CHECKING, WORKING, NOT_WORKING }

/** Checks whether a stream URL is currently playable. Never throws — failures are NOT_WORKING. */
interface StreamProbe {
    suspend fun probe(url: String): LiveStatus
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.example.tuner.livecheck

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StreamProberTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun serve(routes: Map<String, MockResponse>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.path] ?: MockResponse().setResponseCode(404)
        }
    }

    private fun url(path: String) = server.url(path).toString()

    private fun playlist(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/vnd.apple.mpegurl")
        .setBody(body)

    private fun segment(code: Int = 206) = MockResponse()
        .setResponseCode(code)
        .setBody(Buffer().write(ByteArray(1024) { 0x47 }))

    @Test
    fun `master to variant to segment with relative uris is working`() = runBlocking {
        serve(
            mapOf(
                "/live/master.m3u8" to playlist("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nhi/index.m3u8\n"),
                "/live/hi/index.m3u8" to playlist("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\n../seg/100.ts\n"),
                "/live/seg/100.ts" to segment()
            )
        )
        assertEquals(LiveStatus.WORKING, StreamProber().probe(url("/live/master.m3u8")))
    }

    @Test
    fun `media playlist with reachable segment is working`() = runBlocking {
        serve(
            mapOf(
                "/index.m3u8" to playlist("#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n"),
                "/seg1.ts" to segment(code = 200)
            )
        )
        assertEquals(LiveStatus.WORKING, StreamProber().probe(url("/index.m3u8")))
    }

    @Test
    fun `blocked segment is not working`() = runBlocking {
        serve(
            mapOf(
                "/index.m3u8" to playlist("#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n"),
                "/seg1.ts" to MockResponse().setResponseCode(403)
            )
        )
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe(url("/index.m3u8")))
    }

    @Test
    fun `missing playlist is not working`() = runBlocking {
        serve(emptyMap())
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe(url("/gone.m3u8")))
    }

    @Test
    fun `playlist without segments is not working`() = runBlocking {
        serve(mapOf("/empty.m3u8" to playlist("#EXTM3U\n#EXT-X-TARGETDURATION:6\n")))
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe(url("/empty.m3u8")))
    }

    @Test
    fun `direct non-hls stream with bytes is working`() = runBlocking {
        serve(mapOf("/direct.ts" to segment(code = 200)))
        assertEquals(LiveStatus.WORKING, StreamProber().probe(url("/direct.ts")))
    }

    @Test
    fun `unresponsive server times out as not working`() = runBlocking {
        serve(mapOf("/slow.m3u8" to MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)))
        assertEquals(LiveStatus.NOT_WORKING, StreamProber(timeoutMillis = 500).probe(url("/slow.m3u8")))
    }

    @Test
    fun `invalid url is not working`() = runBlocking {
        assertEquals(LiveStatus.NOT_WORKING, StreamProber().probe("not a url"))
    }

    @Test
    fun `sends vlc user agent and a range request for the segment`() = runBlocking {
        serve(
            mapOf(
                "/index.m3u8" to playlist("#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n"),
                "/seg1.ts" to segment()
            )
        )
        StreamProber().probe(url("/index.m3u8"))
        val playlistRequest = server.takeRequest()
        val segmentRequest = server.takeRequest()
        assertEquals(StreamProber.USER_AGENT, playlistRequest.getHeader("User-Agent"))
        assertEquals(StreamProber.USER_AGENT, segmentRequest.getHeader("User-Agent"))
        assertEquals("bytes=0-1023", segmentRequest.getHeader("Range"))
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.livecheck.StreamProberTest"`
Expected: FAIL — `Unresolved reference: StreamProber`.

- [ ] **Step 5: Write the implementation**

```kotlin
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
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.livecheck.StreamProberTest"`
Expected: PASS (9 tests).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/tuner/livecheck/LiveStatus.kt app/src/main/java/com/example/tuner/livecheck/StreamProber.kt app/src/test/java/com/example/tuner/livecheck/StreamProberTest.kt
git commit -m "feat(livecheck): add player-like stream prober"
```

---

### Task 2: Live check scheduler and cache

**Files:**
- Create: `app/src/main/java/com/example/tuner/livecheck/LiveCheckRepository.kt`
- Test: `app/src/test/java/com/example/tuner/livecheck/LiveCheckRepositoryTest.kt`

**Interfaces:**
- Consumes: `LiveStatus`, `StreamProbe` (Task 1).
- Produces: `class LiveCheckRepository(prober: StreamProbe, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default), clock: () -> Long = System::currentTimeMillis, maxConcurrent: Int = 4, ttlMillis: Long = 30 * 60_000L)` with `val statuses: StateFlow<Map<String, LiveStatus>>`, `fun request(urls: List<String>)` (replaces the pending queue), `fun report(url: String, working: Boolean)`, `fun clear()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.tuner.livecheck

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCheckRepositoryTest {

    /** Each probe suspends until the test releases that URL. */
    private class GatedProber : StreamProbe {
        private val gates = HashMap<String, CompletableDeferred<LiveStatus>>()
        val probed = mutableListOf<String>()
        var active = 0
        var maxActive = 0

        override suspend fun probe(url: String): LiveStatus {
            probed += url
            active++
            maxActive = maxOf(maxActive, active)
            try {
                return gates.getOrPut(url) { CompletableDeferred() }.await()
            } finally {
                active--
            }
        }

        fun release(url: String, status: LiveStatus = LiveStatus.WORKING) {
            gates.getOrPut(url) { CompletableDeferred() }.complete(status)
        }
    }

    private class RecordingProber : StreamProbe {
        val probed = mutableListOf<String>()
        override suspend fun probe(url: String): LiveStatus {
            probed += url
            return LiveStatus.WORKING
        }
    }

    @Test
    fun `never runs more than four probes at once`() = runTest {
        val prober = GatedProber()
        val repo = LiveCheckRepository(prober, backgroundScope)
        val urls = (1..10).map { "u$it" }

        repo.request(urls)
        runCurrent()
        assertEquals(4, prober.active)
        assertEquals(4, repo.statuses.value.values.count { it == LiveStatus.CHECKING })

        urls.forEach { prober.release(it) }
        advanceUntilIdle()
        assertEquals(4, prober.maxActive)
        assertEquals(10, prober.probed.size)
        assertEquals(10, repo.statuses.value.size)
        assertTrue(repo.statuses.value.values.all { it == LiveStatus.WORKING })
    }

    @Test
    fun `does not probe the same url twice while in flight`() = runTest {
        val prober = GatedProber()
        val repo = LiveCheckRepository(prober, backgroundScope)

        repo.request(listOf("a", "a"))
        runCurrent()
        repo.request(listOf("a"))
        runCurrent()
        prober.release("a")
        advanceUntilIdle()

        assertEquals(listOf("a"), prober.probed)
    }

    @Test
    fun `results are reused for thirty minutes then rechecked`() = runTest {
        var now = 0L
        val prober = RecordingProber()
        val repo = LiveCheckRepository(prober, backgroundScope, clock = { now })

        repo.request(listOf("a"))
        advanceUntilIdle()
        now += 29 * 60_000L
        repo.request(listOf("a"))
        advanceUntilIdle()
        assertEquals(1, prober.probed.size)

        now += 2 * 60_000L
        repo.request(listOf("a"))
        advanceUntilIdle()
        assertEquals(2, prober.probed.size)
    }

    @Test
    fun `a new request drops queued urls that were not started`() = runTest {
        val prober = GatedProber()
        val repo = LiveCheckRepository(prober, backgroundScope, maxConcurrent = 1)

        repo.request(listOf("a", "b", "c"))
        runCurrent()
        repo.request(listOf("d"))
        prober.release("a")
        runCurrent()
        prober.release("d")
        advanceUntilIdle()

        assertEquals(listOf("a", "d"), prober.probed)
    }

    @Test
    fun `reported playback results are cached without probing`() = runTest {
        val prober = RecordingProber()
        val repo = LiveCheckRepository(prober, backgroundScope)

        repo.report("a", working = false)
        repo.request(listOf("a"))
        advanceUntilIdle()

        assertEquals(LiveStatus.NOT_WORKING, repo.statuses.value["a"])
        assertTrue(prober.probed.isEmpty())
    }

    @Test
    fun `clear forgets results`() = runTest {
        val prober = RecordingProber()
        val repo = LiveCheckRepository(prober, backgroundScope)

        repo.request(listOf("a"))
        advanceUntilIdle()
        repo.clear()
        assertNull(repo.statuses.value["a"])

        repo.request(listOf("a"))
        advanceUntilIdle()
        assertEquals(2, prober.probed.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.livecheck.LiveCheckRepositoryTest"`
Expected: FAIL — `Unresolved reference: LiveCheckRepository`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.example.tuner.livecheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped Live Check scheduler. [request] replaces whatever is still waiting (probes
 * already running finish), so scrolling past channels doesn't pile up work for rows that are
 * gone. A fixed pool of workers caps concurrency; results live in memory for [ttlMillis].
 */
class LiveCheckRepository(
    private val prober: StreamProbe,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 4
        const val DEFAULT_TTL_MILLIS = 30 * 60_000L
    }

    private class CheckResult(val status: LiveStatus, val checkedAt: Long)

    private val lock = Any()
    private val results = HashMap<String, CheckResult>()
    private val inFlight = HashSet<String>()
    private val pending = ArrayDeque<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val _statuses = MutableStateFlow<Map<String, LiveStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, LiveStatus>> = _statuses.asStateFlow()

    init {
        repeat(maxConcurrent) { scope.launch { runWorker() } }
    }

    fun request(urls: List<String>) {
        synchronized(lock) {
            pending.clear()
            urls.distinct().filterTo(pending) { needsCheckLocked(it) }
        }
        wake.trySend(Unit)
    }

    fun report(url: String, working: Boolean) {
        synchronized(lock) {
            results[url] = CheckResult(if (working) LiveStatus.WORKING else LiveStatus.NOT_WORKING, clock())
            publishLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            results.clear()
            pending.clear()
            publishLocked()
        }
    }

    private suspend fun runWorker() {
        while (true) {
            val url = synchronized(lock) { takeNextLocked() }
            if (url == null) {
                wake.receive()
                continue
            }
            val status = try {
                prober.probe(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LiveStatus.NOT_WORKING
            }
            synchronized(lock) {
                inFlight -= url
                results[url] = CheckResult(status, clock())
                publishLocked()
            }
        }
    }

    private fun takeNextLocked(): String? {
        while (pending.isNotEmpty()) {
            val url = pending.removeFirst()
            if (needsCheckLocked(url)) {
                inFlight += url
                publishLocked()
                // Hand the rest of the queue to the next idle worker.
                if (pending.isNotEmpty()) wake.trySend(Unit)
                return url
            }
        }
        return null
    }

    private fun needsCheckLocked(url: String): Boolean {
        if (url in inFlight) return false
        val result = results[url] ?: return true
        return clock() - result.checkedAt >= ttlMillis
    }

    private fun publishLocked() {
        val snapshot = HashMap<String, LiveStatus>(results.size + inFlight.size)
        results.forEach { (url, result) -> snapshot[url] = result.status }
        inFlight.forEach { snapshot[it] = LiveStatus.CHECKING }
        _statuses.value = snapshot
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.livecheck.LiveCheckRepositoryTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/livecheck/LiveCheckRepository.kt app/src/test/java/com/example/tuner/livecheck/LiveCheckRepositoryTest.kt
git commit -m "feat(livecheck): add concurrent live check scheduler with TTL cache"
```

---

### Task 3: Status dot on rows and tiles

**Files:**
- Modify: `app/src/main/java/com/example/tuner/ui/theme/Color.kt`
- Create: `app/src/main/java/com/example/tuner/ui/components/LiveStatusDot.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/components/ChannelRow.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/components/ChannelGridTile.kt`

**Interfaces:**
- Consumes: `LiveStatus` (Task 1).
- Produces:
  - `val TunerGreen: Color @Composable get()`
  - `@Composable fun LiveStatusDot(status: LiveStatus, modifier: Modifier = Modifier, size: Dp = 8.dp)`
  - `ChannelRow(..., liveStatus: LiveStatus = LiveStatus.UNCHECKED, shieldState, onShieldClick, modifier)`
  - `ChannelGridTile(..., liveStatus: LiveStatus = LiveStatus.UNCHECKED, shieldState, onShieldClick, modifier)`

- [ ] **Step 1: Add green to the palette**

In `Color.kt`:
- Add `val green: Color,` after `val red: Color,` in `data class TunerPalette`.
- Add `green = Color(0xFF3DDC84),` after `red = Color(0xFFFF4B5C),` in `DarkTunerPalette`.
- Add `green = Color(0xFF1E8E4E),` after `red = Color(0xFFC62839),` in `LightTunerPalette`.
- Add after the `TunerRed` accessor:

```kotlin
val TunerGreen: Color @Composable get() = LocalTunerPalette.current.green
```

- [ ] **Step 2: Create `LiveStatusDot.kt`**

```kotlin
package com.example.tuner.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuner.livecheck.LiveStatus
import com.example.tuner.ui.theme.TunerGreen
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextSecondary

/** Live Check result: hollow = not checked, pulsing grey = checking, green = working, red = not working. */
@Composable
fun LiveStatusDot(status: LiveStatus, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val description = when (status) {
        LiveStatus.UNCHECKED -> "Stream not checked yet"
        LiveStatus.CHECKING -> "Checking stream"
        LiveStatus.WORKING -> "Stream working"
        LiveStatus.NOT_WORKING -> "Stream not working"
    }
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .semantics { contentDescription = description }

    when (status) {
        LiveStatus.UNCHECKED -> Box(base.border(1.5.dp, TunerTextSecondary, CircleShape))
        LiveStatus.CHECKING -> {
            val transition = rememberInfiniteTransition(label = "liveDot")
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "liveDotAlpha"
            )
            Box(base.alpha(alpha).background(TunerTextSecondary))
        }
        LiveStatus.WORKING -> Box(base.background(TunerGreen))
        LiveStatus.NOT_WORKING -> Box(base.background(TunerRed))
    }
}
```

- [ ] **Step 3: Use it in `ChannelRow`**

Add import `import com.example.tuner.livecheck.LiveStatus`. Add parameter `liveStatus: LiveStatus = LiveStatus.UNCHECKED,` after `onToggleFavorite: () -> Unit,`. Replace the cyan status dot block:

```kotlin
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(TunerCyan)
        )
```

with:

```kotlin
        LiveStatusDot(status = liveStatus)
```

Remove the now-unused imports `TunerCyan` and `CircleShape` if nothing else in the file uses them.

- [ ] **Step 4: Use it in `ChannelGridTile`**

Add imports `import com.example.tuner.livecheck.LiveStatus`, `import androidx.compose.foundation.layout.Row`, `import androidx.compose.foundation.layout.Spacer`, `import androidx.compose.foundation.layout.width`. Add parameter `liveStatus: LiveStatus = LiveStatus.UNCHECKED,` after `onToggleFavorite: () -> Unit,`. Replace the name `Text(...)` block (the one with `maxLines = 2`) with:

```kotlin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                LiveStatusDot(status = liveStatus, size = 7.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) TunerAmber else TunerTextPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
```

- [ ] **Step 5: Build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (all dots show the hollow UNCHECKED state for now).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/tuner/ui/theme/Color.kt app/src/main/java/com/example/tuner/ui/components/LiveStatusDot.kt app/src/main/java/com/example/tuner/ui/components/ChannelRow.kt app/src/main/java/com/example/tuner/ui/components/ChannelGridTile.kt
git commit -m "feat(livecheck): show live status dot on channel rows and tiles"
```

---

### Task 4: ViewModel state, persistence and wiring

**Files:**
- Modify: `app/src/main/java/com/example/tuner/data/repository/AppStateRepository.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/channels/ChannelListViewModel.kt`
- Modify: `app/src/main/java/com/example/tuner/TunerApplication.kt`
- Modify: `app/src/main/java/com/example/tuner/MainActivity.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/player/PlayerViewModel.kt`

**Interfaces:**
- Consumes: `LiveCheckRepository.statuses/request/report/clear` (Task 2); `StreamProber` (Task 1).
- Produces:
  - `AppStateRepository.liveOnly: Flow<Boolean>`, `suspend fun saveLiveOnly(enabled: Boolean)`
  - `ChannelListUiState` new fields `liveOnly: Boolean = false`, `liveStatuses: Map<String, LiveStatus> = emptyMap()`; getters `searchMatchedChannels: List<Channel>` (search filter only), `filteredChannels` (plus Live only), `fun liveStatusOf(channel: Channel): LiveStatus`
  - `ChannelListViewModel.requestLiveChecks(visibleUrls: List<String>)`, `setLiveOnly(enabled: Boolean)`, `clearLiveCheckResults()`
  - `ChannelListViewModel.factory(...)` gains 7th parameter `liveCheckRepository: LiveCheckRepository`
  - `TunerApplication.liveCheckRepository: LiveCheckRepository`

- [ ] **Step 1: Persist "Live only"**

In `AppStateRepository.kt` add import `import androidx.datastore.preferences.core.booleanPreferencesKey`, add the key after `viewModeKey`:

```kotlin
    private val liveOnlyKey = booleanPreferencesKey("live_only")
```

and at the end of the class:

```kotlin
    /** "Live only" channel filter — remembered across restarts. */
    val liveOnly: Flow<Boolean> = context.appStateDataStore.data.map { prefs -> prefs[liveOnlyKey] ?: false }

    suspend fun saveLiveOnly(enabled: Boolean) {
        context.appStateDataStore.edit { prefs -> prefs[liveOnlyKey] = enabled }
    }
```

- [ ] **Step 2: Extend `ChannelListUiState`**

In `ChannelListViewModel.kt` add imports:

```kotlin
import com.example.tuner.livecheck.LiveCheckRepository
import com.example.tuner.livecheck.LiveStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
```

Add after `val pinLockoutUntil: Long = 0L` in the constructor (add a comma after it):

```kotlin
    val liveOnly: Boolean = false,
    val liveStatuses: Map<String, LiveStatus> = emptyMap()
```

Replace the existing `val filteredChannels: List<Channel> get() { ... }` block with:

```kotlin
    /** Current list after the search filter, before the Live only filter. */
    val searchMatchedChannels: List<Channel>
        get() {
            val base = baseChannels
            return if (filterText.isBlank()) base else base.filter { it.name.contains(filterText, ignoreCase = true) }
        }

    val filteredChannels: List<Channel>
        get() = if (!liveOnly) {
            searchMatchedChannels
        } else {
            searchMatchedChannels.filter { liveStatuses[it.streamUrl] == LiveStatus.WORKING }
        }

    fun liveStatusOf(channel: Channel): LiveStatus = liveStatuses[channel.streamUrl] ?: LiveStatus.UNCHECKED
```

- [ ] **Step 3: Constructor, collectors and actions**

Above the class add:

```kotlin
private const val LIVE_STATUS_SAMPLE_MILLIS = 300L
```

Add `@OptIn(FlowPreview::class)` on the line above `class ChannelListViewModel(`, and add the constructor parameter after `parentalControlRepository`:

```kotlin
    private val parentalControlRepository: ParentalControlRepository,
    private val liveCheckRepository: LiveCheckRepository
```

In `init`, before `restoreLastState()` add:

```kotlin
        viewModelScope.launch {
            appStateRepository.liveOnly.collect { enabled ->
                _uiState.update { it.copy(liveOnly = enabled) }
            }
        }
        // Sampled so hundreds of probe results don't each trigger a list recomposition.
        viewModelScope.launch {
            liveCheckRepository.statuses.sample(LIVE_STATUS_SAMPLE_MILLIS).collect { statuses ->
                _uiState.update { it.copy(liveStatuses = statuses) }
            }
        }
```

Add before `// --- Parental control ---`:

```kotlin
    // --- Live Check ---

    /**
     * Queues checks for what's on screen. With Live only on, the rest of the current list is
     * queued behind the visible rows so the filter can fill in.
     */
    fun requestLiveChecks(visibleUrls: List<String>) {
        val state = _uiState.value
        val urls = if (state.liveOnly) {
            visibleUrls + state.searchMatchedChannels.map { it.streamUrl }
        } else {
            visibleUrls
        }
        liveCheckRepository.request(urls)
    }

    fun setLiveOnly(enabled: Boolean) {
        _uiState.update { it.copy(liveOnly = enabled) }
        viewModelScope.launch { appStateRepository.saveLiveOnly(enabled) }
        if (enabled) requestLiveChecks(emptyList())
    }

    fun clearLiveCheckResults() = liveCheckRepository.clear()
```

Update `factory` to take and pass `liveCheckRepository: LiveCheckRepository` as the last parameter/argument.

- [ ] **Step 4: App wiring**

In `TunerApplication.kt` add imports `import com.example.tuner.livecheck.LiveCheckRepository` and `import com.example.tuner.livecheck.StreamProber`, then:

```kotlin
    lateinit var liveCheckRepository: LiveCheckRepository
        private set
```

and at the end of `onCreate()`:

```kotlin
        liveCheckRepository = LiveCheckRepository(StreamProber())
```

In `MainActivity.kt` add `liveCheckRepository = app.liveCheckRepository` as the last argument of `ChannelListViewModel.factory(...)`.

- [ ] **Step 5: Feed playback results from the player**

In `PlayerViewModel.kt`, after `private val castSessionManager = ...` add:

```kotlin
    private val liveCheckRepository = getApplication<TunerApplication>().liveCheckRepository
```

Replace the `Player.STATE_READY -> ...` line with:

```kotlin
                    Player.STATE_READY -> {
                        _uiState.update { it.copy(status = PlaybackUiStatus.LIVE, errorMessage = null) }
                        // Only a direct play proves the listed URL works; a proxy success doesn't.
                        _uiState.value.channel
                            ?.takeIf { !_uiState.value.isViaProxy }
                            ?.let { liveCheckRepository.report(it.streamUrl, working = true) }
                    }
```

In `handlePlaybackFailure`, replace the `else -> { ... }` branch with:

```kotlin
            else -> {
                _uiState.update { it.copy(status = PlaybackUiStatus.SIGNAL_LOST, errorMessage = message) }
                if (!isViaProxy) liveCheckRepository.report(channel.streamUrl, working = false)
            }
```

- [ ] **Step 6: Build and run all unit tests**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/tuner/data/repository/AppStateRepository.kt app/src/main/java/com/example/tuner/ui/channels/ChannelListViewModel.kt app/src/main/java/com/example/tuner/TunerApplication.kt app/src/main/java/com/example/tuner/MainActivity.kt app/src/main/java/com/example/tuner/ui/player/PlayerViewModel.kt
git commit -m "feat(livecheck): wire live check state into view model and player"
```

---

### Task 5: Channel list and Settings UI

**Files:**
- Modify: `app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `ChannelListUiState.liveOnly/liveStatuses/searchMatchedChannels/liveStatusOf`, `ChannelListViewModel.requestLiveChecks/setLiveOnly/clearLiveCheckResults` (Task 4); `LiveStatusDot`, `ChannelRow`/`ChannelGridTile` `liveStatus` parameter (Task 3).
- Produces: private `ChannelList(...)` gains `liveStatusOf: (Channel) -> LiveStatus` and `onVisibleUrlsChanged: (List<String>) -> Unit`.

- [ ] **Step 1: Imports**

In `ChannelListScreen.kt` add:

```kotlin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.example.tuner.livecheck.LiveStatus
import com.example.tuner.ui.components.LiveStatusDot
import com.example.tuner.ui.theme.TunerAmberTint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
```

Add at file level, after `private const val WIDE_LAYOUT_MIN_DP = 700`:

```kotlin
private const val VISIBLE_REPORT_DEBOUNCE_MILLIS = 300L
private const val CHANNEL_KEY_PREFIX = "ch:"

/** Lazy item keys look like "ch:<number>:<source>:<url>"; the number maps back to a stream URL. */
private fun visibleUrlsFrom(keys: List<Any>, urlByNumber: Map<Int, String>): List<String> =
    keys.mapNotNull { key ->
        (key as? String)
            ?.takeIf { it.startsWith(CHANNEL_KEY_PREFIX) }
            ?.removePrefix(CHANNEL_KEY_PREFIX)
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?.let(urlByNumber::get)
    }
```

- [ ] **Step 2: Live only toggle, progress and empty messages in `ChannelListPane`**

Directly after the `OutlinedTextField(...)` search field in `ChannelListPane`, insert:

```kotlin
        val matched = uiState.searchMatchedChannels
        val checkedCount = matched.count {
            val status = uiState.liveStatuses[it.streamUrl]
            status == LiveStatus.WORKING || status == LiveStatus.NOT_WORKING
        }
        val stillChecking = uiState.liveOnly && matched.isNotEmpty() && checkedCount < matched.size

        // While Live only is on, keep the whole list queued even if nothing is visible yet.
        LaunchedEffect(uiState.liveOnly, matched.size, uiState.topMode, uiState.kidsMode, uiState.kidsTab) {
            if (uiState.liveOnly) viewModel.requestLiveChecks(emptyList())
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.liveOnly,
                onClick = { viewModel.setLiveOnly(!uiState.liveOnly) },
                label = { Text("Live only") },
                leadingIcon = { LiveStatusDot(status = LiveStatus.WORKING) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TunerAmberTint,
                    selectedLabelColor = TunerAmber,
                    labelColor = TunerTextPrimary
                )
            )
            Spacer(Modifier.width(10.dp))
            if (stillChecking) {
                Text(
                    text = "Checking $checkedCount / ${matched.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TunerTextSecondary
                )
            }
        }
```

In the `ChannelList(...)` call replace the `emptyMessage = ...` line with:

```kotlin
            emptyMessage = when {
                stillChecking -> "CHECKING CHANNELS…"
                uiState.liveOnly && matched.isNotEmpty() -> "NO WORKING CHANNELS IN THIS LIST"
                uiState.kidsMode -> kidsEmptyMessageFor(uiState.kidsTab)
                else -> emptyMessageFor(uiState.topMode)
            },
```

and add after `onShieldClick = viewModel::onKidsShieldClick,`:

```kotlin
            liveStatusOf = uiState::liveStatusOf,
            onVisibleUrlsChanged = viewModel::requestLiveChecks,
```

- [ ] **Step 3: Report visible channels from `ChannelList`**

Add `@OptIn(FlowPreview::class)` above `@Composable private fun ChannelList(`. Add parameters after `onShieldClick: (Channel) -> Unit,`:

```kotlin
    liveStatusOf: (Channel) -> LiveStatus,
    onVisibleUrlsChanged: (List<String>) -> Unit,
```

After the `val grouped = remember(channels, groupBySource) { ... }` block add:

```kotlin
    val urlByNumber = remember(grouped) {
        grouped.values.flatten().associate { it.index to it.channel.streamUrl }
    }
```

Change both `items(numberedChannels, key = { ... })` key lambdas to:

```kotlin
key = { "$CHANNEL_KEY_PREFIX${it.index}:${it.channel.source}:${it.channel.streamUrl}" }
```

In the grid branch, right after `val gridState = rememberLazyGridState()` add:

```kotlin
            LaunchedEffect(gridState, urlByNumber) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.key } }
                    .map { visibleUrlsFrom(it, urlByNumber) }
                    .distinctUntilChanged()
                    .debounce(VISIBLE_REPORT_DEBOUNCE_MILLIS)
                    .collect { onVisibleUrlsChanged(it) }
            }
```

In the list branch, right after `val listState = rememberLazyListState()` add:

```kotlin
            LaunchedEffect(listState, urlByNumber) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }
                    .map { visibleUrlsFrom(it, urlByNumber) }
                    .distinctUntilChanged()
                    .debounce(VISIBLE_REPORT_DEBOUNCE_MILLIS)
                    .collect { onVisibleUrlsChanged(it) }
            }
```

In the `ChannelGridTile(...)` and `ChannelRow(...)` calls add:

```kotlin
                                liveStatus = liveStatusOf(numbered.channel),
```

- [ ] **Step 4: Settings "Live Check" section**

In `SettingsScreen.kt` add import `import androidx.compose.material3.OutlinedButton`. Directly before the About block (`Spacer(Modifier.height(28.dp))` + `SectionHeader("About")`), after the Parental Control section, insert:

```kotlin
        Spacer(Modifier.height(28.dp))
        SectionHeader("Live Check")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Clear check results", style = MaterialTheme.typography.bodyLarge, color = TunerTextPrimary)
                Text(
                    "Forgets which channels were working so they're checked again.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TunerTextSecondary
                )
            }
            OutlinedButton(onClick = viewModel::clearLiveCheckResults) {
                Text("CLEAR", color = TunerTextPrimary)
            }
        }
```

- [ ] **Step 5: Build and install**

Run: `.\gradlew.bat :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.`

- [ ] **Step 6: Manual check on device**

1. Catalog "All channels": dots on visible rows go hollow → pulsing grey → green/red within ~10 s. Scroll quickly past hundreds of rows → only rows you stop on get checked (dots far above stay hollow).
2. Grid view: dots next to tile names behave the same.
3. Tap "Live only": list empties to "CHECKING CHANNELS…" with "Checking N / M", then fills with green channels only as results arrive.
4. Play a red channel that actually plays → its dot turns green. Play a channel that ends in "signal lost" → red.
5. Force-stop and reopen → "Live only" still selected; dots restart hollow (results not persisted).
6. Settings → Live Check → CLEAR → back on the list, visible dots recheck.
7. Kids Mode on → dots and Live only work on the kids list too.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt app/src/main/java/com/example/tuner/ui/settings/SettingsScreen.kt
git commit -m "feat(livecheck): add Live only filter, progress and visible-row checks"
```

---

### Task 6: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run all unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; `StreamProberTest`, `LiveCheckRepositoryTest` and all Kids Mode / parser tests pass.

- [ ] **Step 2: Repeat Task 5 Step 6 on a clean install**

Run: `.\gradlew.bat :app:installDebug`, then walk through Task 5 Step 6.

- [ ] **Step 3: Check for crashes and runaway work**

Run: `& "C:/Program Files (x86)/Android/android-sdk/platform-tools/adb.exe" logcat -d -b crash`
Expected: no new `FATAL EXCEPTION` for `com.example.tuner`.
With Live only on over "All channels", scroll and switch tabs for a minute — the UI stays responsive (no ANR dialog).
