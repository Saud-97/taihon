package taihon.feature.browse

import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import java.time.Instant

/**
 * Encapsulates search result enrichment logic, including background detail fetching
 * and concurrency management.
 */
class TaihonSearchEnricher(
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateManga: UpdateManga,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val onUpdateDetails: (Long, MangaDetails) -> Unit,
    private val getCurrentDetails: (Long) -> MangaDetails?,
) {
    private val globalSemaphore = Semaphore(10)
    private val sourceMutexes = mutableMapOf<Long, Mutex>()
    private val sessionCache = mutableSetOf<Long>()

    suspend fun fetchMangaDetails(manga: Manga) {
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

        val isCacheValid = Instant.now().toEpochMilli() < manga.nextUpdate

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
            getCurrentDetails(manga.id)?.copy(isLoading = true) ?: MangaDetails(0, null, true),
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
                    onUpdateDetails(
                        manga.id,
                        MangaDetails(
                            chapterCount = chapters.size,
                            latestChapter = chapters.maxOfOrNull { it.chapterNumber },
                            isLoading = false,
                        ),
                    )
                    sessionCache.add(manga.id)
                } catch (e: Exception) {
                    onUpdateDetails(
                        manga.id,
                        getCurrentDetails(manga.id)?.copy(isLoading = false) ?: MangaDetails(0, null, false),
                    )
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
