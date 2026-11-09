package com.stormpanda.betterdualscreenassistant.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.stormpanda.betterdualscreenassistant.system.DeviceProfile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware-accelerated screen + audio recorder running inside [PrivilegedHelperMain] (`UID 0`).
 *
 * Supports:
 * - Single-screen zero-copy capture (`targetMode == 0` Top `1920x1080`, `targetMode == 1` Bottom `1240x1080`)
 *   via `SurfaceControl` display layer-stack mirroring directly onto `MediaCodec`'s input [Surface].
 * - Dual-screen GPU-composited capture (`targetMode == 2` Both `1920x2160`) via an OpenGL ES 2.0
 *   `GL_TEXTURE_EXTERNAL_OES` compositor stacking Top (`1920x1080`) above centered Bottom (`1240x1080`).
 * - Privileged internal game audio loopback (`AudioPolicy` `ROUTE_FLAG_LOOP_BACK_RENDER` +
 *   `allowPrivilegedPlaybackCapture(true)`) without muting device speakers, optional microphone
 *   capture, and 16-bit saturating stereo PCM mixing encoded to `48 kHz` AAC.
 * - Zero threads, surfaces, or audio policies allocated while idle.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi", "MissingPermission")
class ScreenRecorderEngine(
    private val contextProvider: () -> Context?,
    private val displayManagerProxy: Any?
) {

    data class Config(
        val targetMode: Int,
        val includeInternalAudio: Boolean,
        val includeMicAudio: Boolean,
        val fps: Int,
        val outputPath: String
    )

    data class Status(
        val isRecording: Boolean = false,
        val targetMode: Int = 0,
        val startedElapsedRealtimeMs: Long = 0L,
        val outputPath: String = "",
        val lastCompletedPath: String = "",
        val lastStopReason: String = ""
    )

    internal data class ViewportRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )

    internal data class DualScreenViewportLayout(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val topViewport: ViewportRect,
        val bottomViewport: ViewportRect
    )

    internal data class DisplayMirrorSpec(
        val displayId: Int,
        val layerStack: Int,
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    companion object {
        private const val TAG = "ScreenRecorderEngine"

        const val TARGET_TOP = 0
        const val TARGET_BOTTOM = 1
        const val TARGET_BOTH = 2

        const val DEFAULT_RECORDINGS_DIR = "/sdcard/Movies/ScreenRecordings"
        private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC

        private const val AUDIO_SAMPLE_RATE = 48_000
        private const val AUDIO_CHANNEL_COUNT = 2
        private const val AUDIO_BIT_RATE = 192_000
        private const val AUDIO_CHUNK_FRAMES = 960 // 20ms @ 48 kHz
        private const val AUDIO_CHUNK_SHORTS_STEREO = AUDIO_CHUNK_FRAMES * AUDIO_CHANNEL_COUNT
        private const val AUDIO_CHUNK_BYTES_STEREO = AUDIO_CHUNK_SHORTS_STEREO * 2
        private const val AUDIO_TICK_NS = 20_000_000L

        private const val MIN_FREE_STORAGE_BYTES = 250L * 1024L * 1024L // 250 MB
        private const val STORAGE_CHECK_INTERVAL_NS = 5_000_000_000L // 5s
        private const val MUXER_AUDIO_FORMAT_WAIT_MS = 800L
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        fun buildOutputFilePath(
            timestampStr: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()),
            targetMode: Int,
            baseDir: String = DEFAULT_RECORDINGS_DIR
        ): String {
            val suffix = when (targetMode) {
                TARGET_TOP -> "Top"
                TARGET_BOTTOM -> "Bottom"
                else -> "Both"
            }
            val cleanBase = baseDir.trimEnd('/')
            return "$cleanBase/ScreenRecord_${timestampStr}_${suffix}.mp4"
        }

        internal fun isValidRecordingOutputPath(path: String): Boolean {
            if (path.isBlank() || path.contains("..") || !path.endsWith(".mp4")) return false
            return path.startsWith("/sdcard/") || path.startsWith("/storage/emulated/0/")
        }

        internal fun alignEven(value: Int): Int = (value.coerceAtLeast(2) + 1) and 1.inv()

        internal fun computeDualScreenViewportLayout(
            topWidth: Int,
            topHeight: Int,
            bottomWidth: Int,
            bottomHeight: Int
        ): DualScreenViewportLayout {
            val topW = alignEven(topWidth)
            val topH = alignEven(topHeight)
            val botW = alignEven(bottomWidth)
            val botH = alignEven(bottomHeight)
            val canvasW = alignEven(maxOf(topW, botW))
            val canvasH = alignEven(topH + botH)
            // OpenGL viewport origin (0, 0) is bottom-left:
            // Bottom screen sits in [0 .. botH), Top screen sits in [botH .. botH + topH).
            val topVp = ViewportRect(
                x = (canvasW - topW) / 2,
                y = botH,
                width = topW,
                height = topH
            )
            val bottomVp = ViewportRect(
                x = (canvasW - botW) / 2,
                y = 0,
                width = botW,
                height = botH
            )
            return DualScreenViewportLayout(
                canvasWidth = canvasW,
                canvasHeight = canvasH,
                topViewport = topVp,
                bottomViewport = bottomVp
            )
        }

        internal fun mixStereoPcm16InPlace(
            accumulator: ShortArray,
            addition: ShortArray,
            shortCount: Int
        ) {
            val count = minOf(shortCount, accumulator.size, addition.size)
            for (i in 0 until count) {
                val sum = accumulator[i].toInt() + addition[i].toInt()
                accumulator[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        internal fun upmixMonoToStereoPcm16(
            monoSrc: ShortArray,
            monoSampleCount: Int,
            stereoDst: ShortArray
        ): Int {
            val frames = minOf(monoSampleCount, monoSrc.size, stereoDst.size / 2)
            for (i in 0 until frames) {
                val s = monoSrc[i]
                stereoDst[i * 2] = s
                stereoDst[i * 2 + 1] = s
            }
            return frames * 2
        }
    }

    internal class MonotonicPtsNormalizer {
        private var baseRawPtsUs: Long = Long.MIN_VALUE
        private var lastEmittedPtsUs: Long = -1L

        fun normalize(rawPtsUs: Long): Long {
            if (baseRawPtsUs == Long.MIN_VALUE) {
                baseRawPtsUs = rawPtsUs
            }
            val delta = (rawPtsUs - baseRawPtsUs).coerceAtLeast(0L)
            val emitted = if (delta <= lastEmittedPtsUs) {
                lastEmittedPtsUs + 1L
            } else {
                delta
            }
            lastEmittedPtsUs = emitted
            return emitted
        }
    }

    private val stateLock = Any()
    private var activeSession: ActiveRecordingSession? = null
    private var lastCompletedPath: String = ""
    private var lastStopReason: String = ""

    fun getStatus(): Status = synchronized(stateLock) {
        val current = activeSession
        if (current != null && current.isRunning()) {
            Status(
                isRecording = true,
                targetMode = current.config.targetMode,
                startedElapsedRealtimeMs = current.startedElapsedRealtimeMs,
                outputPath = current.config.outputPath,
                lastCompletedPath = lastCompletedPath,
                lastStopReason = lastStopReason
            )
        } else {
            Status(
                isRecording = false,
                targetMode = 0,
                startedElapsedRealtimeMs = 0L,
                outputPath = "",
                lastCompletedPath = lastCompletedPath,
                lastStopReason = lastStopReason
            )
        }
    }

    fun startRecording(config: Config): Boolean {
        if (!isValidRecordingOutputPath(config.outputPath)) {
            Log.w(TAG, "Rejected invalid recording output path: ${config.outputPath}")
            return false
        }
        val normalizedFps = if (config.fps <= 30) 30 else 60
        val normalizedTarget = config.targetMode.coerceIn(TARGET_TOP, TARGET_BOTH)
        val normalizedConfig = config.copy(
            targetMode = normalizedTarget,
            fps = normalizedFps
        )

        synchronized(stateLock) {
            val existing = activeSession
            if (existing != null && existing.isRunning()) {
                Log.w(TAG, "Screen recording already active at ${existing.config.outputPath}")
                return false
            }
            activeSession = null
        }

        val outFile = File(normalizedConfig.outputPath)
        try {
            outFile.parentFile?.mkdirs()
            if (outFile.exists()) {
                outFile.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to prepare output directory for ${normalizedConfig.outputPath}", t)
            return false
        }

        if (!hasSufficientStorage(outFile.parentFile)) {
            Log.w(TAG, "Insufficient storage to start screen recording")
            synchronized(stateLock) {
                lastStopReason = "low_storage"
            }
            return false
        }

        val session = ActiveRecordingSession(normalizedConfig)
        val started = session.start()
        if (!started) {
            session.stop("start_failed")
            return false
        }

        synchronized(stateLock) {
            activeSession = session
            lastStopReason = ""
        }
        Log.i(
            TAG,
            "Started screen recording (mode=${normalizedConfig.targetMode}, fps=${normalizedConfig.fps}, " +
                "internalAudio=${normalizedConfig.includeInternalAudio}, mic=${normalizedConfig.includeMicAudio}) " +
                "-> ${normalizedConfig.outputPath}"
        )
        return true
    }

    fun stopRecording(reason: String = "user"): Status {
        val sessionToStop: ActiveRecordingSession? = synchronized(stateLock) {
            activeSession
        }
        if (sessionToStop != null) {
            sessionToStop.stop(reason)
        }
        return getStatus()
    }

    private fun onSessionFinished(session: ActiveRecordingSession, completedPath: String, reason: String) {
        synchronized(stateLock) {
            if (activeSession === session) {
                activeSession = null
            }
            if (completedPath.isNotEmpty()) {
                lastCompletedPath = completedPath
            }
            lastStopReason = reason
        }
    }

    private fun hasSufficientStorage(dir: File?): Boolean {
        return try {
            val target = dir?.takeIf { it.exists() } ?: File("/sdcard")
            StatFs(target.absolutePath).availableBytes >= MIN_FREE_STORAGE_BYTES
        } catch (_: Throwable) {
            true
        }
    }

    private fun queryDisplaySpec(displayId: Int): DisplayMirrorSpec {
        val profile = DeviceProfile.detectFromSystem()
        val fallbackWidth = if (displayId == 0) 1920 else profile.bottomFallbackWidth
        val fallbackHeight = if (displayId == 0) 1080 else profile.bottomFallbackHeight
        val fallbackLayerStack = displayId
        val fallbackDensity = if (displayId == 0) 369 else profile.bottomFallbackDensityDpi
        val dm = displayManagerProxy
        if (dm != null) {
            try {
                val getInfo = dm.javaClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType)
                val info = getInfo.invoke(dm, displayId)
                if (info != null) {
                    val infoClass = info.javaClass
                    val layerStack = infoClass.getField("layerStack").getInt(info)
                    val w = infoClass.getField("logicalWidth").getInt(info)
                    val h = infoClass.getField("logicalHeight").getInt(info)
                    val dpi = infoClass.getField("logicalDensityDpi").getInt(info)
                    if (w > 0 && h > 0) {
                        return DisplayMirrorSpec(
                            displayId = displayId,
                            layerStack = layerStack,
                            width = alignEven(w),
                            height = alignEven(h),
                            densityDpi = if (dpi > 0) dpi else fallbackDensity
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "IDisplayManager.getDisplayInfo($displayId) failed, using defaults", t)
            }
        }
        return DisplayMirrorSpec(
            displayId = displayId,
            layerStack = fallbackLayerStack,
            width = fallbackWidth,
            height = fallbackHeight,
            densityDpi = fallbackDensity
        )
    }

    private inner class ActiveRecordingSession(val config: Config) {
        val startedElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
        private val running = AtomicBoolean(false)
        private val stopInitiated = AtomicBoolean(false)
        private val finishedLatch = CountDownLatch(1)

        private var videoEncoder: MediaCodec? = null
        private var videoInputSurface: Surface? = null
        private var singleDisplayMirror: DisplayMirrorHandle? = null
        private var dualGlCompositor: DualScreenGlCompositor? = null

        private var audioPipeline: AudioCapturePipeline? = null
        private var muxerCoordinator: MuxerCoordinator? = null
        private var videoDrainThread: Thread? = null

        fun isRunning(): Boolean = running.get() && !stopInitiated.get()

        fun start(): Boolean {
            return try {
                val topSpec = queryDisplaySpec(0)
                val bottomSpec = queryDisplaySpec(DeviceProfile.detectFromSystem().bottomDisplayId)

                val (videoWidth, videoHeight, bitRate) = when (config.targetMode) {
                    TARGET_TOP -> Triple(
                        topSpec.width,
                        topSpec.height,
                        if (config.fps >= 60) 16_000_000 else 10_000_000
                    )
                    TARGET_BOTTOM -> Triple(
                        bottomSpec.width,
                        bottomSpec.height,
                        if (config.fps >= 60) 14_000_000 else 8_000_000
                    )
                    else -> {
                        val layout = computeDualScreenViewportLayout(
                            topSpec.width,
                            topSpec.height,
                            bottomSpec.width,
                            bottomSpec.height
                        )
                        Triple(
                            layout.canvasWidth,
                            layout.canvasHeight,
                            if (config.fps >= 60) 24_000_000 else 14_000_000
                        )
                    }
                }

                val vFormat = MediaFormat.createVideoFormat(VIDEO_MIME, videoWidth, videoHeight).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
                }

                val vCodec = MediaCodec.createEncoderByType(VIDEO_MIME)
                vCodec.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = vCodec.createInputSurface()
                vCodec.start()
                videoEncoder = vCodec
                videoInputSurface = inputSurface

                val wantAudio = config.includeInternalAudio || config.includeMicAudio
                val createdAudio = if (wantAudio) {
                    AudioCapturePipeline(
                        context = contextProvider(),
                        includeInternal = config.includeInternalAudio,
                        includeMic = config.includeMicAudio
                    ).takeIf { it.prepare() }
                } else {
                    null
                }
                audioPipeline = createdAudio

                val coordinator = MuxerCoordinator(
                    outputPath = config.outputPath,
                    expectAudio = createdAudio != null,
                    onLowStorageAutoStop = {
                        Thread({ stop("low_storage") }, "BDSA-LowStorageStop").start()
                    }
                )
                muxerCoordinator = coordinator

                if (config.targetMode == TARGET_BOTH) {
                    val compositor = DualScreenGlCompositor(
                        topSpec = topSpec,
                        bottomSpec = bottomSpec,
                        fps = config.fps,
                        encoderSurface = inputSurface
                    )
                    if (!compositor.start()) {
                        compositor.release()
                        return false
                    }
                    dualGlCompositor = compositor
                } else {
                    val targetSpec = if (config.targetMode == TARGET_TOP) topSpec else bottomSpec
                    val mirror = createDisplayMirror(
                        name = "bdsa_rec_${targetSpec.displayId}",
                        spec = targetSpec,
                        outputSurface = inputSurface
                    ) ?: return false
                    singleDisplayMirror = mirror
                }

                running.set(true)

                val vThread = Thread({ drainVideoEncoderLoop(vCodec, coordinator) }, "BDSA-VideoDrain")
                videoDrainThread = vThread
                vThread.start()

                createdAudio?.start(coordinator)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start ActiveRecordingSession", t)
                false
            }
        }

        fun stop(reason: String) {
            if (!stopInitiated.compareAndSet(false, true)) {
                try {
                    finishedLatch.await(1500L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                }
                return
            }
            running.set(false)
            try {
                // 1. Detach display producer(s) cleanly before signaling encoder EOS
                try {
                    dualGlCompositor?.release()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error releasing GL compositor", t)
                } finally {
                    dualGlCompositor = null
                }

                try {
                    singleDisplayMirror?.release()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error releasing single display mirror", t)
                } finally {
                    singleDisplayMirror = null
                }

                // 2. Signal video & audio EOS and wait briefly for drain threads to flush
                try {
                    videoEncoder?.signalEndOfInputStream()
                } catch (_: Throwable) {
                }

                try {
                    audioPipeline?.stopAndJoin()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error stopping audio pipeline", t)
                } finally {
                    audioPipeline = null
                }

                try {
                    videoDrainThread?.join(800L)
                } catch (_: InterruptedException) {
                } finally {
                    videoDrainThread = null
                }

                // 3. Release video encoder & input surface
                try {
                    videoEncoder?.stop()
                } catch (_: Throwable) {
                }
                try {
                    videoEncoder?.release()
                } catch (_: Throwable) {
                } finally {
                    videoEncoder = null
                }
                try {
                    videoInputSurface?.release()
                } catch (_: Throwable) {
                } finally {
                    videoInputSurface = null
                }

                // 4. Finalize MP4 container
                val finalizedOk = try {
                    muxerCoordinator?.finishAndRelease() ?: false
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to finalize MediaMuxer", t)
                    false
                } finally {
                    muxerCoordinator = null
                }

                val savedPath = if (finalizedOk && File(config.outputPath).exists()) {
                    config.outputPath
                } else {
                    try {
                        File(config.outputPath).delete()
                    } catch (_: Throwable) {
                    }
                    ""
                }
                onSessionFinished(this, savedPath, reason)
                Log.i(TAG, "Stopped screen recording (reason=$reason, savedPath=$savedPath)")
            } finally {
                finishedLatch.countDown()
            }
        }

        private fun drainVideoEncoderLoop(codec: MediaCodec, coordinator: MuxerCoordinator) {
            val bufferInfo = MediaCodec.BufferInfo()
            val ptsNormalizer = MonotonicPtsNormalizer()
            while (true) {
                val outIndex = try {
                    codec.dequeueOutputBuffer(bufferInfo, 25_000L)
                } catch (_: Throwable) {
                    break
                }
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!running.get()) {
                            // Give signalEndOfInputStream a brief chance to emit EOS
                            val retryIndex = try {
                                codec.dequeueOutputBuffer(bufferInfo, 60_000L)
                            } catch (_: Throwable) {
                                break
                            }
                            if (retryIndex < 0) break
                            if (handleVideoOutputBuffer(codec, retryIndex, bufferInfo, ptsNormalizer, coordinator)) {
                                break
                            }
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        try {
                            coordinator.onTrackFormatReady(isVideo = true, format = codec.outputFormat)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed setting video output format", t)
                            break
                        }
                    }
                    outIndex >= 0 -> {
                        if (handleVideoOutputBuffer(codec, outIndex, bufferInfo, ptsNormalizer, coordinator)) {
                            break
                        }
                    }
                }
            }
        }

        private fun handleVideoOutputBuffer(
            codec: MediaCodec,
            outIndex: Int,
            bufferInfo: MediaCodec.BufferInfo,
            ptsNormalizer: MonotonicPtsNormalizer,
            coordinator: MuxerCoordinator
        ): Boolean {
            var sawEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            try {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null) {
                        val normalizedPts = ptsNormalizer.normalize(bufferInfo.presentationTimeUs)
                        coordinator.writeSample(
                            isVideo = true,
                            buffer = outBuf,
                            offset = bufferInfo.offset,
                            size = bufferInfo.size,
                            presentationTimeUs = normalizedPts,
                            flags = bufferInfo.flags
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error writing video sample", t)
                sawEos = true
            } finally {
                try {
                    codec.releaseOutputBuffer(outIndex, false)
                } catch (_: Throwable) {
                }
            }
            return sawEos
        }
    }

    /**
     * Thread-safe coordinator that waits for both video (and optional audio) `MediaFormat`s before
     * starting [MediaMuxer], buffering any early packets up to [MUXER_AUDIO_FORMAT_WAIT_MS] so zero
     * initial keyframes are lost.
     */
    private inner class MuxerCoordinator(
        private val outputPath: String,
        expectAudio: Boolean,
        private val onLowStorageAutoStop: () -> Unit
    ) {
        private inner class PendingPacket(
            val isVideo: Boolean,
            val data: ByteArray,
            val presentationTimeUs: Long,
            val flags: Int
        )

        private val lock = Any()
        private var muxer: MediaMuxer? = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private var wantAudio: Boolean = expectAudio
        private var videoTrackIndex: Int = -1
        private var audioTrackIndex: Int = -1
        private var muxerStarted: Boolean = false
        private var muxerClosed: Boolean = false
        private var firstTrackAddedElapsedMs: Long = 0L
        private var writtenVideoSamples: Long = 0L
        private var lastStorageCheckNs: Long = System.nanoTime()
        private var lowStorageTriggered: Boolean = false
        private val pendingPackets = ArrayDeque<PendingPacket>()

        fun onTrackFormatReady(isVideo: Boolean, format: MediaFormat) {
            synchronized(lock) {
                val m = muxer ?: return
                if (muxerStarted || muxerClosed) return
                if (isVideo) {
                    if (videoTrackIndex < 0) {
                        videoTrackIndex = m.addTrack(format)
                    }
                } else if (wantAudio) {
                    if (audioTrackIndex < 0) {
                        audioTrackIndex = m.addTrack(format)
                    }
                }
                if (firstTrackAddedElapsedMs == 0L) {
                    firstTrackAddedElapsedMs = SystemClock.elapsedRealtime()
                }
                maybeStartMuxerLocked()
            }
        }

        fun writeSample(
            isVideo: Boolean,
            buffer: ByteBuffer,
            offset: Int,
            size: Int,
            presentationTimeUs: Long,
            flags: Int
        ) {
            synchronized(lock) {
                if (muxerClosed) return
                if (!muxerStarted) {
                    if (wantAudio &&
                        videoTrackIndex >= 0 &&
                        audioTrackIndex < 0 &&
                        firstTrackAddedElapsedMs > 0L &&
                        SystemClock.elapsedRealtime() - firstTrackAddedElapsedMs >= MUXER_AUDIO_FORMAT_WAIT_MS
                    ) {
                        Log.w(TAG, "Audio encoder format timed out; starting MediaMuxer with video only")
                        wantAudio = false
                        maybeStartMuxerLocked()
                    }
                }

                if (!muxerStarted) {
                    if (pendingPackets.size < 180) {
                        val copy = ByteArray(size)
                        val dup = buffer.duplicate()
                        dup.position(offset)
                        dup.limit(offset + size)
                        dup.get(copy)
                        pendingPackets.addLast(PendingPacket(isVideo, copy, presentationTimeUs, flags))
                    }
                    return
                }

                val trackIdx = if (isVideo) videoTrackIndex else audioTrackIndex
                if (trackIdx < 0) return
                val m = muxer ?: return
                val info = MediaCodec.BufferInfo().apply {
                    set(offset, size, presentationTimeUs, flags)
                }
                m.writeSampleData(trackIdx, buffer, info)
                if (isVideo) {
                    writtenVideoSamples++
                    checkStoragePeriodicallyLocked()
                }
            }
        }

        private fun maybeStartMuxerLocked() {
            val m = muxer ?: return
            if (muxerStarted || muxerClosed) return
            val ready = videoTrackIndex >= 0 && (!wantAudio || audioTrackIndex >= 0)
            if (!ready) return

            m.start()
            muxerStarted = true
            val info = MediaCodec.BufferInfo()
            while (pendingPackets.isNotEmpty()) {
                val pkt = pendingPackets.removeFirst()
                val trackIdx = if (pkt.isVideo) videoTrackIndex else audioTrackIndex
                if (trackIdx >= 0) {
                    val buf = ByteBuffer.wrap(pkt.data)
                    info.set(0, pkt.data.size, pkt.presentationTimeUs, pkt.flags)
                    m.writeSampleData(trackIdx, buf, info)
                    if (pkt.isVideo) {
                        writtenVideoSamples++
                    }
                }
            }
        }

        private fun checkStoragePeriodicallyLocked() {
            if (lowStorageTriggered) return
            val nowNs = System.nanoTime()
            if (nowNs - lastStorageCheckNs < STORAGE_CHECK_INTERVAL_NS) return
            lastStorageCheckNs = nowNs
            if (!hasSufficientStorage(File(outputPath).parentFile)) {
                lowStorageTriggered = true
                Log.w(TAG, "Low disk space detected during recording; triggering clean stop")
                onLowStorageAutoStop()
            }
        }

        fun finishAndRelease(): Boolean {
            synchronized(lock) {
                if (muxerClosed) return writtenVideoSamples > 0L
                if (!muxerStarted && videoTrackIndex >= 0 && pendingPackets.isNotEmpty()) {
                    wantAudio = false
                    try {
                        maybeStartMuxerLocked()
                    } catch (_: Throwable) {
                    }
                }
                muxerClosed = true
                pendingPackets.clear()
                val m = muxer
                muxer = null
                var stoppedCleanly = false
                if (m != null) {
                    try {
                        if (muxerStarted && writtenVideoSamples > 0L) {
                            m.stop()
                            stoppedCleanly = true
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "MediaMuxer.stop() failed", t)
                    } finally {
                        try {
                            m.release()
                        } catch (_: Throwable) {
                        }
                    }
                }
                return stoppedCleanly
            }
        }
    }

    /**
     * Paced `48 kHz` stereo PCM capture & mixer feeding hardware AAC [MediaCodec].
     */
    private class AudioCapturePipeline(
        private val context: Context?,
        private val includeInternal: Boolean,
        private val includeMic: Boolean
    ) {
        private var audioPolicy: Any? = null
        private var audioManager: AudioManager? = null
        private var internalRecord: AudioRecord? = null
        private var micRecord: AudioRecord? = null
        private var micIsMono: Boolean = false
        private var audioEncoder: MediaCodec? = null
        private val running = AtomicBoolean(false)
        private var feedThread: Thread? = null
        private var drainThread: Thread? = null

        fun prepare(): Boolean {
            val callingToken = Binder.clearCallingIdentity()
            try {
                if (includeInternal) {
                    setupInternalLoopbackRecord()
                }
                if (includeMic) {
                    setupMicrophoneRecord()
                }
                if (internalRecord == null && micRecord == null) {
                    releaseAudioInputs()
                    return false
                }

                return try {
                    val aFormat = MediaFormat.createAudioFormat(
                        AUDIO_MIME,
                        AUDIO_SAMPLE_RATE,
                        AUDIO_CHANNEL_COUNT
                    ).apply {
                        setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_CHUNK_BYTES_STEREO * 2)
                    }
                    val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
                    codec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    audioEncoder = codec
                    true
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to initialize AAC audio encoder; continuing without audio", t)
                    releaseAudioInputs()
                    false
                }
            } finally {
                Binder.restoreCallingIdentity(callingToken)
            }
        }

        private fun setupInternalLoopbackRecord() {
            val ctx = context
            val am = ctx?.getSystemService(AudioManager::class.java)
            try {
                val mixingRuleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
                val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
                val ruleBuilder = ruleBuilderClass.getConstructor().newInstance()

                // Note: Do NOT call allowPrivilegedPlaybackCapture(true) here — in AOSP
                // AudioMix.Builder.build(), allowPrivilegedPlaybackCapture is restricted to
                // <= 16 kHz mono hotword/voice capture and throws IllegalArgumentException at 48 kHz.
                // Standard UID 0 AudioPolicy with MODIFY_AUDIO_ROUTING and ROUTE_FLAG_LOOP_BACK_RENDER
                // tees full 48 kHz stereo to a secondary mix without muting the physical device speakers.
                try {
                    val mixRolePlayers = mixingRuleClass.getField("MIX_ROLE_PLAYERS").getInt(null)
                    ruleBuilderClass
                        .getMethod("setTargetMixRole", Int::class.javaPrimitiveType)
                        .invoke(ruleBuilder, mixRolePlayers)
                } catch (_: Throwable) {
                }

                val matchUsageFlag = mixingRuleClass.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null)
                val addMixRule = ruleBuilderClass.getMethod(
                    "addMixRule",
                    Int::class.javaPrimitiveType,
                    Any::class.java
                )
                for (usage in intArrayOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_GAME,
                    AudioAttributes.USAGE_UNKNOWN
                )) {
                    val attr = AudioAttributes.Builder().setUsage(usage).build()
                    addMixRule.invoke(ruleBuilder, matchUsageFlag, attr)
                }
                val mixingRule = ruleBuilderClass.getMethod("build").invoke(ruleBuilder)

                val pcmFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()

                val audioMixClass = Class.forName("android.media.audiopolicy.AudioMix")
                val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
                val mixBuilder = mixBuilderClass.getConstructor(mixingRuleClass).newInstance(mixingRule)
                mixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, pcmFormat)
                val routeLoopbackRender = try {
                    audioMixClass.getField("ROUTE_FLAG_LOOP_BACK_RENDER").getInt(null)
                } catch (_: Throwable) {
                    0x3 // ROUTE_FLAG_RENDER (0x1) | ROUTE_FLAG_LOOP_BACK (0x2)
                }
                mixBuilderClass
                    .getMethod("setRouteFlags", Int::class.javaPrimitiveType)
                    .invoke(mixBuilder, routeLoopbackRender)
                val audioMix = mixBuilderClass.getMethod("build").invoke(mixBuilder)

                val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
                val policyBuilder = policyBuilderClass.getConstructor(Context::class.java).newInstance(ctx)
                try {
                    Looper.getMainLooper()?.let { mainLooper ->
                        policyBuilderClass.getMethod("setLooper", Looper::class.java)
                            .invoke(policyBuilder, mainLooper)
                    }
                } catch (_: Throwable) {
                }
                policyBuilderClass.getMethod("addMix", audioMixClass).invoke(policyBuilder, audioMix)
                val policy = policyBuilderClass.getMethod("build").invoke(policyBuilder)
                if (policy != null) {
                    val regResult = if (am != null) {
                        val regMethod = AudioManager::class.java.getMethod("registerAudioPolicy", policyClass)
                        regMethod.invoke(am, policy) as? Int ?: -1
                    } else {
                        val regStatic = AudioManager::class.java.getDeclaredMethod("registerAudioPolicyStatic", policyClass)
                        regStatic.isAccessible = true
                        regStatic.invoke(null, policy) as? Int ?: -1
                    }
                    if (regResult == 0 /* AudioManager.SUCCESS */) {
                        val createSink = policyClass.getMethod("createAudioRecordSink", audioMixClass)
                        val record = createSink.invoke(policy, audioMix) as? AudioRecord
                        if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                            record.startRecording()
                            audioManager = am
                            audioPolicy = policy
                            internalRecord = record
                            Log.i(
                                TAG,
                                "Initialized internal audio loopback policy (ROUTE_FLAG_LOOP_BACK_RENDER) @ ${AUDIO_SAMPLE_RATE}Hz stereo"
                            )
                            return
                        } else {
                            Log.w(TAG, "createAudioRecordSink returned uninitialized AudioRecord (state=${record?.state})")
                            try {
                                record?.release()
                            } catch (_: Throwable) {
                            }
                            unregisterPolicyQuietly(am, policy)
                        }
                    } else {
                        Log.w(TAG, "AudioManager.registerAudioPolicy returned $regResult")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to initialize internal audio loopback policy", t)
            }
        }

        private fun setupMicrophoneRecord() {
            try {
                val minStereo = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minStereo > 0) {
                    val stereoRec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minStereo * 4, AUDIO_CHUNK_BYTES_STEREO * 8)
                    )
                    if (stereoRec.state == AudioRecord.STATE_INITIALIZED) {
                        stereoRec.startRecording()
                        micRecord = stereoRec
                        micIsMono = false
                        Log.i(TAG, "Initialized stereo microphone AudioRecord @ ${AUDIO_SAMPLE_RATE}Hz")
                        return
                    }
                    stereoRec.release()
                }

                val minMono = AudioRecord.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minMono > 0) {
                    val monoRec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minMono * 4, AUDIO_CHUNK_BYTES_STEREO * 4)
                    )
                    if (monoRec.state == AudioRecord.STATE_INITIALIZED) {
                        monoRec.startRecording()
                        micRecord = monoRec
                        micIsMono = true
                        Log.i(TAG, "Initialized mono microphone AudioRecord @ ${AUDIO_SAMPLE_RATE}Hz")
                        return
                    }
                    monoRec.release()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to initialize microphone AudioRecord", t)
            }
        }

        fun start(coordinator: MuxerCoordinator) {
            val codec = audioEncoder ?: return
            running.set(true)
            val dThread = Thread({ drainAudioLoop(codec, coordinator) }, "BDSA-AudioDrain")
            val fThread = Thread({ feedAudioPcmLoop(codec) }, "BDSA-AudioFeed")
            drainThread = dThread
            feedThread = fThread
            dThread.start()
            fThread.start()
        }

        private fun readBlockingShorts(
            rec: AudioRecord,
            dest: ShortArray,
            targetCount: Int
        ): Int {
            var offset = 0
            while (offset < targetCount && running.get()) {
                val read = try {
                    rec.read(dest, offset, targetCount - offset, AudioRecord.READ_BLOCKING)
                } catch (_: Throwable) {
                    -1
                }
                if (read <= 0) break
                offset += read
            }
            return offset
        }

        private fun readLoopbackShorts(
            rec: AudioRecord,
            dest: ShortArray,
            targetCount: Int,
            waitForPartialMs: Long
        ): Int {
            var offset = 0
            val deadlineNs = System.nanoTime() + waitForPartialMs * 1_000_000L
            while (offset < targetCount && running.get()) {
                val read = try {
                    rec.read(dest, offset, targetCount - offset, AudioRecord.READ_NON_BLOCKING)
                } catch (_: Throwable) {
                    -1
                }
                if (read < 0) break
                if (read > 0) {
                    offset += read
                    if (offset >= targetCount) break
                } else {
                    // If no samples arrived at all yet (silence), return immediately on this tick.
                    // If partial samples arrived, wait briefly for AudioFlinger to finish the 20ms period.
                    if (offset == 0 || System.nanoTime() >= deadlineNs) {
                        break
                    }
                    try {
                        Thread.sleep(1L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            return offset
        }

        private fun feedAudioPcmLoop(codec: MediaCodec) {
            val mixShorts = ShortArray(AUDIO_CHUNK_SHORTS_STEREO)
            val scratchStereo = ShortArray(AUDIO_CHUNK_SHORTS_STEREO)
            val scratchMono = ShortArray(AUDIO_CHUNK_FRAMES)
            val byteChunk = ByteBuffer.allocateDirect(AUDIO_CHUNK_BYTES_STEREO)
                .order(ByteOrder.nativeOrder())

            var totalFramesSubmitted = 0L
            var nextTickNs = System.nanoTime()

            while (running.get()) {
                val mic = micRecord
                val internal = internalRecord

                if (mic != null) {
                    // Hardware microphone ADC clock naturally paces each 20ms (960-frame) chunk
                    mixShorts.fill(0)
                    if (internal != null) {
                        readLoopbackShorts(
                            rec = internal,
                            dest = mixShorts,
                            targetCount = AUDIO_CHUNK_SHORTS_STEREO,
                            waitForPartialMs = 6L
                        )
                    }
                    if (micIsMono) {
                        scratchMono.fill(0)
                        val readMono = readBlockingShorts(mic, scratchMono, AUDIO_CHUNK_FRAMES)
                        if (readMono > 0) {
                            scratchStereo.fill(0)
                            val stereoCount = upmixMonoToStereoPcm16(scratchMono, readMono, scratchStereo)
                            mixStereoPcm16InPlace(mixShorts, scratchStereo, stereoCount)
                        }
                    } else {
                        scratchStereo.fill(0)
                        val readStereo = readBlockingShorts(mic, scratchStereo, AUDIO_CHUNK_SHORTS_STEREO)
                        if (readStereo > 0) {
                            mixStereoPcm16InPlace(mixShorts, scratchStereo, readStereo)
                        }
                    }
                } else {
                    val nowNs = System.nanoTime()
                    val waitNs = nextTickNs - nowNs
                    if (waitNs > 1_000_000L) {
                        try {
                            Thread.sleep(waitNs / 1_000_000L, (waitNs % 1_000_000L).toInt())
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                    nextTickNs += AUDIO_TICK_NS
                    if (System.nanoTime() - nextTickNs > AUDIO_TICK_NS * 4) {
                        nextTickNs = System.nanoTime()
                    }

                    mixShorts.fill(0)
                    if (internal != null) {
                        readLoopbackShorts(
                            rec = internal,
                            dest = mixShorts,
                            targetCount = AUDIO_CHUNK_SHORTS_STEREO,
                            waitForPartialMs = 18L
                        )
                    }
                }

                val inIdx = try {
                    codec.dequeueInputBuffer(15_000L)
                } catch (_: Throwable) {
                    break
                }
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        byteChunk.clear()
                        byteChunk.asShortBuffer().put(mixShorts)
                        byteChunk.limit(AUDIO_CHUNK_BYTES_STEREO)
                        inBuf.clear()
                        inBuf.put(byteChunk)
                        val ptsUs = (totalFramesSubmitted * 1_000_000L) / AUDIO_SAMPLE_RATE
                        try {
                            codec.queueInputBuffer(inIdx, 0, AUDIO_CHUNK_BYTES_STEREO, ptsUs, 0)
                            totalFramesSubmitted += AUDIO_CHUNK_FRAMES
                        } catch (_: Throwable) {
                            break
                        }
                    }
                }
            }

            // Submit audio EOS
            try {
                val eosIdx = codec.dequeueInputBuffer(25_000L)
                if (eosIdx >= 0) {
                    val ptsUs = (totalFramesSubmitted * 1_000_000L) / AUDIO_SAMPLE_RATE
                    codec.queueInputBuffer(eosIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            } catch (_: Throwable) {
            }
        }

        private fun drainAudioLoop(codec: MediaCodec, coordinator: MuxerCoordinator) {
            val info = MediaCodec.BufferInfo()
            val ptsNormalizer = MonotonicPtsNormalizer()
            while (true) {
                val outIdx = try {
                    codec.dequeueOutputBuffer(info, 25_000L)
                } catch (_: Throwable) {
                    break
                }
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!running.get() && feedThread?.isAlive != true) {
                            val retry = try {
                                codec.dequeueOutputBuffer(info, 40_000L)
                            } catch (_: Throwable) {
                                break
                            }
                            if (retry < 0) break
                            if (handleAudioOutput(codec, retry, info, ptsNormalizer, coordinator)) break
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        try {
                            coordinator.onTrackFormatReady(isVideo = false, format = codec.outputFormat)
                        } catch (_: Throwable) {
                            break
                        }
                    }
                    outIdx >= 0 -> {
                        if (handleAudioOutput(codec, outIdx, info, ptsNormalizer, coordinator)) break
                    }
                }
            }
        }

        private fun handleAudioOutput(
            codec: MediaCodec,
            outIdx: Int,
            info: MediaCodec.BufferInfo,
            ptsNormalizer: MonotonicPtsNormalizer,
            coordinator: MuxerCoordinator
        ): Boolean {
            var sawEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            try {
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0
                }
                if (info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null) {
                        val pts = ptsNormalizer.normalize(info.presentationTimeUs)
                        coordinator.writeSample(
                            isVideo = false,
                            buffer = outBuf,
                            offset = info.offset,
                            size = info.size,
                            presentationTimeUs = pts,
                            flags = info.flags
                        )
                    }
                }
            } catch (_: Throwable) {
                sawEos = true
            } finally {
                try {
                    codec.releaseOutputBuffer(outIdx, false)
                } catch (_: Throwable) {
                }
            }
            return sawEos
        }

        fun stopAndJoin() {
            running.set(false)
            try {
                feedThread?.join(400L)
            } catch (_: InterruptedException) {
            } finally {
                feedThread = null
            }
            try {
                drainThread?.join(500L)
            } catch (_: InterruptedException) {
            } finally {
                drainThread = null
            }
            releaseAudioInputs()
            try {
                audioEncoder?.stop()
            } catch (_: Throwable) {
            }
            try {
                audioEncoder?.release()
            } catch (_: Throwable) {
            } finally {
                audioEncoder = null
            }
        }

        private fun releaseAudioInputs() {
            val callingToken = Binder.clearCallingIdentity()
            try {
                try {
                    internalRecord?.stop()
                } catch (_: Throwable) {
                }
                try {
                    internalRecord?.release()
                } catch (_: Throwable) {
                } finally {
                    internalRecord = null
                }

                val am = audioManager
                val pol = audioPolicy
                audioManager = null
                audioPolicy = null
                if (pol != null) {
                    unregisterPolicyQuietly(am, pol)
                }

                try {
                    micRecord?.stop()
                } catch (_: Throwable) {
                }
                try {
                    micRecord?.release()
                } catch (_: Throwable) {
                } finally {
                    micRecord = null
                }
            } finally {
                Binder.restoreCallingIdentity(callingToken)
            }
        }

        private fun unregisterPolicyQuietly(am: AudioManager?, policy: Any) {
            try {
                val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                if (am != null) {
                    val unreg = AudioManager::class.java.methods.firstOrNull { m ->
                        (m.name == "unregisterAudioPolicyAsync" || m.name == "unregisterAudioPolicy") &&
                            m.parameterTypes.size == 1 &&
                            m.parameterTypes[0] == policyClass
                    }
                    if (unreg != null) {
                        unreg.invoke(am, policy)
                        return
                    }
                }
                val unregStatic = AudioManager::class.java.getDeclaredMethod(
                    "unregisterAudioPolicyAsyncStatic",
                    policyClass
                )
                unregStatic.isAccessible = true
                unregStatic.invoke(null, policy)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to unregister AudioPolicy", t)
            }
        }
    }

    private interface DisplayMirrorHandle {
        fun release()
    }

    private fun createDisplayMirror(
        name: String,
        spec: DisplayMirrorSpec,
        outputSurface: Surface
    ): DisplayMirrorHandle? {
        // Primary path: direct SurfaceFlinger virtual display via SurfaceControl (zero DisplayManager side effects)
        createSurfaceControlMirror(name, spec, outputSurface)?.let { return it }

        // Fallback path: non-public VirtualDisplayConfig with setDisplayIdToMirror
        return createVirtualDisplayMirrorFallback(name, spec, outputSurface)
    }

    private fun createSurfaceControlMirror(
        name: String,
        spec: DisplayMirrorSpec,
        outputSurface: Surface
    ): DisplayMirrorHandle? {
        return try {
            val scClass = Class.forName("android.view.SurfaceControl")
            val tokenAndDestroy: Pair<IBinder, (IBinder) -> Unit> = try {
                val createMethod = scClass.getMethod(
                    "createDisplay",
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )
                val destroyMethod = scClass.getMethod("destroyDisplay", IBinder::class.java)
                val token = createMethod.invoke(null, name, false) as? IBinder ?: return null
                token to { t -> destroyMethod.invoke(null, t) }
            } catch (_: NoSuchMethodException) {
                val dcClass = loadDisplayControlClass() ?: return null
                try {
                    // Android 15 / 16 (SDK 35+): DisplayControl.createVirtualDisplay / destroyVirtualDisplay
                    val createVdMethod = dcClass.getMethod(
                        "createVirtualDisplay",
                        String::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    val destroyVdMethod = dcClass.getMethod("destroyVirtualDisplay", IBinder::class.java)
                    val token = createVdMethod.invoke(null, name, false) as? IBinder ?: return null
                    token to { t -> destroyVdMethod.invoke(null, t) }
                } catch (_: NoSuchMethodException) {
                    // Android 14 (SDK 34): DisplayControl.createDisplay / destroyDisplay
                    val createMethod = dcClass.getMethod(
                        "createDisplay",
                        String::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    val destroyMethod = dcClass.getMethod("destroyDisplay", IBinder::class.java)
                    val token = createMethod.invoke(null, name, false) as? IBinder ?: return null
                    token to { t -> destroyMethod.invoke(null, t) }
                }
            }

            val (displayToken, destroyFn) = tokenAndDestroy
            val rect = Rect(0, 0, spec.width, spec.height)
            val configuredViaTransaction = try {
                val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
                val tx = txClass.getConstructor().newInstance()
                try {
                    txClass.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java)
                        .invoke(tx, displayToken, outputSurface)
                    txClass.getMethod(
                        "setDisplayProjection",
                        IBinder::class.java,
                        Int::class.javaPrimitiveType,
                        Rect::class.java,
                        Rect::class.java
                    ).invoke(tx, displayToken, 0, rect, rect)
                    txClass.getMethod(
                        "setDisplayLayerStack",
                        IBinder::class.java,
                        Int::class.javaPrimitiveType
                    ).invoke(tx, displayToken, spec.layerStack)
                    txClass.getMethod("apply").invoke(tx)
                    true
                } finally {
                    if (tx is AutoCloseable) {
                        try {
                            tx.close()
                        } catch (_: Throwable) {
                        }
                    }
                }
            } catch (_: Throwable) {
                false
            }

            if (!configuredViaTransaction) {
                val openTx = scClass.getMethod("openTransaction")
                val closeTx = scClass.getMethod("closeTransaction")
                val setDisplaySurface = scClass.getMethod(
                    "setDisplaySurface",
                    IBinder::class.java,
                    Surface::class.java
                )
                val setDisplayProjection = scClass.getMethod(
                    "setDisplayProjection",
                    IBinder::class.java,
                    Int::class.javaPrimitiveType,
                    Rect::class.java,
                    Rect::class.java
                )
                val setDisplayLayerStack = scClass.getMethod(
                    "setDisplayLayerStack",
                    IBinder::class.java,
                    Int::class.javaPrimitiveType
                )
                openTx.invoke(null)
                try {
                    setDisplaySurface.invoke(null, displayToken, outputSurface)
                    setDisplayProjection.invoke(null, displayToken, 0, rect, rect)
                    setDisplayLayerStack.invoke(null, displayToken, spec.layerStack)
                } finally {
                    closeTx.invoke(null)
                }
            }

            object : DisplayMirrorHandle {
                override fun release() {
                    try {
                        destroyFn(displayToken)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to destroy SurfaceControl display mirror $name", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "SurfaceControl mirror creation failed for display ${spec.displayId}", t)
            null
        }
    }

    private fun loadDisplayControlClass(): Class<*>? {
        return try {
            val factoryClass = Class.forName("com.android.internal.os.ClassLoaderFactory")
            val createLoader = factoryClass.getDeclaredMethod(
                "createClassLoader",
                String::class.java,
                String::class.java,
                String::class.java,
                ClassLoader::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java
            )
            createLoader.isAccessible = true
            val loader = createLoader.invoke(
                null,
                "/system/framework/services.jar",
                null,
                null,
                ClassLoader.getSystemClassLoader(),
                0,
                true,
                null
            ) as? ClassLoader ?: return null
            loader.loadClass("com.android.server.display.DisplayControl")
        } catch (_: Throwable) {
            null
        }
    }

    private fun createVirtualDisplayMirrorFallback(
        name: String,
        spec: DisplayMirrorSpec,
        outputSurface: Surface
    ): DisplayMirrorHandle? {
        val ctx = contextProvider() ?: return null
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        return try {
            val configClass = Class.forName("android.hardware.display.VirtualDisplayConfig")
            val builderClass = Class.forName("android.hardware.display.VirtualDisplayConfig\$Builder")
            val builder = builderClass.getConstructor(
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).newInstance(name, spec.width, spec.height, spec.densityDpi)

            builderClass.getMethod("setSurface", Surface::class.java).invoke(builder, outputSurface)
            builderClass.getMethod("setFlags", Int::class.javaPrimitiveType)
                .invoke(builder, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
            try {
                builderClass.getMethod("setDisplayIdToMirror", Int::class.javaPrimitiveType)
                    .invoke(builder, spec.displayId)
            } catch (_: Throwable) {
            }
            val vdConfig = builderClass.getMethod("build").invoke(builder)

            val createVd = DisplayManager::class.java.methods.firstOrNull { m ->
                m.name == "createVirtualDisplay" &&
                    m.parameterTypes.isNotEmpty() &&
                    m.parameterTypes[0] == configClass
            } ?: return null
            val virtualDisplay = when (createVd.parameterTypes.size) {
                1 -> createVd.invoke(dm, vdConfig)
                3 -> createVd.invoke(dm, vdConfig, null, null)
                else -> null
            } as? android.hardware.display.VirtualDisplay ?: return null

            object : DisplayMirrorHandle {
                override fun release() {
                    try {
                        virtualDisplay.release()
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "VirtualDisplay fallback mirror failed for display ${spec.displayId}", t)
            null
        }
    }

    /**
     * OpenGL ES 2.0 dual-display compositor stacking Display 0 (`1920x1080`) above centered
     * Display 4 (`1240x1080`) onto a `1920x2160` hardware encoder input [Surface].
     */
    private inner class DualScreenGlCompositor(
        private val topSpec: DisplayMirrorSpec,
        private val bottomSpec: DisplayMirrorSpec,
        private val fps: Int,
        private val encoderSurface: Surface
    ) {
        private val layout = computeDualScreenViewportLayout(
            topSpec.width,
            topSpec.height,
            bottomSpec.width,
            bottomSpec.height
        )
        private val minFrameIntervalNs: Long = 1_000_000_000L / fps.coerceIn(15, 60)
        private val keepAliveIntervalMs: Long = 100L

        private var glThread: HandlerThread? = null
        private var glHandler: Handler? = null
        private val released = AtomicBoolean(false)

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var programId: Int = 0
        private var aPositionLoc: Int = -1
        private var aTexCoordLoc: Int = -1
        private var uTexMatrixLoc: Int = -1

        private var topTexId: Int = 0
        private var bottomTexId: Int = 0
        private var topSurfaceTexture: SurfaceTexture? = null
        private var bottomSurfaceTexture: SurfaceTexture? = null
        private var topInputSurface: Surface? = null
        private var bottomInputSurface: Surface? = null
        private var topMirror: DisplayMirrorHandle? = null
        private var bottomMirror: DisplayMirrorHandle? = null

        private val topTexMatrix = FloatArray(16)
        private val bottomTexMatrix = FloatArray(16)
        private var topDirty: Boolean = false
        private var bottomDirty: Boolean = false
        private var hasTopFrame: Boolean = false
        private var hasBottomFrame: Boolean = false
        private var drawScheduled: Boolean = false
        private var lastDrawNs: Long = 0L
        private var baseNs: Long = 0L

        private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

        private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
                position(0)
            }

        private val drawRunnable: Runnable = Runnable {
            drawScheduled = false
            if (!released.get()) {
                renderCompositeFrame()
            }
        }

        private val keepAliveRunnable: Runnable = object : Runnable {
            override fun run() {
                if (released.get()) return
                val now = System.nanoTime()
                if (now - lastDrawNs >= keepAliveIntervalMs * 1_000_000L) {
                    renderCompositeFrame()
                }
                glHandler?.postDelayed(this, keepAliveIntervalMs)
            }
        }

        fun start(): Boolean {
            val thread = HandlerThread("BDSA-GlCompositor")
            thread.start()
            val handler = Handler(thread.looper)
            glThread = thread
            glHandler = handler

            val initLatch = CountDownLatch(1)
            var initOk = false
            handler.post {
                initOk = initGlAndMirrorsOnGlThread(handler)
                initLatch.countDown()
            }
            try {
                initLatch.await(2500L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
            return initOk
        }

        private fun initGlAndMirrorsOnGlThread(handler: Handler): Boolean {
            try {
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
                val version = IntArray(2)
                if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) ||
                    numConfigs[0] <= 0
                ) {
                    return false
                }
                val config = configs[0] ?: return false

                val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                eglContext = EGL14.eglCreateContext(
                    eglDisplay,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    ctxAttribs,
                    0
                )
                if (eglContext == EGL14.EGL_NO_CONTEXT) return false

                val surfAttribs = intArrayOf(EGL14.EGL_NONE)
                eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    config,
                    encoderSurface,
                    surfAttribs,
                    0
                )
                if (eglSurface == EGL14.EGL_NO_SURFACE) return false
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false

                programId = buildOesShaderProgram()
                if (programId == 0) return false
                aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
                aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
                uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")

                val texIds = IntArray(2)
                GLES20.glGenTextures(2, texIds, 0)
                topTexId = texIds[0]
                bottomTexId = texIds[1]
                configureExternalOesTexture(topTexId)
                configureExternalOesTexture(bottomTexId)

                val topSt = SurfaceTexture(topTexId).apply {
                    setDefaultBufferSize(topSpec.width, topSpec.height)
                    setOnFrameAvailableListener({
                        topDirty = true
                        scheduleFrameRender()
                    }, handler)
                }
                val botSt = SurfaceTexture(bottomTexId).apply {
                    setDefaultBufferSize(bottomSpec.width, bottomSpec.height)
                    setOnFrameAvailableListener({
                        bottomDirty = true
                        scheduleFrameRender()
                    }, handler)
                }
                val topSurf = Surface(topSt)
                val botSurf = Surface(botSt)
                topSurfaceTexture = topSt
                bottomSurfaceTexture = botSt
                topInputSurface = topSurf
                bottomInputSurface = botSurf

                val mTop = createDisplayMirror("bdsa_rec_dual_0", topSpec, topSurf) ?: return false
                topMirror = mTop
                val mBot = createDisplayMirror("bdsa_rec_dual_4", bottomSpec, botSurf) ?: return false
                bottomMirror = mBot

                baseNs = System.nanoTime()
                handler.postDelayed(keepAliveRunnable, keepAliveIntervalMs)
                return true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed initializing DualScreenGlCompositor", t)
                return false
            }
        }

        private fun scheduleFrameRender() {
            if (released.get() || drawScheduled) return
            val handler = glHandler ?: return
            val nowNs = System.nanoTime()
            val elapsedNs = nowNs - lastDrawNs
            drawScheduled = true
            if (lastDrawNs == 0L || elapsedNs >= minFrameIntervalNs) {
                handler.post(drawRunnable)
            } else {
                val delayMs = ((minFrameIntervalNs - elapsedNs) / 1_000_000L).coerceAtLeast(1L)
                handler.postDelayed(drawRunnable, delayMs)
            }
        }

        private fun renderCompositeFrame() {
            if (released.get() || eglDisplay == EGL14.EGL_NO_DISPLAY) return
            try {
                if (topDirty) {
                    topDirty = false
                    topSurfaceTexture?.updateTexImage()
                    topSurfaceTexture?.getTransformMatrix(topTexMatrix)
                    hasTopFrame = true
                }
                if (bottomDirty) {
                    bottomDirty = false
                    bottomSurfaceTexture?.updateTexImage()
                    bottomSurfaceTexture?.getTransformMatrix(bottomTexMatrix)
                    hasBottomFrame = true
                }
                if (!hasTopFrame && !hasBottomFrame) return

                val nowNs = System.nanoTime()
                lastDrawNs = nowNs

                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glUseProgram(programId)

                vertexBuffer.position(0)
                GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                GLES20.glEnableVertexAttribArray(aPositionLoc)

                texCoordBuffer.position(0)
                GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
                GLES20.glEnableVertexAttribArray(aTexCoordLoc)

                if (hasTopFrame) {
                    val vp = layout.topViewport
                    GLES20.glViewport(vp.x, vp.y, vp.width, vp.height)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, topTexId)
                    GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, topTexMatrix, 0)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                }

                if (hasBottomFrame) {
                    val vp = layout.bottomViewport
                    GLES20.glViewport(vp.x, vp.y, vp.width, vp.height)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, bottomTexId)
                    GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, bottomTexMatrix, 0)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                }

                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
                val ptsNs = (nowNs - baseNs).coerceAtLeast(0L)
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            } catch (t: Throwable) {
                Log.w(TAG, "Error rendering dual-screen GL frame", t)
            }
        }

        fun release() {
            if (!released.compareAndSet(false, true)) return
            val handler = glHandler
            val thread = glThread
            if (handler != null && thread != null && thread.isAlive) {
                val latch = CountDownLatch(1)
                handler.post {
                    teardownOnGlThread()
                    latch.countDown()
                }
                try {
                    latch.await(1000L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                }
                thread.quitSafely()
                try {
                    thread.join(500L)
                } catch (_: InterruptedException) {
                }
            }
            glHandler = null
            glThread = null
        }

        private fun teardownOnGlThread() {
            glHandler?.removeCallbacksAndMessages(null)
            try {
                topMirror?.release()
            } catch (_: Throwable) {
            } finally {
                topMirror = null
            }
            try {
                bottomMirror?.release()
            } catch (_: Throwable) {
            } finally {
                bottomMirror = null
            }

            try {
                topSurfaceTexture?.setOnFrameAvailableListener(null)
                bottomSurfaceTexture?.setOnFrameAvailableListener(null)
            } catch (_: Throwable) {
            }
            try {
                topInputSurface?.release()
            } catch (_: Throwable) {
            } finally {
                topInputSurface = null
            }
            try {
                bottomInputSurface?.release()
            } catch (_: Throwable) {
            } finally {
                bottomInputSurface = null
            }
            try {
                topSurfaceTexture?.release()
            } catch (_: Throwable) {
            } finally {
                topSurfaceTexture = null
            }
            try {
                bottomSurfaceTexture?.release()
            } catch (_: Throwable) {
            } finally {
                bottomSurfaceTexture = null
            }

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                try {
                    val texIds = intArrayOf(topTexId, bottomTexId)
                    GLES20.glDeleteTextures(2, texIds, 0)
                    if (programId != 0) {
                        GLES20.glDeleteProgram(programId)
                        programId = 0
                    }
                } catch (_: Throwable) {
                }
                try {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                        eglSurface = EGL14.EGL_NO_SURFACE
                    }
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                        eglContext = EGL14.EGL_NO_CONTEXT
                    }
                    EGL14.eglTerminate(eglDisplay)
                } catch (_: Throwable) {
                } finally {
                    eglDisplay = EGL14.EGL_NO_DISPLAY
                }
            }
        }

        private fun configureExternalOesTexture(texId: Int) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }

        private fun buildOesShaderProgram(): Int {
            val vertexSrc = """
                attribute vec4 aPosition;
                attribute vec4 aTexCoord;
                uniform mat4 uTexMatrix;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = (uTexMatrix * aTexCoord).xy;
                }
            """.trimIndent()

            val fragmentSrc = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTexCoord);
                }
            """.trimIndent()

            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
            if (vs == 0) return 0
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            if (fs == 0) {
                GLES20.glDeleteShader(vs)
                return 0
            }
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "GLES program link error: ${GLES20.glGetProgramInfoLog(prog)}")
                GLES20.glDeleteProgram(prog)
                return 0
            }
            return prog
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) return 0
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "GLES shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }
    }
}
