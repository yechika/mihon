package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SnapshotDebouncer(
    private val scope: CoroutineScope,
    private val delayMillis: Long = DEFAULT_DELAY_MILLIS,
    private val onFire: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var pendingJob: Job? = null

    suspend fun trigger() {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob = scope.launch {
                delay(delayMillis)
                onFire()
            }
        }
    }

    suspend fun flushImmediately() {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob = null
        }
        onFire()
    }

    suspend fun cancelPending() {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob = null
        }
    }

    companion object {
        const val DEFAULT_DELAY_MILLIS: Long = 30_000L
    }
}
