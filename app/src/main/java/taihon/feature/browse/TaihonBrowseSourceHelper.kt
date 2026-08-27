package taihon.feature.browse

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.lang.normalizeApostrophe
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TaihonBrowseSourceHelper {

    fun normalizeQuery(
        query: String?,
        sourceId: Long,
    ): String {
        val taihonPreferences = Injekt.get<TaihonPreferences>()
        val extensionManager = Injekt.get<ExtensionManager>()
        val pkgName = extensionManager.getExtensionPackage(sourceId)
        val isNormalized = taihonPreferences.smartApostropheNormalization.get() &&
            pkgName !in taihonPreferences.smartApostropheNormalizationExceptions.get()
        return if (isNormalized) {
            query?.normalizeApostrophe(fuzzy = true) ?: ""
        } else {
            query ?: ""
        }
    }
}
