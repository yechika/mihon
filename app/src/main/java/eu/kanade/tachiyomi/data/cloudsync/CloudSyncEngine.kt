package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent value store for the engine's last-synced timestamp. Production uses a
 * Preference-backed implementation so the value survives process death; tests use an in-memory
 * fake.
 */
interface LongStore {
    fun get(): Long
    fun set(value: Long)
}

class InMemoryLongStore(initial: Long = 0L) : LongStore {
    private var current: Long = initial
    override fun get(): Long = current
    override fun set(value: Long) {
        current = value
    }
}

private object NoOpLibraryWiper : LocalLibraryWiper {
    override suspend fun wipe() = Unit
}

class CloudSyncEngine(
    private val storage: CloudSyncStorage,
    private val accountManager: AccountManager,
    private val snapshotProducer: SnapshotProducer,
    private val snapshotConsumer: SnapshotConsumer,
    private val lastSyncedAtStore: LongStore = InMemoryLongStore(),
    private val clock: Clock = Clock.System,
    private val deviceLabel: String = "unknown-device",
    private val localLibraryWiper: LocalLibraryWiper = NoOpLibraryWiper,
) {
    private val pushMutex = Mutex()
    private val pullMutex = Mutex()
    private val _lastSyncedAt = MutableStateFlow(lastSyncedAtStore.get())
    val lastSyncedAt: Flow<Long> get() = _lastSyncedAt.asStateFlow()

    suspend fun pushSnapshotIfDirty(force: Boolean = false): PushResult = pushMutex.withLock {
        val signedIn = currentSignedInOrNull() ?: return PushResult.NotSignedIn

        val produced = snapshotProducer.produce()
        if (!force && produced.mangaCount == 0 && lastSyncedAtStore.get() != 0L) {
            return PushResult.NothingToDo
        }

        val now = clock.nowMillis()
        val envelope = SnapshotEnvelope(
            payload = produced.payload,
            updatedAt = now,
            schemaVersion = produced.schemaVersion,
            mangaCount = produced.mangaCount,
            deviceLabel = deviceLabel,
        )

        return runCatching { storage.writeSnapshot(signedIn.uid, envelope) }
            .fold(
                onSuccess = {
                    persistLastSyncedAt(now)
                    PushResult.Pushed(now)
                },
                onFailure = { PushResult.Failed(it.message ?: "unknown error") },
            )
    }

    suspend fun pullAndRestoreIfNewer(): PullResult = pullMutex.withLock {
        val signedIn = currentSignedInOrNull() ?: return PullResult.NotSignedIn

        val metadata = runCatching { storage.fetchSnapshotMetadata(signedIn.uid) }
            .getOrElse { return PullResult.Failed(it.message ?: "unknown error") }
            ?: return PullResult.NoCloudSnapshot

        if (metadata.updatedAt <= lastSyncedAtStore.get()) {
            return PullResult.LocalIsUpToDate
        }

        val envelope = runCatching { storage.fetchSnapshot(signedIn.uid) }
            .getOrElse { return PullResult.Failed(it.message ?: "unknown error") }
            ?: return PullResult.NoCloudSnapshot

        return runCatching { snapshotConsumer.apply(envelope.payload, envelope.schemaVersion) }
            .fold(
                onSuccess = {
                    persistLastSyncedAt(metadata.updatedAt)
                    PullResult.Restored(metadata.updatedAt)
                },
                onFailure = { PullResult.Failed(it.message ?: "unknown error") },
            )
    }

    suspend fun recordChapterRead(mangaId: String, chapterId: String): ProgressResult {
        val signedIn = currentSignedInOrNull() ?: return ProgressResult.NotSignedIn

        val now = clock.nowMillis()
        val existing = runCatching { storage.fetchProgress(signedIn.uid, mangaId) }
            .getOrElse { return ProgressResult.Failed(it.message ?: "unknown error") }

        val merged = MangaProgress(
            mangaId = mangaId,
            lastReadChapterId = chapterId,
            lastReadAt = now,
            readChapterIds = (existing?.readChapterIds.orEmpty()) + chapterId,
        ).let { fresh -> if (existing != null) existing.mergedWith(fresh) else fresh }

        return runCatching { storage.writeProgress(signedIn.uid, merged) }
            .fold(
                onSuccess = { ProgressResult.Recorded(merged) },
                onFailure = { ProgressResult.Failed(it.message ?: "unknown error") },
            )
    }

    suspend fun decideOnSignUp(): SignUpDecision {
        val summary = snapshotProducer.summarise()
        return if (summary.isEmpty) SignUpDecision.StartFresh else SignUpDecision.AskUser(summary)
    }

    suspend fun decideOnSignIn(): SignInDecision {
        val signedIn = currentSignedInOrNull() ?: return SignInDecision.NoAction
        val localSummary = snapshotProducer.summarise()
        val cloudMetadata = runCatching { storage.fetchSnapshotMetadata(signedIn.uid) }.getOrNull()

        val cloudIsEmpty = cloudMetadata == null || cloudMetadata.mangaCount == 0

        return when {
            localSummary.isEmpty && cloudIsEmpty -> SignInDecision.NoAction
            localSummary.isEmpty && !cloudIsEmpty -> SignInDecision.RestoreCloud
            !localSummary.isEmpty && cloudIsEmpty -> SignInDecision.UploadLocal
            else -> SignInDecision.AskUser(localSummary, cloudMetadata!!)
        }
    }

    /**
     * "Use cloud (replace local)" path from the sign-in conflict modal. Bypasses the
     * `lastSyncedAt` freshness check, wipes the local library so the restore truly replaces
     * instead of merging, and then applies the cloud snapshot.
     */
    suspend fun forceRestoreCloud(): PullResult = pullMutex.withLock {
        val signedIn = currentSignedInOrNull() ?: return PullResult.NotSignedIn

        val envelope = runCatching { storage.fetchSnapshot(signedIn.uid) }
            .getOrElse { return PullResult.Failed(it.message ?: "unknown error") }
            ?: return PullResult.NoCloudSnapshot

        runCatching { localLibraryWiper.wipe() }
            .onFailure { return PullResult.Failed("wipe failed: ${it.message ?: "unknown"}") }

        return runCatching { snapshotConsumer.apply(envelope.payload, envelope.schemaVersion) }
            .fold(
                onSuccess = {
                    persistLastSyncedAt(envelope.updatedAt)
                    PullResult.Restored(envelope.updatedAt)
                },
                onFailure = { PullResult.Failed(it.message ?: "unknown error") },
            )
    }

    /**
     * "Use local (replace cloud)" path from the sign-in conflict modal. Deletes every cloud
     * document for the user, then pushes a fresh snapshot from the local library so the
     * cloud is rebuilt from scratch.
     */
    suspend fun replaceCloudWithLocal(): PushResult = pushMutex.withLock {
        val signedIn = currentSignedInOrNull() ?: return PushResult.NotSignedIn

        runCatching { storage.deleteAll(signedIn.uid) }
            .onFailure { return PushResult.Failed("clear cloud failed: ${it.message ?: "unknown"}") }

        // Reset lastSyncedAt so the engine knows the cloud is fresh for this account.
        persistLastSyncedAt(0L)

        val produced = snapshotProducer.produce()
        val now = clock.nowMillis()
        val envelope = SnapshotEnvelope(
            payload = produced.payload,
            updatedAt = now,
            schemaVersion = produced.schemaVersion,
            mangaCount = produced.mangaCount,
            deviceLabel = deviceLabel,
        )
        return runCatching { storage.writeSnapshot(signedIn.uid, envelope) }
            .fold(
                onSuccess = {
                    persistLastSyncedAt(now)
                    PushResult.Pushed(now)
                },
                onFailure = { PushResult.Failed(it.message ?: "unknown error") },
            )
    }

    fun setLastSyncedAt(value: Long) {
        persistLastSyncedAt(value)
    }

    private fun persistLastSyncedAt(value: Long) {
        lastSyncedAtStore.set(value)
        _lastSyncedAt.value = value
    }

    private fun currentSignedInOrNull(): AccountState.SignedIn? {
        return accountManager.state.value as? AccountState.SignedIn
    }
}

sealed interface PushResult {
    data class Pushed(val updatedAt: Long) : PushResult
    data object NothingToDo : PushResult
    data object NotSignedIn : PushResult
    data class Failed(val reason: String) : PushResult
}

sealed interface PullResult {
    data class Restored(val updatedAt: Long) : PullResult
    data object LocalIsUpToDate : PullResult
    data object NoCloudSnapshot : PullResult
    data object NotSignedIn : PullResult
    data class Failed(val reason: String) : PullResult
}

sealed interface ProgressResult {
    data class Recorded(val merged: MangaProgress) : ProgressResult
    data object NotSignedIn : ProgressResult
    data class Failed(val reason: String) : ProgressResult
}
