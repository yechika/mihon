package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotDebouncerTest {

    @Test
    fun `single trigger fires after the delay window`() = runTest {
        val fires = AtomicInteger(0)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val debouncer = SnapshotDebouncer(
            scope = scope,
            delayMillis = 30_000L,
            onFire = { fires.incrementAndGet() },
        )

        debouncer.trigger()
        advanceTimeBy(29_000)
        assertEquals(0, fires.get())
        advanceTimeBy(1_500)
        scope.advanceUntilIdle()
        assertEquals(1, fires.get())
    }

    @Test
    fun `three triggers within the window coalesce into one fire`() = runTest {
        val fires = AtomicInteger(0)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val debouncer = SnapshotDebouncer(
            scope = scope,
            delayMillis = 30_000L,
            onFire = { fires.incrementAndGet() },
        )

        debouncer.trigger()
        advanceTimeBy(5_000)
        debouncer.trigger()
        advanceTimeBy(5_000)
        debouncer.trigger()

        advanceTimeBy(31_000)
        scope.advanceUntilIdle()

        assertEquals(1, fires.get())
    }

    @Test
    fun `flushImmediately fires right away and cancels the pending job`() = runTest {
        val fires = AtomicInteger(0)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val debouncer = SnapshotDebouncer(
            scope = scope,
            delayMillis = 30_000L,
            onFire = { fires.incrementAndGet() },
        )

        debouncer.trigger()
        debouncer.flushImmediately()
        advanceTimeBy(35_000)
        scope.advanceUntilIdle()

        assertEquals(1, fires.get())
    }

    @Test
    fun `cancelPending stops a pending fire`() = runTest {
        val fires = AtomicInteger(0)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val debouncer = SnapshotDebouncer(
            scope = scope,
            delayMillis = 30_000L,
            onFire = { fires.incrementAndGet() },
        )

        debouncer.trigger()
        debouncer.cancelPending()
        advanceTimeBy(35_000)
        scope.advanceUntilIdle()

        assertEquals(0, fires.get())
    }
}
