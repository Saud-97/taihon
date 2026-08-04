package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchCardRow(
    titles: List<Manga>,
    getManga: @Composable (Manga) -> State<Manga>,
    getMangaDetails: @Composable (Manga) -> State<SearchViewModel.MangaDetails?>,
    onClick: (Manga) -> Unit,
    onLongClick: (Manga) -> Unit,
) {
    if (titles.isEmpty()) {
        EmptyResultItem()
        return
    }

    LazyRow(
        contentPadding = PaddingValues(MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(titles) {
            val title by getManga(it)
            val details by getMangaDetails(title)
            MangaItem(
                title = title.title,
                cover = title.asMangaCover(),
                isFavorite = title.favorite,
                chapterCount = details?.chapterCount,
                latestChapter = details?.latestChapter,
                isLoading = details?.isLoading ?: false,
                onClick = { onClick(title) },
                onLongClick = { onLongClick(title) },
            )
        }
    }
}

@Composable
private fun MangaItem(
    title: String,
    cover: MangaCover,
    isFavorite: Boolean,
    chapterCount: Int?,
    latestChapter: Double?,
    isLoading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(modifier = Modifier.width(96.dp)) {
        val formattedLatestChapter = remember(latestChapter) {
            latestChapter?.let(::formatChapterNumber)
        }
        val coverText = formattedLatestChapter?.let {
            stringResource(MR.strings.migrationListScreen_latestChapterLabel, it)
        }

        MangaComfortableGridItem(
            title = title,
            titleMaxLines = 3,
            coverData = cover,
            coverBadgeStart = {
                InLibraryBadge(enabled = isFavorite)
            },
            coverBadgeEnd = {
                if (isLoading) {
                    if (chapterCount != null && chapterCount > 0) {
                        BadgeGroup {
                            Badge(text = "$chapterCount")
                            TaihonLoadingBadge()
                        }
                    } else {
                        TaihonLoadingBadge()
                    }
                } else if (chapterCount != null && chapterCount > 0) {
                    Badge(text = "$chapterCount")
                }
            },
            coverAlpha = if (isFavorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
            onClick = onClick,
            onLongClick = onLongClick,
            coverText = coverText,
        )
    }
}

@Composable
private fun EmptyResultItem() {
    Text(
        text = stringResource(MR.strings.no_results_found),
        modifier = Modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
    )
}
