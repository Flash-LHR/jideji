package com.hackerli.jizhang.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

data class PendingPhoto(val path: String, val uri: Uri)

object PhotoStorage {
    fun createCameraTarget(context: Context): PendingPhoto {
        val file = File(photoDirectory(context), "photo-${UUID.randomUUID()}.jpg")
        check(file.createNewFile()) { "无法创建照片文件" }
        return PendingPhoto(
            path = file.absolutePath,
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        )
    }

    fun copyFromGallery(
        context: Context,
        uris: List<Uri>,
        existingPaths: List<String> = emptyList(),
    ): Result<List<String>> = runCatching {
        val copied = mutableListOf<String>()
        val knownDigests = existingPaths.mapNotNullTo(mutableSetOf()) { digest(File(it)) }
        try {
            uris.distinct().forEach { uri ->
                val extension = sourceExtension(context, uri)
                val target = File(photoDirectory(context), "photo-${UUID.randomUUID()}.$extension")
                val temporary = File(target.parentFile, "${target.name}.part")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取所选照片")
                    require(temporary.length() > 0L) { "所选照片为空" }
                    val targetDigest = digest(temporary) ?: error("无法校验所选照片")
                    if (knownDigests.add(targetDigest)) {
                        check(temporary.renameTo(target)) { "无法保存所选照片" }
                        copied += target.absolutePath
                    } else {
                        temporary.delete()
                    }
                } catch (error: Throwable) {
                    temporary.delete()
                    target.delete()
                    throw error
                }
            }
            copied
        } catch (error: Throwable) {
            deleteAll(copied)
            throw error
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun deleteAll(paths: Iterable<String>) {
        paths.forEach(::delete)
    }

    fun totalBytes(paths: Iterable<String>): Long = paths.distinct().sumOf { File(it).length() }

    fun cleanupOrphans(context: Context, referencedPaths: Set<String>) {
        photoDirectory(context).listFiles()?.forEach { file ->
            if (file.absolutePath !in referencedPaths) file.delete()
        }
    }

    private fun photoDirectory(context: Context): File =
        File(context.filesDir, "expense_photos").apply { mkdirs() }

    private fun sourceExtension(context: Context, uri: Uri): String {
        val mimeExtension = context.contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!mimeExtension.isNullOrBlank()) return sanitizeExtension(mimeExtension)
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return sanitizeExtension(displayName?.substringAfterLast('.', "jpg"))
    }

    private fun sanitizeExtension(value: String?): String = value
        ?.lowercase()
        ?.filter { it.isLetterOrDigit() }
        ?.takeIf { it.length in 2..5 }
        ?: "jpg"

    private fun digest(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
