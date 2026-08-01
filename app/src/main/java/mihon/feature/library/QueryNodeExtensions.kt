package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.util.lang.normalizeApostrophe
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.ComparisonField
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.EmptyQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.MangaField
import mihon.domain.library.model.search.NotNode
import mihon.domain.library.model.search.OrNode
import mihon.domain.library.model.search.QueryNode
import tachiyomi.source.local.LocalSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

fun QueryNode.matches(item: LibraryItem): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item)
    }
}

private fun GeneralQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga

    // Use when so each added field has to be handled explicitly
    val match = MangaField.entries.any { field ->
        if (field.fieldOnly) return@any false

        val normalizedValue = value.normalizeApostrophe()
        when (field) {
            MangaField.TITLE -> manga.title.normalizeApostrophe().contains(normalizedValue, ignoreCase = true)
            MangaField.AUTHOR -> manga.author?.normalizeApostrophe()?.contains(normalizedValue, ignoreCase = true)
                ?: false

            MangaField.ARTIST -> manga.artist?.normalizeApostrophe()?.contains(normalizedValue, ignoreCase = true)
                ?: false

            MangaField.DESCRIPTION -> manga.description?.normalizeApostrophe()?.contains(
                normalizedValue,
                ignoreCase = true,
            )
                ?: false

            MangaField.GENRE -> manga.genre?.any {
                it.normalizeApostrophe().contains(normalizedValue, ignoreCase = true)
            }
                ?: false
            MangaField.SOURCE -> {
                item.sourceName.normalizeApostrophe().contains(normalizedValue, ignoreCase = true) ||
                    (normalizedValue.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }

            MangaField.NOTES -> manga.notes.normalizeApostrophe().contains(normalizedValue, ignoreCase = true)

            // field-only queries; unreachable; added here to make `when` exhaustive
            MangaField.LANGUAGE, MangaField.SOURCE_ID -> error("How did we get here?")
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga
    val normalizedValue = value.normalizeApostrophe()

    val match = when (field) {
        MangaField.GENRE -> {
            if (normalizedValue.isEmpty()) {
                manga.genre.isNullOrEmpty()
            } else {
                manga.genre?.any { it.normalizeApostrophe().contains(normalizedValue, ignoreCase = true) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (normalizedValue.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.normalizeApostrophe().contains(normalizedValue, ignoreCase = true) ||
                    (normalizedValue.equals("local", ignoreCase = true) && manga.source == LocalSource.ID)
            }
        }

        MangaField.SOURCE_ID -> {
            normalizedValue.toLongOrNull()?.let { it == manga.source } ?: false
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> manga.title.normalizeApostrophe()
                MangaField.AUTHOR -> manga.author?.normalizeApostrophe()
                MangaField.ARTIST -> manga.artist?.normalizeApostrophe()
                MangaField.DESCRIPTION -> manga.description?.normalizeApostrophe()
                MangaField.NOTES -> manga.notes.normalizeApostrophe()
                MangaField.LANGUAGE -> item.sourceLanguage

                // unreachable; added here to make `when` exhaustive
                MangaField.GENRE, MangaField.SOURCE, MangaField.SOURCE_ID -> error("How did we get here?")
            }

            if (normalizedValue.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.contains(normalizedValue, ignoreCase = true) ?: false
            }
        }
    }

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: LibraryItem): Boolean {
    val manga = item.libraryManga.manga

    fun compareDates(timestamp: Long, value: String): Boolean? {
        val inputDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val mangaDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return queryComparator.apply(mangaDate, inputDate)
    }

    val match = when (field) {
        ComparisonField.ID -> value.toLongOrNull()?.let { queryComparator.apply(manga.id, it) }

        ComparisonField.DATE_ADDED -> compareDates(manga.dateAdded, value)

        ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()
            ?.let { queryComparator.apply(abs(manga.fetchInterval), it) }

        ComparisonField.NEXT_UPDATE -> compareDates(manga.nextUpdate, value)

        ComparisonField.UNREAD -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.unreadCount, it)
            }
        }

        ComparisonField.READ -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.readCount, it)
            }
        }

        ComparisonField.TOTAL -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryManga.totalChapters, it)
            }
        }
    } ?: false

    return if (negated) !match else match
}
