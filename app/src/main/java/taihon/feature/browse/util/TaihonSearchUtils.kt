package taihon.feature.browse.util

import eu.kanade.tachiyomi.util.lang.normalizeApostrophe

/**
 * Normalizes search query based on Taihon's smart apostrophe normalization rules.
 */
fun String.normalizeSearchQuery(
    pkgName: String?,
    smartNormalizationEnabled: Boolean,
    exceptions: Set<String>,
): String {
    val isNormalized = smartNormalizationEnabled && pkgName !in exceptions
    return if (isNormalized) {
        this.normalizeApostrophe(fuzzy = true)
    } else {
        this
    }
}
