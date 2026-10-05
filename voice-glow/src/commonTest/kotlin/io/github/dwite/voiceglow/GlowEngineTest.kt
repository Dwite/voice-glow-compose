package io.github.dwite.voiceglow

import androidx.compose.ui.graphics.Color
import io.github.dwite.voiceglow.internal.GlowColorMatrix
import io.github.dwite.voiceglow.internal.GlowConfig
import io.github.dwite.voiceglow.internal.GlowEngine
import io.github.dwite.voiceglow.internal.GlowFrame
import io.github.dwite.voiceglow.internal.GlowRgb
import io.github.dwite.voiceglow.internal.GlowTheme
import io.github.dwite.voiceglow.internal.OkLab
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlowEngineTest {
    private val frameSeconds = 1f / 60f
    private val colors = VoiceGlowColors.Colorful

    private fun config(type: VoiceGlowType = VoiceGlowType.Mobile, dark: Boolean = true, options: VoiceGlowOptions = VoiceGlowOptions(), colors: VoiceGlowColors = this.colors) =
        GlowConfig(type, if (dark) GlowTheme.Dark else GlowTheme.Light, options, colors)

    private fun GlowEngine.run(seconds: Float, level: Float, active: Boolean = true, mood: VoiceMood = VoiceMood.Neutral, c: GlowConfig = config()): GlowFrame {
        repeat((seconds / frameSeconds).toInt()) { step(frameSeconds, level, active, mood, colors, c) }
        return frame
    }

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.02f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance but was $actual")

    @Test
    fun silenceBelowTheGateIsNoVoiceAndAShoutRoundsOff() {
        assertEquals(0f, GlowEngine.shape(0.01f, 0.015f))
        assertEquals(1f, GlowEngine.shape(1f, 0.015f))
        // Soft saturation: half the input is already most of the glow.
        assertTrue(GlowEngine.shape(0.5f, 0.015f) > 0.75f)
    }

    @Test
    fun theGlowRisesFasterThanItSettles() {
        val rising = GlowEngine().run(seconds = 0.3f, level = 1f).level
        val falling = GlowEngine().apply { run(seconds = 3f, level = 1f) }.run(seconds = 0.3f, level = 0f).level
        assertTrue(rising > 0.55f, "after 0.3 s of voice the level was $rising")
        assertTrue(1f - falling < rising, "after 0.3 s of silence the level was still $falling")
    }

    @Test
    fun aVoiceLiftsAndWidensTheGlow() {
        val c = config()
        val quiet = GlowEngine().settle(0f, VoiceMood.Neutral, colors, c)
        val loud = GlowEngine().settle(1f, VoiceMood.Neutral, colors, c)
        assertTrue(loud.h > quiet.h * 3f)
        assertTrue(loud.w > quiet.w)
        assertNear(1f, loud.bendA)
    }

    @Test
    fun withNoIdleASilentGlowIsFlat() {
        val silent = GlowEngine().settle(0f, VoiceMood.Neutral, colors, config(options = VoiceGlowOptions(idle = 0f)))
        assertEquals(0f, silent.bendA)
        assertEquals(0.5f, silent.h)
    }

    @Test
    fun theGlowFadesInAndGoesToSleepWhenSwitchedOff() {
        val engine = GlowEngine()
        assertFalse(engine.isVisible)
        assertNear(1f, engine.run(seconds = 1f, level = 0.5f).presence)
        engine.run(seconds = 1f, level = 0.5f, active = false)
        assertFalse(engine.isVisible)
    }

    @Test
    fun lobesWrapAroundTheirRingAndFadeAtItsEdge() {
        val span = config().lobeSpan
        assertNear(-span / 2f + 10f, GlowEngine.wrapX(span / 2f + 10f, span), tolerance = 0.001f)
        assertEquals(1f, GlowEngine.edgeEnvelope(0f, span))
        assertTrue(GlowEngine.edgeEnvelope(span / 2f, span) < 0.05f)
    }

    @Test
    fun aPillHoldsItsColoursInPlaceAndAPhoneLetsThemTravel() {
        val pill = GlowEngine().run(seconds = 2f, level = 1f, c = config(VoiceGlowType.Pill)).lobeX[0]
        assertEquals(0f, pill)
        val phone = GlowEngine().run(seconds = 2f, level = 1f, c = config(VoiceGlowType.Mobile)).lobeX[0]
        assertTrue(phone > 50f, "2 s of voice moved the centre lobe $phone dp")
    }

    @Test
    fun aConfidentMoodTakesTheColoursAndAFaintOneDoesNot() {
        val c = config()
        val plain = GlowEngine().settle(0.8f, VoiceMood.Neutral, colors, c).colors.toList()
        val faint = GlowEngine().settle(0.8f, VoiceMood.Angry.copy(confidence = 0.1f), colors, c)
        assertEquals(plain, faint.colors.toList())
        assertEquals(0f, faint.moodAmount)

        val angry = GlowEngine().settle(0.8f, VoiceMood.Angry, colors, c)
        assertNear(1f, angry.moodAmount)
        assertTrue(angry.colors.all { it.r > it.g && it.r > it.b }, "every lobe of an angry voice is red: ${angry.colors.toList()}")

        val happy = GlowEngine().settle(0.8f, VoiceMood.Happy, colors, c)
        assertTrue(happy.colors.all { it.g > it.r && it.g > it.b }, "every lobe of a happy voice is green: ${happy.colors.toList()}")

        val half = GlowEngine().settle(0.8f, VoiceMood.Happy, colors, config(options = VoiceGlowOptions(moodStrength = 0.5f)))
        assertNear(0.5f, half.moodAmount)
    }

    @Test
    fun monoIsGreyAndHoldsStill() {
        val c = config(colors = VoiceGlowColors.Mono)
        val engine = GlowEngine()
        repeat(120) { engine.step(frameSeconds, 0.8f, true, VoiceMood.Neutral, VoiceGlowColors.Mono, c) }
        val early = engine.frame.colors.toList()
        assertTrue(early.all { abs(it.r - it.g) < 0.02f && abs(it.g - it.b) < 0.02f }, "the lobes of mono are grey: $early")
        repeat(240) { engine.step(frameSeconds, 0.8f, true, VoiceMood.Neutral, VoiceGlowColors.Mono, c) }
        assertEquals(early, engine.frame.colors.toList())
    }

    @Test
    fun aMoodEasesInAndBackOut() {
        val engine = GlowEngine()
        val entering = engine.run(seconds = 0.15f, level = 0.8f, mood = VoiceMood.Happy).moodAmount
        assertTrue(entering > 0.2f && entering < 0.8f, "0.15 s into a mood the glow was $entering in")
        assertNear(1f, engine.run(seconds = 3f, level = 0.8f, mood = VoiceMood.Happy).moodAmount)
        assertNear(0f, engine.run(seconds = 6f, level = 0.8f).moodAmount)
    }

    @Test
    fun newColoursCrossOverInsteadOfCutting() {
        val c = config()
        val engine = GlowEngine()
        // Held still, so the hue drift does not move the colours under the comparison.
        fun hold(seconds: Float, colors: VoiceGlowColors): List<GlowRgb> {
            repeat((seconds / frameSeconds).toInt()) { engine.step(frameSeconds, 0.8f, true, VoiceMood.Neutral, colors, c, still = true) }
            return engine.frame.colors.toList()
        }
        val first = hold(1f, VoiceGlowColors.Colorful)
        val crossing = hold(0.1f, VoiceGlowColors.Ocean)
        val arrived = hold(4f, VoiceGlowColors.Ocean)
        val target = GlowEngine().settle(0.8f, VoiceMood.Neutral, VoiceGlowColors.Ocean, c).colors.toList()
        assertTrue(crossing != first && crossing != arrived)
        arrived.zip(target).forEach { (now, wanted) ->
            assertNear(wanted.r, now.r)
            assertNear(wanted.g, now.g)
            assertNear(wanted.b, now.b)
        }
    }

    @Test
    fun everyOptionOverridesItsTypeAndNothingElse() {
        val plain = config(VoiceGlowType.Standard)
        val tuned = config(VoiceGlowType.Standard, options = VoiceGlowOptions(reach = 2.5f, bandCurve = 2.2f, glowWidth = 0.9f, bandTail = 0f, hueRange = 0f, coreLight = 1f))
        assertEquals(2.5f, tuned.reach)
        assertEquals(2.2f, tuned.bandCurve)
        assertEquals(0.9f, tuned.glowWidth)
        assertEquals(0f, tuned.bandTail)
        assertEquals(0f, tuned.hueRange)
        assertEquals(1f, tuned.coreLight)
        // What was left alone is still the type's.
        assertEquals(plain.spread, tuned.spread)
        assertEquals(plain.bend, tuned.bend)
        assertEquals(plain.bandWidth, tuned.bandWidth)
        assertEquals(plain.saturation, tuned.saturation)
    }

    @Test
    fun scaleSizesEveryLengthOnTopOfTheTypesOwn() {
        val pill = GlowConfig(VoiceGlowType.Pill, GlowTheme.Dark, VoiceGlowOptions(), colors)
        val doubled = GlowConfig(VoiceGlowType.Pill, GlowTheme.Dark, VoiceGlowOptions(), colors, sizing = 2f)
        assertNear(pill.scale * 2f, doubled.scale, tolerance = 0.0001f)
        assertNear(pill.bend * 2f, doubled.bend, tolerance = 0.0001f)
        assertNear(pill.glowWidth * 2f, doubled.glowWidth, tolerance = 0.0001f)
        assertNear(pill.lobeSpan * 2f, doubled.lobeSpan, tolerance = 0.001f)
        // Shares and exponents are not lengths.
        assertEquals(pill.reach, doubled.reach)
        assertEquals(pill.bandCurve, doubled.bandCurve)
    }

    @Test
    fun sensitivityLiftsAQuietSource() {
        val quiet = GlowEngine().settle(0.05f, VoiceMood.Neutral, colors, config()).level
        val lifted = GlowEngine().settle(0.05f, VoiceMood.Neutral, colors, config(options = VoiceGlowOptions(sensitivity = 6f))).level
        assertTrue(lifted > quiet * 3f, "a raw 0.05 read as $quiet, and as $lifted with six times the sensitivity")
    }

    @Test
    fun coloursCanBeYourOwn() {
        val red = VoiceGlowColors(Color.Red)
        val lobes = GlowEngine().settle(0.8f, VoiceMood.Neutral, red, config(colors = red, options = VoiceGlowOptions(hueRange = 0f))).colors
        assertTrue(lobes.all { it.r > 0.9f && it.g < 0.2f && it.b < 0.2f }, "one colour fills every lobe: ${lobes.toList()}")
        // Equal colours are equal sets, so a set made on every recomposition restarts nothing.
        assertEquals(VoiceGlowColors(listOf(Color.Red, Color.Blue)), VoiceGlowColors(listOf(Color.Red, Color.Blue)))
        assertTrue(VoiceGlowColors(Color.Red) != VoiceGlowColors(Color.Blue))
        assertEquals(VoiceGlowColors.Ocean, VoiceGlowColors.Ocean.copy())
        assertTrue(VoiceGlowColors.Ocean != VoiceGlowColors.Ocean.copy(drift = false))
    }

    @Test
    fun aPaletteGrowsAroundOneColour() {
        val seed = Color(0xFF2F6BFF)
        val brand = VoiceGlowColors.from(seed)
        val c = config(colors = brand, options = VoiceGlowOptions(hueRange = 0f, brightness = 1f, saturation = 1f))
        val lobes = GlowEngine().settle(0.8f, VoiceMood.Neutral, brand, c).colors
        // Every lobe stays in the seed's family (blue leads), and they are not all the same.
        assertTrue(lobes.all { it.b > it.r && it.b > 0.6f }, "a blue seed gives blues: ${lobes.toList()}")
        assertTrue(lobes.toSet().size == lobes.size)
        // A wider spread reaches further around the hue circle.
        val wide = VoiceGlowColors.from(seed, hueSpread = 120f)
        val far = GlowEngine().settle(0.8f, VoiceMood.Neutral, wide, config(colors = wide, options = VoiceGlowOptions(hueRange = 0f, brightness = 1f, saturation = 1f))).colors
        assertTrue(far.any { it.b < it.r || it.b < it.g }, "a spread of 120 degrees leaves the blues: ${far.toList()}")
    }

    @Test
    fun theBandTakesItsOwnColours() {
        val own = VoiceGlowColors.Colorful.copy(band = VoiceGlowBandColors(core = Color.Yellow, above = Color.Red, below = Color.Blue))
        val frame = GlowEngine().settle(0.8f, VoiceMood.Neutral, own, config(colors = own))
        assertTrue(frame.bandCore.r > 0.9f && frame.bandCore.g > 0.9f && frame.bandCore.b < 0.1f, "${frame.bandCore}")
        assertTrue(frame.bandAbove.r > 0.9f && frame.bandAbove.b < 0.1f, "${frame.bandAbove}")
        assertTrue(frame.bandBelow.b > 0.9f && frame.bandBelow.r < 0.1f, "${frame.bandBelow}")
        // Left alone, the dark theme's band has a white core.
        assertEquals(GlowRgb(1f, 1f, 1f), GlowEngine().settle(0.8f, VoiceMood.Neutral, colors, config()).bandCore)
    }

    @Test
    fun coloursCanHoldStill() {
        val held = VoiceGlowColors.Colorful.copy(drift = false)
        val c = config(colors = held)
        val engine = GlowEngine()
        repeat(60) { engine.step(frameSeconds, 0.8f, true, VoiceMood.Neutral, held, c) }
        val early = engine.frame.colors.toList()
        repeat(240) { engine.step(frameSeconds, 0.8f, true, VoiceMood.Neutral, held, c) }
        assertEquals(early, engine.frame.colors.toList())
    }

    @Test
    fun twoReadsOfOneVoiceBlend() {
        val words = VoiceMood(valence = -0.8f, arousal = 0.4f, confidence = 0.9f)
        val tone = VoiceMood(valence = 0.2f, arousal = 0.9f, confidence = 0.6f)
        val both = VoiceMood.blend(tone = tone, meaning = words)
        assertTrue(both.valence < -0.3f, "the words carry the valence: ${both.valence}")
        assertTrue(both.arousal > 0.6f, "the tone carries the arousal: ${both.arousal}")
        assertTrue(both.confidence > 0.9f)
        assertEquals(words, VoiceMood.blend(tone = null, meaning = words).copy(confidence = words.confidence))
        assertEquals(0f, VoiceMood.blend(null, null).confidence)
    }

    @Test
    fun oklabGoesThereAndBack() {
        for (color in listOf(GlowRgb.of(255, 70, 120), GlowRgb.of(40, 200, 190), GlowRgb.of(255, 255, 255), GlowRgb.of(0, 0, 0))) {
            val back = OkLab.of(color).toRgb()
            assertNear(color.r, back.r, tolerance = 0.005f)
            assertNear(color.g, back.g, tolerance = 0.005f)
            assertNear(color.b, back.b, tolerance = 0.005f)
        }
    }

    @Test
    fun anUntouchedColourMatrixKeepsTheColour() {
        val color = GlowRgb.of(60, 190, 255)
        val same = GlowColorMatrix.of(hueDegrees = 0f, brightness = 1f, saturation = 1f).apply(color)
        assertNear(color.r, same.r, tolerance = 0.005f)
        assertNear(color.g, same.g, tolerance = 0.005f)
        assertNear(color.b, same.b, tolerance = 0.005f)
    }
}
