package eu.kanade.tachiyomi.data.cloudsync

/**
 * Wipes the local library so a subsequent restore truly *replaces* the local data instead of
 * upsert-merging with it. Used by the "Use cloud (replace local)" branch of the sign-in
 * conflict modal.
 *
 * The contract is "soft wipe": entries are removed from the library and categories are cleared,
 * but downloaded chapter image files and history entries remain untouched on disk so the
 * subsequent restore can re-link them.
 */
interface LocalLibraryWiper {
    suspend fun wipe()
}
