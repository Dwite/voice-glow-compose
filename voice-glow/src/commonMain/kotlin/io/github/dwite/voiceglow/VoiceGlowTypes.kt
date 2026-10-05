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
 * Fine tuning for [VoiceGlow]. Every `null` falls back to the [VoiceGlowType]
 * preset, as the props of the original do.
 */
@Immutable
public data class VoiceGlowOptions(
    /** Noise gate: levels below this are silence. */
    val threshold: Float = 0.015f,
    /** Seconds to rise. */
    val attack: Float = 0.325f,
    /** Seconds to settle. */
    val release: Float = 0.86f,
    /** Breathing presence while silent, 0–1. 0 hides the glow when silent. */
    val idle: Float? = null,
    val breatheSeconds: Float = 5.2f,
    /** The lobes ripple with slow, out-of-phase wobbles of the level. */
    val ripple: Boolean = true,

    /** Sizes the whole effect as one thing, on top of the preset. */
    val scale: Float? = null,
    /** How far the voice lifts the glow. */
    val reach: Float? = null,
    /** How far the voice widens the glow. */
    val spread: Float? = null,
    /** dp per second the colours travel sideways at full level; negative flows the other way, 0 holds them. */
    val flow: Float? = null,
    /** How far the band humps at full level, in dp. */
    val bend: Float? = null,
    val bandStrength: Float? = null,
    val bandWidth: Float? = null,

    val brightness: Float? = null,
    val saturation: Float? = null,
    /** Overall strength, 0–1. */
    val strength: Float? = null,
    /** Hue drift each way, in degrees; 0 holds the colours still. */
    val hueRange: Float? = null,
    val hueSeconds: Float? = null,

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
