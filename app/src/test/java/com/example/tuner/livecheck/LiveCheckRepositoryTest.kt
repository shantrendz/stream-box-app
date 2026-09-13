package com.example.tuner.livecheck

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        runCurrent()
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
        runCurrent()

        assertEquals(listOf("a"), prober.probed)
    }

    @Test
    fun `results are reused for thirty minutes then rechecked`() = runTest {
        var now = 0L
        val prober = RecordingProber()
        val repo = LiveCheckRepository(prober, backgroundScope, clock = { now })

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
        val repo = LiveCheckRepository(prober, backgroundScope, maxConcurrent = 1)

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
        val repo = LiveCheckRepository(prober, backgroundScope)

        repo.report("a", working = false)
        repo.request(listOf("a"))
        runCurrent()

        assertEquals(LiveStatus.NOT_WORKING, repo.statuses.value["a"])
        assertTrue(prober.probed.isEmpty())
    }

    @Test
    fun `clear forgets results`() = runTest {
        val prober = RecordingProber()
        val repo = LiveCheckRepository(prober, backgroundScope)

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
        val repo = LiveCheckRepository(prober, backgroundScope, maxConcurrent = 1)

        repo.request(listOf("bad", "good"))
        runCurrent()

        assertEquals(LiveStatus.NOT_WORKING, repo.statuses.value["bad"])
        assertEquals(LiveStatus.WORKING, repo.statuses.value["good"])
    }
}
