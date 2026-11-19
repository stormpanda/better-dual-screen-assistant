package com.stormpanda.betterdualscreenassistant.plugin

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Memory-bounded, crash-safe icon loader for third-party plugin packages.
 *
 * Pre-checks raster image bounds via `inJustDecodeBounds` + `inSampleSize` and rasterizes
 * external vector or bitmap drawables to at most [BdsaPluginContract.MAX_ICON_SIZE_PX] pixels
 * on the longest edge so a misbehaving plugin APK cannot OOM BDSA's persistent overlay.
 */
object PluginIconLoader {
    private const val TAG = "PluginIconLoader"
    private const val CACHE_ENTRIES = 64

    private sealed interface CachedIcon {
        data class Present(val bitmap: ImageBitmap) : CachedIcon
        data object Missing : CachedIcon
    }

    private val cache = object : LruCache<String, CachedIcon>(CACHE_ENTRIES) {}

    fun getCachedIconBitmap(
        packageName: String,
        versionCode: Long,
        resId: Int
    ): ImageBitmap? {
        if (resId == 0 || packageName.isBlank()) return null
        return (cache.get("$packageName:$versionCode:$resId") as? CachedIcon.Present)?.bitmap
    }

    fun isCached(
        packageName: String,
        versionCode: Long,
        resId: Int
    ): Boolean {
        if (resId == 0 || packageName.isBlank()) return true
        return cache.get("$packageName:$versionCode:$resId") != null
    }

    fun loadIconBitmap(
        context: Context,
        packageName: String,
        versionCode: Long,
        resId: Int
    ): ImageBitmap? {
        if (resId == 0 || packageName.isBlank()) return null
        val cacheKey = "$packageName:$versionCode:$resId"
        when (val existing = cache.get(cacheKey)) {
            is CachedIcon.Present -> return existing.bitmap
            CachedIcon.Missing -> return null
            null -> Unit
        }

        return try {
            val pm = context.packageManager
            val pkgResources = pm.getResourcesForApplication(packageName)
            val maxPx = BdsaPluginContract.MAX_ICON_SIZE_PX

            val rasterBitmap = tryLoadBoundedRasterBitmap(pkgResources, resId, maxPx)
            val bitmap = rasterBitmap ?: run {
                val drawable = pkgResources.getDrawable(resId, null) ?: run {
                    cache.put(cacheKey, CachedIcon.Missing)
                    return null
                }
                val rawW = drawable.intrinsicWidth
                val rawH = drawable.intrinsicHeight
                val isScalableVector = drawable is VectorDrawable || rawW <= 0 || rawH <= 0
                val effW = if (rawW > 0) rawW else maxPx
                val effH = if (rawH > 0) rawH else maxPx
                val longest = max(effW, effH)
                val scale = if (isScalableVector || longest > maxPx) {
                    maxPx.toFloat() / longest.toFloat()
                } else {
                    1f
                }
                val targetW = (effW * scale).roundToInt().coerceIn(1, maxPx)
                val targetH = (effH * scale).roundToInt().coerceIn(1, maxPx)

                if (
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
            }

            val imageBitmap = bitmap.asImageBitmap()
            cache.put(cacheKey, CachedIcon.Present(imageBitmap))
            imageBitmap
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load icon res=$resId from $packageName", t)
            cache.put(cacheKey, CachedIcon.Missing)
            null
        }
    }

    private fun tryLoadBoundedRasterBitmap(
        pkgResources: Resources,
        resId: Int,
        maxPx: Int
    ): Bitmap? {
        val tv = TypedValue()
        try {
            pkgResources.getValue(resId, tv, true)
        } catch (_: Throwable) {
            return null
        }
        val assetPath = tv.string?.toString()?.lowercase(Locale.US).orEmpty()
        val isPlainRaster = !assetPath.endsWith(".9.png") && (
            assetPath.endsWith(".png") ||
                assetPath.endsWith(".jpg") ||
                assetPath.endsWith(".jpeg") ||
                assetPath.endsWith(".webp")
            )
        if (!isPlainRaster) return null

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        pkgResources.openRawResource(resId, tv).use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var inSampleSize = 1
        val maxRaw = max(rawW, rawH)
        while ((maxRaw / (inSampleSize * 2)) >= maxPx) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = pkgResources.openRawResource(resId, tv).use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        if (decoded.width <= maxPx && decoded.height <= maxPx) {
            return decoded
        }

        val longest = max(decoded.width, decoded.height)
        val scale = maxPx.toFloat() / longest.toFloat()
        val targetW = (decoded.width * scale).roundToInt().coerceIn(1, maxPx)
        val targetH = (decoded.height * scale).roundToInt().coerceIn(1, maxPx)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
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
