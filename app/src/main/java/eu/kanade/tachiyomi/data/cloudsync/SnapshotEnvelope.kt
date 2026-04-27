package eu.kanade.tachiyomi.data.cloudsync

data class SnapshotEnvelope(
    val payload: ByteArray,
    val updatedAt: Long,
    val schemaVersion: Int,
    val mangaCount: Int,
    val deviceLabel: String,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotEnvelope) return false
        return updatedAt == other.updatedAt &&
            schemaVersion == other.schemaVersion &&
            mangaCount == other.mangaCount &&
            deviceLabel == other.deviceLabel &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + schemaVersion
        result = 31 * result + mangaCount
        result = 31 * result + deviceLabel.hashCode()
        return result
    }
}

data class SnapshotMetadata(
    val updatedAt: Long,
    val schemaVersion: Int,
    val mangaCount: Int,
    val deviceLabel: String,
)
