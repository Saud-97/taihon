package mihon.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context) {
        // To stop forks/test builds from polluting our data
        if (!context.isTaihonSignedApp()) return

        // Check if Google Play Services is available before initializing Firebase
        if (!isGooglePlayServicesAvailable(context)) {
            logcat(LogPriority.WARN) { "Google Play Services not available, skipping Firebase initialization" }
            return
        }

        try {
            analytics = FirebaseAnalytics.getInstance(context)
            analytics?.setUserProperty("preferred_abi", Build.SUPPORTED_ABIS[0])
            FirebaseApp.initializeApp(context)
            crashlytics = FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize Firebase" }
        }
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            context.packageManager
                .getPackageInfo("com.google.android.gms", PackageManager.GET_META_DATA)
                .applicationInfo
                ?.enabled == true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    private fun Context.isTaihonSignedApp(): Boolean {
        val signatures = packageManager.getPackageInfo(packageName, SignatureFlags)
            .getCertificateFingerprints()

        return when (packageName) {
            TAIHON_PACKAGE -> signatures.any { it == TAIHON_CERTIFICATE_FINGERPRINT }
            TAIHON_DEV_PACKAGE -> signatures.any { it == TAIHON_DEV_CERTIFICATE_FINGERPRINT }
            else -> false
        }
    }
}

private const val TAIHON_PACKAGE = "app.taihon"
private const val TAIHON_CERTIFICATE_FINGERPRINT =
    "AD:FF:AA:D7:51:5C:B0:C6:42:44:7A:32:80:77:F1:18:B2:6B:05:17:B8:16:CC:36:68:48:6A:DF:1B:36:CF:3B"

private const val TAIHON_DEV_PACKAGE = "app.taihon.dev"
private const val TAIHON_DEV_CERTIFICATE_FINGERPRINT =
    "DA:3C:CC:C1:04:75:6E:DC:5A:CE:17:94:AE:32:B6:D6:57:D4:93:5D:9B:29:19:4E:EF:B2:D8:50:63:1D:89:02"
