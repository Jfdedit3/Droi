package com.droiddeck.launcher

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.droiddeck.launcher.core.Downloader
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.RandomAccessFile
import com.droiddeck.launcher.core.Hashes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.BroadcastReceiver
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

internal object DroiUpdates {
    private const val REPO = "Jfdedit3/Droi"
    private const val ASSET = "Droi-Desktop-arm64.apk"
    private const val MIME_TYPE = "application/vnd.android.package-archive"
    private const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
    private val tagPattern = Regex("droi-(\\d+)")
    val status = MutableStateFlow("")
    val ready = MutableStateFlow(0)
    val available = MutableStateFlow(0)
    private fun directory(context: Context) = File(context.cacheDir, "droi-updates")
    private fun apk(context: Context, version: Int) = File(directory(context), "$version.apk")
    private val busy = AtomicBoolean()

    private fun prefs(context: Context) = context.getSharedPreferences("droi_updates", Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean("automatic_download", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("automatic_download", enabled).apply()
    }

    fun check(context: Context, force: Boolean = false, manual: Boolean = false, report: (String) -> Unit = {}) {
        val app = context.applicationContext
        removeInstalledDownload(app)
        val saved = prefs(app)
        val now = System.currentTimeMillis()
        if (!force && status.value.isNotEmpty() && now - saved.getLong("last_check", 0) in 0 until CHECK_INTERVAL) return
        if (!busy.compareAndSet(false, true)) {
            report("Vérification déjà en cours.")
            return
        }

        status.value = "Vérification…"
        Thread({
            try {
                val release = latestRelease()
                available.value = release?.version?.takeIf { it > BuildConfig.VERSION_CODE } ?: 0
                val result = when {
                    release == null -> "Aucune version Droi disponible sur GitHub."
                    release.version <= BuildConfig.VERSION_CODE -> "Droi est à jour."
                    !manual && !isEnabled(app) ->
                        "Nouvelle version Droi ${release.version} disponible. Téléchargement automatique désactivé."
                    else -> download(app, release)
                }
                saved.edit().putLong("last_check", now).apply()
                status.value = result
                report(result)
            } catch (error: Exception) {
                Log.w("DroiUpdates", "Update check failed", error)
                status.value = "Vérification impossible : ${error.message ?: error.javaClass.simpleName}"
                report(status.value)
            } finally {
                busy.set(false)
            }
        }, "droi-updates").start()
    }

    @Synchronized
    fun removeInstalledDownload(context: Context) {
        val saved = prefs(context)
        val cached = saved.getInt("cached_version", 0)
        if (cached > BuildConfig.VERSION_CODE && apk(context, cached).isFile &&
            saved.getString("cached_digest", null)?.matches(Regex("[a-fA-F0-9]{64}")) == true) {
            ready.value = cached
        } else {
            if (cached > 0) apk(context, cached).delete()
            saved.edit().remove("cached_version").remove("cached_digest").apply()
            ready.value = 0
        }
        val version = saved.getInt("download_version", 0)
        val id = saved.getLong("download_id", -1)
        if (id < 0 || version !in 1..BuildConfig.VERSION_CODE) return
        val manager = context.getSystemService(DownloadManager::class.java) ?: return
        runCatching {
            manager.remove(id)
            saved.edit().remove("download_id").remove("download_version").apply()
        }.onFailure { Log.w("DroiUpdates", "Could not clean up installed APK", it) }
    }

    private data class Release(val tag: String, val version: Int, val digest: String, val size: Long)

    private fun latestRelease(): Release? {
        val connection = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Droi-Desktop-Updater")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val status = connection.responseCode
            if (status == 404) return null
            check(status == 200) { "GitHub HTTP $status" }
            val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = data.getString("tag_name")
            val version = checkNotNull(tagPattern.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()) { "Tag Droi invalide : $tag" }
            val assets = data.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.first {
                it.optString("name") == ASSET && it.optString("state") == "uploaded"
            }
            val digest = asset.optString("digest")
            check(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "SHA-256 de la release absent ou invalide" }
            val size = asset.getLong("size")
            check(size in 1L..(1024L * 1024 * 1024)) { "Taille APK invalide" }
            return Release(tag, version, digest.removePrefix("sha256:"), size)
        } finally {
            connection.disconnect()
        }
    }

    private val client = OkHttpClient()

    private fun parallelDownload(url: String, target: File, release: Release): Boolean {
        if (release.size < 8L * 1024 * 1024 || target.exists()) return false
        val staging = File(target.parentFile, "${target.name}.ranges")
        try {
            RandomAccessFile(staging, "rw").use { it.setLength(release.size) }
            val progressLock = Any()
            var received = 0L
            runBlocking {
                (0 until 4).map { index ->
                    async(Dispatchers.IO) {
                        val start = release.size * index / 4
                        val end = release.size * (index + 1) / 4 - 1
                        val request = Request.Builder().url(url)
                            .header("Range", "bytes=$start-$end")
                            .header("Accept-Encoding", "identity").build()
                        client.newCall(request).execute().use { response ->
                            check(response.code == 206 &&
                                response.header("Content-Range") == "bytes $start-$end/${release.size}")
                            val input = checkNotNull(response.body).byteStream()
                            RandomAccessFile(staging, "rw").use { output ->
                                output.seek(start)
                                var remaining = end - start + 1
                                val buffer = ByteArray(128 * 1024)
                                while (remaining > 0) {
                                    val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                                    check(count > 0)
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                    synchronized(progressLock) {
                                        received += count
                                        status.value = "Téléchargement : ${received * 100 / release.size} %"
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            return Hashes.sha256(staging).equals(release.digest, true) && staging.renameTo(target)
        } catch (error: Exception) {
            Log.i("DroiUpdates", "Retrying with a single transfer", error)
            return false
        } finally {
            staging.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun signerSet(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, file: File, version: Int, digest: String) {
        check(file.isFile && Hashes.sha256(file).equals(digest, true)) { "SHA-256 de l’APK incorrect" }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = checkNotNull(pm.getPackageArchiveInfo(file.absolutePath, flags)) { "APK illisible" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val candidateCode = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        check(candidate.packageName == context.packageName) { "Nom de paquet APK incorrect" }
        check(candidateCode == version.toLong() && version > BuildConfig.VERSION_CODE) { "Numéro de version APK incorrect" }
        val expected = signerSet(installed)
        check(expected.isNotEmpty() && signerSet(candidate) == expected) { "Signature APK incompatible" }
    }

    private fun download(context: Context, release: Release): String {
        val target = apk(context, release.version)
        if (ready.value == release.version && target.isFile) {
            if (runCatching { verifyApk(context, target, release.version, release.digest) }.isSuccess)
                return "Mise à jour prête."
            target.delete()
            ready.value = 0
            prefs(context).edit().remove("cached_version").remove("cached_digest").apply()
        }
        val folder = directory(context)
        check(folder.isDirectory || folder.mkdirs())
        // Keep the .apk suffix so PackageManager can inspect the completed download.
        val partial = File(folder, "${release.version}.partial.apk")
        status.value = "Téléchargement…"
        val url = "https://github.com/$REPO/releases/download/${release.tag}/$ASSET"
        val ok = parallelDownload(url, partial, release) || Downloader.downloadFile(url, partial, true, release.digest) { fraction ->
            if (fraction in 0f..1f) status.value = "Téléchargement : ${(fraction * 100).toInt()} %"
        }
        check(ok && partial.length() == release.size) { "Téléchargement APK incomplet" }
        try {
            verifyApk(context, partial, release.version, release.digest)
            check(partial.renameTo(target)) { "Impossible de préparer l’APK" }
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
        prefs(context).edit().putInt("cached_version", release.version).putString("cached_digest", release.digest).apply()
        ready.value = release.version
        folder.listFiles()?.filter { it != target }?.forEach { it.delete() }
        return "Mise à jour prête."
    }

    fun install(context: Context) {
        val version = ready.value
        val file = apk(context, version)
        val digest = prefs(context).getString("cached_digest", null)
        if (!file.isFile || digest == null || version <= BuildConfig.VERSION_CODE) {
            ready.value = 0
            status.value = "Vérifie à nouveau pour télécharger l’APK."
            return
        }
        if (!busy.compareAndSet(false, true)) {
            status.value = "Une opération est déjà en cours."
            return
        }
        status.value = "Vérification de la signature…"
        Thread({
            try {
                verifyApk(context, file, version, digest)
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME_TYPE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        status.value = "Mise à jour prête."
                    }.onFailure { status.value = "Installation impossible : ${it.message}" }
                    busy.set(false)
                }
            } catch (error: Exception) {
                file.delete()
                prefs(context).edit().remove("cached_version").remove("cached_digest").apply()
                ready.value = 0
                status.value = "APK refusé : ${error.message}"
                busy.set(false)
            }
        }, "droi-install-check").start()
    }
}

class DroiUpdateInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) DroiUpdates.removeInstalledDownload(context)
    }
}
