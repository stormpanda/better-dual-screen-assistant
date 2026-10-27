package com.stormpanda.megingiard.math

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportMathTest {

    @Test
    fun `calculateAspectFitScale fits wider content to container width`() {
        val scale = ViewportMath.calculateAspectFitScale(
            containerW = 1000f,
            containerH = 1000f,
            contentW = 2000f,
            contentH = 1000f
        )
        assertEquals(0.5f, scale, 0.0001f)
    }

    @Test
    fun `calculateAspectFitScale fits taller content to container height`() {
        val scale = ViewportMath.calculateAspectFitScale(
            containerW = 1000f,
            containerH = 1000f,
            contentW = 1000f,
            contentH = 2000f
        )
        assertEquals(0.5f, scale, 0.0001f)
    }

    @Test
    fun `calculateAspectFitScale returns 1f for invalid dimensions`() {
        assertEquals(1f, ViewportMath.calculateAspectFitScale(0f, 100f, 100f, 100f), 0.0001f)
        assertEquals(1f, ViewportMath.calculateAspectFitScale(100f, -1f, 100f, 100f), 0.0001f)
        assertEquals(1f, ViewportMath.calculateAspectFitScale(100f, 100f, 0f, 100f), 0.0001f)
        assertEquals(1f, ViewportMath.calculateAspectFitScale(100f, 100f, 100f, -5f), 0.0001f)
    }

    @Test
    fun `calculateAspectFillScale fills container using larger scale axis`() {
        val scaleWider = ViewportMath.calculateAspectFillScale(
            containerW = 1000f,
            containerH = 1000f,
            contentW = 2000f,
            contentH = 1000f
        )
        assertEquals(1.0f, scaleWider, 0.0001f)

        val scaleTaller = ViewportMath.calculateAspectFillScale(
            containerW = 1000f,
            containerH = 1000f,
            contentW = 1000f,
            contentH = 2000f
        )
        assertEquals(1.0f, scaleTaller, 0.0001f)
    }

    @Test
    fun `calculateAspectFillScale returns 1f for invalid dimensions`() {
        assertEquals(1f, ViewportMath.calculateAspectFillScale(0f, 100f, 100f, 100f), 0.0001f)
        assertEquals(1f, ViewportMath.calculateAspectFillScale(100f, 0f, 100f, 100f), 0.0001f)
        assertEquals(1f, ViewportMath.calculateAspectFillScale(100f, 100f, 0f, 100f), 0.0001f)
        assertEquals(1f, ViewportMath.calculateAspectFillScale(100f, 100f, 100f, 0f), 0.0001f)
    }

    @Test
    fun `getMaxOffsets returns half of overflow when scaled content exceeds container`() {
        val (maxOffsetX, maxOffsetY) = ViewportMath.getMaxOffsets(
            containerW = 1000f,
            containerH = 800f,
            contentW = 600f,
            contentH = 500f,
            scale = 2.0f // scaled: 1200 x 1000 -> overflow: 200 x 200 -> maxOffset: 100 x 100
        )
        assertEquals(100f, maxOffsetX, 0.0001f)
        assertEquals(100f, maxOffsetY, 0.0001f)
    }

    @Test
    fun `getMaxOffsets clamps to 0f when scaled content is smaller than container`() {
        val (maxOffsetX, maxOffsetY) = ViewportMath.getMaxOffsets(
            containerW = 1000f,
            containerH = 800f,
            contentW = 600f,
            contentH = 500f,
            scale = 1.0f
        )
        assertEquals(0f, maxOffsetX, 0.0001f)
        assertEquals(0f, maxOffsetY, 0.0001f)
    }

    @Test
    fun `floorMod handles positive negative and zero divisor`() {
        assertEquals(2, 7.floorMod(5))
        assertEquals(0, 10.floorMod(5))
        assertEquals(4, (-1).floorMod(5))
        assertEquals(0, (-10).floorMod(5))
        assertEquals(5, 5.floorMod(0))
        assertEquals(-2, 7.floorMod(-3))
    }

    @Test
    fun `prevItem and nextItem cycle through lists with wrap-around`() {
        val items = listOf("A", "B", "C")
        assertEquals("C", items.prevItem("A"))
        assertEquals("A", items.prevItem("B"))
        assertEquals("B", items.prevItem("C"))
        assertEquals("A", items.prevItem("UNKNOWN"))

        assertEquals("B", items.nextItem("A"))
        assertEquals("C", items.nextItem("B"))
        assertEquals("A", items.nextItem("C"))
        assertEquals("A", items.nextItem("UNKNOWN"))

        val empty = emptyList<String>()
        assertEquals("A", empty.prevItem("A"))
        assertEquals("A", empty.nextItem("A"))
    }
}
