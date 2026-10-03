package com.asmr.player.data.remote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * R2-C4b-3b：封面位图纯函数迁入 data 层，供 OnlineContentRepository 使用
 * （原 ui/library/albumdetail/AlbumDetailViewModelSupport.centerCropSquare，纯搬迁）。
 */

internal fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val side = minOf(w, h)
    val left = (w - side) / 2
    val top = (h - side) / 2
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        src,
        Rect(left, top, left + side, top + side),
        Rect(0, 0, size, size),
        paint
    )
    return out
}
