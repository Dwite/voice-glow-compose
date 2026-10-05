package io.github.dwite.voiceglow.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dwite.voiceglow.VoiceGlow
import io.github.dwite.voiceglow.VoiceGlowBox
import io.github.dwite.voiceglow.VoiceGlowColors
import io.github.dwite.voiceglow.VoiceGlowTheme
import io.github.dwite.voiceglow.VoiceGlowType
import io.github.dwite.voiceglow.VoiceHaloBox
import io.github.dwite.voiceglow.VoiceMood
import kotlin.math.abs
import kotlin.math.sin

/** A voice that is not there: bursts of syllables with a breath between the phrases. */
fun speech(seconds: Float): Float {
    if (seconds % 4.4f > 3.1f) return 0f
    return (0.25f + 0.75f * abs(sin(seconds * 7.3f)) * (0.6f + 0.4f * sin(seconds * 2.1f))).coerceIn(0f, 1f)
}

private val Palettes = listOf(
    "Colorful" to VoiceGlowColors.Colorful, "Mono" to VoiceGlowColors.Mono, "Ocean" to VoiceGlowColors.Ocean, "Sunset" to VoiceGlowColors.Sunset,
    "Forest" to VoiceGlowColors.Forest, "Candy" to VoiceGlowColors.Candy, "Ice" to VoiceGlowColors.Ice, "Gold" to VoiceGlowColors.Gold,
    // A palette grown around one colour, as an app would from its brand colour.
    "From blue" to VoiceGlowColors.from(Color(0xFF2F6BFF)),
)
private val Moods = listOf("None" to VoiceMood.Neutral, "Happy" to VoiceMood.Happy, "Calm" to VoiceMood.Calm, "Angry" to VoiceMood.Angry, "Sad" to VoiceMood.Sad)

/**
 * Every type of the glow with everything that can be chosen for it. On a wide
 * window the phone type sits in a phone-shaped box; on a phone it is the
 * screen itself.
 */
@Composable
fun Sample() {
    var dark by remember { mutableStateOf(true) }
    var palette by remember { mutableStateOf(Palettes.first()) }
    var mood by remember { mutableStateOf(Moods.first()) }
    var speaking by remember { mutableStateOf(true) }
    var manual by remember { mutableFloatStateOf(0.6f) }
    var strength by remember { mutableFloatStateOf(1f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var agentSpeaks by remember { mutableStateOf(false) }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { seconds = it / 1e9f } }

    // Read by the glows every frame; nothing here recomposes with it.
    val level = { if (speaking) speech(seconds) else manual }
    val theme = if (dark) VoiceGlowTheme.Dark else VoiceGlowTheme.Light
    val page = if (dark) Color(0xFF0B0B0C) else Color(0xFFE9E9EC)
    val surface = if (dark) Color(0xFF1D1D1D) else Color(0xFFF4F4F5)
    val ink = if (dark) Color(0xFFEDEDED) else Color(0xFF1B1B1F)

    val hosts: @Composable () -> Unit = {
        // An agent's avatar: the ring listens, or sends waves out while the agent itself speaks.
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            VoiceHaloBox(level, Modifier.size(132.dp), palette.second, theme, mood.second, strength, outgoing = agentSpeaks) {
                Box(Modifier.size(94.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8E7CFF), Color(0xFFFF8FB8)))))
            }
            Choices("Halo", listOf("Listening", "Speaking"), if (agentSpeaks) "Speaking" else "Listening", ink) { agentSpeaks = it == "Speaking" }
        }
        // A chat input or a card.
        VoiceGlowBox(level, Modifier.widthIn(max = 350.dp).fillMaxWidth().height(120.dp), VoiceGlowType.Standard, palette.second, theme, mood.second, strength, scale, cornerRadius = 20.dp) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(surface).padding(20.dp)) {
                Text("Listening…", color = ink.copy(alpha = 0.7f), fontSize = 15.sp)
            }
        }
        // A recording pill.
        VoiceGlowBox(level, Modifier.size(150.dp, 44.dp), VoiceGlowType.Pill, palette.second, theme, mood.second, strength, scale, cornerRadius = 22.dp) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(surface), contentAlignment = Alignment.Center) {
                Text("Recording", color = ink, fontSize = 13.sp)
            }
        }
        Choices("Colours", Palettes.map { it.first }, palette.first, ink) { name -> palette = Palettes.first { it.first == name } }
        Choices("Mood", Moods.map { it.first }, mood.first, ink) { name -> mood = Moods.first { it.first == name } }
        Choices("Theme", listOf("Dark", "Light"), if (dark) "Dark" else "Light", ink) { dark = it == "Dark" }
        Choices("Voice", listOf("Speech", "Slider"), if (speaking) "Speech" else "Slider", ink) { speaking = it == "Speech" }
        if (!speaking) Slider(manual, { manual = it }, Modifier.widthIn(max = 350.dp).fillMaxWidth())
        Amount("Strength", strength, 0f..1f, ink) { strength = it }
        Amount("Scale", scale, 0.5f..1.6f, ink) { scale = it }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(page)) {
        if (maxWidth < 720.dp) {
            // On a phone: the bottom of the screen itself, behind everything.
            Box(Modifier.fillMaxSize().background(surface))
            VoiceGlow(level, Modifier.fillMaxSize(), VoiceGlowType.Mobile, palette.second, theme, mood.second, strength, scale)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) { hosts() }
        } else {
            Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                VoiceGlowBox(level, Modifier.size(320.dp, 680.dp), VoiceGlowType.Mobile, palette.second, theme, mood.second, strength, scale, cornerRadius = 44.dp) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(44.dp)).background(surface), contentAlignment = Alignment.Center) {
                        Text("How can I help you?", color = ink, fontSize = 18.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) { hosts() }
            }
        }
    }
}

@Composable
private fun Amount(title: String, value: Float, range: ClosedFloatingPointRange<Float>, ink: Color, onChange: (Float) -> Unit) {
    Column {
        Text("$title  ${(value * 100).toInt()}%", color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
        Slider(value, onChange, Modifier.widthIn(max = 350.dp).fillMaxWidth(), valueRange = range)
    }
}

@Composable
private fun Choices(title: String, names: List<String>, chosen: String, ink: Color, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (name in names) {
                Text(
                    name,
                    color = ink,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(ink.copy(alpha = if (name == chosen) 0.22f else 0.07f))
                        .clickable { onChoose(name) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}
