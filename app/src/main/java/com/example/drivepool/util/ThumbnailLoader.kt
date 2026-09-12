package com.example.drivepool.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxOf(1024 * 16, maxMemory / 8) // ~16-32 MB of cache
    private val lruCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getCached(key: String): Bitmap? = lruCache.get(key)

    fun putCached(key: String, bitmap: Bitmap) {
        lruCache.put(key, bitmap)
    }

    suspend fun loadThumbnail(
        context: Context,
        cacheKey: String,
        pathOrUri: String?,
        mimeType: String,
        targetSizePx: Int = 140
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pathOrUri.isNullOrBlank()) return@withContext null

        val cached = getCached(cacheKey)
        if (cached != null) return@withContext cached

        val lowerMime = mimeType.lowercase()
        val lowerPath = pathOrUri.lowercase()

        val isImage = lowerMime.startsWith("image/") ||
                lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
                lowerPath.endsWith(".png") || lowerPath.endsWith(".webp") ||
                lowerPath.endsWith(".bmp") || lowerPath.endsWith(".gif")

        val isVideo = lowerMime.startsWith("video/") ||
                lowerPath.endsWith(".mp4") || lowerPath.endsWith(".mkv") ||
                lowerPath.endsWith(".avi") || lowerPath.endsWith(".mov") ||
                lowerPath.endsWith(".3gp") || lowerPath.endsWith(".webm")

        val isPdf = lowerMime == "application/pdf" || lowerPath.endsWith(".pdf")

        val bitmap: Bitmap? = when {
            isImage -> decodeImageThumbnail(context, pathOrUri, targetSizePx, targetSizePx)
            isVideo -> decodeVideoThumbnail(context, pathOrUri, targetSizePx, targetSizePx)
            isPdf -> decodePdfThumbnail(context, pathOrUri, targetSizePx)
            else -> null
        }

        if (bitmap != null) {
            putCached(cacheKey, bitmap)
        }

        bitmap
    }

    private fun decodeImageThumbnail(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        return context.contentResolver.loadThumbnail(uri, Size(reqWidth, reqHeight), null)
                    } catch (_: Exception) {}
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.RGB_565
                        context.contentResolver.openInputStream(uri)?.use { stream2 ->
                            BitmapFactory.decodeStream(stream2, null, options)
                        }
                    } else null
                }
            } else {
                val f = File(pathOrUri)
                if (!f.exists() || f.length() == 0L) return null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    BitmapFactory.decodeFile(f.absolutePath, options)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeVideoThumbnail(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (pathOrUri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(pathOrUri))
            } else {
                val file = File(pathOrUri)
                if (!file.exists() || file.length() == 0L) return null
                retriever.setDataSource(file.absolutePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqWidth, reqHeight)
                    ?: retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqWidth, reqHeight)
            } else {
                val raw = retriever.frameAtTime
                if (raw != null) {
                    Bitmap.createScaledBitmap(raw, reqWidth, maxOf(1, (reqWidth * (raw.height.toFloat() / raw.width)).toInt()), true)
                } else null
            }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun decodePdfThumbnail(context: Context, pathOrUri: String, reqWidth: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(pathOrUri), "r")
            } else {
                val f = File(pathOrUri)
                if (f.exists() && f.length() > 0) {
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                } else null
            }
            if (pfd == null) return null

            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                page = renderer.openPage(0)
                val reqW = reqWidth
                val reqH = maxOf(1, (reqW * (page.height.toFloat() / page.width)).toInt())
                val bitmap = Bitmap.createBitmap(reqW, reqH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } else null
        } catch (_: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return maxOf(1, inSampleSize)
    }
}
