package eu.kanade.tachiyomi.data.cloudsync

data class MangaProgress(
    val mangaId: String,
    val lastReadChapterId: String?,
    val lastReadAt: Long,
    val readChapterIds: Set<String>,
) {

    fun mergedWith(other: MangaProgress): MangaProgress {
        require(mangaId == other.mangaId) { "Cannot merge progress for different manga" }
        val newer = if (other.lastReadAt >= lastReadAt) other else this
        return MangaProgress(
            mangaId = mangaId,
            lastReadChapterId = newer.lastReadChapterId,
            lastReadAt = maxOf(lastReadAt, other.lastReadAt),
            readChapterIds = readChapterIds + other.readChapterIds,
        )
    }
}
