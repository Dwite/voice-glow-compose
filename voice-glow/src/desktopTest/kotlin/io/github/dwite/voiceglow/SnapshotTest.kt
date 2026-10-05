package io.github.dwite.voiceglow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws the glow off screen with Skia (the renderer of the desktop, iOS and
 * web targets) and checks the picture.
 *
 * Set `VOICE_GLOW_SNAPSHOTS=/some/dir` to also write every picture there as a
 * PNG, for a side-by-side with the original: see `tools/parity`.
 */
class SnapshotTest {
    private class Host(val name: String, val type: VoiceGlowType, val width: Int, val height: Int, val radius: Int)

    private val hosts = listOf(
        Host("mobile", VoiceGlowType.Mobile, 393, 851, 0),
        Host("standard", VoiceGlowType.Standard, 350, 120, 20),
        Host("pill", VoiceGlowType.Pill, 150, 44, 22),
    )

    private fun render(
        host: Host,
        level: Float,
        dark: Boolean = true,
        mood: VoiceMood = VoiceMood.Neutral,
        colors: VoiceGlowColors = VoiceGlowColors.Colorful,
        haze: VoiceGlowHaze? = null,
    ): Image {
        val scene = ImageComposeScene(width = host.width * Scale, height = host.height * Scale, density = Density(Scale.toFloat())) {
            Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(host.radius.dp)).background(if (dark) Color(0xFF1D1D1D) else Color(0xFFF4F4F5)))
                VoiceGlow(
                    level = { level },
                    modifier = Modifier.fillMaxSize(),
                    type = host.type,
                    mood = mood,
                    colors = colors,
                    theme = if (dark) VoiceGlowTheme.Dark else VoiceGlowTheme.Light,
                    cornerRadius = host.radius.dp,
                    haze = haze,
                    animated = false,
                )
            }
        }
        return scene.render().also { scene.close() }
    }

    private fun Image.save(name: String) {
        val dir = System.getenv("VOICE_GLOW_SNAPSHOTS") ?: return
        File(dir, "$name.png").apply { parentFile.mkdirs() }.writeBytes(encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    /** The colour at a point given in shares of the picture, as (red, green, blue), 0–1. */
    private fun Image.at(x: Float, y: Float): Triple<Float, Float, Float> {
        val pixels = toComposeImageBitmap().toPixelMap()
        val color = pixels[(x * (pixels.width - 1)).toInt(), (y * (pixels.height - 1)).toInt()]
        return Triple(color.red, color.green, color.blue)
    }

    private fun Triple<Float, Float, Float>.light() = (first + second + third) / 3f

    @Test
    fun everyTypeLightsItsFootAndLeavesItsTopAlone() {
        for (host in hosts) {
            for (dark in listOf(true, false)) {
                val theme = if (dark) "dark" else "light"
                val silent = render(host, 0f, dark).also { it.save("${host.name}_${theme}_000") }
                val quiet = render(host, 0.15f, dark).also { it.save("${host.name}_${theme}_015") }
                val voiced = render(host, 0.35f, dark).also { it.save("${host.name}_${theme}_035") }
                val loud = render(host, 1f, dark).also { it.save("${host.name}_${theme}_100") }

                val ground = if (dark) 0x1D / 255f else 0xF4 / 255f
                // The top of a tall host is never touched; the foot changes with the voice.
                if (host.height > 400) assertTrue(kotlin.math.abs(loud.at(0.5f, 0.02f).light() - ground) < 0.02f, "${host.name} $theme top")
                // The largest change of any channel somewhere along the foot: on a light background the
                // glow is pastel and its centre washed white, so its brightness hardly leaves the ground's.
                val change = { image: Image ->
                    listOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f).maxOf { x ->
                        val (r, g, b) = image.at(x, 0.85f)
                        maxOf(kotlin.math.abs(r - ground), kotlin.math.abs(g - ground), kotlin.math.abs(b - ground))
                    }
                }
                assertTrue(change(loud) > 0.08f, "${host.name} $theme: a loud voice changed the foot by only ${change(loud)}")
                assertTrue(change(silent) < change(loud), "${host.name} $theme: silence ${change(silent)} against a loud voice ${change(loud)}")
                assertTrue(quiet.width == voiced.width)
            }
        }
    }

    @Test
    fun aMoodTakesTheColours() {
        val phone = hosts.first()
        val (hr, hg, hb) = render(phone, 0.6f, mood = VoiceMood.Happy).also { it.save("mobile_dark_happy") }.at(0.5f, 0.9f)
        assertTrue(hg > hr && hg > hb, "a happy voice is green: $hr $hg $hb")
        val (ar, ag, ab) = render(phone, 0.6f, mood = VoiceMood.Angry).also { it.save("mobile_dark_angry") }.at(0.5f, 0.9f)
        assertTrue(ar > ag && ar > ab, "an angry voice is red: $ar $ag $ab")
        render(phone, 0.6f, mood = VoiceMood.Calm).save("mobile_dark_calm")
        render(phone, 0.6f, mood = VoiceMood.Sad).save("mobile_dark_sad")
    }

    @Test
    fun everyPaletteDraws() {
        val palettes = mapOf(
            "colorful" to VoiceGlowColors.Colorful, "mono" to VoiceGlowColors.Mono, "ocean" to VoiceGlowColors.Ocean, "sunset" to VoiceGlowColors.Sunset,
            "forest" to VoiceGlowColors.Forest, "candy" to VoiceGlowColors.Candy, "ice" to VoiceGlowColors.Ice, "gold" to VoiceGlowColors.Gold,
        )
        for ((name, colors) in palettes) {
            val image = render(hosts[1], 0.6f, colors = colors).also { it.save("standard_dark_$name") }
            assertTrue(image.at(0.5f, 0.95f).light() > 0x1D / 255f + 0.05f, "$name lights the foot")
        }
    }

    @Test
    fun aHazeThinsTheLightAboveIt() {
        val phone = hosts.first()
        val plain = render(phone, 1f)
        val hazed = render(phone, 1f, haze = VoiceGlowHaze(from = 60.dp, to = 100.dp, keep = 0.3f)).also { it.save("mobile_dark_haze") }
        // 250 dp up, well above the haze: far less light. 20 dp up, below it: the same.
        assertTrue(hazed.at(0.5f, 1f - 250f / 851f).light() < plain.at(0.5f, 1f - 250f / 851f).light() * 0.75f)
        assertTrue(kotlin.math.abs(hazed.at(0.5f, 1f - 20f / 851f).light() - plain.at(0.5f, 1f - 20f / 851f).light()) < 0.02f)
    }

    private companion object {
        const val Scale = 2
    }
}

/**
 * The pictures of the README, drawn off screen: the three hosts side by side,
 * and a few seconds of the glow following a voice, frame by frame. Only runs
 * when `VOICE_GLOW_SNAPSHOTS` is set; the frames land in its `frames` folder.
 */
class GalleryTest {
    @Test
    fun gallery() {
        val dir = System.getenv("VOICE_GLOW_SNAPSHOTS") ?: return
        for (dark in listOf(true, false)) {
            var seconds = 0f
            val scene = ImageComposeScene(width = 1840, height = 1040, density = Density(2f)) {
                Gallery(dark, level = { galleryVoice(seconds) })
            }
            // Three seconds for the glow to rise before the still; then four more for the film.
            val frames = if (dark) 7 * 30 else 3 * 30
            var still: Image? = null
            for (frame in 0..frames) {
                seconds = frame / 30f
                val image = scene.render(nanoTime = frame * 1_000_000_000L / 30)
                if (frame == 3 * 30 - 6) still = image
                if (dark && frame >= 3 * 30) {
                    File(dir, "frames/%04d.png".format(frame - 3 * 30)).apply { parentFile.mkdirs() }.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
            File(dir, "gallery_${if (dark) "dark" else "light"}.png").writeBytes(still!!.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}

/** A voice for the film: phrases of syllables with a breath between them. */
private fun galleryVoice(seconds: Float): Float {
    if (seconds % 3.6f > 2.9f) return 0f
    return (0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(seconds * 7.3f)) * (0.6f + 0.4f * kotlin.math.sin(seconds * 2.1f))).coerceIn(0f, 1f)
}

@androidx.compose.runtime.Composable
private fun Gallery(dark: Boolean, level: () -> Float) {
    val theme = if (dark) VoiceGlowTheme.Dark else VoiceGlowTheme.Light
    val surface = if (dark) Color(0xFF1D1D1D) else Color(0xFFF4F4F5)
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxSize().background(if (dark) Color(0xFF0B0B0C) else Color(0xFFE4E4E8)).padding(40.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(40.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        VoiceGlowBox(level, Modifier.size(240.dp, 440.dp), VoiceGlowType.Mobile, theme = theme, cornerRadius = 36.dp) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)).background(surface))
        }
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)) {
            VoiceGlowBox(level, Modifier.size(350.dp, 120.dp), VoiceGlowType.Standard, theme = theme, cornerRadius = 20.dp) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(surface))
            }
            VoiceGlowBox(level, Modifier.size(350.dp, 120.dp), VoiceGlowType.Standard, colors = VoiceGlowColors.Sunset, mood = VoiceMood.Neutral, theme = theme, cornerRadius = 20.dp) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(surface))
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
                VoiceGlowBox(level, Modifier.size(150.dp, 44.dp), VoiceGlowType.Pill, theme = theme, cornerRadius = 22.dp) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(surface))
                }
                VoiceGlowBox(level, Modifier.size(150.dp, 44.dp), VoiceGlowType.Pill, colors = VoiceGlowColors.Ocean, theme = theme, cornerRadius = 22.dp) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(surface))
                }
            }
        }
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(32.dp)) {
            VoiceGlowBox(level, Modifier.size(170.dp, 204.dp), VoiceGlowType.Standard, mood = VoiceMood.Happy, theme = theme, cornerRadius = 20.dp) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(surface))
            }
            VoiceGlowBox(level, Modifier.size(170.dp, 204.dp), VoiceGlowType.Standard, mood = VoiceMood.Angry, theme = theme, cornerRadius = 20.dp) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(surface))
            }
        }
    }
}
