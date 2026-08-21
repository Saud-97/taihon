package taihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import taihon.domain.preferences.TaihonPreferences
import tachiyomi.domain.source.model.Source as DomainSource

/**
 * Returns a flow of source filters.
 */
fun LibraryViewModel.getTaihonSourceFiltersFlow(
    sourceManager: SourceManager,
    taihonPreferences: TaihonPreferences,
): Flow<Pair<Map<Long, TriState>, TriState>> {
    return sourceManager.sources
        .map { sources -> sources.filterNot { it is StubSource }.map { it.id }.toSet() }
        .distinctUntilChanged()
        .flatMapLatest { sourceIds ->
            if (sourceIds.isEmpty()) {
                taihonPreferences.filterOrphanedSources.changes().map { emptyMap<Long, TriState>() to it }
            } else {
                val filterFlows = sourceIds.map { id ->
                    taihonPreferences.filterSource(id).changes().map { id to it }
                }
                combine(filterFlows) { it.toMap() }
                    .combine(taihonPreferences.filterOrphanedSources.changes(), ::Pair)
            }
        }
}

/**
 * Applies Taihon source filters to a library item.
 */
fun LibraryViewModel.libraryItemTaihonSourceFilter(
    item: LibraryItem,
    sourceManager: SourceManager,
    sourceFilter: Map<Long, TriState>,
    orphanedSourceFilter: TriState,
): Boolean {
    val excludedSources = sourceFilter.filter { it.value == TriState.ENABLED_NOT }.keys
    val includedSources = sourceFilter.filter { it.value == TriState.ENABLED_IS }.keys
    val orphanedIsExcluded = orphanedSourceFilter == TriState.ENABLED_NOT
    val orphanedIsIncluded = orphanedSourceFilter == TriState.ENABLED_IS

    val sourceId = item.libraryManga.manga.source
    val source = sourceManager.get(sourceId)
    val isOrphaned = source == null || source is StubSource

    val isExcluded = if (isOrphaned) orphanedIsExcluded else sourceId in excludedSources
    val isAnyIncluded = includedSources.isNotEmpty() || orphanedIsIncluded
    val isIncluded = if (isAnyIncluded) {
        if (isOrphaned) orphanedIsIncluded else sourceId in includedSources
    } else {
        true
    }

    return !isExcluded && isIncluded
}

/**
 * Creates a domain source object for the library badge if enabled.
 */
fun createLibraryBadgeSource(
    sourceManager: SourceManager,
    sourceId: Long,
    sourceInstalledBadge: Boolean,
    sourceOrphanedBadge: Boolean,
): DomainSource? {
    val source = sourceManager.getOrStub(sourceId)
    val isStub = source is StubSource
    val showSource = (isStub && sourceOrphanedBadge) || (!isStub && sourceInstalledBadge)
    return if (showSource) {
        DomainSource(
            id = source.id,
            lang = source.lang,
            name = source.name,
            supportsLatest = source.supportsLatest,
            isStub = isStub,
        )
    } else {
        null
    }
}
