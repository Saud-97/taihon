
package taihon.feature.browse

import android.content.Context
import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.core.common.extensions.toEpochMillisOrZero
import mihon.core.common.extensions.toInstantOrNull
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

/**
 * Encapsulates search result enrichment logic, including background detail fetching
 * and concurrency management.
 */
class TaihonSearchEnricher(
    private val onUpdateDetails: (Long, MangaDetails) -> Unit,
    private val getCurrentDetails: (Long) -> MangaDetails?,
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get<Context>().appGraph.updateMangaFromRemote,
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get<Context>().appGraph.getChaptersByMangaId,
    private val updateManga: UpdateManga = Injekt.get<Context>().appGraph.updateManga,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags =
        Injekt.get<Context>().appGraph.setMangaDefaultChapterFlags,
) {

    private val globalSemaphore = Semaphore(10)
    private val sourceMutexes = mutableMapOf<Long, Mutex>()
    private val mangaMutexes = mutableMapOf<Long, Mutex>()
    private val sessionCache = Collections.synchronizedSet(mutableSetOf<Long>())

    fun clear() {
        sessionCache.clear()
    }

    suspend fun fetchMangaDetails(manga: Manga) {
        val mangaMutex = synchronized(mangaMutexes) {
            mangaMutexes.getOrPut(manga.id) { Mutex() }
        }

        mangaMutex.withLock {
            if (manga.id in sessionCache) {
                val chapters = getChaptersByMangaId.await(manga.id)
                onUpdateDetails(
                    manga.id,
                    MangaDetails(
                        chapterCount = chapters.size,
                        latestChapter = chapters.maxOfOrNull { it.chapterNumber },
                        isLoading = false,
                    ),
                )
                return
            }

            val isCacheValid = Instant.now().toEpochMilli() < manga.nextUpdate.toEpochMillisOrZero()

            if (isCacheValid) {
                val chapters = getChaptersByMangaId.await(manga.id)
                onUpdateDetails(
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

            onUpdateDetails(
                manga.id,
                getCurrentDetails(manga.id)?.copy(isLoading = true)
                    ?: MangaDetails(0, null, true),
            )

            val sourceMutex = synchronized(sourceMutexes) {
                sourceMutexes.getOrPut(manga.source) { Mutex() }
            }

            sourceMutex.withLock {
                globalSemaphore.withPermit {
                    try {
                        updateMangaFromRemote(
                            manga,
                            fetchDetails = true,
                            fetchChapters = true,
                        ).getOrThrow()

                        setMangaDefaultChapterFlags.await(manga)

                        updateManga.await(
                            MangaUpdate(id = manga.id) {
                                nextUpdate = (Instant.now().toEpochMilli() + 60 * 60 * 1000L)
                                    .toInstantOrNull()
                            },
                        )

                        val chapters = getChaptersByMangaId.await(manga.id)
                        onUpdateDetails(
                            manga.id,
                            MangaDetails(
                                chapterCount = chapters.size,
                                latestChapter = chapters.maxOfOrNull { it.chapterNumber },
                                isLoading = false,
                            ),
                        )
                        sessionCache.add(manga.id)
                    } catch (e: CancellationException) {
                        onUpdateDetails(
                            manga.id,
                            getCurrentDetails(manga.id)?.copy(isLoading = false)
                                ?: MangaDetails(0, null, false),
                        )
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e)
                        onUpdateDetails(
                            manga.id,
                            getCurrentDetails(manga.id)?.copy(isLoading = false)
                                ?: MangaDetails(0, null, false),
                        )
                    }
                }
            }
        }
    }

    data class MangaDetails(
        val chapterCount: Int,
        val latestChapter: Double?,
        val isLoading: Boolean = false,
    )
}
