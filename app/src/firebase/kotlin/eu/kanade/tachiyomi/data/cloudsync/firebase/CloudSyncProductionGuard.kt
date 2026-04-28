package eu.kanade.tachiyomi.data.cloudsync.firebase

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.security.MessageDigest

/**
 * Gate Firebase initialisation by package name + signing certificate fingerprint and Google Play
 * Services availability. Mirrors the pattern in [mihon.telemetry.TelemetryConfig] but is declared
 * separately so cloud sync can have its own allow-list without coupling to telemetry.
 *
 * Forks must update [ALLOWED_PACKAGES] and [ALLOWED_CERTIFICATE_FINGERPRINTS] to match their
 * release signing key. With the defaults in place a fork build will silently report cloud sync
 * as Unavailable, even with a valid `google-services.json`.
 */
object CloudSyncProductionGuard {

    private val ALLOWED_PACKAGES = setOf(
        // Original mihon flavor — drop-in update for users with the official APK.
        "app.mihon",
        "app.mihon.dev",
        // mihonmod flavor — side-by-side install alongside the official Mihon.
        "app.mihonmod",
        "app.mihonmod.dev",
    )

    /**
     * SHA-1 fingerprints (uppercase, colon-separated) of certificates allowed to enable cloud
     * sync. Add this fork's debug and release certificate SHA-1s here to enable.
     */
    private val ALLOWED_CERTIFICATE_FINGERPRINTS = setOf<String>(
        // "AA:BB:CC:..."
    )

    fun isAllowed(context: Context): Boolean {
        if (context.packageName !in ALLOWED_PACKAGES) return false
        if (!isGooglePlayServicesAvailable(context)) return false

        if (ALLOWED_CERTIFICATE_FINGERPRINTS.isEmpty()) {
            // Empty allow-list = fork developers haven't pinned a fingerprint yet.
            // We let the build through so they can iterate, but we log loudly.
            logcat(LogPriority.WARN) {
                "CloudSyncProductionGuard: certificate fingerprint allow-list is empty; " +
                    "any signed build will be allowed. Pin your release SHA-1 before shipping."
            }
            return true
        }

        return runCatching { context.signingFingerprintsSha1() }
            .getOrDefault(emptyList())
            .any { it in ALLOWED_CERTIFICATE_FINGERPRINTS }
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return runCatching {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrElse {
            logcat(LogPriority.WARN, it) { "CloudSyncProductionGuard: GMS check failed" }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun Context.signingFingerprintsSha1(): List<String> {
        val pm = packageManager
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                ?.toList()
                .orEmpty()
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures
                ?.toList()
                .orEmpty()
        }
        val digest = MessageDigest.getInstance("SHA-1")
        return signatures.map { signature ->
            digest.reset()
            digest.digest(signature.toByteArray()).joinToString(":") { byte ->
                "%02X".format(byte)
            }
        }
    }
}
