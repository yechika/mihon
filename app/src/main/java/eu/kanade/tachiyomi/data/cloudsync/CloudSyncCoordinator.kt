package eu.kanade.tachiyomi.data.cloudsync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.cloudsync.service.CloudSyncPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga

/**
 * Wires the [CloudSyncEngine] to the rest of the app at process-lifetime scope:
 *
 * 1. when the user transitions to [AccountState.SignedIn] AND cloud sync is enabled, run a
 *    pull-and-restore (and let the engine decide if local upload is the right action instead),
 * 2. observe library and category changes; when they settle, debounce-push a snapshot.
 *
 * Triggered from `App.onCreate()` once. Stops everything when cloud sync is disabled.
 */
class CloudSyncCoordinator(
    private val scope: CoroutineScope,
    private val engine: CloudSyncEngine,
    private val accountManager: AccountManager,
    private val cloudSyncPrefs: CloudSyncPreferences,
    private val getLibraryManga: GetLibraryManga,
    private val getCategories: GetCategories,
    private val debouncerFactory: (CoroutineScope, suspend () -> Unit) -> SnapshotDebouncer = { s, fire ->
        SnapshotDebouncer(scope = s, onFire = fire)
    },
) {
    private var mutationJob: Job? = null
    private var signInJob: Job? = null
    private var debouncer: SnapshotDebouncer? = null

    fun start() {
        cloudSyncPrefs.cloudSyncEnabled.changes()
            .onEach { enabled ->
                if (enabled) startObservers() else stopObservers()
            }
            .launchIn(scope)

        if (cloudSyncPrefs.cloudSyncEnabled.get()) {
            startObservers()
        }
    }

    private fun startObservers() {
        if (mutationJob != null) return
        debouncer = debouncerFactory(scope) {
            runCatching { engine.pushSnapshotIfDirty() }
                .onFailure { logcat(LogPriority.WARN, it) { "Cloud sync push failed" } }
        }

        signInJob = accountManager.state
            .filterIsInstance<AccountState.SignedIn>()
            .onEach { _ ->
                runCatching {
                    when (engine.decideOnSignIn()) {
                        SignInDecision.RestoreCloud -> engine.pullAndRestoreIfNewer()
                        SignInDecision.UploadLocal -> engine.pushSnapshotIfDirty(force = true)
                        // AskUser is handled by the UI layer; coordinator does nothing here.
                        else -> Unit
                    }
                }.onFailure { logcat(LogPriority.WARN, it) { "Cloud sync sign-in trigger failed" } }
            }
            .launchIn(scope)

        mutationJob = combine(
            getLibraryManga.subscribe(),
            getCategories.subscribe(),
        ) { mangas, categories -> mangas.size to categories.size }
            .distinctUntilChanged()
            .drop(1) // skip initial emit
            .filter { accountManager.state.value is AccountState.SignedIn }
            .onEach { debouncer?.trigger() }
            .launchIn(scope)
    }

    private fun stopObservers() {
        signInJob?.cancel()
        mutationJob?.cancel()
        signInJob = null
        mutationJob = null
        debouncer = null
    }
}
