package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeClock(initial: Long = 0L) : Clock {
    var current: Long = initial
    override fun nowMillis(): Long = current
    fun advance(deltaMillis: Long) {
        current += deltaMillis
    }
}

class FakeCloudSyncStorage : CloudSyncStorage {
    val snapshots = mutableMapOf<String, SnapshotEnvelope>()
    val progress = mutableMapOf<Pair<String, String>, MangaProgress>()
    var fetchSnapshotMetadataCount = 0
    var fetchSnapshotCount = 0
    var writeSnapshotCount = 0
    var writeProgressCount = 0
    var failNext: Throwable? = null

    private fun maybeFail() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun fetchSnapshotMetadata(uid: String): SnapshotMetadata? {
        fetchSnapshotMetadataCount++
        maybeFail()
        return snapshots[uid]?.let {
            SnapshotMetadata(it.updatedAt, it.schemaVersion, it.mangaCount, it.deviceLabel)
        }
    }

    override suspend fun fetchSnapshot(uid: String): SnapshotEnvelope? {
        fetchSnapshotCount++
        maybeFail()
        return snapshots[uid]
    }

    override suspend fun writeSnapshot(uid: String, envelope: SnapshotEnvelope) {
        writeSnapshotCount++
        maybeFail()
        snapshots[uid] = envelope
    }

    override suspend fun fetchProgress(uid: String, mangaId: String): MangaProgress? {
        maybeFail()
        return progress[uid to mangaId]
    }

    override suspend fun writeProgress(uid: String, progressIn: MangaProgress) {
        writeProgressCount++
        maybeFail()
        val existing = progress[uid to progressIn.mangaId]
        progress[uid to progressIn.mangaId] = existing?.mergedWith(progressIn) ?: progressIn
    }

    override suspend fun deleteAll(uid: String) {
        maybeFail()
        snapshots.remove(uid)
        progress.keys.removeAll { it.first == uid }
    }
}

class FakeAccountManager(initialState: AccountState = AccountState.SignedOut) : AccountManager {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<AccountState> = _state

    fun set(state: AccountState) {
        _state.value = state
    }

    override suspend fun signUp(username: String, password: String, email: String?): SignUpResult {
        val uid = "uid-$username"
        _state.value = AccountState.SignedIn(uid, username, email != null)
        return SignUpResult.Success(uid, username)
    }

    override suspend fun signIn(usernameOrEmail: String, password: String): SignInResult {
        val username = usernameOrEmail.substringBefore('@')
        val uid = "uid-$username"
        _state.value = AccountState.SignedIn(uid, username, false)
        return SignInResult.Success(uid, username)
    }

    override suspend fun signOut() {
        _state.value = AccountState.SignedOut
    }

    override suspend fun requestPasswordReset(usernameOrEmail: String): PasswordResetResult =
        PasswordResetResult.Sent

    override suspend fun addRecoveryEmail(email: String): AddEmailResult = AddEmailResult.Success

    override suspend fun deleteAccount(password: String): DeleteAccountResult =
        DeleteAccountResult.Success
}

class FakeSnapshotProducer(
    var nextPayload: ByteArray = byteArrayOf(0x42),
    var nextSchemaVersion: Int = 1,
    var nextMangaCount: Int = 0,
    var nextSummary: LibrarySummary = LibrarySummary(0, 0, 0L),
) : SnapshotProducer {

    var produceCount = 0

    override suspend fun produce(): SnapshotProducer.ProducedSnapshot {
        produceCount++
        return SnapshotProducer.ProducedSnapshot(
            payload = nextPayload,
            schemaVersion = nextSchemaVersion,
            mangaCount = nextMangaCount,
        )
    }

    override suspend fun summarise(): LibrarySummary = nextSummary
}

class FakeSnapshotConsumer : SnapshotConsumer {

    val applied = mutableListOf<Pair<ByteArray, Int>>()
    var failNext: Throwable? = null

    override suspend fun apply(payload: ByteArray, schemaVersion: Int) {
        failNext?.let {
            failNext = null
            throw it
        }
        applied += payload to schemaVersion
    }
}
