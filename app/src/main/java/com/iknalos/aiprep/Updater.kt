package com.iknalos.aiprep

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable
class AppVersion(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val notes: String = "",
    val published: String = "",
    val minSdk: Int = 26
)

sealed class UpdateResult {
    object UpToDate : UpdateResult()
    object NoNetwork : UpdateResult()
    /** Downloaded and handed to the installer; the process may die mid-install. */
    data class Installing(val version: AppVersion) : UpdateResult()
    /** Ready on disk but Android needs the one-time "install unknown apps" grant. */
    data class NeedsPermission(val version: AppVersion) : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

/**
 * Self-update from the project's published version manifest.
 *
 * Android lets an app update *itself* with no confirmation dialog when it declares
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION (API 31+) and the replacement is signed with the
 * same key, which CI guarantees via the committed keystore. What cannot be skipped by
 * any app, including ones from Play, is the one-time "allow installs from this source"
 * grant, so that is surfaced to the user rather than silently retried forever.
 *
 * On API 26-30 the silent path does not exist, so those devices get the normal
 * installer confirmation once per update.
 */
class Updater(private val context: Context) {

    companion object {
        const val VERSION_URL = "https://iknalos.github.io/AI-Interview-Prep/app-version.json"
        private const val TIMEOUT_MS = 30_000
        private const val APK_NAME = "update.apk"
        const val ACTION_INSTALL_STATUS = "com.iknalos.aiprep.INSTALL_STATUS"

        /** Refuse anything implausible for a ~15 MB APK. */
        private const val MAX_APK_BYTES = 120L * 1024 * 1024
        private const val MIN_APK_BYTES = 1L * 1024 * 1024
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val currentVersionCode: Int
        get() = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            0
        }

    val currentVersionName: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }

    /** True once the user has granted this app permission to install packages. */
    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Whether an update can install with no dialog at all. */
    fun silentInstallSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canInstall()

    fun fetchLatest(): AppVersion? {
        val text = fetchText(VERSION_URL) ?: return null
        return try {
            val v = json.decodeFromString(AppVersion.serializer(), text)
            if (v.versionCode <= 0 || v.apkUrl.isBlank()) null else v
        } catch (e: Exception) {
            null
        }
    }

    /** Blocking. Safe to call from a worker. */
    fun checkAndInstall(): UpdateResult {
        val latest = fetchLatest() ?: return UpdateResult.NoNetwork

        if (latest.versionCode <= currentVersionCode) return UpdateResult.UpToDate
        if (Build.VERSION.SDK_INT < latest.minSdk) {
            return UpdateResult.Failed("Update needs a newer Android version")
        }

        val apk = File(context.cacheDir, APK_NAME)
        if (!download(latest.apkUrl, apk)) {
            apk.delete()
            return UpdateResult.Failed("Download failed")
        }

        if (apk.length() < MIN_APK_BYTES) {
            apk.delete()
            return UpdateResult.Failed("Downloaded file is too small to be an APK")
        }

        // Verify before installing. A truncated or tampered APK would fail signature
        // checks anyway, but failing here gives a clearer signal and wastes less.
        if (latest.sha256.isNotBlank()) {
            val actual = sha256(apk)
            if (!actual.equals(latest.sha256, ignoreCase = true)) {
                apk.delete()
                return UpdateResult.Failed("Checksum mismatch")
            }
        }

        if (!canInstall()) return UpdateResult.NeedsPermission(latest)

        return try {
            install(apk)
            UpdateResult.Installing(latest)
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "Install failed")
        }
    }

    /** Opens the system screen where the user grants this app install permission. */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some OEM builds hide the per-app screen; fall back to app details.
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Nothing further to try.
            }
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setAppPackageName(context.packageName)

        // The line that makes 4am updates actually silent. Only honoured when the app
        // is updating itself and the permission is declared, both of which hold here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("aiprep", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }

            val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Must be mutable: the system fills in the status extras.
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun download(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) return false
            val declared = conn.contentLengthLong
            if (declared > MAX_APK_BYTES) return false

            dest.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_APK_BYTES) return false
                        out.write(buf, 0, n)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    private fun fetchText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Receives the installer's verdict. Mostly useful for diagnosing a silent install
 * that the system declined; on success the process is replaced so nothing else runs.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system wants a confirmation after all, which happens on API < 31
                // or if the OEM overrides the silent path. Launch its dialog.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    if (confirm != null) context.startActivity(confirm)
                } catch (e: Exception) {
                    prefs.edit().putString("lastInstallStatus", "confirmation unavailable").apply()
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                prefs.edit().putString("lastInstallStatus", "installed").apply()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                prefs.edit()
                    .putString("lastInstallStatus", "failed: ${msg ?: status.toString()}")
                    .apply()
            }
        }
    }
}
