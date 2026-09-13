package com.example.tuner.livecheck

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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

    /** Publishes synchronously so runCurrent() is enough to observe results. */
    private fun repo(
        prober: StreamProbe,
        scope: CoroutineScope,
        clock: () -> Long = { 0L },
        maxConcurrent: Int = LiveCheckRepository.DEFAULT_MAX_CONCURRENT
    ) = LiveCheckRepository(prober, scope, clock = clock, maxConcurrent = maxConcurrent, publishIntervalMillis = 0L)

    @Test
    fun `never runs more than four probes at once`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope)
        val urls = (1..10).map { "u$it" }

        repo.request(urls)
        runCurrent()
        assertEquals(4, prober.active)
        assertEquals(4, repo.statuses.value.values.count { it == LiveStatus.CHECKING })

        urls.forEach { prober.release(it) }
        runCurrent()
        assertEquals(4, prober.maxActive)
        assertEquals(10, prober.probed.size)
        assertEquals(10, repo.statuses.value.size)
        assertTrue(repo.statuses.value.values.all { it == LiveStatus.WORKING })
    }

    @Test
    fun `does not probe the same url twice while in flight`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope)

        repo.request(listOf("a", "a"))
        runCurrent()
        repo.request(listOf("a"))
        runCurrent()
        prober.release("a")
        runCurrent()

        assertEquals(listOf("a"), prober.probed)
    }

    @Test
    fun `results are reused for thirty minutes then rechecked`() = runTest {
        var now = 0L
        val prober = RecordingProber()
        val repo = repo(prober, backgroundScope, clock = { now })

        repo.request(listOf("a"))
        runCurrent()
        now += 29 * 60_000L
        repo.request(listOf("a"))
        runCurrent()
        assertEquals(1, prober.probed.size)

        now += 2 * 60_000L
        repo.request(listOf("a"))
        runCurrent()
        assertEquals(2, prober.probed.size)
    }

    @Test
    fun `a new request drops queued urls that were not started`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope, maxConcurrent = 1)

        repo.request(listOf("a", "b", "c"))
        runCurrent()
        repo.request(listOf("d"))
        prober.release("a")
        runCurrent()
        prober.release("d")
        runCurrent()

        assertEquals(listOf("a", "d"), prober.probed)
    }

    @Test
    fun `reported playback results are cached without probing`() = runTest {
        val prober = RecordingProber()
        val repo = repo(prober, backgroundScope)

        repo.report("a", working = false)
        repo.request(listOf("a"))
        runCurrent()

        assertEquals(LiveStatus.NOT_WORKING, repo.statuses.value["a"])
        assertTrue(prober.probed.isEmpty())
    }

    @Test
    fun `clear forgets results`() = runTest {
        val prober = RecordingProber()
        val repo = repo(prober, backgroundScope)

        repo.request(listOf("a"))
        runCurrent()
        repo.clear()
        assertNull(repo.statuses.value["a"])

        repo.request(listOf("a"))
        runCurrent()
        assertEquals(2, prober.probed.size)
    }

    @Test
    fun `a probe that throws is recorded as not working and the worker keeps going`() = runTest {
        val prober = object : StreamProbe {
            override suspend fun probe(url: String): LiveStatus {
                if (url == "bad") throw IllegalStateException("boom")
                return LiveStatus.WORKING
            }
        }
        val repo = repo(prober, backgroundScope, maxConcurrent = 1)

        repo.request(listOf("bad", "good"))
        runCurrent()

        assertEquals(LiveStatus.NOT_WORKING, repo.statuses.value["bad"])
        assertEquals(LiveStatus.WORKING, repo.statuses.value["good"])
    }

    @Test
    fun `visible urls are probed before bulk urls`() = runTest {
        val prober = RecordingProber()
        val repo = repo(prober, backgroundScope, maxConcurrent = 1)

        repo.request(visibleUrls = listOf("v1", "v2"), bulkUrls = listOf("b1", "v2", "b2", "b1"))
        runCurrent()

        assertEquals(listOf("v1", "v2", "b1", "b2"), prober.probed)
    }

    @Test
    fun `an expired bulk url is not re-probed but an unchecked one is`() = runTest {
        var now = 0L
        val prober = RecordingProber()
        val repo = repo(prober, backgroundScope, clock = { now }, maxConcurrent = 1)

        repo.request(listOf("old"))
        runCurrent()
        now += 31 * 60_000L
        repo.request(visibleUrls = emptyList(), bulkUrls = listOf("old", "new"))
        runCurrent()

        assertEquals(listOf("old", "new"), prober.probed)
    }

    @Test
    fun `an expired visible url is re-probed`() = runTest {
        var now = 0L
        val prober = RecordingProber()
        val repo = repo(prober, backgroundScope, clock = { now }, maxConcurrent = 1)

        repo.request(listOf("a"))
        runCurrent()
        now += 31 * 60_000L
        repo.request(visibleUrls = listOf("a"), bulkUrls = listOf("a", "b"))
        runCurrent()

        assertEquals(listOf("a", "a", "b"), prober.probed)
    }

    @Test
    fun `pause drops queued urls that were not started`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope, maxConcurrent = 1)

        repo.request(listOf("a", "b", "c"))
        runCurrent()
        repo.pause()
        prober.release("a")
        runCurrent()

        assertEquals(listOf("a"), prober.probed)
        assertEquals(LiveStatus.WORKING, repo.statuses.value["a"])
    }

    @Test
    fun `resume re-queues the last requested urls after a pause`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope, maxConcurrent = 1)

        repo.request(listOf("a", "b", "c"))
        runCurrent()
        // a in flight
        repo.pause()
        prober.release("a")
        runCurrent()

        // b, c not probed while paused
        assertEquals(listOf("a"), prober.probed)

        repo.resume()
        runCurrent()
        prober.release("b")
        runCurrent()
        prober.release("c")
        runCurrent()

        assertEquals(listOf("a", "b", "c"), prober.probed)
    }

    @Test
    fun `requests while paused are remembered but not started`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope, maxConcurrent = 1)

        repo.pause()
        repo.request(listOf("x"))
        runCurrent()

        assertTrue(prober.probed.isEmpty())

        repo.resume()
        runCurrent()
        prober.release("x")
        runCurrent()

        assertEquals(listOf("x"), prober.probed)
    }

    @Test
    fun `a cached result keeps showing while it is re-checked`() = runTest {
        var now = 0L
        val secondProbe = CompletableDeferred<LiveStatus>()
        var calls = 0
        val prober = object : StreamProbe {
            override suspend fun probe(url: String): LiveStatus {
                calls++
                return if (calls == 1) LiveStatus.WORKING else secondProbe.await()
            }
        }
        val repo = repo(prober, backgroundScope, clock = { now })

        repo.request(listOf("a"))
        runCurrent()
        assertEquals(LiveStatus.WORKING, repo.statuses.value["a"])

        now += 31 * 60_000L
        repo.request(listOf("a"))
        runCurrent()
        assertEquals(2, calls)
        assertEquals(LiveStatus.WORKING, repo.statuses.value["a"])

        secondProbe.complete(LiveStatus.NOT_WORKING)
        runCurrent()
        assertEquals(LiveStatus.NOT_WORKING, repo.statuses.value["a"])
    }

    @Test
    fun `clear while a probe is in flight discards its result`() = runTest {
        val prober = GatedProber()
        val repo = repo(prober, backgroundScope)

        repo.request(listOf("a"))
        runCurrent()
        repo.clear()
        prober.release("a")
        runCurrent()

        assertNull(repo.statuses.value["a"])
        assertEquals(listOf("a"), prober.probed)
    }

    @Test
    fun `status updates are published at most once per interval`() = runTest {
        val prober = GatedProber()
        val repo = LiveCheckRepository(prober, backgroundScope, publishIntervalMillis = 250L)

        repo.request(listOf("a"))
        runCurrent()
        assertEquals(LiveStatus.CHECKING, repo.statuses.value["a"])

        prober.release("a")
        runCurrent()
        assertEquals(LiveStatus.CHECKING, repo.statuses.value["a"])

        advanceTimeBy(250L)
        runCurrent()
        assertEquals(LiveStatus.WORKING, repo.statuses.value["a"])
    }
}
