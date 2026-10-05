package io.github.dwite.voiceglow

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.dwite.voiceglow.internal.GlowLobes
import io.github.dwite.voiceglow.internal.GlowRgb
import io.github.dwite.voiceglow.internal.OkLab

/**
 * The colours of a glow.
 *
 * The glow is seven lobes of light, the centre one first and then the pairs
 * outward, each with its own colour; fewer than seven colours repeat. It can
 * have one set for a dark background and one for a light one.
 *
 * ```
 * VoiceGlowColors.Sunset                                   // one of the eight palettes
 * VoiceGlowColors(listOf(Color(0xFFFF78BE), Color(0xFF9664FF)))  // your own
 * VoiceGlowColors(Color(0xFFFF78BE))                       // one colour
 * VoiceGlowColors.from(MaterialTheme.colorScheme.primary)  // a palette around one colour
 * VoiceGlowColors.Ocean.copy(drift = false)                // a palette, changed
 * ```
 *
 * New colours cross over instead of cutting, so a glow can change colour with
 * whoever is speaking.
 */
@Immutable
public class VoiceGlowColors private constructor(
    private val dark: Array<OkLab>,
    private val light: Array<OkLab>,
    /** The band's own colours; `null` keeps the theme's (a white core with red, green and blue fringes on dark). */
    public val band: VoiceGlowBandColors?,
    /** The colours a [VoiceMood] takes the glow to. */
    public val mood: VoiceMoodColors,
    /** The colours wander slowly in hue. Off holds them exactly as given. */
    public val drift: Boolean,
    /** The monochrome look: every layer dimmer. */
    internal val dim: Float,
) {
    /** [dark] on a dark background and [light] on a light one. */
    public constructor(
        dark: List<Color>,
        light: List<Color> = dark,
        band: VoiceGlowBandColors? = null,
        mood: VoiceMoodColors = VoiceMoodColors.Standard,
        drift: Boolean = true,
    ) : this(dark.lab(), light.lab(), band, mood, drift, dim = 1f)

    /** One [color] for the whole glow, on any background. */
    public constructor(color: Color) : this(listOf(color))

    /** These colours with some things changed. */
    public fun copy(band: VoiceGlowBandColors? = this.band, mood: VoiceMoodColors = this.mood, drift: Boolean = this.drift): VoiceGlowColors =
        VoiceGlowColors(dark, light, band, mood, drift, dim)

    internal fun lab(isDark: Boolean): Array<OkLab> = if (isDark) dark else light

    override fun equals(other: Any?): Boolean =
        other is VoiceGlowColors && dark.contentEquals(other.dark) && light.contentEquals(other.light) &&
            band == other.band && mood == other.mood && drift == other.drift && dim == other.dim

    override fun hashCode(): Int = 31 * (31 * dark.contentHashCode() + light.contentHashCode()) + (band?.hashCode() ?: 0)

    public companion object {
        /** The whole spectrum: the default. */
        public val Colorful: VoiceGlowColors = palette(
            dark = intArrayOf(255, 70, 120, 60, 190, 255, 175, 70, 255, 60, 220, 130, 255, 150, 40, 90, 100, 255, 40, 200, 190),
            light = intArrayOf(255, 201, 21, 126, 196, 255, 180, 40, 230, 235, 100, 160, 255, 176, 122, 154, 160, 255, 127, 217, 238),
        )

        /** Greys that hold still, a little dimmer. */
        public val Mono: VoiceGlowColors = palette(
            dark = intArrayOf(215, 215, 215, 180, 180, 180, 190, 190, 190, 160, 160, 160, 170, 170, 170, 150, 150, 150, 155, 155, 155),
            light = intArrayOf(60, 60, 60, 90, 90, 90, 85, 85, 85, 110, 110, 110, 105, 105, 105, 125, 125, 125, 120, 120, 120),
            drift = false,
            dim = 0.6f,
        )
        public val Ocean: VoiceGlowColors = palette(
            dark = intArrayOf(80, 140, 255, 40, 200, 230, 120, 90, 255, 30, 170, 210, 160, 80, 240, 60, 110, 255, 40, 190, 180),
            light = intArrayOf(40, 100, 240, 20, 160, 200, 90, 60, 230, 20, 130, 180, 130, 50, 220, 40, 80, 230, 20, 150, 150),
        )
        public val Sunset: VoiceGlowColors = palette(
            dark = intArrayOf(255, 110, 60, 255, 180, 40, 255, 60, 90, 255, 210, 80, 240, 70, 140, 255, 140, 50, 230, 50, 110),
            light = intArrayOf(235, 80, 30, 230, 150, 10, 230, 30, 70, 225, 175, 30, 215, 40, 110, 235, 110, 20, 205, 30, 90),
        )
        public val Forest: VoiceGlowColors = palette(
            dark = intArrayOf(70, 220, 120, 40, 200, 180, 140, 230, 80, 30, 170, 140, 190, 235, 70, 50, 190, 110, 30, 150, 120),
            light = intArrayOf(30, 170, 80, 20, 150, 130, 90, 180, 30, 20, 130, 100, 130, 180, 20, 30, 150, 80, 20, 120, 90),
        )
        public val Candy: VoiceGlowColors = palette(
            dark = intArrayOf(255, 90, 170, 255, 120, 220, 210, 80, 255, 255, 150, 190, 180, 110, 255, 255, 70, 140, 230, 100, 240),
            light = intArrayOf(235, 40, 140, 230, 70, 190, 180, 40, 230, 235, 100, 160, 150, 70, 230, 230, 30, 110, 200, 60, 210),
        )
        public val Ice: VoiceGlowColors = palette(
            dark = intArrayOf(150, 230, 255, 90, 200, 255, 190, 240, 255, 120, 190, 255, 160, 220, 250, 80, 170, 255, 200, 235, 255),
            light = intArrayOf(30, 160, 220, 20, 130, 210, 60, 180, 230, 40, 120, 220, 50, 160, 220, 20, 110, 220, 70, 170, 230),
        )
        public val Gold: VoiceGlowColors = palette(
            dark = intArrayOf(255, 200, 70, 255, 170, 40, 255, 220, 110, 240, 150, 30, 255, 235, 140, 230, 160, 40, 250, 210, 90),
            light = intArrayOf(200, 140, 10, 190, 120, 0, 210, 160, 30, 180, 110, 0, 205, 170, 40, 175, 115, 5, 195, 150, 20),
        )

        /**
         * A palette around one colour, such as a brand colour: [seed] at the
         * centre and its neighbours in hue on the lobes beside it, [hueSpread]
         * degrees to each side at the widest. It is made bright enough to glow
         * on a dark background and deep enough to show on a light one, so a
         * very dark or very pale seed keeps its hue more than its lightness.
         */
        public fun from(seed: Color, hueSpread: Float = 48f): VoiceGlowColors {
            val base = OkLab.of(GlowRgb.of(seed))
            // Per lobe, centre first then the pairs outward: a share of the spread, and a small step in lightness.
            val turns = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 0.25f, -0.25f)
            val steps = floatArrayOf(0f, 0.04f, -0.03f, 0.06f, -0.05f, 0.02f, -0.02f)
            fun around(lightness: ClosedFloatingPointRange<Float>) =
                Array(GlowLobes.size) { i -> base.shifted(hueSpread * turns[i], (base.l.coerceIn(lightness) + steps[i]), minChroma = 0.12f) }
            return VoiceGlowColors(around(0.66f..0.86f), around(0.5f..0.7f), band = null, mood = VoiceMoodColors.Standard, drift = true, dim = 1f)
        }

        private fun palette(dark: IntArray, light: IntArray, drift: Boolean = true, dim: Float = 1f) =
            VoiceGlowColors(channels(dark).lab(), channels(light).lab(), band = null, mood = VoiceMoodColors.Standard, drift = drift, dim = dim)
    }
}

/**
 * The colours of the band, the bright line on the glow's ceiling: its [core],
 * the fringe that rides [above] it, the fringe [below] it, and the faint
 * third colour [between] the core and the upper fringe.
 */
@Immutable
public data class VoiceGlowBandColors(val core: Color, val above: Color, val below: Color, val between: Color = core)

/**
 * The colours the mood plane maps to: one set of seven per corner, for a dark
 * and for a light background.
 *
 * ```
 *  arousal 1   angry ──────── happy
 *                │              │
 *  arousal 0    sad  ──────── calm
 *            valence −1     valence +1
 * ```
 *
 * A mood is the blend of the four corners at its (valence, arousal), mixed in
 * OKLab so the in-betweens stay clean: red to green passes through amber, not
 * mud. Anything below −0.35 valence is fully the negative side, above +0.35
 * fully the positive side.
 */
@Immutable
public class VoiceMoodColors private constructor(private val dark: Corners, private val light: Corners) {
    internal class Corners(val happy: Array<OkLab>, val angry: Array<OkLab>, val sad: Array<OkLab>, val calm: Array<OkLab>) {
        fun same(other: Corners) =
            happy.contentEquals(other.happy) && angry.contentEquals(other.angry) && sad.contentEquals(other.sad) && calm.contentEquals(other.calm)
    }

    override fun equals(other: Any?): Boolean = other is VoiceMoodColors && dark.same(other.dark) && light.same(other.light)

    override fun hashCode(): Int = 31 * dark.happy.contentHashCode() + light.happy.contentHashCode()

    /** One list of colours per corner, used on both backgrounds. Fewer than seven repeat. */
    public constructor(happy: List<Color>, angry: List<Color>, sad: List<Color>, calm: List<Color>) :
        this(Corners(happy.lab(), angry.lab(), sad.lab(), calm.lab()))

    /** One colour per corner: the simplest way to say what each feeling looks like. */
    public constructor(happy: Color, angry: Color, sad: Color, calm: Color) :
        this(listOf(happy), listOf(angry), listOf(sad), listOf(calm))

    private constructor(both: Corners) : this(both, both)

    internal fun colors(valence: Float, arousal: Float, isDark: Boolean): Array<OkLab> {
        val c = if (isDark) dark else light
        val t = ((valence + 0.35f) / 0.7f).coerceIn(0f, 1f)
        val u = t * t * (3f - 2f * t)
        val a = arousal.coerceIn(0f, 1f)
        return Array(GlowLobes.size) { i -> c.sad[i].mixed(c.calm[i], u).mixed(c.angry[i].mixed(c.happy[i], u), a) }
    }

    public companion object {
        /** Happy green and calm teal; every negative mood red, angry a hot red and sad a deeper crimson. */
        public val Standard: VoiceMoodColors = VoiceMoodColors(
            dark = Corners(
                happy = channels(intArrayOf(70, 230, 120, 150, 235, 70, 40, 215, 165, 190, 240, 80, 60, 220, 100, 30, 195, 140, 120, 230, 90)).lab(),
                angry = channels(intArrayOf(255, 50, 55, 255, 85, 60, 235, 30, 80, 255, 65, 45, 240, 40, 100, 255, 100, 75, 215, 30, 50)).lab(),
                sad = channels(intArrayOf(200, 30, 60, 225, 45, 75, 180, 25, 70, 210, 40, 55, 190, 30, 90, 230, 60, 80, 170, 20, 50)).lab(),
                calm = channels(intArrayOf(60, 210, 200, 90, 200, 255, 80, 230, 170, 40, 180, 215, 120, 220, 235, 50, 200, 160, 100, 190, 240)).lab(),
            ),
            light = Corners(
                happy = channels(intArrayOf(30, 175, 75, 100, 185, 25, 20, 160, 120, 140, 190, 30, 25, 165, 60, 15, 145, 100, 80, 175, 45)).lab(),
                angry = channels(intArrayOf(220, 30, 40, 230, 60, 35, 205, 20, 65, 225, 45, 30, 210, 25, 85, 230, 75, 50, 185, 20, 40)).lab(),
                sad = channels(intArrayOf(180, 25, 50, 200, 40, 60, 160, 20, 60, 190, 35, 45, 170, 25, 75, 205, 50, 65, 145, 15, 40)).lab(),
                calm = channels(intArrayOf(20, 165, 160, 40, 150, 220, 30, 175, 130, 20, 135, 175, 60, 165, 200, 25, 155, 125, 50, 145, 205)).lab(),
            ),
        )
    }
}

/** Colours from channel values, 0–255, three per colour. */
private fun channels(values: IntArray): List<Color> = List(values.size / 3) { i -> Color(values[i * 3], values[i * 3 + 1], values[i * 3 + 2]) }

private fun List<Color>.lab(): Array<OkLab> {
    require(isNotEmpty()) { "A glow needs at least one colour" }
    return Array(GlowLobes.size) { i -> OkLab.of(GlowRgb.of(this[i % size])) }
}
