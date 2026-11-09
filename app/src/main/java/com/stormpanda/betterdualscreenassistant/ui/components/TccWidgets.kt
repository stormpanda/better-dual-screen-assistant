package com.stormpanda.betterdualscreenassistant.ui.components

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccUiLook
import com.stormpanda.betterdualscreenassistant.ui.theme.TccUiLook
import com.stormpanda.betterdualscreenassistant.ui.theme.contrastingContentColor
import com.stormpanda.betterdualscreenassistant.ui.theme.retroidContentOnAccent
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDisplayFontFamily
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDisplayTextStyle
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import com.stormpanda.betterdualscreenassistant.ui.theme.tccFontFamily
import com.stormpanda.betterdualscreenassistant.ui.theme.tccTextStyle
import kotlin.math.min as mathMin
import kotlin.math.roundToInt

/**
 * Crisp 1-step 90-degree inward corner cutout [Shape] matching Stock Retroid's `O0/a.java`
 * (`TccButtonView`, `120 FPS MODE` badge, and dialog checkboxes).
 */
class PixelCornerCutShape(private val cutSize: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val rawCut = with(density) { cutSize.toPx() }
        val cut = rawCut.coerceIn(0f, mathMin(w, h) * 0.35f).roundToInt().toFloat()
        if (cut < 1f) {
            return Outline.Rectangle(Rect(0f, 0f, w, h))
        }
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(w - cut, 0f)
            lineTo(w - cut, cut)
            lineTo(w, cut)
            lineTo(w, h - cut)
            lineTo(w - cut, h - cut)
            lineTo(w - cut, h)
            lineTo(cut, h)
            lineTo(cut, h - cut)
            lineTo(0f, h - cut)
            lineTo(0f, cut)
            lineTo(cut, cut)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Crisp 2-step pixel-notched corner [Shape] matching Retroid's `PIXEL_NOTCHED` (`sliding/h.java`,
 * `P0/c.java`) geometry so `.clip(...)` and `.border(...)` render sharp stepped pixel corners
 * across every theme palette.
 */
class PixelNotchedShape(private val notchSize: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val rawNotch = with(density) { notchSize.toPx() }
        val n = rawNotch.coerceIn(0f, mathMin(w, h) * 0.35f)
        if (n < 1f) {
            return Outline.Rectangle(Rect(0f, 0f, w, h))
        }
        val step = (n * 0.5f).roundToInt().coerceAtLeast(1).toFloat()
        val inset = step * 2f
        val path = Path().apply {
            // Top edge (left -> right)
            moveTo(inset, 0f)
            lineTo(w - inset, 0f)
            // Top-right 2-step pixel notch
            lineTo(w - inset, step)
            lineTo(w - step, step)
            lineTo(w - step, inset)
            lineTo(w, inset)
            // Right edge
            lineTo(w, h - inset)
            // Bottom-right 2-step pixel notch
            lineTo(w - step, h - inset)
            lineTo(w - step, h - step)
            lineTo(w - inset, h - step)
            lineTo(w - inset, h)
            // Bottom edge
            lineTo(inset, h)
            // Bottom-left 2-step pixel notch
            lineTo(inset, h - step)
            lineTo(step, h - step)
            lineTo(step, h - inset)
            lineTo(0f, h - inset)
            // Left edge
            lineTo(0f, inset)
            // Top-left 2-step pixel notch
            lineTo(step, inset)
            lineTo(step, step)
            lineTo(inset, step)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Replicates `TCCButtonView` (`com.odin.dualscreen.assistant.views.wiget.TCCButtonView`):
 * - Fixed stock dimensions derived from `ScreenUtil.g() * 0.135f` (`145px = 72.5dp` on 1080p height):
 *   - `buttonWidth = 72.5.dp`
 *   - `topMargin = 8.dp`
 *   - `circleDiameter = 50.5.dp` (`145px * (1 - 2 * 0.152)`)
 *   - `iconSize = 26.dp` (`circleDiameter - 2 * 12.4dp`)
 *   - `titleHeight = 29.dp` (`145px * 0.4f`)
 *   - `titleTextSize = 11.sp` (`29dp * 0.38f`)
 */
@Composable
fun TccButton(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    retroidTileSize: Dp = 52.5.dp,
    retroidLabelWidth: Dp = 132.dp,
    retroidCompactVertical: Boolean = false
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val iconTint = if (isRetroidLook) {
        if (selected) {
            colors.retroidContentOnAccent()
        } else {
            colors.secondary
        }
    } else if (selected) {
        if (colors.isMegingiardStyle) {
            colors.onAccent
        } else if (colors.isDark) {
            Color.White
        } else {
            colors.primary
        }
    } else {
        colors.secondary
    }
    val textColor = if (selected) colors.primary else colors.secondary
    val outerShape = RoundedCornerShape(10.dp)
    val buttonShape: Shape = if (isRetroidLook) PixelCornerCutShape(9.dp) else CircleShape
    val columnWidth = if (isRetroidLook) retroidLabelWidth else 72.5.dp
    val tileBoxSize = if (isRetroidLook) retroidTileSize else 50.5.dp
    val iconBoxSize = if (isRetroidLook) (retroidTileSize * 0.56f) else 26.dp
    val topGap = if (isRetroidLook) {
        if (retroidCompactVertical) 3.dp else 5.dp
    } else {
        8.dp
    }
    val labelTopGap = if (isRetroidLook) {
        when {
            retroidCompactVertical -> 5.dp
            retroidTileSize >= 60.dp -> 12.dp
            else -> 8.dp
        }
    } else {
        0.dp
    }
    val titleBoxHeight = if (isRetroidLook) {
        when {
            retroidCompactVertical -> 22.dp
            retroidTileSize >= 60.dp -> 26.dp
            else -> 36.dp
        }
    } else {
        29.dp
    }
    val labelFontSize = if (isRetroidLook) {
        if (retroidTileSize >= 60.dp) 15.sp else 14.sp
    } else {
        11.sp
    }
    val labelLineHeight = if (isRetroidLook) {
        if (retroidTileSize >= 60.dp) 18.sp else 16.5.sp
    } else {
        12.5.sp
    }

    Box(
        modifier = modifier
            .clip(outerShape)
            .background(if (isFocused) colors.panelBackgroundSelected else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                    (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(columnWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(topGap))

            Box(
                modifier = Modifier
                    .size(tileBoxSize)
                    .clip(buttonShape)
                    .then(
                        if (isRetroidLook) {
                            if (selected) {
                                Modifier.background(colors.accent)
                            } else {
                                Modifier.background(colors.fifth)
                            }
                        } else if (selected) {
                            if (colors.isMegingiardStyle) {
                                Modifier.background(colors.accent)
                            } else if (colors.isDark) {
                                Modifier
                                    .background(Color(0xFF272727))
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to Color.Transparent,
                                            0.5f to Color(0x1A9E21BE),
                                            1.0f to Color(0x66AB24CE)
                                        )
                                    )
                            } else {
                                Modifier.background(Color(0xFFD0D0D0))
                            }
                        } else {
                            Modifier.background(colors.fifth)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = tccDrawable(iconRes)),
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(iconTint),
                    modifier = Modifier.size(iconBoxSize),
                    contentScale = ContentScale.Fit
                )
            }

            if (labelTopGap > 0.dp) {
                Spacer(modifier = Modifier.height(labelTopGap))
            }

            Box(
                modifier = Modifier
                    .width(columnWidth)
                    .height(titleBoxHeight)
                    .padding(horizontal = 2.dp),
                contentAlignment = if (isRetroidLook && !retroidCompactVertical) {
                    Alignment.TopCenter
                } else {
                    Alignment.Center
                }
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = labelFontSize,
                    lineHeight = labelLineHeight,
                    fontFamily = pixelFont,
                    style = pixelStyle,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = if (isRetroidLook && retroidCompactVertical) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

enum class SliderKind {
    BRIGHTNESS,
    VOLUME,
    LED_BRIGHTNESS
}

/**
 * Replicates `C0501a` (`x0.a`):
 * - Sits inside the brightness/volume panel card (transparent unless gamepad-focused).
 * - Left title: `width = 40.dp`, `marginStart = 5.dp`, 2 lines centered, `11.3.sp`, `colors.primary`.
 * - Track (`bVar`): `weight(1f)`, `fillMaxHeight()`, `padding(8.dp)`, `RoundedCornerShape(15.dp)`, `colors.fifth`.
 * - Progress fill: straight vertical right edge clipped by the `15.dp` rounded capsule track (`colors.tertiary`).
 * - Icon inside left of track: `height = 55%` of track height, `start = 8.dp`, tinted `colors.primary`.
 */
@Composable
fun HorizontalSlidingBar(
    label: String,
    progress: Int,
    kind: SliderKind,
    onProgressChange: (newProgress: Int, temporary: Boolean) -> Unit,
    isFillDimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()
    var isFocused by remember { mutableStateOf(false) }
    var dragProgress by remember(progress) { mutableFloatStateOf(progress.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    val displayProgress = if (isDragging) dragProgress.roundToInt() else progress
    val effectiveFillDimmed = isFillDimmed && !isDragging

    @DrawableRes
    val rawIconRes = when (kind) {
        SliderKind.BRIGHTNESS -> when {
            displayProgress > 50 -> R.drawable.ic_brightness_2
            displayProgress > 0 -> R.drawable.ic_brightness_1
            else -> R.drawable.ic_brightness_0
        }
        SliderKind.VOLUME -> when {
            displayProgress <= 0 -> R.drawable.ic_volume_0
            displayProgress <= 33 -> R.drawable.ic_volume_1
            displayProgress <= 66 -> R.drawable.ic_volume_2
            else -> R.drawable.ic_volume_3
        }
        SliderKind.LED_BRIGHTNESS -> R.drawable.ic_qs_ambient_light
    }

    val controlShape: Shape = if (isRetroidLook) RoundedCornerShape(12.dp) else RoundedCornerShape(15.dp)
    val trackShape: Shape = if (isRetroidLook) PixelNotchedShape(9.dp) else RoundedCornerShape(15.dp)
    val trackBackground = if (isRetroidLook && !colors.isMegingiardStyle && colors.isDark) {
        Color(0xFF161616)
    } else {
        colors.fifth
    }

    Row(
        modifier = modifier
            .clip(controlShape)
            .background(if (isFocused) colors.panelBackgroundSelected else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_L1 -> {
                            val next = (displayProgress - 10).coerceIn(0, 100)
                            onProgressChange(next, false)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_R1 -> {
                            val next = (displayProgress + 10).coerceIn(0, 100)
                            onProgressChange(next, false)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (isRetroidLook) 2.dp else 5.dp)
                .width(if (isRetroidLook) 56.dp else 42.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = colors.primary,
                fontSize = if (isRetroidLook) 14.sp else 11.3.sp,
                lineHeight = if (isRetroidLook) 16.5.sp else 13.sp,
                fontFamily = pixelFont,
                style = pixelStyle,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    horizontal = if (isRetroidLook) 10.dp else 8.dp,
                    vertical = if (isRetroidLook) 9.5.dp else 8.dp
                )
                .clip(trackShape)
                .background(trackBackground)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val pct = (ratio * 100f).roundToInt()
                        dragProgress = pct.toFloat()
                        onProgressChange(pct, false)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragProgress = ratio * 100f
                            onProgressChange(dragProgress.roundToInt(), true)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragProgress = ratio * 100f
                            onProgressChange(dragProgress.roundToInt(), true)
                        },
                        onDragEnd = {
                            isDragging = false
                            onProgressChange(dragProgress.roundToInt(), false)
                        },
                        onDragCancel = {
                            isDragging = false
                            onProgressChange(dragProgress.roundToInt(), false)
                        }
                    )
                }
        ) {
            val trackWidth = maxWidth
            val trackHeight = maxHeight
            val fraction = (displayProgress / 100f).coerceIn(0f, 1f)

            val fillColor = if (effectiveFillDimmed) {
                if (isRetroidLook && !colors.isMegingiardStyle && colors.isDark) {
                    Color(0xFF242424)
                } else if (colors.isDark) {
                    Color.White.copy(alpha = 0.18f)
                } else {
                    Color.Black.copy(alpha = 0.10f)
                }
            } else {
                colors.sliderFill
            }
            Box(
                modifier = Modifier
                    .width(trackWidth * fraction)
                    .fillMaxHeight()
                    .then(if (isRetroidLook) Modifier.clip(trackShape) else Modifier)
                    .background(fillColor)
            )

            val iconSize = trackHeight * (if (isRetroidLook) 0.58f else 0.55f)
            val sliderIconTint = when {
                !effectiveFillDimmed && colors.isMegingiardStyle && displayProgress >= 12 -> {
                    if (isRetroidLook) {
                        colors.retroidContentOnAccent()
                    } else {
                        colors.onAccent
                    }
                }
                isRetroidLook && displayProgress <= 0 -> colors.secondary.copy(alpha = 0.75f)
                else -> colors.primary
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = if (isRetroidLook) 10.dp else 7.5.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Image(
                    painter = painterResource(id = tccDrawable(rawIconRes)),
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(sliderIconTint),
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Replicates `A0.e` (`CpuTempInfoComponent` & `FanInfoComponent` in `hardware_info_panel_layout_2.xml`):
 * - Rounded card (`20.dp` radius, `colors.panelBackground`)
 * - Top-left (`20%` x, `20%` y): icon (`minDim * iconSizeRate`)
 * - Top-right (`right = 20%` x, centered at `30%` y): title (`"TEMP"` or `"FAN"`, `colors.tertiary`, `minDim * 0.12f`)
 * - Bottom-left (`left = 20%` x, centered at `70%` y): value + unit (`"44℃"` or `"20%"`, `colors.primary`, `minDim * 0.25f`)
 */
@Composable
fun HardwareInfoComponentCard(
    title: String,
    valueWithUnit: String,
    @DrawableRes iconRes: Int,
    iconTint: Color,
    iconSizeRate: Float = 0.17f,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val labelFont = tccFontFamily()
    val labelStyle = tccTextStyle()
    val displayFont = tccDisplayFontFamily()
    val displayStyle = tccDisplayTextStyle()
    val cardShape: Shape = if (isRetroidLook) RoundedCornerShape(22.dp) else RoundedCornerShape(20.dp)
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .clip(cardShape)
            .background(
                if (onClick != null && isFocused) {
                    colors.panelBackgroundSelected
                } else {
                    colors.panelBackground
                }
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                                (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) {
                                onClick()
                                true
                            } else {
                                false
                            }
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
    ) {
        val density = LocalDensity.current
        val minDim = min(maxWidth, maxHeight)
        val iconSize = minDim * (if (isRetroidLook) iconSizeRate * 1.15f else iconSizeRate)
        val titleSp = with(density) { (minDim * (if (isRetroidLook) 0.135f else 0.12f)).toSp() }
        val valueSp = with(density) { (minDim * (if (isRetroidLook) 0.215f else 0.25f)).toSp() }

        val leftX = maxWidth * (if (isRetroidLook) 0.16f else 0.20f)
        val rightPad = maxWidth * (if (isRetroidLook) 0.16f else 0.20f)
        val iconTopY = maxHeight * 0.20f

        // Icon at (20% width, 20% height)
        Image(
            painter = painterResource(id = tccDrawable(iconRes)),
            contentDescription = title,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = Modifier
                .offset(x = leftX, y = iconTopY)
                .size(iconSize),
            contentScale = ContentScale.Fit
        )

        // Title ("TEMP" / "FAN") right-aligned at 80% width, vertically centered at 30% height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight * 0.60f)
                .padding(end = rightPad),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = title,
                color = colors.tertiary,
                fontSize = titleSp,
                fontFamily = labelFont,
                style = labelStyle,
                fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Medium,
                maxLines = 1
            )
        }

        // Value ("44℃" / "20%") left-aligned at 20% width, vertically centered at 70% height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight * 0.60f)
                .align(Alignment.BottomStart)
                .padding(start = leftX),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = valueWithUnit,
                color = colors.primary,
                fontSize = valueSp,
                fontFamily = displayFont,
                style = displayStyle,
                fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// Matches Android's default DecelerateInterpolator(1.0f) used in B0.d
private val RingDecelerateEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

/**
 * Replicates `B0.d` (`HardwareInfoRingView` for `CPU`, `GPU`, `PWR`, `RAM`):
 * - `strokeWidth = minDim * 0.05f`
 * - Track circle: `colors.fourth` (`#3B3B3B`)
 * - Progress arc: `ringColor.copy(alpha = 150f / 255f)` with `StrokeCap.Round`, animated over `300ms`
 *   with `DecelerateInterpolator` (`B0.d.setProgressWithAnimation`)
 * - Top indicator point at `-90°`: `Color.White`, diameter = `strokeWidth * 0.85f`
 * - Title at `15%` y (`minDim * 0.13f`, `colors.tertiary`)
 * - Value at `50%` y (`minDim * 0.25f`, `colors.primary`)
 * - Unit at `65%` y (`minDim * 0.13f`, `colors.tertiary`)
 */
@Composable
fun HardwareRingGauge(
    title: String,
    valueText: String,
    unitText: String,
    value: Float,
    minValue: Float,
    maxValue: Float,
    ringColor: Color,
    bidirectional: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val labelFont = tccFontFamily()
    val labelStyle = tccTextStyle()
    val displayFont = tccDisplayFontFamily()
    val displayStyle = tccDisplayTextStyle()
    val clampedTarget = value.coerceIn(minValue, maxValue)
    val animatedRingProgress = remember { Animatable(0f) }

    LaunchedEffect(clampedTarget) {
        if (animatedRingProgress.value != clampedTarget) {
            animatedRingProgress.animateTo(
                targetValue = clampedTarget,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = RingDecelerateEasing
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val rawSizeDp = min(maxWidth, maxHeight)
        val sizeDp = if (isRetroidLook) rawSizeDp * 0.90f else rawSizeDp
        val density = LocalDensity.current
        val titleSp = with(density) { (sizeDp * 0.13f).toSp() }
        val valueSp = with(density) { (sizeDp * (if (isRetroidLook) 0.185f else 0.25f)).toSp() }
        val unitSp = with(density) { (sizeDp * (if (isRetroidLook) 0.125f else 0.13f)).toSp() }

        Box(
            modifier = Modifier.size(sizeDp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.055f
                val halfStroke = strokeWidth * 0.5f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(halfStroke, halfStroke)

                // Track circle
                drawArc(
                    color = colors.fourth,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc (alpha = 150/255 in B0.d), using animatedRingProgress
                val ringProgress = animatedRingProgress.value.coerceIn(minValue, maxValue)
                val sweepAngle = if (!bidirectional) {
                    val denom = (maxValue - minValue).coerceAtLeast(0.001f)
                    ((ringProgress - minValue) / denom).coerceIn(0f, 1f) * 360f
                } else {
                    val median = 0f
                    if (ringProgress > median) {
                        ((ringProgress - median) / (maxValue - median).coerceAtLeast(0.001f)).coerceIn(0f, 1f) * 360f
                    } else if (ringProgress < median) {
                        -((median - ringProgress) / (median - minValue).coerceAtLeast(0.001f)).coerceIn(0f, 1f) * 360f
                    } else {
                        0f
                    }
                }

                if (sweepAngle != 0f) {
                    drawArc(
                        color = ringColor.copy(alpha = if (isRetroidLook && !colors.isMegingiardStyle) 215f / 255f else 150f / 255f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                val dotColor = if (colors.isMegingiardStyle) colors.primary else Color.White
                drawCircle(
                    color = dotColor,
                    radius = (strokeWidth * 0.85f) * 0.5f,
                    center = Offset(size.width * 0.5f, halfStroke)
                )
            }

            // Title at ~22% center Y
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizeDp * (if (isRetroidLook) 0.46f else 0.44f))
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = colors.tertiary,
                    fontSize = titleSp,
                    fontFamily = labelFont,
                    style = labelStyle,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
            }

            // Value at 50% center Y
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueText,
                    color = colors.primary,
                    fontSize = valueSp,
                    fontFamily = displayFont,
                    style = displayStyle,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
            }

            // Unit at ~72% center Y
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizeDp * (if (isRetroidLook) 0.48f else 0.52f))
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unitText,
                    color = colors.tertiary,
                    fontSize = unitSp,
                    fontFamily = labelFont,
                    style = labelStyle,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

data class TccDialogOption(
    val id: Int,
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val preserveIconColors: Boolean = false,
    val swatchColors: Pair<Color, Color>? = null
)

/**
 * Replicates `DialogOptionView` radio checkmark indicator (`accent` filled circle + contrasting check when selected,
 * or hollow ring in `primaryColor` when unselected).
 */
@Composable
fun TccRadioCheckIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    if (isRetroidLook) {
        val boxShape = PixelCornerCutShape(3.dp)
        val uncheckedBg = if (colors.isMegingiardStyle) {
            colors.primaryBackground
        } else if (colors.isDark) {
            Color.Black
        } else {
            colors.fourth
        }
        Box(
            modifier = modifier
                .clip(boxShape)
                .background(if (selected) colors.accent else uncheckedBg),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                val checkTint = colors.retroidContentOnAccent()
                Image(
                    painter = painterResource(id = R.drawable.rp_ic_dialog_option_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(checkTint),
                    modifier = Modifier.fillMaxSize(0.54f),
                    contentScale = ContentScale.Fit
                )
            }
        }
        return
    }
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        if (selected) {
            drawCircle(
                color = colors.accent,
                radius = radius
            )
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            val checkColor = if (colors.isMegingiardStyle) {
                colors.onAccent
            } else {
                Color.White
            }
            drawPath(
                path = path,
                color = checkColor,
                style = Stroke(
                    width = size.minDimension * 0.11f,
                    cap = StrokeCap.Round
                )
            )
        } else {
            drawCircle(
                color = colors.primary,
                radius = radius * 0.9f,
                style = Stroke(width = size.minDimension * 0.08f)
            )
        }
    }
}

/**
 * Replicates `com.odin.dualscreen.assistant.dialog.b` modal selection dialog for:
 * - Screen Focus Lock
 * - Performance Mode
 * - Fan Control (including Custom fan speed slider when `selectedId == 6`)
 * - Controller Style
 * - Theme Mode (including scrollable options, dual-tone theme swatches, and accent color presets)
 */
@Composable
fun TccOptionDialog(
    title: String,
    options: List<TccDialogOption>,
    selectedId: Int,
    onSelectOption: (Int) -> Unit,
    onDismiss: () -> Unit,
    @DrawableRes titleIconRes: Int? = null,
    showCustomFanSlider: Boolean = false,
    customFanSpeed: Int = 50,
    onCustomFanSpeedChange: ((Int) -> Unit)? = null,
    accentPresets: List<Color> = emptyList(),
    selectedAccentArgb: Int? = null,
    onSelectAccentColor: ((Color) -> Unit)? = null,
    showOsThemeToggle: Boolean = false,
    injectOsThemeEnabled: Boolean = false,
    onToggleInjectOsTheme: ((Boolean) -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()
    val dialogShape: Shape = if (isRetroidLook) RoundedCornerShape(20.dp) else RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isRetroidLook) 0.55f else 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isRetroidLook) 0.55f else 0.56f)
                .clip(dialogShape)
                .background(colors.dialogBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(vertical = if (isRetroidLook) 22.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRetroidLook) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    if (titleIconRes != null) {
                        Image(
                            painter = painterResource(id = tccDrawable(titleIconRes)),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.primary),
                            modifier = Modifier.size(33.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = title,
                        color = colors.primary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 44.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    colors.secondary.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = title,
                    color = colors.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isRetroidLook) 380.dp else 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isRetroidLook) 76.dp else 46.dp)
                            .clickable { onSelectOption(option.id) }
                            .padding(horizontal = if (isRetroidLook) 64.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            if (option.swatchColors != null) {
                                val (surfaceSwatch, accentSwatch) = option.swatchColors
                                val swatchShape = if (isRetroidLook) PixelCornerCutShape(3.dp) else CircleShape
                                Box(
                                    modifier = Modifier
                                        .size(if (isRetroidLook) 30.dp else 20.dp)
                                        .clip(swatchShape)
                                        .background(surfaceSwatch)
                                        .border(1.5.dp, accentSwatch, swatchShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isRetroidLook) 14.dp else 9.dp)
                                            .clip(swatchShape)
                                            .background(accentSwatch)
                                    )
                                }
                            } else {
                                val resolvedIcon = if (option.preserveIconColors) {
                                    option.iconRes
                                } else {
                                    tccDrawable(option.iconRes)
                                }
                                Image(
                                    painter = painterResource(id = resolvedIcon),
                                    contentDescription = option.label,
                                    colorFilter = if (option.preserveIconColors) null else ColorFilter.tint(colors.primary),
                                    modifier = Modifier.size(if (isRetroidLook) 34.dp else 20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(if (isRetroidLook) 26.dp else 14.dp))
                            Text(
                                text = option.label,
                                color = colors.primary,
                                fontSize = if (isRetroidLook) 21.sp else 14.sp,
                                fontFamily = pixelFont,
                                style = pixelStyle,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TccRadioCheckIndicator(
                            selected = isSelected,
                            modifier = Modifier.size(if (isRetroidLook) 34.dp else 18.dp)
                        )
                    }

                    val hasBelowFooter = (showCustomFanSlider && selectedId == 6) ||
                        accentPresets.isNotEmpty() ||
                        showOsThemeToggle ||
                        footerContent != null
                    val showRowDivider = if (isRetroidLook) {
                        index == options.lastIndex && hasBelowFooter
                    } else {
                        index < options.lastIndex || hasBelowFooter
                    }
                    if (showRowDivider) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isRetroidLook) 28.dp else 16.dp)
                                .height(1.dp)
                                .background(colors.fourth)
                        )
                    }
                }
            }

            if (accentPresets.isNotEmpty() && onSelectAccentColor != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isRetroidLook) 28.dp else 18.dp,
                            vertical = if (isRetroidLook) 12.dp else 10.dp
                        )
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(if (isRetroidLook) 12.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val swatchShape: Shape = if (isRetroidLook) PixelCornerCutShape(3.dp) else CircleShape
                    accentPresets.forEach { preset ->
                        val isSelectedSwatch = selectedAccentArgb != null &&
                            Color(selectedAccentArgb) == preset
                        Box(
                            modifier = Modifier
                                .size(if (isRetroidLook) 32.dp else 26.dp)
                                .clip(swatchShape)
                                .background(preset)
                                .then(
                                    if (isSelectedSwatch) {
                                        Modifier.border(2.dp, colors.primary, swatchShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelectAccentColor(preset) }
                        )
                    }
                }
                if (showOsThemeToggle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isRetroidLook) 28.dp else 16.dp)
                            .height(1.dp)
                            .background(colors.fourth)
                    )
                }
            }

            if (showOsThemeToggle && onToggleInjectOsTheme != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isRetroidLook) 54.dp else 44.dp)
                        .clickable { onToggleInjectOsTheme(!injectOsThemeEnabled) }
                        .padding(horizontal = if (isRetroidLook) 34.dp else 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Inject Theme Colors into OS",
                        color = colors.primary,
                        fontSize = if (isRetroidLook) 16.sp else 13.sp,
                        fontFamily = pixelFont,
                        style = pixelStyle,
                        fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Medium
                    )

                    val trackColor = if (injectOsThemeEnabled) colors.accent else colors.fourth
                    val thumbColor = if (injectOsThemeEnabled) {
                        if (isRetroidLook) colors.retroidContentOnAccent()
                        else if (colors.isMegingiardStyle) colors.onAccent
                        else Color.White
                    } else {
                        colors.secondary
                    }
                    val swShape: Shape = if (isRetroidLook) PixelCornerCutShape(3.dp) else RoundedCornerShape(10.dp)
                    val thumbShape: Shape = if (isRetroidLook) PixelCornerCutShape(2.dp) else CircleShape
                    Box(
                        modifier = Modifier
                            .width(if (isRetroidLook) 46.dp else 38.dp)
                            .height(if (isRetroidLook) 24.dp else 20.dp)
                            .clip(swShape)
                            .background(trackColor)
                            .then(
                                if (!injectOsThemeEnabled) {
                                    Modifier.border(1.dp, colors.tertiary, swShape)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 2.dp),
                        contentAlignment = if (injectOsThemeEnabled) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.CenterStart
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isRetroidLook) 20.dp else 16.dp)
                                .clip(thumbShape)
                                .background(thumbColor)
                        )
                    }
                }
            }

            if (showCustomFanSlider && selectedId == 6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isRetroidLook) 60.dp else 50.dp)
                        .padding(horizontal = if (isRetroidLook) 34.dp else 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = tccDrawable(R.drawable.ic_fan_custom_progress)),
                        contentDescription = "Fan Speed",
                        colorFilter = ColorFilter.tint(colors.primary),
                        modifier = Modifier.size(if (isRetroidLook) 26.dp else 20.dp)
                    )
                    Spacer(modifier = Modifier.width(if (isRetroidLook) 16.dp else 12.dp))
                    Slider(
                        value = customFanSpeed.coerceIn(35, 100).toFloat(),
                        onValueChange = { onCustomFanSpeedChange?.invoke(it.roundToInt()) },
                        valueRange = 35f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.tertiary
                        )
                    )
                    Spacer(modifier = Modifier.width(if (isRetroidLook) 10.dp else 8.dp))
                    Text(
                        text = "${customFanSpeed.coerceIn(35, 100)}%",
                        color = colors.primary,
                        fontSize = if (isRetroidLook) 16.sp else 13.sp,
                        fontFamily = pixelFont,
                        style = pixelStyle,
                        fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }

            if (footerContent != null) {
                footerContent()
            }
        }
    }
}

@Composable
fun TccDialogToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()
    val swShape: Shape = if (isRetroidLook) PixelCornerCutShape(3.dp) else RoundedCornerShape(10.dp)
    val thumbShape: Shape = if (isRetroidLook) PixelCornerCutShape(2.dp) else CircleShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isRetroidLook) 54.dp else 42.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = if (isRetroidLook) 34.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.primary,
            fontSize = if (isRetroidLook) 16.sp else 13.sp,
            fontFamily = pixelFont,
            style = pixelStyle,
            fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Medium
        )

        val trackColor = if (checked) colors.accent else colors.fourth
        val thumbColor = if (checked) {
            if (isRetroidLook) colors.retroidContentOnAccent()
            else if (colors.isMegingiardStyle) colors.onAccent
            else Color.White
        } else {
            colors.secondary
        }
        Box(
            modifier = Modifier
                .width(if (isRetroidLook) 46.dp else 38.dp)
                .height(if (isRetroidLook) 24.dp else 20.dp)
                .clip(swShape)
                .background(trackColor)
                .then(
                    if (!checked) {
                        Modifier.border(1.dp, colors.tertiary, swShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(if (isRetroidLook) 20.dp else 16.dp)
                    .clip(thumbShape)
                    .background(thumbColor)
            )
        }
    }
}

@Composable
fun TccDialogSegmentedRow(
    label: String,
    segments: List<Pair<Int, String>>,
    selectedId: Int,
    onSelectId: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val isRetroidLook = LocalTccUiLook.current == TccUiLook.RETROID
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()
    val outerShape: Shape = if (isRetroidLook) PixelCornerCutShape(3.5.dp) else RoundedCornerShape(10.dp)
    val innerShape: Shape = if (isRetroidLook) PixelCornerCutShape(2.5.dp) else RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isRetroidLook) 54.dp else 42.dp)
            .padding(horizontal = if (isRetroidLook) 34.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.primary,
            fontSize = if (isRetroidLook) 16.sp else 13.sp,
            fontFamily = pixelFont,
            style = pixelStyle,
            fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Medium
        )

        Row(
            modifier = Modifier
                .clip(outerShape)
                .background(colors.fourth)
                .padding(if (isRetroidLook) 3.dp else 2.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isRetroidLook) 3.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEach { (id, segLabel) ->
                val isSelected = selectedId == id
                val pillBg = if (isSelected) colors.accent else Color.Transparent
                val pillFg = if (isSelected) {
                    if (isRetroidLook) colors.retroidContentOnAccent()
                    else if (colors.isMegingiardStyle) colors.onAccent
                    else Color.White
                } else {
                    colors.secondary
                }
                Box(
                    modifier = Modifier
                        .clip(innerShape)
                        .background(pillBg)
                        .clickable { onSelectId(id) }
                        .padding(
                            horizontal = if (isRetroidLook) 12.dp else 9.dp,
                            vertical = if (isRetroidLook) 5.dp else 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = segLabel,
                        color = pillFg,
                        fontSize = if (isRetroidLook) 14.sp else 11.sp,
                        fontFamily = pixelFont,
                        style = pixelStyle,
                        fontWeight = if (isRetroidLook) FontWeight.Normal else FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TccDialogFpsSelectorRow(
    selectedFps: Int,
    onSelectFps: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalized = if (selectedFps <= 30) 30 else 60
    TccDialogSegmentedRow(
        label = "Frame Rate",
        segments = listOf(30 to "30 FPS", 60 to "60 FPS"),
        selectedId = normalized,
        onSelectId = onSelectFps,
        modifier = modifier
    )
}
