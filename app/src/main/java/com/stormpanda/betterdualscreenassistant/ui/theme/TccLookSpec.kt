package com.stormpanda.betterdualscreenassistant.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.stormpanda.betterdualscreenassistant.ui.components.PixelCornerCutShape
import com.stormpanda.betterdualscreenassistant.ui.components.PixelNotchedShape
import kotlin.math.min as mathMin

/**
 * Canonical virtual viewport height (`720dp`) shared by every [TccLookSpec] inside the
 * Assistant Overlay. Normalizing `LocalDensity` to `min(windowWidthPx, windowHeightPx) / 720f`
 * makes vertical proportions invariant across arbitrary resolutions and firmware DPIs while
 * preserving exact native pixel parity on both AYN Thor (`1080 / 720 = 1.5x`) and Retroid
 * Pocket Duo (`960 / 720 = 1.3333x`).
 */
const val CANONICAL_OVERLAY_HEIGHT_DP: Float = 720f

/**
 * Exact terminating rational factor `(369 / 160) / (1080 / 720) = 369 / 240 = 1.5375f`
 * mapping stock AYN Thor `369 dpi` (`468.2927dp` tall) dimensions into the shared `720dp`
 * canonical coordinate space with zero floating-point drift (`1.5375f * 1.5f == 2.30625f`).
 */
internal const val AYN_TO_CANONICAL_720_SCALE: Float = 1.5375f

internal fun aynDp(rawThorDp: Float): Dp = (rawThorDp * AYN_TO_CANONICAL_720_SCALE).dp
internal fun aynSp(rawThorSp: Float): TextUnit = (rawThorSp * AYN_TO_CANONICAL_720_SCALE).sp

fun computeOverlayNormalizedDensity(
    widthPx: Int,
    heightPx: Int,
    fallbackDensity: Float = 1.5f
): Float {
    if (widthPx <= 0 || heightPx <= 0 || widthPx == Int.MAX_VALUE || heightPx == Int.MAX_VALUE) {
        return fallbackDensity
    }
    val shortSidePx = mathMin(widthPx, heightPx).toFloat()
    return (shortSidePx / CANONICAL_OVERLAY_HEIGHT_DP).coerceAtLeast(0.5f)
}

fun resolveResponsiveDialogWidthDp(
    preferredWidth: Dp,
    viewportWidth: Dp,
    maxViewportWidthFraction: Float
): Dp {
    val maxAllowed = viewportWidth * maxViewportWidthFraction
    return min(preferredWidth, maxAllowed)
}

@Immutable
data class OverlayViewportMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val normalizedDensity: Float,
    val widthDp: Dp,
    val heightDp: Dp,
    val aspectRatio: Float
) {
    /**
     * Smooth `0f..1f` horizontal interpolation factor between narrow (~`8:7` = `1.15f`)
     * and standard `4:3`+ (`1.333f`) secondary displays.
     */
    fun aspectFraction(
        narrowAspect: Float = 1.15f,
        wideAspect: Float = 4f / 3f
    ): Float {
        val span = (wideAspect - narrowAspect).coerceAtLeast(0.001f)
        return ((aspectRatio - narrowAspect) / span).coerceIn(0f, 1f)
    }
}

val DefaultOverlayViewportMetrics = OverlayViewportMetrics(
    widthPx = 1280,
    heightPx = 960,
    normalizedDensity = 4f / 3f,
    widthDp = 960.dp,
    heightDp = 720.dp,
    aspectRatio = 4f / 3f
)

val LocalOverlayViewport = compositionLocalOf { DefaultOverlayViewportMetrics }

/**
 * Normalizes `LocalDensity` to [CANONICAL_OVERLAY_HEIGHT_DP] (`720dp` short-side height,
 * `fontScale = 1f`) from the live container's pixel bounds and provides [LocalOverlayViewport]
 * so all UI looks adapt cleanly to any display resolution, aspect ratio, or firmware DPI.
 */
@Composable
fun OverlayViewport(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val normalizedDensity = computeOverlayNormalizedDensity(
            widthPx = widthPx,
            heightPx = heightPx,
            fallbackDensity = baseDensity.density
        )
        val metrics = remember(widthPx, heightPx, normalizedDensity) {
            val safeW = if (widthPx > 0 && widthPx != Int.MAX_VALUE) widthPx else 1280
            val safeH = if (heightPx > 0 && heightPx != Int.MAX_VALUE) heightPx else 960
            OverlayViewportMetrics(
                widthPx = safeW,
                heightPx = safeH,
                normalizedDensity = normalizedDensity,
                widthDp = (safeW / normalizedDensity).dp,
                heightDp = (safeH / normalizedDensity).dp,
                aspectRatio = safeW.toFloat() / safeH.toFloat().coerceAtLeast(1f)
            )
        }
        val effectiveDensity = remember(normalizedDensity) {
            Density(density = normalizedDensity, fontScale = 1f)
        }
        CompositionLocalProvider(
            LocalDensity provides effectiveDensity,
            LocalOverlayViewport provides metrics
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Immutable
data class StatusBarLookSpec(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val horizontalPadding: Dp,
    val timeHorizontalPadding: Dp,
    val itemSpacing: Dp,
    val fontSize: TextUnit,
    val recordingFontSize: TextUnit = aynSp(12f),
    val bluetoothIconSize: Dp,
    val wifiWidth: Dp,
    val wifiHeight: Dp,
    val batteryWidth: Dp,
    val batteryHeight: Dp,
    val batteryPercentGap: Dp
)

@Immutable
data class TileButtonLookSpec(
    val buttonShape: Shape,
    val modeTileSize: Dp,
    val qsTileSize: Dp,
    val navTileSize: Dp,
    val modeLabelWidth: Dp,
    val qsLabelWidth: Dp,
    val navLabelWidth: Dp,
    val iconSizeRatio: Float,
    val topGap: Dp,
    val compactTopGap: Dp,
    val modeLabelTopGap: Dp,
    val qsLabelTopGap: Dp,
    val compactLabelTopGap: Dp,
    val modeTitleHeight: Dp,
    val qsTitleHeight: Dp,
    val compactTitleHeight: Dp,
    val modeLabelFontSize: TextUnit,
    val qsLabelFontSize: TextUnit,
    val modeLabelLineHeight: TextUnit,
    val qsLabelLineHeight: TextUnit
)

@Immutable
data class SliderLookSpec(
    val controlShape: Shape,
    val trackShape: Shape,
    val labelStartPadding: Dp,
    val labelWidth: Dp,
    val labelFontSize: TextUnit,
    val labelLineHeight: TextUnit,
    val trackHorizontalPadding: Dp,
    val trackVerticalPaddingRatio: Float,
    val iconHeightRatio: Float,
    val iconStartPadding: Dp
)

@Immutable
data class ModeTabLookSpec(
    val upperCardWeight: Float,
    val lowerRowWeight: Float,
    val upperCardTopPadding: Dp,
    val upperCardInnerVerticalPadding: Dp,
    val modeItemHorizontalPadding: Dp,
    val modeItemVerticalPadding: Dp,
    val middleGap: Dp,
    val lowerRowBottomPadding: Dp,
    val cardHalfGap: Dp,
    val cardHorizontalPadding: Dp,
    val fixedCardVerticalPadding: Dp,
    val threeSliderRowHeight: Dp,
    val fourSliderRowHeight: Dp
)

@Immutable
data class SettingsTabLookSpec(
    val topRowWeight: Float,
    val bottomRowWeight: Float,
    val narrowLeftColWeight: Float,
    val wideLeftColWeight: Float,
    val cardHalfGap: Dp,
    val topRowTopPadding: Dp,
    val topRowBottomPadding: Dp,
    val bottomRowTopPadding: Dp,
    val pagerPadding: PaddingValues,
    val indicatorDotSize: Dp,
    val indicatorDotSpacing: Dp,
    val indicatorShape: Shape,
    val fpsBadgeShape: Shape,
    val fpsBadgePreferredWidth: Dp?,
    val fpsBadgeHeight: Dp?,
    val fpsBadgeHorizontalPadding: Dp,
    val fpsBadgeVerticalPadding: Dp,
    val fpsBadgeFontSize: TextUnit,
    val fpsTipFontSize: TextUnit,
    val tempFanGap: Dp,
    val ringsHorizontalPadding: Dp,
    val ringsVerticalPadding: Dp,
    val tempFanTitleScale: Float = 0.12f,
    val tempFanValueScale: Float,
    val ringDiameterRatio: Float,
    val ringTitleScale: Float = 0.13f,
    val ringValueScale: Float,
    val ringUnitScale: Float = 0.13f
)

@Immutable
data class NavBarLookSpec(
    val fixedOuterHeight: Dp?,
    val outerTopPadding: Dp,
    val outerBottomPadding: Dp,
    val innerVerticalPadding: Dp,
    val itemHorizontalPadding: Dp,
    val narrowRowFraction3: Float,
    val wideRowFraction3: Float,
    val narrowRowFraction4: Float,
    val wideRowFraction4: Float
)

@Immutable
data class DialogLookSpec(
    val dialogShape: Shape,
    val backdropDimAlpha: Float,
    val narrowPreferredWidth: Dp,
    val widePreferredWidth: Dp,
    val maxViewportWidthFraction: Float,
    val verticalPadding: Dp,
    val headerBottomPadding: Dp,
    val headerIconSize: Dp,
    val titleFontSize: TextUnit,
    val maxScrollHeight: Dp,
    val rowHeight: Dp,
    val rowHorizontalPadding: Dp,
    val swatchShape: Shape,
    val swatchOuterSize: Dp,
    val optionIconSize: Dp,
    val optionIconGap: Dp,
    val optionFontSize: TextUnit,
    val checkIndicatorSize: Dp,
    val toggleRowHeight: Dp,
    val toggleRowHorizontalPadding: Dp,
    val toggleFontSize: TextUnit,
    val switchShape: Shape,
    val switchThumbShape: Shape,
    val switchWidth: Dp,
    val switchHeight: Dp,
    val segmentedOuterShape: Shape,
    val segmentedInnerShape: Shape,
    val segmentedPillFontSize: TextUnit
)

@Immutable
data class TccLookSpec(
    val look: TccUiLook,
    val retroidReadabilityBoost: Boolean = false,
    val maxContentAspectRatio: Float,
    val outerHorizontalPadding: Dp,
    val contentBottomPadding: Dp,
    val panelCardShape: Shape,
    val statusBar: StatusBarLookSpec,
    val tile: TileButtonLookSpec,
    val slider: SliderLookSpec,
    val modeTab: ModeTabLookSpec,
    val settingsTab: SettingsTabLookSpec,
    val navBar: NavBarLookSpec,
    val dialog: DialogLookSpec
) {
    val isRetroid: Boolean get() = look == TccUiLook.RETROID
}

val AynLookSpec = TccLookSpec(
    look = TccUiLook.AYN,
    maxContentAspectRatio = 1.45f,
    outerHorizontalPadding = aynDp(15f),
    contentBottomPadding = aynDp(5f),
    panelCardShape = RoundedCornerShape(aynDp(20f)),
    statusBar = StatusBarLookSpec(
        topPadding = aynDp(15f),
        bottomPadding = 0.dp,
        horizontalPadding = aynDp(15f),
        timeHorizontalPadding = aynDp(6f),
        itemSpacing = aynDp(10f),
        fontSize = aynSp(15f),
        bluetoothIconSize = aynDp(16f),
        wifiWidth = aynDp(17f),
        wifiHeight = aynDp(17f),
        batteryWidth = aynDp(25f),
        batteryHeight = aynDp(14f),
        batteryPercentGap = aynDp(4f)
    ),
    tile = TileButtonLookSpec(
        buttonShape = CircleShape,
        modeTileSize = aynDp(50.5f),
        qsTileSize = aynDp(50.5f),
        navTileSize = aynDp(50.5f),
        modeLabelWidth = aynDp(72.5f),
        qsLabelWidth = aynDp(82f),
        navLabelWidth = aynDp(72.5f),
        iconSizeRatio = 26f / 50.5f,
        topGap = aynDp(8f),
        compactTopGap = aynDp(8f),
        modeLabelTopGap = 0.dp,
        qsLabelTopGap = 0.dp,
        compactLabelTopGap = 0.dp,
        modeTitleHeight = aynDp(29f),
        qsTitleHeight = aynDp(29f),
        compactTitleHeight = aynDp(29f),
        modeLabelFontSize = aynSp(11f),
        qsLabelFontSize = aynSp(11f),
        modeLabelLineHeight = aynSp(12.5f),
        qsLabelLineHeight = aynSp(12.5f)
    ),
    slider = SliderLookSpec(
        controlShape = RoundedCornerShape(aynDp(15f)),
        trackShape = RoundedCornerShape(aynDp(15f)),
        labelStartPadding = aynDp(5f),
        labelWidth = aynDp(42f),
        labelFontSize = aynSp(11.3f),
        labelLineHeight = aynSp(13f),
        trackHorizontalPadding = aynDp(8f),
        trackVerticalPaddingRatio = 0.19f,
        iconHeightRatio = 0.55f,
        iconStartPadding = aynDp(7.5f)
    ),
    modeTab = ModeTabLookSpec(
        upperCardWeight = 0.50f,
        lowerRowWeight = 0.50f,
        upperCardTopPadding = aynDp(8f),
        upperCardInnerVerticalPadding = aynDp(8f),
        modeItemHorizontalPadding = aynDp(15f),
        modeItemVerticalPadding = aynDp(6f),
        middleGap = aynDp(10f),
        lowerRowBottomPadding = 0.dp,
        cardHalfGap = aynDp(5f),
        cardHorizontalPadding = aynDp(10f),
        fixedCardVerticalPadding = aynDp(9f),
        threeSliderRowHeight = aynDp(43f),
        fourSliderRowHeight = aynDp(43f)
    ),
    settingsTab = SettingsTabLookSpec(
        topRowWeight = 0.70f,
        bottomRowWeight = 0.30f,
        narrowLeftColWeight = 0.40f,
        wideLeftColWeight = 0.40f,
        cardHalfGap = aynDp(5f),
        topRowTopPadding = aynDp(5f),
        topRowBottomPadding = aynDp(5f),
        bottomRowTopPadding = aynDp(5f),
        pagerPadding = PaddingValues(horizontal = aynDp(10f), vertical = 0.dp),
        indicatorDotSize = aynDp(6f),
        indicatorDotSpacing = aynDp(8f),
        indicatorShape = CircleShape,
        fpsBadgeShape = RoundedCornerShape(aynDp(25f)),
        fpsBadgePreferredWidth = null,
        fpsBadgeHeight = null,
        fpsBadgeHorizontalPadding = aynDp(15f),
        fpsBadgeVerticalPadding = aynDp(3f),
        fpsBadgeFontSize = aynSp(16f),
        fpsTipFontSize = aynSp(16f),
        tempFanGap = aynDp(10f),
        ringsHorizontalPadding = aynDp(12f),
        ringsVerticalPadding = aynDp(12f),
        tempFanValueScale = 0.25f,
        ringDiameterRatio = 1.0f,
        ringValueScale = 0.25f
    ),
    navBar = NavBarLookSpec(
        fixedOuterHeight = null,
        outerTopPadding = aynDp(5f),
        outerBottomPadding = aynDp(15f),
        innerVerticalPadding = aynDp(5f),
        itemHorizontalPadding = aynDp(8f),
        narrowRowFraction3 = 0.70f,
        wideRowFraction3 = 0.76f,
        narrowRowFraction4 = 0.82f,
        wideRowFraction4 = 0.86f
    ),
    dialog = DialogLookSpec(
        dialogShape = RoundedCornerShape(aynDp(18f)),
        backdropDimAlpha = 0.45f,
        narrowPreferredWidth = aynDp(301f),
        widePreferredWidth = aynDp(328f),
        maxViewportWidthFraction = 0.78f,
        verticalPadding = aynDp(12f),
        headerBottomPadding = aynDp(8f),
        headerIconSize = aynDp(20f),
        titleFontSize = aynSp(16f),
        maxScrollHeight = aynDp(320f),
        rowHeight = aynDp(46f),
        rowHorizontalPadding = aynDp(20f),
        swatchShape = CircleShape,
        swatchOuterSize = aynDp(20f),
        optionIconSize = aynDp(20f),
        optionIconGap = aynDp(14f),
        optionFontSize = aynSp(14f),
        checkIndicatorSize = aynDp(18f),
        toggleRowHeight = aynDp(42f),
        toggleRowHorizontalPadding = aynDp(20f),
        toggleFontSize = aynSp(13f),
        switchShape = RoundedCornerShape(aynDp(10f)),
        switchThumbShape = CircleShape,
        switchWidth = aynDp(38f),
        switchHeight = aynDp(20f),
        segmentedOuterShape = RoundedCornerShape(aynDp(10f)),
        segmentedInnerShape = RoundedCornerShape(aynDp(8f)),
        segmentedPillFontSize = aynSp(11f)
    )
)

val RetroidLookSpec = TccLookSpec(
    look = TccUiLook.RETROID,
    maxContentAspectRatio = 1.30f,
    outerHorizontalPadding = 14.dp,
    contentBottomPadding = 3.dp,
    panelCardShape = RoundedCornerShape(22.dp),
    statusBar = StatusBarLookSpec(
        topPadding = 34.5.dp,
        bottomPadding = 28.5.dp,
        horizontalPadding = 14.dp,
        timeHorizontalPadding = 0.dp,
        itemSpacing = 10.dp,
        fontSize = 20.5.sp,
        recordingFontSize = 15.sp,
        bluetoothIconSize = 21.dp,
        wifiWidth = 28.dp,
        wifiHeight = 21.5.dp,
        batteryWidth = 39.dp,
        batteryHeight = 21.5.dp,
        batteryPercentGap = 7.dp
    ),
    tile = TileButtonLookSpec(
        buttonShape = PixelCornerCutShape(9.dp),
        modeTileSize = 67.5.dp,
        qsTileSize = 52.5.dp,
        navTileSize = 52.5.dp,
        modeLabelWidth = 165.dp,
        qsLabelWidth = 132.dp,
        navLabelWidth = 110.dp,
        iconSizeRatio = 0.56f,
        topGap = 5.dp,
        compactTopGap = 3.dp,
        modeLabelTopGap = 12.dp,
        qsLabelTopGap = 8.dp,
        compactLabelTopGap = 5.dp,
        modeTitleHeight = 26.dp,
        qsTitleHeight = 36.dp,
        compactTitleHeight = 22.dp,
        modeLabelFontSize = 15.sp,
        qsLabelFontSize = 14.sp,
        modeLabelLineHeight = 18.sp,
        qsLabelLineHeight = 16.5.sp
    ),
    slider = SliderLookSpec(
        controlShape = RoundedCornerShape(12.dp),
        trackShape = PixelNotchedShape(9.dp),
        labelStartPadding = 2.dp,
        labelWidth = 56.dp,
        labelFontSize = 14.sp,
        labelLineHeight = 16.5.sp,
        trackHorizontalPadding = 10.dp,
        trackVerticalPaddingRatio = 9.5f / 54f,
        iconHeightRatio = 0.58f,
        iconStartPadding = 10.dp
    ),
    modeTab = ModeTabLookSpec(
        upperCardWeight = 0.44f,
        lowerRowWeight = 0.56f,
        upperCardTopPadding = 0.dp,
        upperCardInnerVerticalPadding = 10.dp,
        modeItemHorizontalPadding = 6.dp,
        modeItemVerticalPadding = 4.dp,
        middleGap = 30.dp,
        lowerRowBottomPadding = 25.5.dp,
        cardHalfGap = 7.5.dp,
        cardHorizontalPadding = 16.dp,
        fixedCardVerticalPadding = 14.dp,
        threeSliderRowHeight = 54.dp,
        fourSliderRowHeight = 44.dp
    ),
    settingsTab = SettingsTabLookSpec(
        topRowWeight = 0.68f,
        bottomRowWeight = 0.32f,
        narrowLeftColWeight = 0.38f,
        wideLeftColWeight = 0.345f,
        cardHalfGap = 7.5.dp,
        topRowTopPadding = 4.5.dp,
        topRowBottomPadding = 8.25.dp,
        bottomRowTopPadding = 8.25.dp,
        pagerPadding = PaddingValues(top = 12.dp, bottom = 2.dp, start = 14.dp, end = 14.dp),
        indicatorDotSize = 8.dp,
        indicatorDotSpacing = 10.dp,
        indicatorShape = RectangleShape,
        fpsBadgeShape = PixelCornerCutShape(3.5.dp),
        fpsBadgePreferredWidth = 220.dp,
        fpsBadgeHeight = 38.dp,
        fpsBadgeHorizontalPadding = 12.dp,
        fpsBadgeVerticalPadding = 0.dp,
        fpsBadgeFontSize = 19.sp,
        fpsTipFontSize = 18.sp,
        tempFanGap = 15.dp,
        ringsHorizontalPadding = 16.dp,
        ringsVerticalPadding = 12.dp,
        tempFanTitleScale = 0.135f,
        tempFanValueScale = 0.215f,
        ringDiameterRatio = 0.90f,
        ringTitleScale = 0.13f,
        ringValueScale = 0.185f,
        ringUnitScale = 0.125f
    ),
    navBar = NavBarLookSpec(
        fixedOuterHeight = 155.5.dp,
        outerTopPadding = 15.dp,
        outerBottomPadding = 30.5.dp,
        innerVerticalPadding = 4.dp,
        itemHorizontalPadding = 4.dp,
        narrowRowFraction3 = 0.74f,
        wideRowFraction3 = 0.68f,
        narrowRowFraction4 = 0.86f,
        wideRowFraction4 = 0.78f
    ),
    dialog = DialogLookSpec(
        dialogShape = RoundedCornerShape(20.dp),
        backdropDimAlpha = 0.55f,
        narrowPreferredWidth = 528.dp,
        widePreferredWidth = 528.dp,
        maxViewportWidthFraction = 0.72f,
        verticalPadding = 22.dp,
        headerBottomPadding = 18.dp,
        headerIconSize = 33.dp,
        titleFontSize = 26.sp,
        maxScrollHeight = 380.dp,
        rowHeight = 76.dp,
        rowHorizontalPadding = 64.dp,
        swatchShape = PixelCornerCutShape(3.dp),
        swatchOuterSize = 30.dp,
        optionIconSize = 34.dp,
        optionIconGap = 26.dp,
        optionFontSize = 21.sp,
        checkIndicatorSize = 34.dp,
        toggleRowHeight = 54.dp,
        toggleRowHorizontalPadding = 34.dp,
        toggleFontSize = 16.sp,
        switchShape = PixelCornerCutShape(3.dp),
        switchThumbShape = PixelCornerCutShape(2.dp),
        switchWidth = 46.dp,
        switchHeight = 24.dp,
        segmentedOuterShape = PixelCornerCutShape(3.5.dp),
        segmentedInnerShape = PixelCornerCutShape(2.5.dp),
        segmentedPillFontSize = 14.sp
    )
)

/**
 * Enhanced-readability variant of [RetroidLookSpec] that boosts pixel font sizes ~20–25%
 * across tiles, sliders, telemetry readouts, status bar, and dialogs while expanding tight
 * label containers so larger text never wraps mid-word or clips.
 */
val RetroidReadableLookSpec: TccLookSpec = RetroidLookSpec.copy(
    retroidReadabilityBoost = true,
    statusBar = RetroidLookSpec.statusBar.copy(
        topPadding = 30.dp,
        bottomPadding = 24.dp,
        fontSize = 29.sp,
        recordingFontSize = 22.sp
    ),
    tile = RetroidLookSpec.tile.copy(
        modeLabelWidth = 192.dp,
        qsLabelWidth = 156.dp,
        navLabelWidth = 136.dp,
        topGap = 2.dp,
        compactTopGap = 2.dp,
        modeLabelTopGap = 7.dp,
        qsLabelTopGap = 5.dp,
        compactLabelTopGap = 4.dp,
        modeTitleHeight = 54.dp,
        qsTitleHeight = 52.dp,
        compactTitleHeight = 32.dp,
        modeLabelFontSize = 22.5.sp,
        qsLabelFontSize = 21.5.sp,
        modeLabelLineHeight = 26.sp,
        qsLabelLineHeight = 24.5.sp
    ),
    slider = RetroidLookSpec.slider.copy(
        labelWidth = 84.dp,
        labelFontSize = 20.5.sp,
        labelLineHeight = 23.5.sp
    ),
    modeTab = RetroidLookSpec.modeTab.copy(
        cardHorizontalPadding = 12.dp,
        fixedCardVerticalPadding = 10.dp,
        threeSliderRowHeight = 56.dp,
        fourSliderRowHeight = 48.dp
    ),
    settingsTab = RetroidLookSpec.settingsTab.copy(
        fpsBadgePreferredWidth = 276.dp,
        fpsBadgeHeight = 46.dp,
        fpsBadgeFontSize = 27.sp,
        fpsTipFontSize = 26.sp,
        tempFanTitleScale = 0.20f,
        tempFanValueScale = 0.30f,
        ringTitleScale = 0.185f,
        ringValueScale = 0.255f,
        ringUnitScale = 0.18f
    ),
    navBar = RetroidLookSpec.navBar.copy(
        narrowRowFraction3 = 0.78f,
        wideRowFraction3 = 0.72f,
        narrowRowFraction4 = 0.90f,
        wideRowFraction4 = 0.82f
    ),
    dialog = RetroidLookSpec.dialog.copy(
        narrowPreferredWidth = 568.dp,
        widePreferredWidth = 568.dp,
        maxViewportWidthFraction = 0.78f,
        titleFontSize = 34.sp,
        rowHorizontalPadding = 48.dp,
        optionFontSize = 29.sp,
        toggleRowHeight = 60.dp,
        toggleRowHorizontalPadding = 26.dp,
        toggleFontSize = 23.sp,
        segmentedPillFontSize = 20.sp
    )
)

fun lookSpecFor(
    uiLook: TccUiLook,
    retroidReadabilityBoost: Boolean = false
): TccLookSpec = when (uiLook) {
    TccUiLook.AYN -> AynLookSpec
    TccUiLook.RETROID -> if (retroidReadabilityBoost) RetroidReadableLookSpec else RetroidLookSpec
}

fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp = lerp(start, stop, fraction.coerceIn(0f, 1f))

val LocalTccLookSpec = compositionLocalOf { AynLookSpec }
