package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.viewmodel.StateViewModel
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.SortedMap

class SourcesFilterViewModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getLanguagesWithSources: GetLanguagesWithSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
    private val sourceRepository: SourceRepository = Injekt.get(),
) : StateViewModel<SourcesFilterViewModel.State>(State.Loading) {

    init {
        viewModelScope.launch {
            val localSourceFlow = sourceRepository.getSources()
                .map { sources -> sources.find { it.id == 0L } }

            combine(
                getLanguagesWithSources.subscribe(),
                localSourceFlow,
                preferences.enabledLanguages.changes(),
                preferences.disabledSources.changes(),
            ) { languagesWithSources, localSource, enabledLanguages, disabledSources ->
                State.Success(
                    items = languagesWithSources,
                    localSource = localSource,
                    enabledLanguages = enabledLanguages,
                    disabledSources = disabledSources,
                )
            }
                .catch { throwable ->
                    mutableState.update {
                        State.Error(
                            throwable = throwable,
                        )
                    }
                }
                .collectLatest { successState ->
                    mutableState.update { successState }
                }
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<Source>>,
            val localSource: Source?,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()
        }
    }
}
