package com.example.tuner.livecheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped Live Check scheduler. [request] replaces whatever is still waiting (probes
 * already running finish), so scrolling past channels doesn't pile up work for rows that are
 * gone. A fixed pool of workers caps concurrency; results live in memory for [ttlMillis].
 * Status snapshots are published at most once per [publishIntervalMillis] (0 = immediately).
 */
class LiveCheckRepository(
    private val prober: StreamProbe,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val publishIntervalMillis: Long = DEFAULT_PUBLISH_INTERVAL_MILLIS
) {

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 4
        const val DEFAULT_TTL_MILLIS = 30 * 60_000L
        const val DEFAULT_PUBLISH_INTERVAL_MILLIS = 250L
    }

    private class CheckResult(val status: LiveStatus, val checkedAt: Long)

    private val lock = Any()
    private val results = HashMap<String, CheckResult>()
    private val inFlight = HashSet<String>()
    private val pending = ArrayDeque<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val publishSignal = Channel<Unit>(Channel.CONFLATED)

    // Last lists passed to request(), remembered so resume() can re-queue them after a pause.
    private var lastVisibleUrls: List<String> = emptyList()
    private var lastBulkUrls: List<String> = emptyList()
    private var paused: Boolean = false

    // Bumped by clear() so probes started before it don't store their results afterwards.
    private var generation = 0L

    private val _statuses = MutableStateFlow<Map<String, LiveStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, LiveStatus>> = _statuses.asStateFlow()

    init {
        repeat(maxConcurrent) { scope.launch { runWorker() } }
        if (publishIntervalMillis > 0L) scope.launch { runPublisher() }
    }

    /**
     * Replaces the queue with [visibleUrls] that are unchecked or expired, followed by
     * [bulkUrls] that have never been checked. Expired off-screen results aren't re-queued,
     * so a long bulk pass always makes progress through the unchecked tail.
     */
    fun request(visibleUrls: List<String>, bulkUrls: List<String> = emptyList()) {
        val shouldWake = synchronized(lock) {
            lastVisibleUrls = visibleUrls
            lastBulkUrls = bulkUrls
            if (paused) {
                false
            } else {
                buildQueueLocked(visibleUrls, bulkUrls)
                true
            }
        }
        if (shouldWake) wake.trySend(Unit)
    }

    /** Drops queued checks (running probes finish), e.g. when the app leaves the foreground. */
    fun pause() {
        synchronized(lock) {
            paused = true
            pending.clear()
        }
    }

    /** Resumes probing, re-queueing whatever was last requested while paused. */
    fun resume() {
        val shouldWake = synchronized(lock) {
            paused = false
            if (lastVisibleUrls.isEmpty() && lastBulkUrls.isEmpty()) {
                false
            } else {
                buildQueueLocked(lastVisibleUrls, lastBulkUrls)
                true
            }
        }
        if (shouldWake) wake.trySend(Unit)
    }

    /** Replaces [pending] per the visible-then-bulk composition rules. Caller holds [lock]. */
    private fun buildQueueLocked(visibleUrls: List<String>, bulkUrls: List<String>) {
        val visible = visibleUrls.distinct()
        val visibleSet = visible.toHashSet()
        val bulk = bulkUrls.filter { it !in visibleSet }.distinct()
        pending.clear()
        visible.filterTo(pending) { needsCheckLocked(it) }
        bulk.filterTo(pending) { it !in inFlight && it !in results }
    }

    fun report(url: String, working: Boolean) {
        synchronized(lock) {
            results[url] = CheckResult(if (working) LiveStatus.WORKING else LiveStatus.NOT_WORKING, clock())
            publishLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            generation++
            results.clear()
            pending.clear()
            publishLocked()
        }
    }

    private suspend fun runWorker() {
        while (true) {
            var takenGeneration = 0L
            val url = synchronized(lock) {
                takenGeneration = generation
                takeNextLocked()
            }
            if (url == null) {
                wake.receive()
                continue
            }
            var status: LiveStatus? = null
            try {
                status = try {
                    prober.probe(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LiveStatus.NOT_WORKING
                }
            } finally {
                val finished = status
                synchronized(lock) {
                    inFlight -= url
                    if (finished != null && takenGeneration == generation) {
                        results[url] = CheckResult(finished, clock())
                    }
                    publishLocked()
                }
            }
        }
    }

    private suspend fun runPublisher() {
        while (true) {
            publishSignal.receive()
            _statuses.value = synchronized(lock) { snapshotLocked() }
            delay(publishIntervalMillis)
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
        if (publishIntervalMillis > 0L) {
            publishSignal.trySend(Unit)
        } else {
            _statuses.value = snapshotLocked()
        }
    }

    private fun snapshotLocked(): Map<String, LiveStatus> {
        val snapshot = HashMap<String, LiveStatus>(results.size + inFlight.size)
        results.forEach { (url, result) -> snapshot[url] = result.status }
        // A re-check keeps showing the previous result until the new one lands.
        inFlight.forEach { if (it !in results) snapshot[it] = LiveStatus.CHECKING }
        return snapshot
    }
}
