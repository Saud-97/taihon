package taihon.feature.browse

import android.content.Context
import eu.kanade.tachiyomi.util.lang.normalizeApostrophe
import mihon.app.di.appGraph
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TaihonBrowseSourceHelper {

    suspend fun normalizeQuery(
        query: String?,
        sourceId: Long,
    ): String {
        val taihonPreferences = Injekt.get<Context>().appGraph.taihonPreferences
        val extensionManager = Injekt.get<Context>().appGraph.extensionManager
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
