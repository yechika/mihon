package tachiyomi.domain.mihonmod.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class MihonModPreferences(
    preferenceStore: PreferenceStore,
) {

    /**
     * Set to `true` after the first-launch "Mihon detected" dialog has been shown once on this
     * install. The dialog is one-shot per install and will not reappear unless the user
     * uninstalls and reinstalls MihonMod.
     */
    val mihonmodOnboardingShown: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("mihonmod_onboarding_shown"),
        false,
    )
}
