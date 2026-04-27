package eu.kanade.tachiyomi.data.cloudsync

import android.content.Context
import com.google.firebase.FirebaseApp
import eu.kanade.tachiyomi.data.cloudsync.firebase.CloudSyncProductionGuard
import eu.kanade.tachiyomi.data.cloudsync.firebase.FirebaseAccountManager
import eu.kanade.tachiyomi.data.cloudsync.firebase.FirestoreCloudSyncStorage
import eu.kanade.tachiyomi.data.cloudsync.noop.NoopAccountManager
import eu.kanade.tachiyomi.data.cloudsync.noop.NoopCloudSyncStorage

class CloudSyncBindings(context: Context) {

    private val available: Boolean = run {
        if (!CloudSyncProductionGuard.isAllowed(context)) return@run false
        runCatching { FirebaseApp.initializeApp(context) }.isSuccess
    }

    val accountManager: AccountManager =
        if (available) FirebaseAccountManager() else NoopAccountManager()

    val storage: CloudSyncStorage =
        if (available) FirestoreCloudSyncStorage() else NoopCloudSyncStorage()

    fun isFeatureAvailable(): Boolean = available
}
