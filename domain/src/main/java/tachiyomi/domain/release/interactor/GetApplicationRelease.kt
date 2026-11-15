package tachiyomi.domain.release.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

@Inject
class GetApplicationRelease(
    private val service: ReleaseService,
) {
    suspend fun await(arguments: Arguments): Result {
        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isNightly,
            arguments.commitCount,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isNightly: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        val newVersionDigits = versionTag.replace("[^\\d.]".toRegex(), "")

        // Nightly builds: Compare commit revision directly (e.g., "r1234" -> 1234)
        if (isNightly) {
            val newCommitCount = newVersionDigits.toIntOrNull() ?: return false
            return newCommitCount > commitCount
        }

        // Release builds: Compare semantic versioning segments (e.g., "v1.2.3" -> [1, 2, 3])
        val oldParts = versionName.extractVersionParts()
        val newParts = newVersionDigits.extractVersionParts()

        val maxLength = maxOf(oldParts.size, newParts.size)
        for (i in 0 until maxLength) {
            val oldPart = oldParts.getOrElse(i) { 0 }
            val newPart = newParts.getOrElse(i) { 0 }

            if (newPart > oldPart) return true
            if (newPart < oldPart) return false
        }

        return false
    }

    private fun String.extractVersionParts(): List<Int> {
        return this.replace("[^\\d.]".toRegex(), "")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    data class Arguments(
        val isFoss: Boolean,
        val isNightly: Boolean,
        val commitCount: Int,
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
