package eu.kanade.tachiyomi.data.cloudsync

import android.content.Context
import eu.kanade.tachiyomi.data.cloudsync.noop.NoopAccountManager
import eu.kanade.tachiyomi.data.cloudsync.noop.NoopCloudSyncStorage

@Suppress("UNUSED_PARAMETER")
class CloudSyncBindings(context: Context) {

    val accountManager: AccountManager = NoopAccountManager()

    val storage: CloudSyncStorage = NoopCloudSyncStorage()

    fun isFeatureAvailable(): Boolean = false
}
