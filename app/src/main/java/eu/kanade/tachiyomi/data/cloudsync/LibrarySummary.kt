package eu.kanade.tachiyomi.data.cloudsync

data class LibrarySummary(
    val mangaCount: Int,
    val customCategoryCount: Int,
    val lastEditedAt: Long,
) {
    val isEmpty: Boolean
        get() = mangaCount == 0 && customCategoryCount == 0
}
