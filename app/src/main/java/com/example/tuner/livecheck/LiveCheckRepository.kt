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
