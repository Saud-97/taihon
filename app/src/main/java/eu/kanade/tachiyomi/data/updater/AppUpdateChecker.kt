package eu.kanade.tachiyomi.data.updater

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isFossBuildType
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    suspend fun checkForUpdate(forceCheck: Boolean = false): GetApplicationRelease.Result {
        // Disable app update checks for older Android versions that we're going to drop support for
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        //     return GetApplicationRelease.Result.OsTooOld
        // }

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isFossBuildType,
                    BuildConfig.VERSION_NAME,
                    GITHUB_REPO,
                    forceCheck,
                ),
            )

            result
        }
    }
}

val GITHUB_REPO = "Saud-97/taihon"

val RELEASE_TAG = "v${BuildConfig.VERSION_NAME}"

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
