package io.github.dwite.voiceglow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import io.github.dwite.voiceglow.internal.GlowConfig
import io.github.dwite.voiceglow.internal.GlowEngine
import io.github.dwite.voiceglow.internal.GlowTheme
import io.github.dwite.voiceglow.internal.HaloMotion
import io.github.dwite.voiceglow.internal.HaloPainter
import kotlinx.coroutines.flow.first

/**
 * The voice glow as a ring around a figure: an avatar, a mascot, an agent's
 * orb. A fine ring of light shows as soon as the halo is on, the sign that
 * someone is listening; it swells and blooms into colour with a voice, the
 * colours travelling around it. A [mood] tints it with how the voice feels.
 *
 * This arrangement is not part of the original voice-glow. It is made of the
 * same parts (the seven lobes, the band with its fringes, the same response
 * to a voice) and takes the same [colors], [mood] and [options].
 *
 * Draw it behind the figure, in a box around it; [VoiceHaloBox] does that.
 * The ring sits near the edge of the box's shorter side, and its light reaches
 * a little past the box.
 *
 * ```
 * VoiceHaloBox(
 *     level = { if (agent.isSpeaking) agent.loudness else microphone.loudness },
 *     outgoing = agent.isSpeaking,
 *     colors = if (agent.isSpeaking) agentColors else VoiceGlowColors.Colorful,
 *     modifier = Modifier.size(220.dp),
 * ) {
 *     Avatar(Modifier.size(160.dp))
 * }
 * ```
 *
 * @param level the voice, 0–1, read every frame without recomposing.
 * @param colors the lobe, band and mood colours; a new set crosses over, so the ring can change with the speaker.
 * @param theme the background the halo is tuned for.
 * @param mood how the voice feels; [VoiceMood.Neutral] keeps the halo's own colours.
 * @param strength how much of the halo shows, 0–1; it can be animated freely.
 * @param active fades the halo in and out.
 * @param paused holds the halo exactly where it is, without fading it out.
 * @param outgoing the voice is the figure's own: soft waves leave the ring
 *   while it sounds, so speaking does not look like being listened to.
 * @param shape the ring's own geometry and motion.
 * @param options the response to the voice, the light and the mood, as for [VoiceGlow].
 *   Of the band's options, `bandStrength` and `bandAberration` apply; the options
 *   that shape the bottom-edge glow (`reach`, `bend`, the lobe and range sizes) do not.
 * @param animated off draws the one frame a steady [level] settles on: for previews and screenshot tests.
 */
@Composable
public fun VoiceHalo(
    level: () -> Float,
    modifier: Modifier = Modifier,
    colors: VoiceGlowColors = VoiceGlowColors.Colorful,
    theme: VoiceGlowTheme = VoiceGlowTheme.Auto,
    mood: VoiceMood = VoiceMood.Neutral,
    strength: Float = 1f,
    active: Boolean = true,
    paused: Boolean = false,
    outgoing: Boolean = false,
    shape: VoiceHaloShape = DefaultShape,
    options: VoiceGlowOptions = DefaultHaloOptions,
    animated: Boolean = true,
) {
    val dark = when (theme) {
        VoiceGlowTheme.Dark -> true
        VoiceGlowTheme.Light -> false
        VoiceGlowTheme.Auto -> isSystemInDarkTheme()
    }
    // The phone type's response and light: the halo was tuned with them.
    val config = remember(dark, options, colors) { GlowConfig(VoiceGlowType.Mobile, if (dark) GlowTheme.Dark else GlowTheme.Light, options, colors) }
    val engine = remember { GlowEngine() }
    val motion = remember { HaloMotion() }
    val painter = remember { HaloPainter() }
    val latestLevel by rememberUpdatedState(level)
    val latestActive by rememberUpdatedState(active)
    val latestPaused by rememberUpdatedState(paused)
    val latestOutgoing by rememberUpdatedState(outgoing)
    val latestMood by rememberUpdatedState(mood)
    val latestColors by rememberUpdatedState(colors)
    val latestConfig by rememberUpdatedState(config)
    val latestShape by rememberUpdatedState(shape)
    var frameTime by remember { mutableLongStateOf(0L) }

    if (animated) {
        LaunchedEffect(engine) {
            // With system animations off the halo still follows the voice, but nothing drifts on its own.
            val still = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
            var last = 0L
            while (true) {
                // Asleep while hidden or paused: no frames are asked for until it is needed again.
                if (latestPaused || (!latestActive && !engine.isVisible)) {
                    snapshotFlow { !latestPaused && (latestActive || engine.isVisible) }.first { it }
                    last = 0L
                }
                withFrameNanos { now ->
                    val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    last = now
                    val frame = engine.step(dt, latestLevel(), latestActive, latestMood, latestColors, latestConfig, still)
                    if (!still) motion.step(dt, frame, latestShape, latestOutgoing && latestActive)
                    frameTime = now
                }
            }
        }
    }

    Canvas(modifier) {
        val frame = if (animated) {
            frameTime // Read here, so each frame only redraws.
            engine.frame
        } else {
            if (!active) return@Canvas
            engine.settle(level(), mood, colors, config).also { motion.settle(it, outgoing) }
        }
        if (frame.presence > 0.002f) with(painter) { drawHalo(frame, config, shape, motion, strength) }
    }
}

/**
 * [content] (the figure) centred on a [VoiceHalo] that fills this box. Give
 * the box the size of the ring: the figure should be about three quarters of
 * it, so that the ring runs around it. See [VoiceHalo] for the parameters.
 */
@Composable
public fun VoiceHaloBox(
    level: () -> Float,
    modifier: Modifier = Modifier,
    colors: VoiceGlowColors = VoiceGlowColors.Colorful,
    theme: VoiceGlowTheme = VoiceGlowTheme.Auto,
    mood: VoiceMood = VoiceMood.Neutral,
    strength: Float = 1f,
    active: Boolean = true,
    paused: Boolean = false,
    outgoing: Boolean = false,
    shape: VoiceHaloShape = DefaultShape,
    options: VoiceGlowOptions = DefaultHaloOptions,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        VoiceHalo(level, Modifier.matchParentSize(), colors, theme, mood, strength, active, paused, outgoing, shape, options)
        content()
    }
}

/**
 * The geometry and motion of a [VoiceHalo]. Every size is a share of the
 * shorter side of the halo's box, so the halo scales with the box.
 */
@Immutable
public data class VoiceHaloShape(
    /** The ring's radius at rest. 0.5 would touch the box's edge. */
    val radius: Float = 0.485f,
    /** How far a full voice widens the ring. */
    val swell: Float = 0.03f,
    /** How far the coloured light reaches inside the ring, toward the figure. */
    val auraInside: Float = 0.155f,
    /** How far the coloured light reaches outside the ring. */
    val auraOutside: Float = 0.105f,
    /** Thickness of the band, the bright ring that rises with the voice. 0 leaves only the fine line and the aura. */
    val bandWidth: Float = 0.032f,
    /** How visible the fine ring is while silent, 0–1. 0 shows nothing until a voice comes. */
    val restingRing: Float = 0.34f,
    /** Seconds the colours take to travel once around at full voice. 0 holds them in place. */
    val turnSeconds: Float = 5.7f,
    /** How far a wave of an outgoing voice travels from the ring before it is gone. */
    val waveTravel: Float = 0.11f,
    /** Seconds between two waves, and the seconds each one travels. */
    val waveEvery: Float = 0.7f,
    val waveSeconds: Float = 1.5f,
)

private val DefaultShape = VoiceHaloShape()
private val DefaultHaloOptions = VoiceGlowOptions()
