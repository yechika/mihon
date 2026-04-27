package eu.kanade.tachiyomi.data.cloudsync

import eu.kanade.domain.manga.interactor.UpdateManga
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Removes every favourite from the local library and deletes every user-created category.
 * Used by the "Use cloud (replace local)" branch of the sign-in conflict modal so that the
 * subsequent restore truly replaces local data instead of merging with it.
 *
 * The wipe is "soft": chapters and history rows stay in the database. Only the [favorite]
 * flag and the category list are touched. After the cloud restore reapplies its favourites,
 * any manga that exists locally but not in the cloud snapshot stays in the database with
 * `favorite = false`, effectively hidden from the library view.
 */
class BackupBackedLibraryWiper(
    private val getFavorites: GetFavorites = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val deleteCategory: DeleteCategory = Injekt.get(),
) : LocalLibraryWiper {

    override suspend fun wipe() {
        val favourites = getFavorites.await()
        if (favourites.isNotEmpty()) {
            updateManga.awaitAll(
                favourites.map { MangaUpdate(id = it.id, favorite = false) },
            )
        }
        val customCategories = getCategories.await().filter { !it.isSystemCategory }
        customCategories.forEach { category: Category ->
            deleteCategory.await(category.id)
        }
    }
}
