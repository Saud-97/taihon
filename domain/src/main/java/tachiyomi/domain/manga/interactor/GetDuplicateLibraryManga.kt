package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.util.lang.normalizeApostrophe
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

@Inject
class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        val query = manga.title.normalizeApostrophe().lowercase().replace("'", "_")
        return mangaRepository.getDuplicateLibraryManga(manga.id, query)
    }
}
