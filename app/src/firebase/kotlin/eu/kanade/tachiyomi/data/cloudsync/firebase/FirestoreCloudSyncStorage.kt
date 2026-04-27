package eu.kanade.tachiyomi.data.cloudsync.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import eu.kanade.tachiyomi.data.cloudsync.CloudSyncStorage
import eu.kanade.tachiyomi.data.cloudsync.MangaProgress
import eu.kanade.tachiyomi.data.cloudsync.SnapshotEnvelope
import eu.kanade.tachiyomi.data.cloudsync.SnapshotMetadata
import kotlinx.coroutines.tasks.await

class FirestoreCloudSyncStorage(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CloudSyncStorage {

    override suspend fun fetchSnapshotMetadata(uid: String): SnapshotMetadata? {
        val snap = snapshotDocRef(uid)
            .get()
            .await()
        if (!snap.exists()) return null

        val updatedAt = snap.getLong(FIELD_UPDATED_AT) ?: return null
        return SnapshotMetadata(
            updatedAt = updatedAt,
            schemaVersion = snap.getLong(FIELD_SCHEMA_VERSION)?.toInt() ?: 0,
            mangaCount = snap.getLong(FIELD_MANGA_COUNT)?.toInt() ?: 0,
            deviceLabel = snap.getString(FIELD_DEVICE_LABEL).orEmpty(),
        )
    }

    override suspend fun fetchSnapshot(uid: String): SnapshotEnvelope? {
        val snap = snapshotDocRef(uid).get().await()
        if (!snap.exists()) return null

        val payloadBlob = snap.getBlob(FIELD_PAYLOAD) ?: return null
        val updatedAt = snap.getLong(FIELD_UPDATED_AT) ?: return null
        return SnapshotEnvelope(
            payload = payloadBlob.toBytes(),
            updatedAt = updatedAt,
            schemaVersion = snap.getLong(FIELD_SCHEMA_VERSION)?.toInt() ?: 0,
            mangaCount = snap.getLong(FIELD_MANGA_COUNT)?.toInt() ?: 0,
            deviceLabel = snap.getString(FIELD_DEVICE_LABEL).orEmpty(),
        )
    }

    override suspend fun writeSnapshot(uid: String, envelope: SnapshotEnvelope) {
        val data = mapOf(
            FIELD_PAYLOAD to com.google.firebase.firestore.Blob.fromBytes(envelope.payload),
            FIELD_UPDATED_AT to envelope.updatedAt,
            FIELD_SCHEMA_VERSION to envelope.schemaVersion,
            FIELD_MANGA_COUNT to envelope.mangaCount,
            FIELD_DEVICE_LABEL to envelope.deviceLabel,
        )
        snapshotDocRef(uid).set(data).await()
    }

    override suspend fun fetchProgress(uid: String, mangaId: String): MangaProgress? {
        val snap = progressDocRef(uid, mangaId).get().await()
        if (!snap.exists()) return null

        val readChapters = (snap.get(FIELD_READ_CHAPTER_IDS) as? List<*>)
            ?.filterIsInstance<String>()
            ?.toSet()
            .orEmpty()
        return MangaProgress(
            mangaId = snap.getString(FIELD_MANGA_ID) ?: mangaId,
            lastReadChapterId = snap.getString(FIELD_LAST_READ_CHAPTER_ID),
            lastReadAt = snap.getLong(FIELD_LAST_READ_AT) ?: 0L,
            readChapterIds = readChapters,
        )
    }

    override suspend fun writeProgress(uid: String, progress: MangaProgress) {
        val data = mapOf(
            FIELD_MANGA_ID to progress.mangaId,
            FIELD_LAST_READ_CHAPTER_ID to progress.lastReadChapterId,
            FIELD_LAST_READ_AT to progress.lastReadAt,
            FIELD_READ_CHAPTER_IDS to FieldValue.arrayUnion(*progress.readChapterIds.toTypedArray()),
        )
        progressDocRef(uid, progress.mangaId)
            .set(data, SetOptions.merge())
            .await()
    }

    override suspend fun deleteAll(uid: String) {
        // Client-side recursive delete. For large libraries the Firestore docs recommend a
        // Cloud Function; v1 accepts the client-side cost.
        val userDoc = firestore.collection(COLLECTION_USERS).document(uid)
        // Delete known subcollections first.
        deleteCollection(userDoc.collection(SUBCOLLECTION_LIBRARY))
        deleteCollection(userDoc.collection(SUBCOLLECTION_PROGRESS))
        deleteCollection(userDoc.collection(SUBCOLLECTION_PROFILE))
        userDoc.delete().await()
    }

    private suspend fun deleteCollection(
        ref: com.google.firebase.firestore.CollectionReference,
    ) {
        val docs = ref.get().await().documents
        for (doc in docs) {
            doc.reference.delete().await()
        }
    }

    private fun snapshotDocRef(uid: String) =
        firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(SUBCOLLECTION_LIBRARY)
            .document(DOC_SNAPSHOT)

    private fun progressDocRef(uid: String, mangaId: String) =
        firestore.collection(COLLECTION_USERS)
            .document(uid)
            .collection(SUBCOLLECTION_PROGRESS)
            .document(mangaId)

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_USERNAMES = "usernames"
        const val SUBCOLLECTION_LIBRARY = "library"
        const val SUBCOLLECTION_PROGRESS = "progress"
        const val SUBCOLLECTION_PROFILE = "profile"
        const val DOC_SNAPSHOT = "snapshot"

        const val FIELD_PAYLOAD = "payload"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_SCHEMA_VERSION = "schemaVersion"
        const val FIELD_MANGA_COUNT = "mangaCount"
        const val FIELD_DEVICE_LABEL = "deviceLabel"

        const val FIELD_MANGA_ID = "mangaId"
        const val FIELD_LAST_READ_CHAPTER_ID = "lastReadChapterId"
        const val FIELD_LAST_READ_AT = "lastReadAt"
        const val FIELD_READ_CHAPTER_IDS = "readChapterIds"
    }
}
