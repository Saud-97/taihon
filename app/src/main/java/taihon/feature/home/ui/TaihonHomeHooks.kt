package taihon.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TaihonHomeHooks {
    @Composable
    fun extensionUpdatesCount(): State<Int> {
        val pref = Injekt.get<SourcePreferences>()
        val taihonPref = Injekt.get<TaihonPreferences>()
        return produceState(initialValue = 0) {
            combine(
                taihonPref.hideExtensionUpdatesCount.changes(),
                pref.extensionUpdatesCount.changes(),
            ) { hide, count -> if (!hide) count else 0 }
                .collectLatest { value = it }
        }
    }
}
