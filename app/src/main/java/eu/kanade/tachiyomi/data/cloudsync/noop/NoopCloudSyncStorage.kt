package eu.kanade.tachiyomi.data.cloudsync.noop

import eu.kanade.tachiyomi.data.cloudsync.CloudSyncStorage
import eu.kanade.tachiyomi.data.cloudsync.MangaProgress
import eu.kanade.tachiyomi.data.cloudsync.SnapshotEnvelope
import eu.kanade.tachiyomi.data.cloudsync.SnapshotMetadata

class NoopCloudSyncStorage : CloudSyncStorage {

    override suspend fun fetchSnapshotMetadata(uid: String): SnapshotMetadata? = unsupported()

    override suspend fun fetchSnapshot(uid: String): SnapshotEnvelope? = unsupported()

    override suspend fun writeSnapshot(uid: String, envelope: SnapshotEnvelope) = unsupported()

    override suspend fun fetchProgress(uid: String, mangaId: String): MangaProgress? = unsupported()

    override suspend fun writeProgress(uid: String, progress: MangaProgress) = unsupported()

    override suspend fun deleteAll(uid: String) = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("Cloud sync unavailable in this build")
}
