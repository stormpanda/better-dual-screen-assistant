package com.stormpanda.betterdualscreenassistant.plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Memory-bounded, crash-safe icon loader for third-party plugin packages.
 *
 * Rasterizes external vector or bitmap drawables to at most [BdsaPluginContract.MAX_ICON_SIZE_PX]
 * pixels on the longest edge so a misbehaving plugin APK cannot OOM BDSA's persistent overlay.
 */
object PluginIconLoader {
    private const val TAG = "PluginIconLoader"
    private const val CACHE_ENTRIES = 64

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_ENTRIES) {}

    fun loadIconBitmap(
        context: Context,
        packageName: String,
        versionCode: Long,
        resId: Int
    ): ImageBitmap? {
        if (resId == 0 || packageName.isBlank()) return null
        val cacheKey = "$packageName:$versionCode:$resId"
        cache.get(cacheKey)?.let { return it }

        return try {
            val pm = context.packageManager
            val pkgResources = pm.getResourcesForApplication(packageName)
            val drawable = pkgResources.getDrawable(resId, null) ?: return null

            val maxPx = BdsaPluginContract.MAX_ICON_SIZE_PX
            val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
            val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
            val longest = max(rawW, rawH)
            val scale = if (longest > maxPx) maxPx.toFloat() / longest.toFloat() else 1f
            val targetW = (rawW * scale).roundToInt().coerceIn(1, maxPx)
            val targetH = (rawH * scale).roundToInt().coerceIn(1, maxPx)

            val bitmap = if (
                drawable is BitmapDrawable &&
                drawable.bitmap != null &&
                !drawable.bitmap.isRecycled &&
                drawable.bitmap.width <= maxPx &&
                drawable.bitmap.height <= maxPx
            ) {
                drawable.bitmap
            } else {
                val rendered = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(rendered)
                drawable.setBounds(0, 0, targetW, targetH)
                drawable.draw(canvas)
                rendered
            }

            val imageBitmap = bitmap.asImageBitmap()
            cache.put(cacheKey, imageBitmap)
            imageBitmap
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load icon res=$resId from $packageName", t)
            null
        }
    }

    fun evictPackage(packageName: String) {
        val prefix = "$packageName:"
        val snapshot = cache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith(prefix)) {
                cache.remove(key)
            }
        }
    }

    fun clearCache() {
        cache.evictAll()
    }
}
