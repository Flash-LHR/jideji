package com.hackerli.jizhang.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

object TagImageStorage {
    private const val DIRECTORY = "tag_icons"
    private const val DECODE_LIMIT = 2_048
    private const val OUTPUT_SIZE = 384

    fun decode(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取图片" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式不支持" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > DECODE_LIMIT) sample *= 2
        val decoded = resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取图片" }
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        requireNotNull(decoded) { "图片解码失败" }

        val orientation = runCatching {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return decoded.oriented(orientation)
    }

    fun saveCrop(
        context: Context,
        source: Bitmap,
        zoom: Float,
        offsetXFraction: Float,
        offsetYFraction: Float,
    ): String {
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.BLACK)
            val baseScale = max(OUTPUT_SIZE.toFloat() / source.width, OUTPUT_SIZE.toFloat() / source.height)
            translate(
                OUTPUT_SIZE / 2f + offsetXFraction * OUTPUT_SIZE,
                OUTPUT_SIZE / 2f + offsetYFraction * OUTPUT_SIZE,
            )
            scale(baseScale * zoom, baseScale * zoom)
            drawBitmap(source, -source.width / 2f, -source.height / 2f, null)
        }

        val directory = directory(context)
        val finalFile = File(directory, "tag-${UUID.randomUUID()}.jpg")
        val temporaryFile = File(directory, "${finalFile.name}.tmp")
        try {
            FileOutputStream(temporaryFile).use { stream ->
                require(output.compress(Bitmap.CompressFormat.JPEG, 90, stream)) { "图片保存失败" }
                stream.fd.sync()
            }
            require(temporaryFile.renameTo(finalFile)) { "图片保存失败" }
            return finalFile.absolutePath
        } finally {
            output.recycle()
            temporaryFile.delete()
        }
    }

    fun delete(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.parentFile?.canonicalFile == directory(context).canonicalFile) file.delete()
        }
    }

    fun cleanupOrphans(context: Context, usedPaths: Set<String>) {
        val used = usedPaths.mapTo(mutableSetOf()) { File(it).absolutePath }
        directory(context).listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.absolutePath !in used) file.delete()
        }
    }

    fun totalBytes(context: Context): Long = directory(context).listFiles().orEmpty().sumOf { it.length() }

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY).apply {
        require(isDirectory || mkdirs()) { "无法创建标签图片目录" }
    }
}

private fun Bitmap.oriented(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    val result = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (result !== this) recycle()
    return result
}
