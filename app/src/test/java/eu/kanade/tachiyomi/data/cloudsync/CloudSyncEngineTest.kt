package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudSyncEngineTest {

    private fun newEngine(
        accountState: AccountState = AccountState.SignedIn("uid-1", "tester", false),
    ): Quadruple<CloudSyncEngine, FakeCloudSyncStorage, FakeSnapshotProducer, FakeSnapshotConsumer> {
        val storage = FakeCloudSyncStorage()
        val account = FakeAccountManager(accountState)
        val producer = FakeSnapshotProducer(nextMangaCount = 5)
        val consumer = FakeSnapshotConsumer()
        val engine = CloudSyncEngine(
            storage = storage,
            accountManager = account,
            snapshotProducer = producer,
            snapshotConsumer = consumer,
            clock = FakeClock(1_000L),
            deviceLabel = "test-device",
        )
        return Quadruple(engine, storage, producer, consumer)
    }

    @Test
    fun `pushSnapshotIfDirty writes to storage when signed in`() = runTest {
        val (engine, storage, _, _) = newEngine()

        val result = engine.pushSnapshotIfDirty()

        assertInstanceOf(PushResult.Pushed::class.java, result)
        assertEquals(1, storage.writeSnapshotCount)
        assertEquals(5, storage.snapshots["uid-1"]!!.mangaCount)
        assertEquals("test-device", storage.snapshots["uid-1"]!!.deviceLabel)
    }

    @Test
    fun `pushSnapshotIfDirty refuses when signed out`() = runTest {
        val (engine, storage, _, _) = newEngine(accountState = AccountState.SignedOut)

        val result = engine.pushSnapshotIfDirty()

        assertEquals(PushResult.NotSignedIn, result)
        assertEquals(0, storage.writeSnapshotCount)
    }

    @Test
    fun `pushSnapshotIfDirty refuses when unavailable`() = runTest {
        val (engine, storage, _, _) = newEngine(accountState = AccountState.Unavailable)

        val result = engine.pushSnapshotIfDirty()

        assertEquals(PushResult.NotSignedIn, result)
        assertEquals(0, storage.writeSnapshotCount)
    }

    @Test
    fun `pushSnapshotIfDirty skips empty payload after first sync unless forced`() = runTest {
        val (engine, storage, producer, _) = newEngine()
        producer.nextMangaCount = 0
        engine.setLastSyncedAt(500L)

        val result = engine.pushSnapshotIfDirty()

        assertEquals(PushResult.NothingToDo, result)
        assertEquals(0, storage.writeSnapshotCount)
    }

    @Test
    fun `pushSnapshotIfDirty force still uploads empty payload`() = runTest {
        val (engine, storage, producer, _) = newEngine()
        producer.nextMangaCount = 0
        engine.setLastSyncedAt(500L)

        val result = engine.pushSnapshotIfDirty(force = true)

        assertInstanceOf(PushResult.Pushed::class.java, result)
        assertEquals(1, storage.writeSnapshotCount)
    }

    @Test
    fun `pullAndRestoreIfNewer skips when local is up to date`() = runTest {
        val (engine, storage, _, consumer) = newEngine()
        storage.snapshots["uid-1"] = SnapshotEnvelope(
            payload = byteArrayOf(1),
            updatedAt = 100L,
            schemaVersion = 1,
            mangaCount = 1,
            deviceLabel = "other",
        )
        engine.setLastSyncedAt(200L)

        val result = engine.pullAndRestoreIfNewer()

        assertEquals(PullResult.LocalIsUpToDate, result)
        assertTrue(consumer.applied.isEmpty())
        assertEquals(0, storage.fetchSnapshotCount)
    }

    @Test
    fun `pullAndRestoreIfNewer downloads payload only when newer`() = runTest {
        val (engine, storage, _, consumer) = newEngine()
        val payload = byteArrayOf(7, 8, 9)
        storage.snapshots["uid-1"] = SnapshotEnvelope(
            payload = payload,
            updatedAt = 5_000L,
            schemaVersion = 2,
            mangaCount = 3,
            deviceLabel = "other",
        )
        engine.setLastSyncedAt(0L)

        val result = engine.pullAndRestoreIfNewer()

        assertInstanceOf(PullResult.Restored::class.java, result)
        assertEquals(1, consumer.applied.size)
        assertTrue(consumer.applied.first().first.contentEquals(payload))
        assertEquals(2, consumer.applied.first().second)
    }

    @Test
    fun `pullAndRestoreIfNewer reports no cloud snapshot when missing`() = runTest {
        val (engine, _, _, _) = newEngine()

        val result = engine.pullAndRestoreIfNewer()

        assertEquals(PullResult.NoCloudSnapshot, result)
    }

    @Test
    fun `pullAndRestoreIfNewer surfaces failure when storage throws`() = runTest {
        val (engine, storage, _, _) = newEngine()
        storage.failNext = RuntimeException("boom")

        val result = engine.pullAndRestoreIfNewer()

        assertInstanceOf(PullResult.Failed::class.java, result)
    }

    @Test
    fun `recordChapterRead writes a new progress doc`() = runTest {
        val (engine, storage, _, _) = newEngine()

        val result = engine.recordChapterRead("manga-1", "ch-1")

        assertInstanceOf(ProgressResult.Recorded::class.java, result)
        val stored = storage.progress["uid-1" to "manga-1"]!!
        assertTrue(stored.readChapterIds.contains("ch-1"))
    }

    @Test
    fun `recordChapterRead unions read sets across two writes`() = runTest {
        val (engine, storage, _, _) = newEngine()

        engine.recordChapterRead("manga-1", "ch-1")
        engine.recordChapterRead("manga-1", "ch-2")

        val stored = storage.progress["uid-1" to "manga-1"]!!
        assertEquals(setOf("ch-1", "ch-2"), stored.readChapterIds)
    }

    @Test
    fun `decideOnSignUp returns StartFresh on empty library`() = runTest {
        val (engine, _, producer, _) = newEngine()
        producer.nextSummary = LibrarySummary(0, 0, 0L)

        assertEquals(SignUpDecision.StartFresh, engine.decideOnSignUp())
    }

    @Test
    fun `decideOnSignUp returns AskUser when library is non-empty`() = runTest {
        val (engine, _, producer, _) = newEngine()
        producer.nextSummary = LibrarySummary(7, 2, 12_345L)

        val decision = engine.decideOnSignUp()

        assertInstanceOf(SignUpDecision.AskUser::class.java, decision)
        assertEquals(7, (decision as SignUpDecision.AskUser).localSummary.mangaCount)
    }

    @Test
    fun `decideOnSignIn empty-empty returns NoAction`() = runTest {
        val (engine, _, producer, _) = newEngine()
        producer.nextSummary = LibrarySummary(0, 0, 0L)

        assertEquals(SignInDecision.NoAction, engine.decideOnSignIn())
    }

    @Test
    fun `decideOnSignIn empty local with cloud returns RestoreCloud`() = runTest {
        val (engine, storage, producer, _) = newEngine()
        producer.nextSummary = LibrarySummary(0, 0, 0L)
        storage.snapshots["uid-1"] = SnapshotEnvelope(byteArrayOf(1), 100L, 1, 9, "other")

        assertEquals(SignInDecision.RestoreCloud, engine.decideOnSignIn())
    }

    @Test
    fun `decideOnSignIn local with empty cloud returns UploadLocal`() = runTest {
        val (engine, _, producer, _) = newEngine()
        producer.nextSummary = LibrarySummary(3, 1, 100L)

        assertEquals(SignInDecision.UploadLocal, engine.decideOnSignIn())
    }

    @Test
    fun `decideOnSignIn both populated returns AskUser`() = runTest {
        val (engine, storage, producer, _) = newEngine()
        producer.nextSummary = LibrarySummary(3, 1, 100L)
        storage.snapshots["uid-1"] = SnapshotEnvelope(byteArrayOf(1), 200L, 1, 9, "other")

        val decision = engine.decideOnSignIn()

        assertInstanceOf(SignInDecision.AskUser::class.java, decision)
        val ask = decision as SignInDecision.AskUser
        assertEquals(3, ask.localSummary.mangaCount)
        assertEquals(9, ask.cloudMetadata.mangaCount)
    }

    @Test
    fun `decideOnSignIn returns NoAction when signed out`() = runTest {
        val storage = FakeCloudSyncStorage()
        val account = FakeAccountManager(AccountState.SignedOut)
        val producer = FakeSnapshotProducer(nextSummary = LibrarySummary(5, 0, 0L))
        val consumer = FakeSnapshotConsumer()
        val engine = CloudSyncEngine(
            storage = storage,
            accountManager = account,
            snapshotProducer = producer,
            snapshotConsumer = consumer,
            lastSyncedAtStore = InMemoryLongStore(),
            clock = FakeClock(),
            deviceLabel = "x",
        )

        assertEquals(SignInDecision.NoAction, engine.decideOnSignIn())
    }

    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
