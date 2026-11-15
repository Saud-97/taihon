package taihon.feature.browse

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import mihon.app.di.appGraph
import tachiyomi.domain.source.model.Source
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.SortedMap

object TaihonSourcesFilterHelper {

    const val TAIHON_LOCAL_KEY = "taihon_local"

    fun wrapFilterFlow(flow: Flow<SortedMap<String, List<Source>>>): Flow<SortedMap<String, List<Source>>> {
        val sourceManager = Injekt.get<Context>().appGraph.sourceManager
        val localSourceFlow = sourceManager.sources.map { sources ->
            sources.find { it.id == LocalSource.ID }?.let {
                Source(
                    id = it.id,
                    lang = it.lang,
                    name = it.name,
                    supportsLatest = it.supportsLatest,
                    isStub = false,
                )
            }
        }

        return combine(flow, localSourceFlow) { items, local ->
            if (local != null) {
                val newMap = java.util.TreeMap<String, List<Source>>(items.comparator())
                newMap.putAll(items)
                newMap[TAIHON_LOCAL_KEY] = listOf(local)
                newMap
            } else {
                items
            }
        }
    }
}
