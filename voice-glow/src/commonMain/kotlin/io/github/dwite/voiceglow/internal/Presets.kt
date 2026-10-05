package io.github.dwite.voiceglow.internal

import io.github.dwite.voiceglow.VoiceGlowColors
import io.github.dwite.voiceglow.VoiceGlowOptions
import io.github.dwite.voiceglow.VoiceGlowType
import kotlin.math.max

/** One lobe of the glow, in dp for a ~350 dp wide host. [band] is the voice band that lifts it. */
internal class GlowLobe(val x: Float, val w: Float, val h: Float, val band: Int)

/**
 * The seven lobes. The centre one follows the low band, its neighbours the
 * mids, the outer pair the highs and the far pair the mids again, so a voice
 * makes the colours ripple outward.
 */
internal val GlowLobes = listOf(
    GlowLobe(x = 0f, w = 74f, h = 46f, band = 0),
    GlowLobe(x = -36f, w = 54f, h = 40f, band = 1),
    GlowLobe(x = 36f, w = 54f, h = 40f, band = 1),
    GlowLobe(x = -72f, w = 48f, h = 32f, band = 2),
    GlowLobe(x = 72f, w = 48f, h = 32f, band = 2),
    GlowLobe(x = -108f, w = 42f, h = 26f, band = 1),
    GlowLobe(x = 108f, w = 42f, h = 26f, band = 1),
)

private const val LobeRestSpacing = 36f

/** What a background changes: voice-glow's `themePresets`, with the band and core colours of each. */
internal class GlowTheme(
    val dark: Boolean,
    val strokeOpacity: Float,
    val innerOpacity: Float,
    val bloomOpacity: Float,
    val saturation: Float,
    val brightness: Float,
    val hueRange: Float,
    val hueSeconds: Float,
    val hueBase: Float,
    val strength: Float,
    /** The bloom's lobes are this opaque at their centres. */
    val bloomAlpha: Float,
    /** The band's strokes are this opaque at full strength. */
    val bandLight: Float,
    val bandCore: GlowRgb,
    val bandAbove: OkLab,
    val bandMid: OkLab,
    val bandBelow: OkLab,
    /** The hot core on the edge: white on dark, ink on light. Its width in dp, and alpha at its centre, at [coreMid] and gone at [coreEnd]. */
    val core: GlowRgb,
    val coreWidth: Float,
    val coreAlpha: Float,
    val coreMid: Float,
    val coreMidAlpha: Float,
    val coreEnd: Float,
) {
    companion object {
        val Dark = GlowTheme(
            dark = true,
            strokeOpacity = 1.16f, innerOpacity = 0.47f, bloomOpacity = 0.89f,
            saturation = 1.2f, brightness = 1.1f,
            hueRange = 24f, hueSeconds = 12f, hueBase = 0f, strength = 1f,
            bloomAlpha = 0.9f, bandLight = 0.42f,
            bandCore = GlowRgb.of(255, 255, 255),
            bandAbove = OkLab.of(GlowRgb.of(255, 70, 80)),
            bandMid = OkLab.of(GlowRgb.of(90, 255, 150)),
            bandBelow = OkLab.of(GlowRgb.of(80, 140, 255)),
            core = GlowRgb.of(255, 255, 255), coreWidth = 30f, coreAlpha = 0.45f, coreMid = 0.30f, coreMidAlpha = 0.14f, coreEnd = 0.65f,
        )
        val Light = GlowTheme(
            dark = false,
            strokeOpacity = 1.2f, innerOpacity = 0.85f, bloomOpacity = 0.5f,
            saturation = 1.6f, brightness = 0.95f,
            hueRange = 40f, hueSeconds = 8.5f, hueBase = 5f, strength = 0.8f,
            bloomAlpha = 0.7f, bandLight = 0.4f,
            bandCore = GlowRgb.of(197, 139, 255),
            bandAbove = OkLab.of(GlowRgb.of(255, 122, 182)),
            bandMid = OkLab.of(GlowRgb.of(126, 196, 255)),
            bandBelow = OkLab.of(GlowRgb.of(45, 255, 171)),
            core = GlowRgb.of(0, 0, 0), coreWidth = 40f, coreAlpha = 0.55f, coreMid = 0.35f, coreMidAlpha = 0.22f, coreEnd = 0.70f,
        )
    }
}

/**
 * Everything the engine and the painter need for one glow: the type's
 * geometry (voice-glow's `presets`), the theme, and the caller's options and
 * colours over both. Every length is in dp and already multiplied by [scale],
 * as the original does.
 *
 * [sizing] is the caller's own scale, on top of the type's.
 */
internal class GlowConfig(type: VoiceGlowType, val theme: GlowTheme, o: VoiceGlowOptions, colors: VoiceGlowColors, sizing: Float = 1f) {
    val dark = theme.dark

    val scale: Float
    val glowSize: Float
    val strokeOpacity: Float
    val innerOpacity: Float
    val bloomOpacity: Float
    val idle: Float
    val reach: Float
    val spread: Float
    val flow: Float
    val bend: Float
    val bandStrength: Float
    val bandWidth: Float
    val bandPosition: Float
    val bandCurve: Float
    val bandSpread: Float
    val bandSkew: Float
    val bandOffset: Float
    val bandTail: Float
    val bandTailPosition: Float
    val bandTailCurve: Float
    val bandTailOverflow: Float
    val bandAberration: Float
    val glowWidth: Float
    val glowHeight: Float
    val lobeSpacing: Float
    val rangeWidth: Float
    val rangeHeight: Float
    val softness: Float
    val coreSize: Float

    /** The white wash at the source on a light background; 0 for none. */
    val coreLight: Float
    val coreLightWidth: Float
    val coreLightHeight: Float
    val strokeScale: Float
    val innerScale: Float
    val innerHeight: Float
    val bloomScale: Float
    val bloomHeight: Float

    val strength: Float
    val brightness: Float
    val saturation: Float
    val hueRange: Float
    val hueSeconds: Float
    val hueBase = theme.hueBase

    /** The band's colours: the caller's, or the theme's. */
    val bandCore: GlowRgb = colors.band?.let { GlowRgb.of(it.core) } ?: theme.bandCore
    val bandAbove: OkLab = colors.band?.let { OkLab.of(GlowRgb.of(it.above)) } ?: theme.bandAbove
    val bandMid: OkLab = colors.band?.let { OkLab.of(GlowRgb.of(it.between)) } ?: theme.bandMid
    val bandBelow: OkLab = colors.band?.let { OkLab.of(GlowRgb.of(it.below)) } ?: theme.bandBelow

    /** No hue drift. */
    val still = !colors.drift

    /** The monochrome look: every layer dimmer. */
    val dim = colors.dim

    val sensitivity = max(0f, o.sensitivity)
    val threshold = o.threshold
    val attack = o.attack
    val release = o.release
    val breatheSeconds = max(0.2f, o.breatheSeconds)
    val ripple = o.ripple
    val moodStrength = o.moodStrength.coerceIn(0f, 1f)
    val moodSmoothing = max(0.02f, o.moodSmoothing)
    val moodRelease = max(0.02f, o.moodRelease)

    /** Width of the ring the lobes travel around: one full turn of the flow. */
    val lobeSpan: Float

    init {
        // The type's numbers, before the scale.
        var scale = 1f
        var glowSize = 1f
        var strokeOpacity = 1f
        var innerOpacity = 1f
        var idle = 0.18f
        var reach = 1.2f
        var spread = 1.05f
        var flow = 48f
        var bend = 60f
        var bandStrength = 1.55f
        var bandWidth = 2.15f
        var bandCurve = 1.75f
        var bandSpread = 0.87f
        var bandOffset = -27f
        var bandTail = 0.59f
        var bandTailPosition = 0.67f
        var bandTailCurve = 2.4f
        var bandTailOverflow = 15f
        var glowWidth = 0.65f
        var glowHeight = 1.25f
        var lobeSpacing = 0.85f
        var rangeWidth = 0.75f
        var rangeHeight = 1f
        var softness = 1.07f
        var coreSize = 1f
        var coreLight = 0f
        var strokeScale = 1f
        var innerScale = 1f
        var bloomScale = 1f
        var bloomHeight = 1f
        var brightness: Float? = null
        var saturation: Float? = null
        var strength: Float? = null

        when (type) {
            VoiceGlowType.Standard -> if (dark) {
                brightness = 1.15f
            } else {
                reach = 1.8f
                spread = 0.8f
                coreLight = 1.8f
                bandStrength = 1.7f
            }
            VoiceGlowType.Pill -> {
                if (!dark) coreLight = 1.8f
                scale = 0.45f
                glowSize = 0.95f
                strokeOpacity = 1.2f
                innerOpacity = 0.85f
                reach = 1.35f
                spread = 1.1f
                flow = 0f
                bend = 23f
                bandStrength = if (dark) 1.55f else 2f
                bandWidth = 1.85f
                bandCurve = 1.95f
                bandSpread = 0.38f
                bandOffset = -16f
                bandTail = 0f
                glowWidth = 0.65f
                glowHeight = 0.95f
                lobeSpacing = 0.45f
                rangeWidth = 0.8f
                rangeHeight = 0.7f
                softness = 0.88f
                coreSize = 0.25f
                strokeScale = 1.25f
                innerScale = 0.95f
                bloomScale = 1.05f
                bloomHeight = 2.25f
                if (dark) {
                    brightness = 1.35f
                    saturation = 1.5f
                }
            }
            VoiceGlowType.Mobile -> {
                if (!dark) coreLight = 1.8f
                scale = 1.25f
                spread = 0.45f
                reach = 3f
                flow = 60f
                bend = 70f
                bandWidth = 2.4f
                bandCurve = 1.55f
                bandSpread = 0.9f
                bandOffset = -50f
                bandTail = 0.62f
                bandTailPosition = 0.42f
                bandTailCurve = 2.7f
                bandTailOverflow = 22f
                bandStrength = if (dark) 1.8f else 1.7f
                glowWidth = 1.15f
                glowHeight = 2.1f
                lobeSpacing = 1.35f
                rangeWidth = 1.25f
                rangeHeight = 1.2f
                softness = 1.1f
                strength = 1f
                if (dark) {
                    brightness = 1.2f
                    saturation = 1.5f
                }
            }
        }

        // The caller's options over the type, then the scale over every length.
        val sc = max(0.05f, scale * sizing)
        this.scale = sc
        this.glowSize = max(0f, o.glowSize ?: glowSize) * sc
        this.strokeOpacity = max(0f, o.strokeOpacity ?: strokeOpacity)
        this.innerOpacity = max(0f, o.innerOpacity ?: innerOpacity)
        this.bloomOpacity = max(0f, o.bloomOpacity ?: 1f)
        this.idle = (o.idle ?: idle).coerceIn(0f, 1f)
        this.reach = o.reach ?: reach
        this.spread = o.spread ?: spread
        this.flow = (o.flow ?: flow) * sc
        this.bend = max(0f, o.bend ?: bend) * sc
        this.bandStrength = max(0f, o.bandStrength ?: bandStrength)
        this.bandWidth = max(0f, o.bandWidth ?: bandWidth) * sc
        this.bandPosition = o.bandPosition ?: 0.35f
        this.bandCurve = max(0.1f, o.bandCurve ?: bandCurve)
        this.bandSpread = o.bandSpread ?: bandSpread
        this.bandSkew = (o.bandSkew ?: 0.12f).coerceIn(-0.9f, 0.9f)
        this.bandOffset = (o.bandOffset ?: bandOffset) * sc
        this.bandTail = (o.bandTail ?: bandTail).coerceIn(0f, 1f)
        this.bandTailPosition = o.bandTailPosition ?: bandTailPosition
        this.bandTailCurve = o.bandTailCurve ?: bandTailCurve
        this.bandTailOverflow = max(0f, o.bandTailOverflow ?: bandTailOverflow) * sc
        this.bandAberration = (o.bandAberration ?: 0.89f).coerceIn(0f, 1f)
        this.glowWidth = max(0f, o.glowWidth ?: glowWidth) * sc
        this.glowHeight = max(0f, o.glowHeight ?: glowHeight) * sc
        this.lobeSpacing = max(0.01f, o.lobeSpacing ?: lobeSpacing) * sc
        this.rangeWidth = max(0f, o.rangeWidth ?: rangeWidth) * sc
        this.rangeHeight = max(0f, o.rangeHeight ?: rangeHeight) * sc
        this.softness = o.softness ?: softness
        this.coreSize = max(0f, o.coreSize ?: coreSize) * sc
        this.coreLight = (o.coreLight ?: coreLight).coerceIn(0f, 3f)
        this.coreLightWidth = max(0f, o.coreLightWidth ?: 1f)
        this.coreLightHeight = max(0f, o.coreLightHeight ?: 1f)
        this.strokeScale = max(0f, o.strokeScale ?: strokeScale)
        this.innerScale = max(0f, o.innerScale ?: innerScale)
        this.innerHeight = max(0f, o.innerHeight ?: 1f)
        this.bloomScale = max(0f, o.bloomScale ?: bloomScale)
        this.bloomHeight = max(0f, o.bloomHeight ?: bloomHeight)
        this.strength = (strength ?: theme.strength).coerceIn(0f, 1f)
        this.brightness = max(0f, o.brightness ?: brightness ?: theme.brightness)
        this.saturation = max(0f, o.saturation ?: saturation ?: theme.saturation)
        this.hueRange = max(0f, o.hueRange ?: theme.hueRange)
        this.hueSeconds = max(0.5f, o.hueSeconds ?: theme.hueSeconds)
        this.lobeSpan = LobeRestSpacing * GlowLobes.size * this.lobeSpacing
    }
}
