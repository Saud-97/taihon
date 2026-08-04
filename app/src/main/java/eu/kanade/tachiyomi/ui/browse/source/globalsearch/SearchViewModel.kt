package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.normalizeApostrophe
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mihon.core.viewmodel.StateViewModel
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.Executors

abstract class SearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    private val updateMangaFromRemote: mihon.domain.source.interactor.UpdateMangaFromRemote = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: eu.kanade.domain.manga.interactor.UpdateManga = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
) : StateViewModel<SearchViewModel.State>(initialState) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val globalSemaphore = Semaphore(10)
    private val sourceMutexes = mutableMapOf<Long, Mutex>()
    private val sessionCache = mutableSetOf<Long>()

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    protected val pinnedSources = sourcePreferences.pinnedSources.get()

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
    }

    private suspend fun fetchMangaDetails(manga: Manga) {
        if (manga.id in sessionCache) {
            val chapters = getChaptersByMangaId.await(manga.id)
            updateMangaDetails(
                manga.id,
                MangaDetails(
                    chapterCount = chapters.size,
                    latestChapter = chapters.maxOfOrNull { it.chapterNumber },
                    isLoading = false,
                ),
            )
            return
        }

        val isCacheValid = Instant.now().toEpochMilli() < manga.nextUpdate

        if (isCacheValid) {
            val chapters = getChaptersByMangaId.await(manga.id)
            updateMangaDetails(
                manga.id,
                MangaDetails(
                    chapterCount = chapters.size,
                    latestChapter = chapters.maxOfOrNull { it.chapterNumber },
                    isLoading = false,
                ),
            )
            sessionCache.add(manga.id)
            return
        }

        updateMangaDetails(
            manga.id,
            state.value.mangaDetails[manga.id]?.copy(isLoading = true) ?: MangaDetails(0, null, true),
        )

        val mutex = synchronized(sourceMutexes) {
            sourceMutexes.getOrPut(manga.source) { Mutex() }
        }

        mutex.withLock {
            globalSemaphore.withPermit {
                try {
                    updateMangaFromRemote(manga, fetchDetails = true, fetchChapters = true).getOrThrow()
                    setMangaDefaultChapterFlags.await(manga)
                    updateManga.await(
                        MangaUpdate(
                            id = manga.id,
                            nextUpdate = Instant.now().toEpochMilli() + 60 * 60 * 1000L,
                        ),
                    )

                    val chapters = getChaptersByMangaId.await(manga.id)
                    updateMangaDetails(
                        manga.id,
                        MangaDetails(
                            chapterCount = chapters.size,
                            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
                            isLoading = false,
                        ),
                    )
                    sessionCache.add(manga.id)
                } catch (e: Exception) {
                    updateMangaDetails(
                        manga.id,
                        state.value.mangaDetails[manga.id]?.copy(isLoading = false) ?: MangaDetails(0, null, false),
                    )
                }
            }
        }
    }

    private fun updateMangaDetails(mangaId: Long, details: MangaDetails) {
        mutableState.update {
            it.copy(mangaDetails = it.mangaDetails + (mangaId to details))
        }
    }

    @Composable
    fun getMangaDetails(manga: Manga): androidx.compose.runtime.State<MangaDetails?> {
        return produceState<MangaDetails?>(initialValue = null, manga.id) {
            state.collectLatest {
                value = it.mangaDetails[manga.id]
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<Source> {
        return sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: SearchItemResult.Loading },
            )
        } else {
            updateItems(
                sources
                    .associateWith { SearchItemResult.Loading },
            )
        }

        searchJob = viewModelScope.launchIO {
            val smartNormalizationEnabled = preferences.smartApostropheNormalization.get()
            val exceptions = preferences.smartApostropheNormalizationExceptions.get()

            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val pkgName = extensionManager.getExtensionPackage(source.id)
                        val isNormalized = smartNormalizationEnabled && pkgName !in exceptions
                        val normalizedQuery = if (isNormalized) {
                            query.normalizeApostrophe(fuzzy = true)
                        } else {
                            query
                        }

                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, normalizedQuery, source.getFilterList())
                        }

                        val titles = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                            if (preferences.globalSearchEnrichResults.get()) {
                                titles.forEach { title ->
                                    viewModelScope.launchIO {
                                        fetchMangaDetails(title)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items)),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        updateItems(state.value.items + (source to result))
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            mutableState.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: Map<Source, SearchItemResult> = mapOf(),
        val mangaDetails: Map<Long, MangaDetails> = mapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }

    data class MangaDetails(
        val chapterCount: Int,
        val latestChapter: Double?,
        val isLoading: Boolean = false,
    )

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
