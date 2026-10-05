package io.github.dwite.voiceglow

import io.github.dwite.voiceglow.internal.GlowConfig
import io.github.dwite.voiceglow.internal.GlowEngine
import io.github.dwite.voiceglow.internal.GlowTheme
import io.github.dwite.voiceglow.internal.HaloMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaloMotionTest {
    private val frameSeconds = 1f / 60f
    private val colors = VoiceGlowColors.Colorful
    private val config = GlowConfig(VoiceGlowType.Mobile, GlowTheme.Dark, VoiceGlowOptions(), colors)

    private class Halo(val engine: GlowEngine = GlowEngine(), val motion: HaloMotion = HaloMotion())

    private fun Halo.run(seconds: Float, level: Float, outgoing: Boolean = false, shape: VoiceHaloShape = VoiceHaloShape()) = apply {
        repeat((seconds / frameSeconds).toInt()) {
            motion.step(frameSeconds, engine.step(frameSeconds, level, true, VoiceMood.Neutral, colors, config), shape, outgoing)
        }
    }

    private fun Halo.waves() = motion.waveTravel.count { it >= 0f }

    @Test
    fun theColoursTravelWithTheVoiceAndCanBeHeld() {
        val loud = Halo().run(2f, 1f).motion.turn
        val quiet = Halo().run(2f, 0f).motion.turn
        assertTrue(loud > quiet * 3f && loud < 1f, "2 s of voice turned the colours $loud, 2 s of silence $quiet")
        assertEquals(0f, Halo().run(2f, 1f, shape = VoiceHaloShape(turnSeconds = 0f)).motion.turn)
    }

    @Test
    fun wavesLeaveOnlyWhileTheFiguresOwnVoiceSounds() {
        assertEquals(0, Halo().run(3f, 0.8f, outgoing = false).waves())

        val speaking = Halo().run(3f, 0.8f, outgoing = true)
        assertTrue(speaking.waves() in 2..HaloMotion.MaxWaves, "a steady voice keeps a few waves on their way: ${speaking.waves()}")

        // The voice stops: no new waves, and the ones on their way finish and are gone.
        speaking.run(0.3f, 0f, outgoing = false)
        assertTrue(speaking.waves() > 0)
        speaking.run(VoiceHaloShape().waveSeconds, 0f, outgoing = false)
        assertEquals(0, speaking.waves())

        // A silent figure sends none, however long its turn.
        assertEquals(0, Halo().run(3f, 0f, outgoing = true).waves())
    }

    @Test
    fun aStillPictureOfASpeakingFigureShowsOneWave() {
        val frame = GlowEngine().settle(0.6f, VoiceMood.Neutral, colors, config)
        assertEquals(1, HaloMotion().apply { settle(frame, outgoing = true) }.waveTravel.count { it >= 0f })
        assertEquals(0, HaloMotion().apply { settle(frame, outgoing = false) }.waveTravel.count { it >= 0f })
    }
}
