package com.asmr.player.work

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.centerCropSquare
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileOutputStream

class AlbumCoverThumbWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val albumId = inputData.getLong(KEY_ALBUM_ID, 0L)
        if (albumId <= 0L) return Result.failure()

        val database = AppDatabaseProvider.get(applicationContext)
        val albumDao = database.albumDao()
        val entity = albumDao.getAlbumById(albumId) ?: return Result.success()

        val source = entity.coverPath.takeIf { it.isNotBlank() && it != "null" } ?: entity.coverUrl
        if (source.isBlank()) return Result.success()

        val sourceHash = source.trim().hashCode().toString()
        val dir = File(applicationContext.filesDir, "album_thumbs")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "a_${albumId}_${sourceHash}_v$THUMB_VERSION.jpg")

        if (entity.coverThumbPath == target.absolutePath && target.exists() && target.length() > 0L) {
            return Result.success()
        }

        val manager = EntryPointAccessors.fromApplication(applicationContext, ImageCacheEntryPoint::class.java).imageCacheManager()
        val model: Any = run {
            val headers = if (source.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(source) else emptyMap()
            if (headers.isEmpty()) source else CacheImageModel(data = source, headers = headers, keyTag = "dlsite")
        }
        val bitmap = manager.loadImage(model = model, size = IntSize(THUMB_SIZE_PX, THUMB_SIZE_PX)).asAndroidBitmap()
        val thumb = centerCropSquare(bitmap, THUMB_SIZE_PX)

        FileOutputStream(target).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val updated = entity.copy(coverThumbPath = target.absolutePath)
        albumDao.updateAlbum(updated)
        return Result.success()
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        private const val THUMB_VERSION = 2
        private const val THUMB_SIZE_PX = 640
    }
}
