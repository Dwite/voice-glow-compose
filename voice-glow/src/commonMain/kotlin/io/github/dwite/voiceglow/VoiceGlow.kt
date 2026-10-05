package io.github.dwite.voiceglow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dwite.voiceglow.internal.GlowConfig
import io.github.dwite.voiceglow.internal.GlowEngine
import io.github.dwite.voiceglow.internal.GlowPainter
import io.github.dwite.voiceglow.internal.GlowTheme
import kotlinx.coroutines.flow.first

/**
 * A sound-reactive glow: a centred, colourful light along the bottom edge of
 * its box that rises and blooms with a voice, with a bright band riding its
 * ceiling. A [mood] tints it with how the voice feels.
 *
 * A Compose Multiplatform port of Jakub Antalik's voice-glow
 * (libraries.dev/voice). Give it the size of its host and draw it over the
 * host's content, as the original does, or behind it; [VoiceGlowBox] does the
 * first for you.
 *
 * ```
 * VoiceGlow(
 *     level = { meter.level },
 *     type = VoiceGlowType.Mobile,
 *     colors = VoiceGlowColors.from(MaterialTheme.colorScheme.primary),
 *     strength = 0.7f,
 * )
 * ```
 *
 * @param level the voice, 0–1, read every frame without recomposing: the
 *   loudness of a microphone, of a playing voice, of a speech API's volume
 *   event. A steady value works too; the glow moves on its own.
 * @param type the host the glow sits in. It sets the starting point of everything in [options].
 * @param colors the lobe, band and mood colours; a new set crosses over.
 * @param theme the background the glow is tuned for.
 * @param mood how the voice feels; [VoiceMood.Neutral] keeps the glow's own colours.
 * @param strength how much of the glow shows, 0–1; it can be animated freely.
 * @param scale sizes the whole effect as one thing, on top of the [type]'s own size.
 * @param active fades the glow in and out.
 * @param paused holds the glow exactly where it is, without fading it out.
 * @param cornerRadius the host's corner radius, which the glow is cut to and its edge line follows.
 * @param options every finer adjustment.
 * @param haze thins the light with height, where text sits low on a screen.
 * @param animated off draws the one frame a steady [level] settles on: for previews and screenshot tests.
 */
@Composable
public fun VoiceGlow(
    level: () -> Float,
    modifier: Modifier = Modifier,
    type: VoiceGlowType = VoiceGlowType.Standard,
    colors: VoiceGlowColors = VoiceGlowColors.Colorful,
    theme: VoiceGlowTheme = VoiceGlowTheme.Auto,
    mood: VoiceMood = VoiceMood.Neutral,
    strength: Float = 1f,
    scale: Float = 1f,
    active: Boolean = true,
    paused: Boolean = false,
    cornerRadius: Dp = 0.dp,
    options: VoiceGlowOptions = DefaultOptions,
    haze: VoiceGlowHaze? = null,
    animated: Boolean = true,
) {
    val dark = when (theme) {
        VoiceGlowTheme.Dark -> true
        VoiceGlowTheme.Light -> false
        VoiceGlowTheme.Auto -> isSystemInDarkTheme()
    }
    val config = remember(type, dark, options, colors, scale) { GlowConfig(type, if (dark) GlowTheme.Dark else GlowTheme.Light, options, colors, scale) }
    val engine = remember { GlowEngine() }
    val painter = remember { GlowPainter() }
    val latestLevel by rememberUpdatedState(level)
    val latestActive by rememberUpdatedState(active)
    val latestPaused by rememberUpdatedState(paused)
    val latestMood by rememberUpdatedState(mood)
    val latestColors by rememberUpdatedState(colors)
    val latestConfig by rememberUpdatedState(config)
    var frameTime by remember { mutableLongStateOf(0L) }

    if (animated) {
        LaunchedEffect(engine) {
            // With system animations off the glow still follows the voice, but nothing drifts on its own.
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
                    engine.step(dt, latestLevel(), latestActive, latestMood, latestColors, latestConfig, still)
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
            engine.settle(level(), mood, colors, config)
        }
        if (frame.presence > 0.002f) with(painter) { drawGlow(frame, config, strength, cornerRadius.toPx(), haze) }
    }
}

/**
 * [content] with a [VoiceGlow] over it, cut to [cornerRadius]: the shape of
 * the original component. The glow takes no touches. See [VoiceGlow] for the
 * parameters.
 */
@Composable
public fun VoiceGlowBox(
    level: () -> Float,
    modifier: Modifier = Modifier,
    type: VoiceGlowType = VoiceGlowType.Standard,
    colors: VoiceGlowColors = VoiceGlowColors.Colorful,
    theme: VoiceGlowTheme = VoiceGlowTheme.Auto,
    mood: VoiceMood = VoiceMood.Neutral,
    strength: Float = 1f,
    scale: Float = 1f,
    active: Boolean = true,
    paused: Boolean = false,
    cornerRadius: Dp = 0.dp,
    options: VoiceGlowOptions = DefaultOptions,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        content()
        VoiceGlow(level, Modifier.matchParentSize(), type, colors, theme, mood, strength, scale, active, paused, cornerRadius, options)
    }
}

private val DefaultOptions = VoiceGlowOptions()
