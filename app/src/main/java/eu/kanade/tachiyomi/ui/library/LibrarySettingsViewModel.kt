package eu.kanade.tachiyomi.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class LibrarySettingsViewModel(
    val preferences: BasePreferences,
    val libraryPreferences: LibraryPreferences,
    private val setDisplayMode: SetDisplayMode,
    private val setSortModeForCategory: SetSortModeForCategory,
    trackerManager: TrackerManager,
    getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount,
) : ViewModel() {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    val sourcesFlow = getSourcesWithFavoriteCount.subscribe()
        .map { list -> list.map { it.first } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun toggleSource(id: Long) {
        libraryPreferences.taihon.filterSource(id).getAndSet { it.next() }
    }

    fun toggleOrphanedSources() {
        libraryPreferences.taihon.filterOrphanedSources.getAndSet { it.next() }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        viewModelScope.launch {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
