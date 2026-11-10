package com.stormpanda.betterdualscreenassistant.ui.components

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalOverlayViewport
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccLookSpec
import com.stormpanda.betterdualscreenassistant.ui.theme.lerpDp
import com.stormpanda.betterdualscreenassistant.ui.theme.resolveResponsiveDialogWidthDp
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

enum class TccTileVariant {
    MODE,
    QUICK_SETTING,
    NAV_BAR
}

/**
 * Unified, look-driven tile button replicating `TCCButtonView` across both AYN Smooth and
 * Retroid Pixel UI looks in the shared `720dp` canonical coordinate space.
 */
@Composable
fun TccButton(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TccTileVariant = TccTileVariant.QUICK_SETTING,
    maxLabelWidth: Dp? = null
) {
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val tileSpec = lookSpec.tile
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val iconTint = if (tileSpec.useRetroidAccentContrast) {
        if (selected) colors.retroidContentOnAccent() else colors.secondary
    } else if (selected) {
        when {
            colors.isMegingiardStyle -> colors.onAccent
            colors.isDark -> Color.White
            else -> colors.primary
        }
    } else {
        colors.secondary
    }
    val textColor = if (selected) colors.primary else colors.secondary

    val isCompact = variant == TccTileVariant.NAV_BAR
    val tileBoxSize = when (variant) {
        TccTileVariant.MODE -> tileSpec.modeTileSize
        TccTileVariant.QUICK_SETTING -> tileSpec.qsTileSize
        TccTileVariant.NAV_BAR -> tileSpec.navTileSize
    }
    val defaultLabelWidth = when (variant) {
        TccTileVariant.MODE -> tileSpec.modeLabelWidth
        TccTileVariant.QUICK_SETTING -> tileSpec.qsLabelWidth
        TccTileVariant.NAV_BAR -> tileSpec.navLabelWidth
    }
    val columnWidth = if (maxLabelWidth != null) {
        min(defaultLabelWidth, maxLabelWidth)
    } else {
        defaultLabelWidth
    }
    val iconBoxSize = tileBoxSize * tileSpec.iconSizeRatio
    val topGap = if (isCompact) tileSpec.compactTopGap else tileSpec.topGap
    val labelTopGap = when (variant) {
        TccTileVariant.MODE -> tileSpec.modeLabelTopGap
        TccTileVariant.QUICK_SETTING -> tileSpec.qsLabelTopGap
        TccTileVariant.NAV_BAR -> tileSpec.compactLabelTopGap
    }
    val titleBoxHeight = when (variant) {
        TccTileVariant.MODE -> tileSpec.modeTitleHeight
        TccTileVariant.QUICK_SETTING -> tileSpec.qsTitleHeight
        TccTileVariant.NAV_BAR -> tileSpec.compactTitleHeight
    }
    val labelFontSize = if (variant == TccTileVariant.MODE) {
        tileSpec.modeLabelFontSize
    } else {
        tileSpec.qsLabelFontSize
    }
    val labelLineHeight = if (variant == TccTileVariant.MODE) {
        tileSpec.modeLabelLineHeight
    } else {
        tileSpec.qsLabelLineHeight
    }

    Box(
        modifier = modifier
            .clip(tileSpec.outerFocusShape)
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
                    .clip(tileSpec.buttonShape)
                    .then(
                        if (tileSpec.useRetroidAccentContrast) {
                            Modifier.background(if (selected) colors.accent else colors.fifth)
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
                    .padding(horizontal = tileSpec.labelHorizontalPadding),
                contentAlignment = if (tileSpec.alignNonCompactLabelTop && !isCompact) {
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
                    maxLines = if (tileSpec.singleLineWhenCompact && isCompact) 1 else 2,
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
 * Responsive brightness/volume/LED slider bar driven by [LocalTccLookSpec].
 * Scales vertical track padding proportionally to row height so 3-slider and 4-slider cards
 * maintain balanced track thickness across all screen sizes and UI looks.
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
    val sliderSpec = LocalTccLookSpec.current.slider
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

    val trackBackground = if (sliderSpec.useDarkPixelTrackOverrides && !colors.isMegingiardStyle && colors.isDark) {
        Color(0xFF161616)
    } else {
        colors.fifth
    }

    Row(
        modifier = modifier
            .clip(sliderSpec.controlShape)
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
                .padding(start = sliderSpec.labelStartPadding)
                .width(sliderSpec.labelWidth),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = colors.primary,
                fontSize = sliderSpec.labelFontSize,
                lineHeight = sliderSpec.labelLineHeight,
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
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            val verticalTrackPad = maxHeight * sliderSpec.trackVerticalPaddingRatio

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = sliderSpec.trackHorizontalPadding,
                        vertical = verticalTrackPad
                    )
                    .clip(sliderSpec.trackShape)
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
                    if (sliderSpec.useDarkPixelTrackOverrides && !colors.isMegingiardStyle && colors.isDark) {
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
                        .then(if (sliderSpec.clipFillToTrackShape) Modifier.clip(sliderSpec.trackShape) else Modifier)
                        .background(fillColor)
                )

                val iconSize = trackHeight * sliderSpec.iconHeightRatio
                val sliderIconTint = when {
                    !effectiveFillDimmed && colors.isMegingiardStyle && displayProgress >= 12 -> {
                        if (sliderSpec.clipFillToTrackShape) {
                            colors.retroidContentOnAccent()
                        } else {
                            colors.onAccent
                        }
                    }
                    sliderSpec.dimZeroIconInDark && displayProgress <= 0 -> colors.secondary.copy(alpha = 0.75f)
                    else -> colors.primary
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = sliderSpec.iconStartPadding),
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
}

/**
 * Replicates `CpuTempInfoComponent` & `FanInfoComponent` (`TEMP` / `FAN` telemetry cards),
 * sizing internal typography and icon bounds proportionally to `min(maxWidth, maxHeight)`.
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
    val lookSpec = LocalTccLookSpec.current
    val settingsSpec = lookSpec.settingsTab
    val labelFont = tccFontFamily()
    val labelStyle = tccTextStyle()
    val displayFont = tccDisplayFontFamily()
    val displayStyle = tccDisplayTextStyle()
    val textWeight = if (lookSpec.isRetroid) FontWeight.Normal else FontWeight.Medium
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .clip(lookSpec.panelCardShape)
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
        val iconSize = minDim * (iconSizeRate * settingsSpec.tempFanIconScaleMultiplier)
        val titleSp = with(density) { (minDim * settingsSpec.tempFanTitleScale).toSp() }
        val valueSp = with(density) { (minDim * settingsSpec.tempFanValueScale).toSp() }

        val leftX = maxWidth * settingsSpec.tempFanSideInsetRatio
        val rightPad = maxWidth * settingsSpec.tempFanSideInsetRatio
        val iconTopY = maxHeight * 0.20f

        Image(
            painter = painterResource(id = tccDrawable(iconRes)),
            contentDescription = title,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = Modifier
                .offset(x = leftX, y = iconTopY)
                .size(iconSize),
            contentScale = ContentScale.Fit
        )

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
                fontWeight = textWeight,
                maxLines = 1
            )
        }

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
                fontWeight = textWeight,
                maxLines = 1
            )
        }
    }
}

private val RingDecelerateEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

/**
 * Replicates `HardwareInfoRingView` (`CPU`, `GPU`, `PWR`, `RAM` circular telemetry gauges),
 * scaling diameter and typography dynamically from `min(maxWidth, maxHeight)`.
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
    val lookSpec = LocalTccLookSpec.current
    val settingsSpec = lookSpec.settingsTab
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
        val sizeDp = rawSizeDp * settingsSpec.ringDiameterRatio
        val density = LocalDensity.current
        val titleSp = with(density) { (sizeDp * settingsSpec.ringTitleScale).toSp() }
        val valueSp = with(density) { (sizeDp * settingsSpec.ringValueScale).toSp() }
        val unitSp = with(density) { (sizeDp * settingsSpec.ringUnitScale).toSp() }

        Box(
            modifier = Modifier.size(sizeDp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.055f
                val halfStroke = strokeWidth * 0.5f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(halfStroke, halfStroke)

                drawArc(
                    color = colors.fourth,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

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
                        color = ringColor.copy(
                            alpha = if (lookSpec.isRetroid && !colors.isMegingiardStyle) {
                                215f / 255f
                            } else {
                                150f / 255f
                            }
                        ),
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizeDp * settingsSpec.ringTitleBoxRatio)
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizeDp * settingsSpec.ringUnitBoxRatio)
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

@Composable
fun TccRadioCheckIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val dialogSpec = lookSpec.dialog
    if (lookSpec.isRetroid) {
        val boxShape = PixelCornerCutShape(dialogSpec.pixelCheckCutSize)
        val uncheckedBg = when {
            colors.isMegingiardStyle -> colors.primaryBackground
            colors.isDark -> Color.Black
            else -> colors.fourth
        }
        Box(
            modifier = modifier
                .clip(boxShape)
                .background(if (selected) colors.accent else uncheckedBg),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Image(
                    painter = painterResource(id = R.drawable.rp_ic_dialog_option_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.retroidContentOnAccent()),
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
 * Responsive modal selection dialog consuming [LocalTccLookSpec] and [LocalOverlayViewport].
 * Uses a preferred canonical width clamped to viewport bounds so option rows stay spacious on
 * narrower `8:7` screens without over-stretching on wider `4:3` / `16:9` panels, and bounds
 * total dialog height so multi-footer dialogs (`Theme Mode`, `Record Screen`) never clip vertically.
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
    val lookSpec = LocalTccLookSpec.current
    val dialogSpec = lookSpec.dialog
    val viewport = LocalOverlayViewport.current
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()

    val preferredWidth = lerpDp(
        start = dialogSpec.narrowPreferredWidth,
        stop = dialogSpec.widePreferredWidth,
        fraction = viewport.aspectFraction()
    )
    val dialogWidth = resolveResponsiveDialogWidthDp(
        preferredWidth = preferredWidth,
        viewportWidth = viewport.widthDp,
        maxViewportWidthFraction = dialogSpec.maxViewportWidthFraction
    )
    val widthScale = (dialogWidth / preferredWidth.coerceAtLeast(1.dp)).coerceIn(0.65f, 1f)
    val rowHorizontalPad = dialogSpec.rowHorizontalPadding * widthScale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dialogSpec.backdropDimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = viewport.heightDp * dialogSpec.maxViewportHeightFraction)
                .clip(dialogSpec.dialogShape)
                .background(colors.dialogBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(vertical = dialogSpec.verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (dialogSpec.showHeaderIconAndDivider) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = dialogSpec.headerBottomPadding)
                ) {
                    if (titleIconRes != null) {
                        Image(
                            painter = painterResource(id = tccDrawable(titleIconRes)),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.primary),
                            modifier = Modifier.size(dialogSpec.headerIconSize)
                        )
                        Spacer(modifier = Modifier.width(dialogSpec.headerIconGap))
                    }
                    Text(
                        text = title,
                        color = colors.primary,
                        fontSize = dialogSpec.titleFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dialogSpec.headerDividerHorizontalPadding * widthScale)
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
                if (dialogSpec.headerAfterDividerSpacer > 0.dp) {
                    Spacer(modifier = Modifier.height(dialogSpec.headerAfterDividerSpacer))
                }
            } else {
                Text(
                    text = title,
                    color = colors.primary,
                    fontSize = dialogSpec.titleFontSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = dialogSpec.headerBottomPadding)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f, fill = false)
                    .heightIn(max = dialogSpec.maxScrollHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dialogSpec.rowHeight)
                            .clickable { onSelectOption(option.id) }
                            .padding(horizontal = rowHorizontalPad),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = dialogSpec.optionEndPadding)
                        ) {
                            if (option.swatchColors != null) {
                                val (surfaceSwatch, accentSwatch) = option.swatchColors
                                Box(
                                    modifier = Modifier
                                        .size(dialogSpec.swatchOuterSize)
                                        .clip(dialogSpec.swatchShape)
                                        .background(surfaceSwatch)
                                        .border(dialogSpec.swatchBorderWidth, accentSwatch, dialogSpec.swatchShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(dialogSpec.swatchInnerSize)
                                            .clip(dialogSpec.swatchShape)
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
                                    modifier = Modifier.size(dialogSpec.optionIconSize)
                                )
                            }
                            Spacer(modifier = Modifier.width(dialogSpec.optionIconGap * widthScale))
                            Text(
                                text = option.label,
                                color = colors.primary,
                                fontSize = dialogSpec.optionFontSize,
                                fontFamily = pixelFont,
                                style = pixelStyle,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TccRadioCheckIndicator(
                            selected = isSelected,
                            modifier = Modifier.size(dialogSpec.checkIndicatorSize)
                        )
                    }

                    val hasBelowFooter = (showCustomFanSlider && selectedId == 6) ||
                        accentPresets.isNotEmpty() ||
                        showOsThemeToggle ||
                        footerContent != null
                    val showRowDivider = if (dialogSpec.showInterRowDividers) {
                        index < options.lastIndex || hasBelowFooter
                    } else {
                        index == options.lastIndex && hasBelowFooter
                    }
                    if (showRowDivider) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dialogSpec.dividerHorizontalPadding)
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
                            horizontal = dialogSpec.accentRowHorizontalPadding,
                            vertical = dialogSpec.accentRowVerticalPadding
                        )
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(dialogSpec.accentSwatchSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    accentPresets.forEach { preset ->
                        val isSelectedSwatch = selectedAccentArgb != null &&
                            Color(selectedAccentArgb) == preset
                        Box(
                            modifier = Modifier
                                .size(dialogSpec.accentSwatchSize)
                                .clip(dialogSpec.swatchShape)
                                .background(preset)
                                .then(
                                    if (isSelectedSwatch) {
                                        Modifier.border(
                                            dialogSpec.accentSelectedBorderWidth,
                                            colors.primary,
                                            dialogSpec.swatchShape
                                        )
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
                            .padding(horizontal = dialogSpec.dividerHorizontalPadding)
                            .height(1.dp)
                            .background(colors.fourth)
                    )
                }
            }

            if (showOsThemeToggle && onToggleInjectOsTheme != null) {
                TccDialogToggleRow(
                    label = "Inject Theme Colors into OS",
                    checked = injectOsThemeEnabled,
                    onCheckedChange = onToggleInjectOsTheme
                )
            }

            if (showCustomFanSlider && selectedId == 6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dialogSpec.fanSliderRowHeight)
                        .padding(horizontal = dialogSpec.toggleRowHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = tccDrawable(R.drawable.ic_fan_custom_progress)),
                        contentDescription = "Fan Speed",
                        colorFilter = ColorFilter.tint(colors.primary),
                        modifier = Modifier.size(dialogSpec.fanSliderIconSize)
                    )
                    Spacer(modifier = Modifier.width(dialogSpec.fanSliderIconGap))
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
                    Spacer(modifier = Modifier.width(dialogSpec.fanSliderValueGap))
                    Text(
                        text = "${customFanSpeed.coerceIn(35, 100)}%",
                        color = colors.primary,
                        fontSize = dialogSpec.toggleFontSize,
                        fontFamily = pixelFont,
                        style = pixelStyle,
                        fontWeight = if (lookSpec.isRetroid) FontWeight.Normal else FontWeight.Bold
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
    val lookSpec = LocalTccLookSpec.current
    val dialogSpec = lookSpec.dialog
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dialogSpec.toggleRowHeight)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = dialogSpec.toggleRowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.primary,
            fontSize = dialogSpec.toggleFontSize,
            fontFamily = pixelFont,
            style = pixelStyle,
            fontWeight = if (lookSpec.isRetroid) FontWeight.Normal else FontWeight.Medium
        )

        val trackColor = if (checked) colors.accent else colors.fourth
        val thumbColor = if (checked) {
            when {
                lookSpec.isRetroid -> colors.retroidContentOnAccent()
                colors.isMegingiardStyle -> colors.onAccent
                else -> Color.White
            }
        } else {
            colors.secondary
        }
        Box(
            modifier = Modifier
                .width(dialogSpec.switchWidth)
                .height(dialogSpec.switchHeight)
                .clip(dialogSpec.switchShape)
                .background(trackColor)
                .then(
                    if (!checked) {
                        Modifier.border(1.dp, colors.tertiary, dialogSpec.switchShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = dialogSpec.switchHorizontalPadding),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(dialogSpec.switchThumbSize)
                    .clip(dialogSpec.switchThumbShape)
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
    val lookSpec = LocalTccLookSpec.current
    val dialogSpec = lookSpec.dialog
    val pixelFont = tccFontFamily()
    val pixelStyle = tccTextStyle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dialogSpec.toggleRowHeight)
            .padding(horizontal = dialogSpec.toggleRowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colors.primary,
            fontSize = dialogSpec.toggleFontSize,
            fontFamily = pixelFont,
            style = pixelStyle,
            fontWeight = if (lookSpec.isRetroid) FontWeight.Normal else FontWeight.Medium
        )

        Row(
            modifier = Modifier
                .clip(dialogSpec.segmentedOuterShape)
                .background(colors.fourth)
                .padding(dialogSpec.segmentedContainerPadding),
            horizontalArrangement = Arrangement.spacedBy(dialogSpec.segmentedItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEach { (id, segLabel) ->
                val isSelected = selectedId == id
                val pillBg = if (isSelected) colors.accent else Color.Transparent
                val pillFg = if (isSelected) {
                    when {
                        lookSpec.isRetroid -> colors.retroidContentOnAccent()
                        colors.isMegingiardStyle -> colors.onAccent
                        else -> Color.White
                    }
                } else {
                    colors.secondary
                }
                Box(
                    modifier = Modifier
                        .clip(dialogSpec.segmentedInnerShape)
                        .background(pillBg)
                        .clickable { onSelectId(id) }
                        .padding(
                            horizontal = dialogSpec.segmentedPillHorizontalPadding,
                            vertical = dialogSpec.segmentedPillVerticalPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = segLabel,
                        color = pillFg,
                        fontSize = dialogSpec.segmentedPillFontSize,
                        fontFamily = pixelFont,
                        style = pixelStyle,
                        fontWeight = if (lookSpec.isRetroid) FontWeight.Normal else FontWeight.Bold
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
