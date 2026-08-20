package com.hackerli.jizhang.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.hackerli.jizhang.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Disabled : UpdateState
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Downloading(val percent: Int?) : UpdateState
    data class Ready(
        val versionName: String,
        val apkPath: String,
        val changelog: List<String>,
    ) : UpdateState
    data class Installing(val versionName: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
    private val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
    private val apkFile = File(updateDirectory, "update.apk")
    private val partialFile = File(updateDirectory, "update.apk.part")
    private val checkMutex = Mutex()
    private val installMutex = Mutex()

    private val _state = MutableStateFlow<UpdateState>(initialState())
    val state = _state.asStateFlow()

    suspend fun check() {
        if (!checkMutex.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                val installing = _state.value as? UpdateState.Installing
                if (
                    installing != null &&
                    preferences.getString(SILENT_INSTALL_FAILED_VERSION, null) == installing.versionName
                ) {
                    _state.value = initialState()
                }
                if (
                    !isConfigured() ||
                    _state.value is UpdateState.Ready ||
                    _state.value is UpdateState.Installing
                ) return@withContext
                _state.value = UpdateState.Checking
                try {
                    val release = fetchLatestRelease()
                    val currentTag = BuildConfig.VERSION_NAME.removePrefix("v")
                    if (release.tag.removePrefix("v") == currentTag) {
                        clearDownloadedUpdate()
                        _state.value = UpdateState.Idle
                        return@withContext
                    }
                    download(release)
                    val info = verifiedArchive(apkFile) ?: error("更新包校验失败")
                    val versionName = info.versionName ?: release.tag
                    preferences.edit {
                        putString(KEY_READY_VERSION, versionName)
                        putString(KEY_READY_CHANGELOG, JSONArray(release.changelog).toString())
                        remove(SILENT_INSTALL_FAILED_VERSION)
                    }
                    _state.value = UpdateState.Ready(versionName, apkFile.absolutePath, release.changelog)
                } catch (error: Throwable) {
                    partialFile.delete()
                    apkFile.delete()
                    preferences.edit {
                        remove(KEY_READY_VERSION)
                        remove(KEY_READY_CHANGELOG)
                    }
                    if (error is CancellationException) throw error
                    _state.value = UpdateState.Error("暂时无法检查更新")
                }
            }
        } finally {
            checkMutex.unlock()
        }
    }

    fun requestInstall(): Intent? {
        val ready = _state.value as? UpdateState.Ready ?: return null
        if (!context.packageManager.canRequestPackageInstalls()) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val file = File(ready.apkPath)
        if (verifiedArchive(file) == null) {
            clearDownloadedUpdate()
            _state.value = UpdateState.Error("更新包校验失败")
            return null
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canInstallWithoutUserAction(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.canRequestPackageInstalls() &&
            preferences.getString(SILENT_INSTALL_FAILED_VERSION, null) != ready.versionName
    }

    suspend fun installWithoutUserAction() {
        if (!installMutex.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext
                val ready = _state.value as? UpdateState.Ready ?: return@withContext
                if (!canInstallWithoutUserAction()) return@withContext
                val file = File(ready.apkPath)
                if (verifiedArchive(file) == null) {
                    clearDownloadedUpdate()
                    _state.value = UpdateState.Error("更新包校验失败")
                    return@withContext
                }

                _state.value = UpdateState.Installing(ready.versionName)
                runCatching { commitInstallSession(file, ready.versionName) }
                    .onFailure {
                        preferences.edit { putString(SILENT_INSTALL_FAILED_VERSION, ready.versionName) }
                        _state.value = ready
                    }
            }
        } finally {
            installMutex.unlock()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun commitInstallSession(file: File, versionName: String) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val installer = context.packageManager.packageInstaller
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(parameters)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, UpdateInstallActivity::class.java).apply {
                    action = ACTION_UPDATE_INSTALL_STATUS
                    putExtra(EXTRA_UPDATE_VERSION, versionName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    sessionId,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    installCallbackOptions(),
                )
                session.commit(pendingIntent.intentSender)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    private fun installCallbackOptions() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic().apply {
            setPendingIntentCreatorBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                },
            )
        }.toBundle()
    } else {
        null
    }

    private fun initialState(): UpdateState {
        if (!isConfigured()) return UpdateState.Disabled
        val info = verifiedArchive(apkFile) ?: run {
            clearDownloadedUpdate()
            return UpdateState.Idle
        }
        return UpdateState.Ready(
            preferences.getString(KEY_READY_VERSION, null) ?: info.versionName.orEmpty(),
            apkFile.absolutePath,
            readChangelog(preferences.getString(KEY_READY_CHANGELOG, null)),
        )
    }

    private fun isConfigured(): Boolean = BuildConfig.GITHUB_OWNER.isNotBlank() && BuildConfig.GITHUB_REPO.isNotBlank()

    private fun fetchLatestRelease(): ReleaseInfo {
        val url = URL(
            "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest/download/update.json",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "JiDeJi/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub 返回 ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val downloadUrl = URL(json.getString("apk_url"))
            require(downloadUrl.protocol == "https" && downloadUrl.host == "github.com") {
                "Release 下载地址不是 GitHub HTTPS 地址"
            }
            return ReleaseInfo(
                tag = json.getString("tag"),
                downloadUrl = downloadUrl.toString(),
                changelog = json.optJSONArray("changelog").toChangelog(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun download(release: ReleaseInfo) {
        partialFile.delete()
        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "JiDeJi/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) error("下载失败")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        _state.value = UpdateState.Downloading(
                            if (total > 0) ((copied * 100L / total).coerceIn(0, 100)).toInt() else null,
                        )
                    }
                }
            }
            if (total > 0 && partialFile.length() != total) error("更新包不完整")
            apkFile.delete()
            if (!partialFile.renameTo(apkFile)) error("无法保存更新包")
        } finally {
            connection.disconnect()
        }
    }

    private fun verifiedArchive(file: File): PackageInfo? {
        if (!file.isFile || file.length() == 0L) return null
        val archive = packageInfo(file.absolutePath) ?: return null
        val current = packageInfo(context.packageName, installed = true) ?: return null
        if (archive.packageName != context.packageName) return null
        if (archive.longVersionCodeCompat() <= current.longVersionCodeCompat()) return null
        if (signerDigest(archive) != signerDigest(current)) return null
        return archive
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(value: String, installed: Boolean = false): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return runCatching {
            if (installed) {
                context.packageManager.getPackageInfo(value, flags)
            } else {
                context.packageManager.getPackageArchiveInfo(value, flags)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signerDigest(info: PackageInfo): String? {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun clearDownloadedUpdate() {
        apkFile.delete()
        partialFile.delete()
        preferences.edit {
            remove(KEY_READY_VERSION)
            remove(KEY_READY_CHANGELOG)
            remove(SILENT_INSTALL_FAILED_VERSION)
        }
    }

    private fun readChangelog(value: String?): List<String> = runCatching {
        if (value == null) emptyList() else JSONArray(value).toChangelog()
    }.getOrDefault(emptyList())

    private fun JSONArray?.toChangelog(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optString(index).trim()
                if (item.isNotEmpty()) add(item)
            }
        }.take(MAX_CHANGELOG_ITEMS)
    }

    private data class ReleaseInfo(
        val tag: String,
        val downloadUrl: String,
        val changelog: List<String>,
    )

    companion object {
        private const val MAX_CHANGELOG_ITEMS = 4
        private const val KEY_READY_VERSION = "ready_version"
        private const val KEY_READY_CHANGELOG = "ready_changelog"
    }
}

internal const val ACTION_UPDATE_INSTALL_STATUS = "com.hackerli.jizhang.UPDATE_INSTALL_STATUS"
internal const val EXTRA_UPDATE_VERSION = "update_version"
internal const val UPDATE_PREFERENCES = "updates"
internal const val SILENT_INSTALL_FAILED_VERSION = "silent_install_failed_version"
