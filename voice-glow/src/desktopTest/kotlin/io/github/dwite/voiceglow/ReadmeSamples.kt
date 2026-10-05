package io.github.dwite.voiceglow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The code of the README, kept here so that it has to compile. Never run. */
@Suppress("unused", "UNUSED_VARIABLE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun ReadmeSamples(brandColor: Color, pink: Color, lavender: Color, violet: Color, onDark: List<Color>, onLight: List<Color>) {
    var level by remember { mutableFloatStateOf(0f) }

    VoiceGlowBox(level = { level }, cornerRadius = 20.dp) {
        Box(Modifier.fillMaxSize())
    }

    Box(Modifier.fillMaxSize()) {
        VoiceGlow(level = { level }, modifier = Modifier.fillMaxSize(), type = VoiceGlowType.Mobile)
    }

    VoiceGlow(
        level = { level },
        type = VoiceGlowType.Mobile,
        colors = VoiceGlowColors.from(brandColor),
        strength = 0.7f,
        scale = 1.2f,
        options = VoiceGlowOptions(reach = 2f, attack = 0.15f, bandStrength = 1.2f),
    )

    val palettes = listOf(
        VoiceGlowColors.Sunset,
        VoiceGlowColors(Color(0xFFFF78BE)),
        VoiceGlowColors(listOf(pink, lavender, violet)),
        VoiceGlowColors(dark = onDark, light = onLight),
        VoiceGlowColors.from(brandColor),
        VoiceGlowColors.from(brandColor, hueSpread = 90f),
        VoiceGlowColors.Ocean.copy(
            band = VoiceGlowBandColors(core = Color.White, above = Color(0xFFFFB347), below = Color(0xFF4FC3F7)),
            mood = VoiceMoodColors(happy = Color(0xFF46E678), angry = Color(0xFFFF7A5C), sad = Color(0xFF5A7BFF), calm = Color(0xFF3CD2C8)),
            drift = false,
        ),
    )

    VoiceGlow(level = { level }, mood = VoiceMood(valence = 0.8f, arousal = 0.7f))
    val merged = VoiceMood.blend(tone = VoiceMood.Calm, meaning = VoiceMood.Happy)

    VoiceGlow(level = { level }, type = VoiceGlowType.Mobile, haze = VoiceGlowHaze(from = 60.dp, to = 90.dp, keep = 0.4f))

    val agentSpeaking = false
    val agentColors = VoiceGlowColors.Candy
    VoiceHaloBox(
        level = { level },
        outgoing = agentSpeaking,
        colors = if (agentSpeaking) agentColors else VoiceGlowColors.Colorful,
        modifier = Modifier.size(220.dp),
    ) {
        Box(Modifier.size(160.dp))
    }
    VoiceHalo(level = { level }, shape = VoiceHaloShape(radius = 0.4f, restingRing = 0f, turnSeconds = 8f), options = VoiceGlowOptions(attack = 0.15f))
}
