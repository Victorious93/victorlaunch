package com.victorious.launch.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.core.theme.FleetThemePalettes

fun Drawable.toImageBitmap(): ImageBitmap = toBitmap().asImageBitmap()

fun Drawable.toThemedImageBitmap(style: FleetIconStyle, tintArgb: Int): ImageBitmap {
    val source =
        if (style == FleetIconStyle.MONOCHROME) {
            monochromeLayer() ?: this
        } else {
            this
        }
    val bitmap = source.toBitmap()
    return when (style) {
        FleetIconStyle.SYSTEM, FleetIconStyle.ICON_PACK -> bitmap.asImageBitmap()
        FleetIconStyle.MONOCHROME -> tinted(bitmap, tintArgb, saturate = false).asImageBitmap()
        FleetIconStyle.TINTED -> tinted(bitmap, tintArgb, saturate = true).asImageBitmap()
    }
}

private fun Drawable.monochromeLayer(): Drawable? {
    if (Build.VERSION.SDK_INT < 33) return null
    val adaptive = this as? AdaptiveIconDrawable ?: return null
    return adaptive.monochrome
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
        return bitmap
    }
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun tinted(src: Bitmap, tintArgb: Int, saturate: Boolean): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (!saturate) {
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint.colorFilter = PorterDuffColorFilter(tintArgb, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(out, 0f, 0f, paint)
    } else {
        paint.colorFilter = PorterDuffColorFilter(tintArgb, PorterDuff.Mode.SRC_ATOP)
        canvas.drawBitmap(src, 0f, 0f, paint)
    }
    return out
}

fun defaultTintArgb(): Int = FleetThemePalettes.parseArgb(FleetThemePalettes.NEON.primary)

