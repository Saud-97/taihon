package taihon.feature.settings

import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TaihonSettingsHooks {
    /**
     * The GitHub URL for the Taihon project.
     */
    const val TAIHON_GITHUB_URL = "https://github.com/Saud-97/taihon"

    /**
     * Whether to show the support item in the More screen.
     */
    fun isSupportItemVisible(): Boolean = false

    /**
     * Whether to show the donation campaign in the main activity.
     */
    fun shouldShowDonationCampaign(): Boolean = false

    /**
     * Hook for when page transitions are toggled.
     */
    fun taihonOnPageTransitionsChanged(newValue: Boolean) {
        val taihonPref = Injekt.get<TaihonPreferences>()
        if (!newValue) {
            taihonPref.pageTransitionSpeed.set(0)
        }
    }
}
