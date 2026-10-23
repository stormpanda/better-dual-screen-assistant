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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.R
import com.stormpanda.betterdualscreenassistant.ui.theme.LocalTccColors
import com.stormpanda.betterdualscreenassistant.ui.theme.tccDrawable
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val iconTint = if (selected) {
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
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
            modifier = Modifier.width(72.5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(50.5.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
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
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Box(
                modifier = Modifier
                    .width(72.5.dp)
                    .height(29.dp)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 11.sp,
                    lineHeight = 12.5.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
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
                .padding(start = 5.dp)
                .width(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = colors.primary,
                fontSize = 11.3.sp,
                lineHeight = 13.sp,
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
                .padding(8.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.fifth)
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

            // Straight vertical right edge clipped by outer 15.dp rounded rect, matching x0.C0501a.b
            val fillColor = if (effectiveFillDimmed) {
                if (colors.isDark) {
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
                    .background(fillColor)
            )

            val iconSize = trackHeight * 0.55f
            val sliderIconTint = if (!effectiveFillDimmed && colors.isMegingiardStyle && displayProgress >= 12) {
                colors.onAccent
            } else {
                colors.primary
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 7.5.dp),
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
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panelBackground)
    ) {
        val density = LocalDensity.current
        val minDim = min(maxWidth, maxHeight)
        val iconSize = minDim * iconSizeRate
        val titleSp = with(density) { (minDim * 0.12f).toSp() }
        val valueSp = with(density) { (minDim * 0.25f).toSp() }

        val leftX = maxWidth * 0.20f
        val rightPad = maxWidth * 0.20f
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
                fontWeight = FontWeight.Medium,
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
                fontWeight = FontWeight.Medium,
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
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val sizeDp = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val titleSp = with(density) { (sizeDp * 0.13f).toSp() }
        val valueSp = with(density) { (sizeDp * 0.25f).toSp() }
        val unitSp = with(density) { (sizeDp * 0.13f).toSp() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.05f
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
                    color = ringColor.copy(alpha = 150f / 255f),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Indicator dot at top center (-90 deg), strokeWidth * 0.85f
            drawCircle(
                color = if (colors.isMegingiardStyle) colors.primary else Color.White,
                radius = (strokeWidth * 0.85f) * 0.5f,
                center = Offset(size.width * 0.5f, halfStroke)
            )
        }

        // Title at ~22% center Y
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sizeDp * 0.44f)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = colors.tertiary,
                fontSize = titleSp,
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
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }

        // Unit at ~72% center Y
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sizeDp * 0.52f)
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = unitText,
                color = colors.tertiary,
                fontSize = unitSp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

data class TccDialogOption(
    val id: Int,
    val label: String,
    @DrawableRes val iconRes: Int,
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
            drawPath(
                path = path,
                color = if (colors.isMegingiardStyle) colors.onAccent else Color.White,
                style = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
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
    showCustomFanSlider: Boolean = false,
    customFanSpeed: Int = 50,
    onCustomFanSpeedChange: ((Int) -> Unit)? = null,
    accentPresets: List<Color> = emptyList(),
    selectedAccentArgb: Int? = null,
    onSelectAccentColor: ((Color) -> Unit)? = null,
    showOsThemeToggle: Boolean = false,
    injectOsThemeEnabled: Boolean = false,
    onToggleInjectOsTheme: ((Boolean) -> Unit)? = null
) {
    val colors = LocalTccColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.dialogBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable { onSelectOption(option.id) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (option.swatchColors != null) {
                                val (surfaceSwatch, accentSwatch) = option.swatchColors
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(surfaceSwatch)
                                        .border(1.5.dp, accentSwatch, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = option.label,
                                color = colors.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TccRadioCheckIndicator(
                            selected = isSelected,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (index < options.lastIndex || (showCustomFanSlider && selectedId == 6) || accentPresets.isNotEmpty() || showOsThemeToggle) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
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
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    accentPresets.forEach { preset ->
                        val isSelectedSwatch = selectedAccentArgb != null &&
                            Color(selectedAccentArgb) == preset
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .then(
                                    if (isSelectedSwatch) {
                                        Modifier.border(2.dp, colors.primary, CircleShape)
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
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(colors.fourth)
                    )
                }
            }

            if (showOsThemeToggle && onToggleInjectOsTheme != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { onToggleInjectOsTheme(!injectOsThemeEnabled) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Inject Theme Colors into OS",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    val trackColor = if (injectOsThemeEnabled) colors.accent else colors.fourth
                    val thumbColor = if (injectOsThemeEnabled) {
                        if (colors.isMegingiardStyle) colors.onAccent else Color.White
                    } else {
                        colors.secondary
                    }
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(trackColor)
                            .then(
                                if (!injectOsThemeEnabled) {
                                    Modifier.border(1.dp, colors.tertiary, RoundedCornerShape(10.dp))
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
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(thumbColor)
                        )
                    }
                }
            }

            if (showCustomFanSlider && selectedId == 6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = tccDrawable(R.drawable.ic_fan_custom_progress)),
                        contentDescription = "Fan Speed",
                        colorFilter = ColorFilter.tint(colors.primary),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${customFanSpeed.coerceIn(35, 100)}%",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
