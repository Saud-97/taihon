package taihon.feature.download

import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tachiyomi.domain.download.service.DownloadPreferences
import java.util.concurrent.ConcurrentHashMap

private val activePrefetchJobs = ConcurrentHashMap<Download, Job>()

/**
 * Pre-fetches page lists for upcoming queued chapters for [source].
 *
 * Maintains a buffer of at most [count] upcoming chapters,
 * prioritizing their actual download queue order.
 */
fun CoroutineScope.prefetchUpcomingChapters(
    queue: List<Download>,
    source: HttpSource,
    downloadJobs: Map<Download, Job>,
    currentDownload: Download,
    pref: DownloadPreferences,
    count: Int = 3,
) {
    if (!pref.taihon.prefetchUpcomingChapters.get()) {
        synchronized(activePrefetchJobs) {
            activePrefetchJobs.entries
                .filter { it.key.source == source }
                .forEach { (download, job) ->
                    activePrefetchJobs.remove(download, job)
                    job.cancel()
                }
        }
        return
    }

    val currentIndex = queue.indexOf(currentDownload)
    if (currentIndex == -1) return

    // Select the next eligible chapters in actual queue order.
    val upcomingDownloads = queue.asSequence()
        .drop(currentIndex + 1)
        .filter {
            it.source == source &&
                it.status == Download.State.QUEUE &&
                it !in downloadJobs
        }
        .take(count.coerceAtLeast(0))
        .toList()

    for (upcoming in upcomingDownloads) {
        // Skip chapters that are already prepared or scheduled.
        if (upcoming.pages != null ||
            upcoming.status != Download.State.QUEUE ||
            upcoming in downloadJobs
        ) {
            continue
        }

        synchronized(activePrefetchJobs) {
            // Another invocation may already be preparing this chapter.
            val existingJob = activePrefetchJobs[upcoming]
            if (existingJob?.isActive == true) return@synchronized

            // Remove a completed job before creating a replacement.
            if (existingJob != null) {
                activePrefetchJobs.remove(upcoming, existingJob)
            }

            val job = launch(start = CoroutineStart.LAZY) {
                // Recheck immediately before making the request.
                if (upcoming.pages != null ||
                    upcoming.status != Download.State.QUEUE ||
                    upcoming in downloadJobs
                ) {
                    return@launch
                }

                try {
                    val pages = source.getPageList(
                        upcoming.chapter.toSChapter(),
                    )

                    // Only store the result if the chapter is still waiting.
                    if (pages.isNotEmpty() &&
                        upcoming.pages == null &&
                        upcoming.status == Download.State.QUEUE &&
                        upcoming !in downloadJobs
                    ) {
                        upcoming.pages = pages.mapIndexed { index, page ->
                            Page(index, page.url, page.imageUrl, page.uri)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Failed prefetches can be retried on a later invocation.
                }
            }

            activePrefetchJobs[upcoming] = job

            job.invokeOnCompletion {
                activePrefetchJobs.remove(upcoming, job)
            }

            job.start()
        }
    }
}
