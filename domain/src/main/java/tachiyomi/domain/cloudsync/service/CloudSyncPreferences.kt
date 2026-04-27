package tachiyomi.domain.cloudsync.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class CloudSyncPreferences(
    preferenceStore: PreferenceStore,
) {

    val cloudSyncEnabled: Preference<Boolean> = preferenceStore.getBoolean(
        "cloud_sync_enabled",
        false,
    )

    val lastSyncedAt: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("cloud_sync_last_synced_at"),
        0L,
    )

    val divergedFromCloud: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("cloud_sync_diverged_from_cloud"),
        false,
    )
}
