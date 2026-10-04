package com.asmr.player.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * 封面位图纯函数（中性 util 包，供 ui / work / data 三方共用）。
 *
 * R3-A5：此前 `data/remote/CoverSupport`、`work/AlbumCoverThumbWorker`、
 * `ui/library/LibraryViewModel` 各有一份**逐字相同**的实现，收敛为单一来源。
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
