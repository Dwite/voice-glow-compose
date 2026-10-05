package io.github.dwite.voiceglow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws the halo off screen with Skia and checks the picture. Set
 * `VOICE_GLOW_SNAPSHOTS=/some/dir` to also write the pictures there.
 */
class HaloSnapshotTest {
    private val ground = 0x1D / 255f

    private fun render(
        level: Float,
        dark: Boolean = true,
        colors: VoiceGlowColors = VoiceGlowColors.Colorful,
        mood: VoiceMood = VoiceMood.Neutral,
        strength: Float = 1f,
        outgoing: Boolean = false,
        shape: VoiceHaloShape = VoiceHaloShape(),
    ): Image {
        val scene = ImageComposeScene(width = Side * Scale, height = Side * Scale, density = Density(Scale.toFloat())) {
            Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF1D1D1D) else Color(0xFFF4F4F5)), contentAlignment = Alignment.Center) {
                VoiceHalo(
                    level = { level },
                    modifier = Modifier.size(Ring.dp),
                    colors = colors,
                    theme = if (dark) VoiceGlowTheme.Dark else VoiceGlowTheme.Light,
                    mood = mood,
                    strength = strength,
                    outgoing = outgoing,
                    shape = shape,
                    animated = false,
                )
            }
        }
        return scene.render().also { scene.close() }
    }

    private fun Image.save(name: String) = apply {
        val dir = System.getenv("VOICE_GLOW_SNAPSHOTS") ?: return@apply
        File(dir, "$name.png").apply { parentFile.mkdirs() }.writeBytes(encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    /** The colour at a distance from the centre, as a share of the ring box's side, to the right of it. */
    private fun Image.atRadius(share: Float): Triple<Float, Float, Float> {
        val pixels = toComposeImageBitmap().toPixelMap()
        val color = pixels[(pixels.width / 2 + share * Ring * Scale).toInt().coerceIn(0, pixels.width - 1), pixels.height / 2]
        return Triple(color.red, color.green, color.blue)
    }

    private fun Triple<Float, Float, Float>.light() = (first + second + third) / 3f

    /** How far the picture is from the bare ground, over a grid of points. */
    private fun Image.lightAdded(): Float {
        val pixels = toComposeImageBitmap().toPixelMap()
        var total = 0f
        var count = 0
        for (x in 1..19) for (y in 1..19) {
            val c = pixels[x * pixels.width / 20, y * pixels.height / 20]
            total += abs((c.red + c.green + c.blue) / 3f - ground)
            count++
        }
        return total / count
    }

    @Test
    fun aRingShowsAtRestAndBloomsWithAVoice() {
        val rest = render(0f).save("halo_dark_rest")
        val voiced = render(0.6f).save("halo_dark_voice")
        // On the ring there is light even in silence; at the centre, where the figure goes, never.
        assertTrue(rest.atRadius(0.485f).light() > ground + 0.05f, "the resting ring: ${rest.atRadius(0.485f)}")
        assertTrue(abs(voiced.atRadius(0f).light() - ground) < 0.01f, "the centre stays clear: ${voiced.atRadius(0f)}")
        assertTrue(voiced.lightAdded() > rest.lightAdded() * 3f, "a voice makes it bloom: ${rest.lightAdded()} to ${voiced.lightAdded()}")
        render(0.6f, dark = false).save("halo_light_voice")
        render(0.6f, mood = VoiceMood.Happy).save("halo_dark_happy")
    }

    @Test
    fun strengthColoursAndShapeReachThePicture() {
        assertTrue(render(0.6f, strength = 0f).lightAdded() < 0.001f)
        val half = render(0.6f, strength = 0.5f).lightAdded()
        val full = render(0.6f).lightAdded()
        assertTrue(half > full * 0.3f && half < full * 0.7f, "half strength gave $half of $full")

        val (r, g, b) = render(0.6f, colors = VoiceGlowColors(Color.Red), shape = VoiceHaloShape(bandWidth = 0f)).save("halo_dark_red").atRadius(0.44f)
        assertTrue(r > g * 2f && r > b * 2f, "a red halo is red: $r $g $b")

        // A smaller ring: light where the default has none, and none where the default has its ring.
        val small = render(0.6f, shape = VoiceHaloShape(radius = 0.3f)).save("halo_dark_small")
        assertTrue(small.atRadius(0.3f).light() > ground + 0.1f)
        assertTrue(abs(small.atRadius(0.5f).light() - ground) < 0.02f)

        // No resting ring: nothing at all until a voice comes.
        assertTrue(render(0f, shape = VoiceHaloShape(restingRing = 0f)).atRadius(0.485f).light() < render(0f).atRadius(0.485f).light())
    }

    @Test
    fun anOutgoingVoiceSendsWavesPastTheRing() {
        val listening = render(0.6f)
        val speaking = render(0.6f, outgoing = true, colors = VoiceGlowColors.Candy).save("halo_dark_outgoing")
        // The still picture's wave is 40% of its way: between the ring and the end of its travel.
        val wave = 0.485f + 0.03f * 0.9f + 0.11f * (1f - 0.6f * 0.6f)
        assertTrue(speaking.atRadius(wave).light() > listening.atRadius(wave).light() + 0.02f, "a wave at $wave: ${speaking.atRadius(wave)} against ${listening.atRadius(wave)}")
    }

    /** The picture of the README: a figure listening, hearing a happy voice, and speaking, on both backgrounds. */
    @Test
    fun gallery() {
        val dir = System.getenv("VOICE_GLOW_SNAPSHOTS") ?: return
        for (dark in listOf(true, false)) {
            val scene = ImageComposeScene(width = 1840, height = 560, density = Density(2f)) { HaloGallery(dark) }
            File(dir, "halo_gallery_${if (dark) "dark" else "light"}.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }

    private companion object {
        const val Scale = 2
        const val Ring = 200
        const val Side = 300
    }
}

@Composable
private fun HaloGallery(dark: Boolean) {
    val theme = if (dark) VoiceGlowTheme.Dark else VoiceGlowTheme.Light
    Row(
        Modifier.fillMaxSize().background(if (dark) Color(0xFF141416) else Color(0xFFEDEDF0)).padding(horizontal = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Figure(0f, theme)
        Figure(0.7f, theme)
        Figure(0.7f, theme, mood = VoiceMood.Happy)
        Figure(0.5f, theme, colors = VoiceGlowColors.Candy, outgoing = true)
    }
}

@Composable
private fun Figure(level: Float, theme: VoiceGlowTheme, colors: VoiceGlowColors = VoiceGlowColors.Colorful, mood: VoiceMood = VoiceMood.Neutral, outgoing: Boolean = false) {
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        VoiceHalo(level = { level }, modifier = Modifier.fillMaxSize(), colors = colors, theme = theme, mood = mood, outgoing = outgoing, animated = false)
        // A stand-in for an avatar.
        Box(Modifier.size(136.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8E7CFF), Color(0xFFFF8FB8)))))
    }
}
