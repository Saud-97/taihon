package taihon.feature.manga.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import coil3.request.ImageRequest
import coil3.request.allowHardware
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaViewModel
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import taihon.domain.preferences.TaihonPreferences
import taihon.feature.manga.ui.components.TaihonMangaContinueChip
import taihon.feature.manga.ui.components.TaihonMangaScrollControls
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TaihonMangaHooks {
    @Composable
    fun rememberCoverModel(data: Any?): Any {
        val context = LocalContext.current
        val taihonPreferences = remember { Injekt.get<TaihonPreferences>() }
        val allowHardware by taihonPreferences.allowHardwareBitmapForCovers.collectAsState()

        return remember(data, allowHardware) {
            if (data is ImageRequest) {
                data.newBuilder().allowHardware(allowHardware).build()
            } else {
                ImageRequest.Builder(context)
                    .data(data)
                    .allowHardware(allowHardware)
                    .build()
            }
        }
    }

    @Composable
    fun MangaScreenOverlay(
        chapterListState: LazyListState,
        currentlyReadingIndex: Int,
        isAnySelected: Boolean,
        topPadding: Dp,
        centerOffset: Int,
        content: @Composable () -> Unit,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            content()

            TaihonMangaScrollControls(
                chapterListState = chapterListState,
                currentlyReadingIndex = currentlyReadingIndex,
                isAnySelected = isAnySelected,
                topPadding = topPadding,
                centerOffset = centerOffset,
            )
        }
    }

    @Composable
    fun rememberScrollStateInfo(
        state: MangaViewModel.State.Success,
        listItem: List<ChapterList>,
        chapterListState: LazyListState,
        headerOffset: Int = 4,
    ): ScrollStateInfo {
        val density = LocalDensity.current
        val nextUnreadChapter = remember(state) {
            state.chapters.getNextUnread(state.manga)
        }

        val currentlyReadingIndex = remember(listItem, nextUnreadChapter) {
            val index = listItem.indexOfFirst { it is ChapterList.Item && it.chapter.id == nextUnreadChapter?.id }
            if (index != -1) index + headerOffset else -1
        }

        val isReading = remember(state.chapters) {
            state.chapters.fastAny { it.chapter.read }
        }

        val centerOffset = remember {
            derivedStateOf {
                -(chapterListState.layoutInfo.viewportSize.height / 2 - with(density) { 72.dp.roundToPx() } / 2)
            }
        }

        return remember(nextUnreadChapter, currentlyReadingIndex, isReading, centerOffset) {
            ScrollStateInfo(
                nextUnreadChapter = nextUnreadChapter,
                currentlyReadingIndex = currentlyReadingIndex,
                isReading = isReading,
                centerOffset = centerOffset,
            )
        }
    }

    data class ScrollStateInfo(
        val nextUnreadChapter: Chapter?,
        val currentlyReadingIndex: Int,
        val isReading: Boolean,
        val centerOffset: State<Int>,
    )

    @Composable
    fun ContinueReadingHeader(
        nextUnreadChapter: Chapter?,
        onResumeClicked: () -> Unit,
    ) {
        if (nextUnreadChapter != null) {
            val isChapterStarted = nextUnreadChapter.lastPageRead > 0
            val actionLabel = stringResource(
                if (isChapterStarted) {
                    MR.strings.migrationConfigScreen_continueButtonText
                } else {
                    MR.strings.action_start
                },
            )
            val readingLabel = stringResource(MR.strings.reading).lowercase()
            val chapterLabel = stringResource(
                MR.strings.display_mode_chapter,
                formatChapterNumber(nextUnreadChapter.chapterNumber),
            )
            val resumeText = "$actionLabel $readingLabel $chapterLabel"

            TaihonMangaContinueChip(
                resumeText = resumeText,
                onResumeClicked = onResumeClicked,
            )
        }
    }
}
