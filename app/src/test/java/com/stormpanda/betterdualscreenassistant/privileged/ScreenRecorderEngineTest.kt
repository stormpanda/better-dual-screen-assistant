package com.stormpanda.betterdualscreenassistant.privileged

import com.stormpanda.betterdualscreenassistant.ui.overlay.formatRecordingElapsedSeconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecorderEngineTest {

    @Test
    fun `buildOutputFilePath formats Top Bottom and Both MP4 paths in Movies ScreenRecordings`() {
        assertEquals(
            "/sdcard/Movies/ScreenRecordings/ScreenRecord_20261104_140000_Top.mp4",
            ScreenRecorderEngine.buildOutputFilePath(
                timestampStr = "20261104_140000",
                targetMode = ScreenRecorderEngine.TARGET_TOP
            )
        )
        assertEquals(
            "/sdcard/Movies/ScreenRecordings/ScreenRecord_20261104_140000_Bottom.mp4",
            ScreenRecorderEngine.buildOutputFilePath(
                timestampStr = "20261104_140000",
                targetMode = ScreenRecorderEngine.TARGET_BOTTOM
            )
        )
        assertEquals(
            "/sdcard/Movies/ScreenRecordings/ScreenRecord_20261104_140000_Both.mp4",
            ScreenRecorderEngine.buildOutputFilePath(
                timestampStr = "20261104_140000",
                targetMode = ScreenRecorderEngine.TARGET_BOTH
            )
        )
    }

    @Test
    fun `isValidRecordingOutputPath enforces sdcard MP4 destination and blocks path traversal`() {
        assertTrue(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/sdcard/Movies/ScreenRecordings/ScreenRecord_20261104_Top.mp4"
            )
        )
        assertTrue(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/storage/emulated/0/Movies/ScreenRecordings/ScreenRecord_20261104_Both.mp4"
            )
        )
        assertFalse(ScreenRecorderEngine.isValidRecordingOutputPath(""))
        assertFalse(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/sdcard/Movies/../../data/system/bad.mp4"
            )
        )
        assertFalse(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/data/local/tmp/ScreenRecord.mp4"
            )
        )
        assertFalse(
            ScreenRecorderEngine.isValidRecordingOutputPath(
                "/sdcard/Movies/ScreenRecordings/ScreenRecord.png"
            )
        )
    }

    @Test
    fun `computeDualScreenViewportLayout stacks 1920x1080 Top above centered 1240x1080 Bottom on 1920x2160 canvas`() {
        val layout = ScreenRecorderEngine.computeDualScreenViewportLayout(
            topWidth = 1920,
            topHeight = 1080,
            bottomWidth = 1240,
            bottomHeight = 1080
        )

        assertEquals(1920, layout.canvasWidth)
        assertEquals(2160, layout.canvasHeight)

        // OpenGL viewport origin (0, 0) is bottom-left:
        // Top display occupies upper half [1080..2160), Bottom display centered at x = 340 in [0..1080).
        assertEquals(
            ScreenRecorderEngine.ViewportRect(x = 0, y = 1080, width = 1920, height = 1080),
            layout.topViewport
        )
        assertEquals(
            ScreenRecorderEngine.ViewportRect(x = 340, y = 0, width = 1240, height = 1080),
            layout.bottomViewport
        )

        // Odd dimensions are aligned up to even macroblock boundaries
        val oddLayout = ScreenRecorderEngine.computeDualScreenViewportLayout(
            topWidth = 1919,
            topHeight = 1079,
            bottomWidth = 1239,
            bottomHeight = 1079
        )
        assertEquals(1920, oddLayout.canvasWidth)
        assertEquals(2160, oddLayout.canvasHeight)
    }

    @Test
    fun `mixStereoPcm16InPlace sums samples and saturates cleanly without wraparound`() {
        val internal = shortArrayOf(10_000, -10_000, 30_000, -30_000)
        val mic = shortArrayOf(5_000, -2_500, 10_000, -10_000)

        ScreenRecorderEngine.mixStereoPcm16InPlace(internal, mic, 4)

        assertArrayEquals(
            shortArrayOf(15_000, -12_500, Short.MAX_VALUE, Short.MIN_VALUE),
            internal
        )
    }

    @Test
    fun `upmixMonoToStereoPcm16 duplicates mono frames across left and right stereo channels`() {
        val mono = shortArrayOf(1234, -5678, 32000)
        val stereo = ShortArray(6)

        val writtenShorts = ScreenRecorderEngine.upmixMonoToStereoPcm16(mono, 3, stereo)

        assertEquals(6, writtenShorts)
        assertArrayEquals(shortArrayOf(1234, 1234, -5678, -5678, 32000, 32000), stereo)
    }

    @Test
    fun `MonotonicPtsNormalizer anchors first timestamp at zero and enforces strictly increasing PTS`() {
        val normalizer = ScreenRecorderEngine.MonotonicPtsNormalizer()

        assertEquals(0L, normalizer.normalize(5_000_000L))
        assertEquals(16_667L, normalizer.normalize(5_016_667L))
        // Duplicate or slightly regressed timestamp must still advance by +1us for MPEG4Writer safety
        assertEquals(16_668L, normalizer.normalize(5_016_667L))
        assertEquals(16_669L, normalizer.normalize(5_010_000L))
        assertEquals(33_333L, normalizer.normalize(5_033_333L))
    }

    @Test
    fun `formatRecordingElapsedSeconds formats MM SS under one hour and H MM SS above one hour`() {
        assertEquals("00:00", formatRecordingElapsedSeconds(0L))
        assertEquals("00:00", formatRecordingElapsedSeconds(-5L))
        assertEquals("01:24", formatRecordingElapsedSeconds(84L))
        assertEquals("59:59", formatRecordingElapsedSeconds(3599L))
        assertEquals("1:00:00", formatRecordingElapsedSeconds(3600L))
        assertEquals("2:05:09", formatRecordingElapsedSeconds(7509L))
    }
}
