package io.github.dwite.voiceglow.internal

import io.github.dwite.voiceglow.VoiceGlowColors
import io.github.dwite.voiceglow.VoiceMood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The driven values of one frame. Reused between frames: read it in the draw phase only. */
internal class GlowFrame {
    var level = 0f

    /** Presence of the whole effect, 0–1 (the fade in and out). */
    var presence = 0f

    /** Layer opacity factor. */
    var glow = 0.4f

    /** Height and spread multipliers. */
    var h = 0.8f
    var w = 1f

    /** The ceiling's extra height in dp, and the band's strength. */
    var lift = 0f
    var bendA = 0f

    /** Per lobe: offset along the flow in dp, and amplitude. */
    val lobeX = FloatArray(GlowLobes.size)
    val lobeL = FloatArray(GlowLobes.size) { 1f }

    /** Lobe colours after the mood blend and the hue drift. */
    val colors = Array(GlowLobes.size) { GlowRgb(0f, 0f, 0f) }

    /**
     * The band: its core and the fringes above, between and below, before
     * [tone]. The band is toned once it is mixed, as the original filters its
     * whole band layer: that keeps the ridge bright and clean.
     */
    var bandCore = GlowRgb(1f, 1f, 1f)
    var bandAbove = GlowRgb(1f, 1f, 1f)
    var bandMid = GlowRgb(1f, 1f, 1f)
    var bandBelow = GlowRgb(1f, 1f, 1f)

    /** This frame's hue drift, brightness and saturation. */
    var tone = GlowColorMatrix.of(0f, 1f, 1f)

    /** How far the glow has moved into the mood colours, 0–1. */
    var moodAmount = 0f
}

/**
 * The voice driver of voice-glow, ported from its `VoiceGlowEngine`: shapes the
 * raw level (noise gate, soft saturation), follows it with an attack and
 * release envelope, slides the lobes sideways while the voice sounds, folds in
 * the idle breathing and the hue drift, and eases the mood colour in.
 */
internal class GlowEngine {
    val frame = GlowFrame()

    private var level = 0f
    private val bands = FloatArray(3)
    private var phase = 0f
    private var t = 0f
    private var presence = 0f

    private var moodValence = 0f
    private var moodArousal = 0.3f
    private var moodAmount = 0f

    private var base: Array<OkLab>? = null

    /** True while the effect is visible or fading; nothing is drawn below this. */
    val isVisible: Boolean get() = presence > 0.002f

    /**
     * Advances the glow by [dt] seconds toward [rawLevel] (0–1). [still] holds
     * everything that moves on its own (flow, breathing, hue drift).
     */
    fun step(dt: Float, rawLevel: Float, active: Boolean, mood: VoiceMood, colors: VoiceGlowColors, c: GlowConfig, still: Boolean = false): GlowFrame {
        t += dt
        val fadeStep = dt / if (active) 0.6f else 0.5f
        presence = if (active) min(1f, presence + fadeStep) else max(0f, presence - fadeStep)

        // No spectrum from a plain level: the bands get slow, out-of-phase
        // wobbles scaled by the level, so the lobes still ripple.
        val raw = (rawLevel * c.sensitivity).coerceIn(0f, 1f)
        level = follow(level, shape(raw, c.threshold), dt, c.attack, c.release)
        for (b in 0 until 3) {
            val wobble = when {
                b == 0 || still -> 1f
                b == 1 -> 0.72f + 0.28f * sin(t * 9.1f)
                else -> 0.6f + 0.4f * sin(t * 13.7f + 2f)
            }
            val target = shape(raw * wobble, c.threshold * 0.6f)
            bands[b] = follow(bands[b], target, dt, c.attack, c.release * 1.15f)
        }

        // Idle breathing folded under the voice.
        val breathe = if (still) 0.5f else 0.5f + 0.5f * sin(2f * PI.toFloat() * t / c.breatheSeconds)
        val eff = level + (1f - level) * c.idle * breathe

        val f = frame
        f.level = level
        f.presence = presence * presence * (3f - 2f * presence)
        f.glow = 0.15f + 0.85f * eff
        f.h = 0.5f + c.reach * eff
        f.w = 0.85f + c.spread * eff
        f.lift = c.bend * eff
        f.bendA = if (c.bend > 0f) eff else 0f

        // Flow: the spectrum slides sideways as the voice comes in.
        val span = c.lobeSpan
        if (!still && c.flow != 0f) phase = ((phase + c.flow * eff * dt) % span + span) % span
        GlowLobes.forEachIndexed { i, lobe ->
            val x = wrapX(lobe.x * c.lobeSpacing + phase, span)
            f.lobeX[i] = x
            f.lobeL[i] = (if (c.ripple) 0.6f + 0.7f * bands[lobe.band] else 1f) * edgeEnvelope(x, span)
        }

        stepColors(dt, mood, colors, c, still)
        return f
    }

    /** The frame a steady [rawLevel] settles on, with nothing in motion: for previews and screenshots. */
    fun settle(rawLevel: Float, mood: VoiceMood, colors: VoiceGlowColors, c: GlowConfig): GlowFrame {
        presence = 1f
        level = shape((rawLevel * c.sensitivity).coerceIn(0f, 1f), c.threshold)
        bands.fill(level)
        moodValence = mood.valence
        moodArousal = mood.arousal
        moodAmount = moodTarget(mood, c)
        base = colors.lab(c.dark)
        return step(0f, rawLevel, active = true, mood = mood, colors = colors, c = c, still = true)
    }

    private fun stepColors(dt: Float, mood: VoiceMood, colors: VoiceGlowColors, c: GlowConfig, still: Boolean) {
        // New colours (a new speaker, a new theme): the base crosses over instead of cutting.
        val target = colors.lab(c.dark)
        val a = 1f - exp(-dt / PaletteSeconds)
        val current = base?.let { now -> Array(now.size) { i -> now[i].mixed(target[i], a) } } ?: target
        base = current

        // The hue follows at full speed once the estimate means it; a
        // barely-there one only nudges it.
        if (mood.confidence > 0f) {
            val pull = (1f - exp(-dt / c.moodSmoothing)) * min(1f, mood.confidence / MoodFloor)
            moodValence += (mood.valence.coerceIn(-1f, 1f) - moodValence) * pull
            moodArousal += (mood.arousal.coerceIn(0f, 1f) - moodArousal) * pull
        }
        // Into a mood fast, back to the glow's own colours a little slower.
        moodAmount = follow(moodAmount, moodTarget(mood, c), dt, c.moodSmoothing, c.moodRelease)

        // Hue drift, calmer while a mood holds the colour.
        val hueRange = c.hueRange * (1f - 0.7f * moodAmount)
        val hue = if (still || c.still || hueRange == 0f) 0f else -hueRange + 2f * hueRange * pingPong(t / c.hueSeconds)
        val matrix = GlowColorMatrix.of(c.hueBase + hue, c.brightness, c.saturation)

        val f = frame
        f.moodAmount = moodAmount
        f.tone = matrix
        val moodColors = if (moodAmount > 0.001f) colors.mood.colors(moodValence, moodArousal, c.dark) else null
        for (i in GlowLobes.indices) {
            val color = moodColors?.let { current[i].mixed(it[i], moodAmount) } ?: current[i]
            f.colors[i] = matrix.apply(color.toRgb())
        }
        // A confident mood tints the fringes (not the core), so the band
        // reads in the mood's colour along with the glow.
        fun fringe(color: OkLab, moodIndex: Int): GlowRgb =
            (moodColors?.let { color.mixed(it[moodIndex], 0.75f * moodAmount) } ?: color).toRgb()
        f.bandCore = c.bandCore
        f.bandAbove = fringe(c.bandAbove, 1)
        f.bandMid = fringe(c.bandMid, 0)
        f.bandBelow = fringe(c.bandBelow, 2)
    }

    private fun moodTarget(mood: VoiceMood, c: GlowConfig): Float =
        max(0f, (mood.confidence.coerceIn(0f, 1f) - MoodFloor) / (1f - MoodFloor)) * c.moodStrength

    internal companion object {
        /** Seconds the base colours take to cross to another set. */
        private const val PaletteSeconds = 0.45f

        /** Below this confidence a mood counts as none, so a neutral voice keeps its colours. */
        private const val MoodFloor = 0.15f

        /** Noise gate, then soft saturation, so a shout rounds off instead of clipping. */
        fun shape(raw: Float, threshold: Float): Float {
            if (raw <= threshold) return 0f
            val t = (raw - threshold) / max(0.001f, 1f - threshold)
            return ((1f - exp(-3f * t)) / (1f - exp(-3f))).coerceIn(0f, 1f)
        }

        /** One-pole follower: fast up ([attack]), slow down ([release]). */
        fun follow(prev: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
            val tau = if (target > prev) attack else release
            return prev + (target - prev) * (1f - exp(-dt / max(0.001f, tau)))
        }

        /** Wraps a lobe offset into [-span/2, span/2). */
        fun wrapX(x: Float, span: Float): Float {
            val half = span / 2f
            val m = (x + half) % span
            return (if (m < 0f) m + span else m) - half
        }

        /** Full at the centre, gone at the wrap edge, so a lobe never pops sides. */
        fun edgeEnvelope(x: Float, span: Float): Float {
            val t = x / (span / 2f + 4f)
            return max(0f, 1f - t * t)
        }

        fun pingPong(phase: Float): Float = (1f - cos(2f * PI.toFloat() * phase)) / 2f
    }
}
