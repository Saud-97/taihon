package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationRelease(
    private val service: ReleaseService,
) {
    suspend fun await(arguments: Arguments): Result {
        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "v" and any non-numeric characters except dots
        val oldVersion = versionName.replace("[^\\d.]".toRegex(), "").split(".")
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "").split(".")

        val maxLength = maxOf(oldVersion.size, newVersion.size)
        for (i in 0 until maxLength) {
            val oldPart = oldVersion.getOrNull(i)?.toIntOrNull() ?: 0
            val newPart = newVersion.getOrNull(i)?.toIntOrNull() ?: 0

            if (newPart > oldPart) {
                return true
            } else if (newPart < oldPart) {
                return false
            }
        }

        return false
    }

    data class Arguments(
        val isFoss: Boolean,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}
