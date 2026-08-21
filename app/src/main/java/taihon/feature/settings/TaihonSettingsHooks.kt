package taihon.feature.settings

import eu.kanade.tachiyomi.R
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns the resource ID for the Taihon logo.
 */
fun taihonLogoResource(): Int = R.drawable.ic_taihon

/**
 * The GitHub URL for the Taihon project.
 */
const val TAIHON_GITHUB_URL = "https://github.com/Saud-97/taihon"

/**
 * Whether to show the support item in the More screen.
 */
fun isSupportItemVisible(): Boolean = false

/**
 * Hook for when page transitions are toggled.
 */
fun taihonOnPageTransitionsChanged(newValue: Boolean) {
    val taihonPref = Injekt.get<TaihonPreferences>()
    if (!newValue) {
        taihonPref.pageTransitionSpeed.set(0)
    }
}
