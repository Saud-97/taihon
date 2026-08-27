package taihon.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.model.Manga
import taihon.domain.preferences.TaihonPreferences
import taihon.feature.browse.util.normalizeSearchQuery
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Helper to isolate Taihon search logic from SearchViewModel.
 */
object TaihonSearchHelper {
    private val mangaDetails = MutableStateFlow<Map<Long, TaihonSearchEnricher.MangaDetails>>(mapOf())
    private val activeJobs = mutableSetOf<Job>()

    private val enricher = TaihonSearchEnricher(
        onUpdateDetails = { mangaId, details ->
            mangaDetails.update { it + (mangaId to details) }
        },
        getCurrentDetails = { mangaDetails.value[it] },
    )

    private fun registerJob(job: Job) {
        synchronized(activeJobs) {
            if (activeJobs.add(job)) {
                job.invokeOnCompletion {
                    synchronized(activeJobs) {
                        activeJobs.remove(job)
                        if (activeJobs.isEmpty()) {
                            clear()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun getMangaDetails(manga: Manga): State<TaihonSearchEnricher.MangaDetails?> {
        val detailsState = mangaDetails.collectAsState()
        return androidx.compose.runtime.produceState<TaihonSearchEnricher.MangaDetails?>(
            initialValue = null,
            key1 = manga.id,
            key2 = detailsState.value,
        ) {
            value = detailsState.value[manga.id]
        }
    }

    fun clear() {
        mangaDetails.update { mapOf() }
        enricher.clear()
    }

    internal fun normalize(source: Source, query: String): String {
        val extensionManager: ExtensionManager = Injekt.get()
        val taihonPreferences: TaihonPreferences = Injekt.get()

        val pkgName = extensionManager.getExtensionPackage(source.id)
        return query.normalizeSearchQuery(
            pkgName = pkgName,
            smartNormalizationEnabled = taihonPreferences.smartApostropheNormalization.get(),
            exceptions = taihonPreferences.smartApostropheNormalizationExceptions.get(),
        )
    }

    internal fun enrich(scope: CoroutineScope, titles: List<Manga>) {
        val taihonPreferences: TaihonPreferences = Injekt.get()

        if (taihonPreferences.globalSearchEnrichResults.get()) {
            scope.coroutineContext[Job]?.let { registerJob(it) }
            titles.forEach { title ->
                scope.launchIO {
                    enricher.fetchMangaDetails(title)
                }
            }
        }
    }
}

fun Source.taihonNormalize(query: String): String = TaihonSearchHelper.normalize(this, query)

fun List<Manga>.taihonEnrich(scope: CoroutineScope) = TaihonSearchHelper.enrich(scope, this)
