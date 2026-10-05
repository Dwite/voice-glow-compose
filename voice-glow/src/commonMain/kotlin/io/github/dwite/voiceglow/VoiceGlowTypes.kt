package io.github.dwite.voiceglow

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/** Host preset: retunes the glow's geometry for what it sits in. */
public enum class VoiceGlowType {
    /** A chat input or a card, about 350 dp wide. */
    Standard,

    /** A small recording pill, about 150 × 44 dp: the glow pulled in, a shallower bend. */
    Pill,

    /** The bottom of a phone screen: a wider range, a taller rise. */
    Mobile,
}

/** The background the glow is tuned for. */
public enum class VoiceGlowTheme {
    Dark,
    Light,

    /** Follows the system's dark theme setting. */
    Auto,
}

/**
 * How a voice feels, as the two axes emotion research uses.
 *
 * - [valence]: −1 (negative: angry, sad) … +1 (positive: happy, calm).
 * - [arousal]: 0 (calm, low energy) … 1 (excited, high energy).
 * - [confidence]: 0 … 1, how sure the source is. The glow only leaves its own
 *   colours in proportion to it, so an unsure estimate stays quiet. Below 15%
 *   the glow keeps its colours.
 *
 * Any source can make one: a sentiment model on the transcript, an emotion
 * model on the audio, a server, a slider in a demo.
 */
@Immutable
public data class VoiceMood(val valence: Float, val arousal: Float, val confidence: Float = 1f) {
    public companion object {
        /** No opinion: the glow keeps its colours. */
        public val Neutral: VoiceMood = VoiceMood(valence = 0f, arousal = 0.3f, confidence = 0f)
        public val Happy: VoiceMood = VoiceMood(valence = 0.8f, arousal = 0.75f)
        public val Angry: VoiceMood = VoiceMood(valence = -0.75f, arousal = 0.85f)
        public val Sad: VoiceMood = VoiceMood(valence = -0.7f, arousal = 0.15f)
        public val Calm: VoiceMood = VoiceMood(valence = 0.6f, arousal = 0.15f)

        /**
         * Two reads of one voice, merged: how it sounds ([tone]) and what it
         * says ([meaning]). Each axis trusts the source that reads it better,
         * the words for most of the valence and the tone for most of the
         * arousal, and each source counts in proportion to its confidence.
         * Either may be null or unsure; the other then speaks alone.
         */
        public fun blend(tone: VoiceMood?, meaning: VoiceMood?, meaningWeight: Float = 0.65f): VoiceMood {
            val t = tone ?: Neutral
            val m = meaning ?: Neutral
            val w = meaningWeight.coerceIn(0f, 1f)
            if (t.confidence + m.confidence <= 0.001f) return VoiceMood(m.valence, t.arousal, 0f)
            val tv = t.confidence * (1f - w)
            val mv = m.confidence * w
            val ta = t.confidence * w
            val ma = m.confidence * (1f - w)
            return VoiceMood(
                valence = if (tv + mv > 0f) (t.valence * tv + m.valence * mv) / (tv + mv) else 0f,
                arousal = if (ta + ma > 0f) (t.arousal * ta + m.arousal * ma) / (ta + ma) else 0.3f,
                // Agreeing sources reinforce; the stronger one sets the floor.
                confidence = 1f - (1f - t.confidence) * (1f - m.confidence),
            )
        }
    }
}

/**
 * Every fine adjustment of [VoiceGlow], under the names the original's props
 * have, so numbers tuned there can be carried over as they are. Every `null`
 * keeps the value of the [VoiceGlowType] and theme in use.
 *
 * The overall [VoiceGlow] `strength` and `scale`, the colours and the corner
 * radius are parameters of the composable itself.
 */
@Immutable
public data class VoiceGlowOptions(
    // How it answers the sound.
    /** Gain on the level before anything else. Raise it for a quiet source, such as the raw loudness of a microphone. */
    val sensitivity: Float = 1f,
    /** Noise gate, 0–1: levels below it are silence. */
    val threshold: Float = 0.015f,
    /** Seconds the glow takes to rise toward a louder level. */
    val attack: Float = 0.325f,
    /** Seconds the glow takes to settle after the sound drops. */
    val release: Float = 0.86f,
    /** Resting presence, 0–1: how much the glow breathes while silent. 0 hides it between sounds. */
    val idle: Float? = null,
    /** Period of the idle breathing, in seconds. */
    val breatheSeconds: Float = 5.2f,
    /** The lobes ripple with slow, out-of-phase wobbles of the level (the original's `bands`). */
    val ripple: Boolean = true,

    // How the voice moves it.
    /** How tall the glow grows at full level, as a multiple of its resting height. */
    val reach: Float? = null,
    /** How far the glow widens at full level, as a share of its resting width. */
    val spread: Float? = null,
    /** dp per second the colours travel sideways at full level; negative flows the other way, 0 holds them. */
    val flow: Float? = null,
    /** How far, in dp, the glow's ceiling humps up at the centre at full level. 0 also hides the band. */
    val bend: Float? = null,

    // Its light.
    val brightness: Float? = null,
    val saturation: Float? = null,
    /** The colours wander this many degrees of hue each way; 0 holds them still. */
    val hueRange: Float? = null,
    /** Period of the hue drift, in seconds. */
    val hueSeconds: Float? = null,
    /** The softness of the bloom: below 1 tighter, above 1 wider and softer. */
    val glowSize: Float? = null,
    /** Opacity of the fine line on the edge, on top of the theme's own. */
    val strokeOpacity: Float? = null,
    /** Opacity of the soft light along the inside of the edges, on top of the theme's own. */
    val innerOpacity: Float? = null,
    /** Opacity of the wide bloom, on top of the theme's own. */
    val bloomOpacity: Float? = null,

    // The band: the bright line on the glow's ceiling.
    /** Opacity of the band; 0 hides it. */
    val bandStrength: Float? = null,
    /** Thickness of the band. */
    val bandWidth: Float? = null,
    /** Height of the band's peak as a share of the glow's ceiling, about 0.1–1.3. */
    val bandPosition: Float? = null,
    /** The bell's exponent: below 2 a cusp-like rise, 2 a Gaussian, above a flatter top. */
    val bandCurve: Float? = null,
    /** The bell's width as a share of the glow's half-range: small is a narrow spike, large a broad dome. */
    val bandSpread: Float? = null,
    /** Asymmetry, −0.6–0.6: positive widens the right side and steepens the left. */
    val bandSkew: Float? = null,
    /** Vertical shift of the whole band line, in dp; negative sinks it toward the edge. */
    val bandOffset: Float? = null,
    /** How far the band's ends rise again toward the corners, as a share of its peak, 0–1. 0 ends it inside the host. */
    val bandTail: Float? = null,
    /** Where that rise starts, as a share of the way from the centre to the host's edge. */
    val bandTailPosition: Float? = null,
    /** Exponent of the rise: 1 a straight ramp, 2 a parabola, higher a hook that whips up at the corner. */
    val bandTailCurve: Float? = null,
    /** dp the band runs past each side of the host, so the hook peaks outside and is cropped. */
    val bandTailOverflow: Float? = null,
    /** How far the band's warm and cool fringes split from its core, 0–1. */
    val bandAberration: Float? = null,

    // The lobes: the seven lights the glow is made of.
    /** Width of every lobe. */
    val glowWidth: Float? = null,
    /** Height of every lobe. */
    val glowHeight: Float? = null,
    /** Distance between the lobes, and the length of the ring the flow carries them around. */
    val lobeSpacing: Float? = null,
    /** Width of the ellipse the glow is kept to. */
    val rangeWidth: Float? = null,
    /** Height of the ellipse the glow is kept to. */
    val rangeHeight: Float? = null,
    /** Where each lobe fades out: below 1 crisper, above 1 softer. */
    val softness: Float? = null,
    /** Size of the lobes in the fine line on the edge. */
    val strokeScale: Float? = null,
    /** Size of the lobes in the soft light along the inside of the edges. */
    val innerScale: Float? = null,
    /** Extra height of that inner light: how far it reaches into the host. */
    val innerHeight: Float? = null,
    /** Size of the lobes in the bloom. */
    val bloomScale: Float? = null,
    /** Extra height of the bloom: how far the soft light climbs. */
    val bloomHeight: Float? = null,

    // The core.
    /** Size of the hot spot at the centre of the edge. */
    val coreSize: Float? = null,
    /** The white wash at the source, under the band, 0–3. The light theme uses 1.8; 0 is none. */
    val coreLight: Float? = null,
    /** Width of that wash. */
    val coreLightWidth: Float? = null,
    /** Height of that wash. */
    val coreLightHeight: Float? = null,

    // The mood.
    /** How far a confident mood takes the glow from its colours, 0–1. */
    val moodStrength: Float = 1f,
    /** Seconds the mood colour takes to follow a new estimate. Raise it if a noisy source flickers. */
    val moodSmoothing: Float = 0.3f,
    /** Seconds the glow takes to fall back to its colours when the estimate loses confidence. */
    val moodRelease: Float = 0.8f,
)

/**
 * Thins the glow with height, like light in mist: all of it up to [from] above
 * the bottom edge, only [keep] of it from [to] upward. For a screen with
 * buttons and a line of text near its foot: the light stays bright behind the
 * buttons and the text above them stays easy to read. Not in the original.
 */
@Immutable
public data class VoiceGlowHaze(val from: Dp, val to: Dp, val keep: Float)
