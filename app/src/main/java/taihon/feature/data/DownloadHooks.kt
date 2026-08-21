package taihon.feature.data

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import taihon.domain.preferences.TaihonPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

private val mangaDirLock = Any()

fun UniFile.getTaihonMangaDir(
    context: Context,
    mangaTitle: String,
    source: Source,
    getSourceDirName: (Source) -> String,
    getMangaDirName: (String) -> String,
    findMangaDir: (String, Source) -> UniFile?,
): Result<UniFile> {
    findMangaDir(mangaTitle, source)?.let { return Result.success(it) }

    return synchronized(mangaDirLock) {
        val sourceDirName = getSourceDirName(source)
        val sourceDir = this.findFile(sourceDirName) ?: this.createDirectory(sourceDirName)
        if (sourceDir == null) {
            val displayablePath = this.displayablePath + "/$sourceDirName"
            logcat(LogPriority.ERROR) { "Failed to create source download directory: $displayablePath" }
            return@synchronized Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        val mangaDirName = getMangaDirName(mangaTitle)
        val mangaDir = sourceDir.findFile(mangaDirName) ?: sourceDir.createDirectory(mangaDirName)
        if (mangaDir == null) {
            val displayablePath = sourceDir.displayablePath + "/$mangaDirName"
            logcat(LogPriority.ERROR) { "Failed to create manga download directory: $displayablePath" }
            return@synchronized Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        Result.success(mangaDir)
    }
}

fun calculateTaihonActiveDownloads(
    queue: List<Download>,
    sourceLimit: Int,
): List<Download> {
    val taihonPreferences = Injekt.get<TaihonPreferences>()
    val chapterLimit = taihonPreferences.parallelChapterLimit.get()

    return queue.asSequence()
        // Ignore completed downloads, leave them in the queue
        .filter { it.status.value <= Download.State.DOWNLOADING.value }
        .groupBy { it.source }
        .toList()
        .take(sourceLimit)
        .flatMap { (_, downloads) ->
            downloads.take(chapterLimit)
        }
        .toList()
}

fun shouldShowTaihonDownloadWarnings(): Boolean = false

fun List<UniFile>.sortedByTaihonName(): List<UniFile> {
    return this.sortedWith(compareBy(String::compareToCaseInsensitiveNaturalOrder) { it.name.orEmpty() })
}

fun UniFile.createTaihonNoMediaFile(context: Context) {
    DiskUtil.createNoMediaFile(this, context)
}
