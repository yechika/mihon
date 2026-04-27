package eu.kanade.tachiyomi.data.cloudsync

interface SnapshotProducer {

    suspend fun produce(): ProducedSnapshot

    suspend fun summarise(): LibrarySummary

    data class ProducedSnapshot(
        val payload: ByteArray,
        val schemaVersion: Int,
        val mangaCount: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProducedSnapshot) return false
            return schemaVersion == other.schemaVersion &&
                mangaCount == other.mangaCount &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = payload.contentHashCode()
            result = 31 * result + schemaVersion
            result = 31 * result + mangaCount
            return result
        }
    }
}

interface SnapshotConsumer {

    suspend fun apply(payload: ByteArray, schemaVersion: Int)
}
