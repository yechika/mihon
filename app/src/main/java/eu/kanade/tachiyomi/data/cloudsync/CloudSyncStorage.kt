package eu.kanade.tachiyomi.data.cloudsync

interface CloudSyncStorage {

    suspend fun fetchSnapshotMetadata(uid: String): SnapshotMetadata?

    suspend fun fetchSnapshot(uid: String): SnapshotEnvelope?

    suspend fun writeSnapshot(uid: String, envelope: SnapshotEnvelope)

    suspend fun fetchProgress(uid: String, mangaId: String): MangaProgress?

    suspend fun writeProgress(uid: String, progress: MangaProgress)

    suspend fun deleteAll(uid: String)
}
