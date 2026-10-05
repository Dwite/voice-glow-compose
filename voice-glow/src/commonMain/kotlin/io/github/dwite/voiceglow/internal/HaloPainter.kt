package io.github.dwite.voiceglow.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.dwite.voiceglow.VoiceHaloShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * What moves in a halo beside the glow itself: how far the colours have
 * travelled around the ring, and the waves that leave it while its voice goes
 * out.
 */
internal class HaloMotion {
    /** How far the colours have travelled around, in turns. */
    var turn = 0f

    /** Per wave: how far it has travelled, 0–1 (negative for none), and how strong it set off. */
    val waveTravel = FloatArray(MaxWaves) { -1f }
    val waveStrength = FloatArray(MaxWaves)

    private var sinceWave = Float.MAX_VALUE

    fun step(dt: Float, f: GlowFrame, shape: VoiceHaloShape, outgoing: Boolean) {
        if (shape.turnSeconds > 0f) turn = (turn + dt * f.rise / shape.turnSeconds) % 1f
        // Waves already on their way finish their trip; new ones only set off while the voice sounds.
        for (i in 0 until MaxWaves) {
            if (waveTravel[i] < 0f) continue
            waveTravel[i] += dt / max(0.1f, shape.waveSeconds)
            if (waveTravel[i] >= 1f) waveTravel[i] = -1f
        }
        if (sinceWave < Float.MAX_VALUE) sinceWave += dt
        if (!outgoing || f.level < 0.1f || sinceWave < shape.waveEvery) return
        val free = waveTravel.indexOfFirst { it < 0f }
        if (free < 0) return
        waveTravel[free] = 0f
        waveStrength[free] = f.level
        sinceWave = 0f
    }

    /** Nothing in motion, for a still picture: an outgoing voice shows one wave, part of the way out. */
    fun settle(f: GlowFrame, outgoing: Boolean) {
        turn = 0f
        waveTravel.fill(-1f)
        if (outgoing && f.level >= 0.1f) {
            waveTravel[0] = 0.4f
            waveStrength[0] = f.level
        }
    }

    companion object {
        const val MaxWaves = 4
    }
}

/**
 * Draws one [GlowFrame] as a halo, back to front: the aura (the seven lobes,
 * as lights spaced around the ring), a fine line in the lobes' colours with
 * the waves that leave it, and the band (a bright ring with a warm fringe
 * outside and a cool one inside). All sizes are shares of the box's shorter
 * side.
 */
internal class HaloPainter {
    private val layerPaint = Paint()

    /** One light across its radius. */
    private val lobeLight = Brush.radialGradient(
        0f to Color.White,
        0.5f to Color.White.copy(alpha = 0.45f),
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = UnitRadius,
    )

    private var maskFor: VoiceHaloShape? = null
    private lateinit var auraMask: Brush

    fun DrawScope.drawHalo(f: GlowFrame, c: GlowConfig, shape: VoiceHaloShape, motion: HaloMotion, strength: Float) {
        val amount = c.strength * strength.coerceIn(0f, 1f) * c.dim
        if (amount <= 0.002f) return
        if (maskFor != shape) {
            maskFor = shape
            // Keeps the aura to the ring: nothing far inside it, full on the ring, gone a little outside it.
            val outer = shape.radius + shape.auraOutside
            auraMask = Brush.radialGradient(
                0f to Color.Transparent,
                ((shape.radius - shape.auraInside) / outer).coerceIn(0f, 0.97f) to Color.Transparent,
                ((shape.radius - 0.02f) / outer).coerceIn(0f, 0.98f) to Color.Black,
                ((shape.radius + 0.015f) / outer).coerceIn(0f, 0.99f) to Color.Black,
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = UnitRadius,
            )
        }
        val side = size.minDimension
        // The colours travel around the figure; the first lobe starts at the foot.
        rotate(360f * motion.turn + 90f, center) {
            aura(f, c, shape, side, amount)
            ring(f, shape, motion, side, amount)
        }
        band(f, c, shape, side, amount)
    }

    /** The seven lobes around the ring, larger and brighter with the voice. */
    private fun DrawScope.aura(f: GlowFrame, c: GlowConfig, shape: VoiceHaloShape, side: Float, amount: Float) {
        val alpha = f.presence * (0.2f + 0.8f * f.rise) * amount
        if (alpha <= 0.002f) return
        val reach = side * (shape.radius + shape.auraOutside)
        layerPaint.alpha = min(1f, alpha)
        drawIntoCanvas { it.saveLayer(Rect(center.x - reach, center.y - reach, center.x + reach, center.y + reach), layerPaint) }
        val light = 0.62f * c.theme.bloomAlpha / 0.9f
        for (k in RingOrder.indices.reversed()) {
            val i = RingOrder[k]
            // Wide enough to run into its neighbours: the colours meet, and the voice only makes them breathe.
            val radius = side * shape.radius * (0.7f + 0.2f * f.rise) * (0.75f + 0.25f * f.lobeLift[i])
            val rgb = f.colors[i]
            withTransform({
                translate(center.x + side * shape.radius * RingCos[k], center.y + side * shape.radius * RingSin[k])
                scale(radius / UnitRadius, radius / UnitRadius, Offset.Zero)
            }) {
                drawCircle(lobeLight, radius = UnitRadius, center = Offset.Zero, alpha = light, colorFilter = ColorFilter.tint(Color(rgb.r, rgb.g, rgb.b)))
            }
        }
        withTransform({
            translate(center.x, center.y)
            scale(reach / UnitRadius, reach / UnitRadius, Offset.Zero)
        }) {
            drawCircle(auraMask, radius = UnitRadius * 1.5f, center = Offset.Zero, blendMode = BlendMode.DstIn)
        }
        drawIntoCanvas { it.restore() }
    }

    /**
     * A fine line on the ring in the lobes' own colours. It is there, faintly,
     * as soon as the halo is: the sign that someone is listening. The waves of
     * an outgoing voice are the same line, leaving: wider, softer and fainter
     * the further they get.
     */
    private fun DrawScope.ring(f: GlowFrame, shape: VoiceHaloShape, motion: HaloMotion, side: Float, amount: Float) {
        val resting = shape.restingRing.coerceIn(0f, 1f)
        val alpha = f.presence * (resting + (0.9f - resting).coerceAtLeast(0f) * f.rise) * amount
        val count = RingOrder.size
        val colors = Array(count + 1) { k ->
            val rgb = f.colors[RingOrder[k % count]]
            // The sweep starts at the ring's first lobe.
            (k.toFloat() / count) to Color(rgb.r, rgb.g, rgb.b)
        }
        val radius = side * (shape.radius + shape.swell * f.rise)
        rotate(RingStartDegrees, center) {
            val line = Brush.sweepGradient(*colors, center = center)
            for (i in 0 until HaloMotion.MaxWaves) {
                val travel = motion.waveTravel[i]
                if (travel < 0f) continue
                // Quick off the ring, then slowing: sound thinning into the air.
                val out = 1f - (1f - travel) * (1f - travel)
                val fade = f.presence * motion.waveStrength[i] * (1f - travel) * (1f - travel) * amount
                val reach = radius + side * shape.waveTravel * out
                drawCircle(line, reach, center, alpha = min(1f, 0.22f * fade), style = Stroke((5f + 9f * out).dp.toPx()))
                drawCircle(line, reach, center, alpha = min(1f, 0.6f * fade), style = Stroke((2.2f - 1.2f * out).dp.toPx()))
            }
            if (alpha > 0.002f) drawCircle(line, radius, center, alpha = min(1f, alpha), style = Stroke((1.25f + 0.75f * f.rise).dp.toPx()))
        }
    }

    /**
     * The band: a soft bright ring that swells with the voice, traced by a
     * core with a warm fringe on its outside and a cool one on its inside that
     * split further and thicken as the voice rises.
     */
    private fun DrawScope.band(f: GlowFrame, c: GlowConfig, shape: VoiceHaloShape, side: Float, amount: Float) {
        val alpha = min(1f, 0.6f * c.bandStrength * f.rise) * f.presence * amount
        if (alpha < 0.005f || shape.bandWidth <= 0f) return
        val ring = side * (shape.radius + shape.swell * f.rise)
        val thickness = side * shape.bandWidth * (1f + 0.35f * f.level)
        val fringe = thickness * (0.23f + 0.1f * f.level) * c.bandAberration / 0.89f
        val reach = thickness * HaloReach
        val outer = ring + reach
        val light = (c.theme.bandLight + 0.03f) * alpha

        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        fun over(color: GlowRgb, share: Float) {
            r = color.r * share + r * (1f - share)
            g = color.g * share + g * (1f - share)
            b = color.b * share + b * (1f - share)
            a = share + a * (1f - share)
        }
        fun bump(v: Float, at: Float): Float {
            val u = (v - at) / (thickness / 2f)
            return 0.833f * exp(-u * u / 0.459f)
        }
        val stops = Array(SectionStops + 1) { k ->
            // Nothing inside the band's reach, then its cross-section from the inside out.
            if (k == 0) return@Array 0f to Color.Transparent
            val v = -reach + 2f * reach * (k - 1) / (SectionStops - 1f)
            r = 0f
            g = 0f
            b = 0f
            a = 0f
            val haze = ((HaloReach - 0.05f - abs(v) / thickness) / 1.5f).coerceIn(0f, 1f)
            over(f.bandCore, light * 0.3f * haze * haze * (3f - 2f * haze))
            over(f.bandAbove, light * 0.96f * bump(v, fringe))
            over(f.bandMid, light * 0.96f * 0.55f * bump(v, fringe * 0.35f))
            over(f.bandBelow, light * 0.96f * bump(v, -fringe))
            over(f.bandCore, light * 0.96f * 0.9f * bump(v, 0f))
            val position = ((ring + v) / outer).coerceIn(0f, 1f)
            if (a > 0.0005f) {
                val toned = f.tone.apply(GlowRgb((r / a).coerceIn(0f, 1f), (g / a).coerceIn(0f, 1f), (b / a).coerceIn(0f, 1f)))
                position to Color(toned.r, toned.g, toned.b, a.coerceIn(0f, 1f))
            } else {
                position to Color.Transparent
            }
        }
        drawCircle(Brush.radialGradient(*stops, center = center, radius = outer), radius = outer, center = center)
    }

    private companion object {
        /** The lights and the mask are drawn once at this radius and scaled into place. */
        const val UnitRadius = 256f

        /** How far the band's halo reaches from its line, in band thicknesses. */
        const val HaloReach = 1.9f
        const val SectionStops = 25

        /** The lobes in the order they sit around the ring, the centre lobe in the middle. */
        val RingOrder = intArrayOf(5, 3, 1, 0, 2, 4, 6)
        val RingCos = FloatArray(RingOrder.size) { k -> cos(2f * PI.toFloat() * (k - 3) / RingOrder.size) }
        val RingSin = FloatArray(RingOrder.size) { k -> sin(2f * PI.toFloat() * (k - 3) / RingOrder.size) }

        /** The angle of the ring's first lobe, where the fine line's sweep begins. */
        val RingStartDegrees = 360f * -3f / RingOrder.size
    }
}
