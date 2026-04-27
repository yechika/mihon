package eu.kanade.tachiyomi.data.cloudsync

import tachiyomi.core.common.preference.Preference

class PreferenceLongStore(
    private val preference: Preference<Long>,
) : LongStore {

    override fun get(): Long = preference.get()

    override fun set(value: Long) = preference.set(value)
}
