package eu.kanade.presentation.more.mihonmod

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.BuildConfig

private const val OFFICIAL_MIHON_PACKAGE = "app.mihon"

/**
 * Returns true if the running flavor is `mihonmod`, the official Mihon (`app.mihon`) is
 * currently installed on the device, and the user has not yet acknowledged the migration
 * prompt on this install.
 */
fun shouldShowMihonModOnboarding(
    context: Context,
    onboardingShown: Boolean,
): Boolean {
    if (BuildConfig.FLAVOR != "mihonmod") return false
    if (onboardingShown) return false
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(OFFICIAL_MIHON_PACKAGE, 0)
        true
    }.getOrDefault(false)
}

/**
 * One-time dialog shown on the first launch of the MihonMod flavor when the official Mihon is
 * detected. Explains that data is NOT shared automatically between the two installs and offers
 * three explicit migration paths: open Mihon (to export a backup), restore from a backup file,
 * or sign in to cloud sync. The dialog never silently touches the official Mihon's data; every
 * action requires explicit user input.
 */
@Composable
fun MihonDetectionDialog(
    onOpenMihon: () -> Unit,
    onRestoreBackup: () -> Unit,
    onSignInCloudSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mihon detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The official Mihon app is installed on this device. MihonMod is a separate " +
                        "app: it does not automatically read or share data with Mihon.",
                )
                Text(
                    "Pick one of the migration paths below to bring your library across, " +
                        "or close this dialog to start with an empty library.",
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = onOpenMihon) {
                    Text("Open Mihon (export backup there)")
                }
                TextButton(onClick = onRestoreBackup) {
                    Text("Restore from backup file")
                }
                TextButton(onClick = onSignInCloudSync) {
                    Text("Sign in to cloud sync")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

/**
 * Launches the official Mihon's main activity if it is currently installed. No-op otherwise.
 */
fun openOfficialMihon(context: Context) {
    val intent: Intent? = context.packageManager.getLaunchIntentForPackage(OFFICIAL_MIHON_PACKAGE)
    if (intent != null) {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
