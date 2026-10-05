package io.github.dwite.voiceglow.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** An sRGB colour, 0–1 per channel. */
@Immutable
internal data class GlowRgb(val r: Float, val g: Float, val b: Float) {
    companion object {
        /** From 0–255 channels, the unit the palettes are written in. */
        fun of(r: Int, g: Int, b: Int) = GlowRgb(r / 255f, g / 255f, b / 255f)

        fun of(color: Color): GlowRgb = color.convert(ColorSpaces.Srgb).let { GlowRgb(it.red, it.green, it.blue) }
    }
}

/**
 * Björn Ottosson's OKLab: a straight mix between two colours keeps its
 * lightness and chroma, so red to green passes through amber and not mud.
 */
internal data class OkLab(val l: Float, val a: Float, val b: Float) {
    fun mixed(other: OkLab, t: Float) = OkLab(
        l = l + (other.l - l) * t,
        a = a + (other.a - a) * t,
        b = b + (other.b - b) * t,
    )

    /** This colour turned [hueDegrees] around the hue circle, at [lightness], with at least [minChroma] of colour. */
    fun shifted(hueDegrees: Float, lightness: Float, minChroma: Float): OkLab {
        val chroma = max(minChroma, sqrt(a * a + b * b))
        val hue = atan2(b, a) + hueDegrees * PI.toFloat() / 180f
        return OkLab(lightness.coerceIn(0f, 1f), chroma * cos(hue), chroma * sin(hue))
    }

    fun toRgb(): GlowRgb {
        val l3 = (l + 0.3963377774f * a + 0.2158037573f * b).cubed()
        val m3 = (l - 0.1055613458f * a - 0.0638541728f * b).cubed()
        val s3 = (l - 0.0894841775f * a - 1.2914855480f * b).cubed()
        return GlowRgb(
            encode(4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3),
            encode(-1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3),
            encode(-0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3),
        )
    }

    companion object {
        fun of(c: GlowRgb): OkLab {
            val r = decode(c.r)
            val g = decode(c.g)
            val b = decode(c.b)
            val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
            val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
            val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
            return OkLab(
                l = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
            )
        }

        private fun Float.cubed() = this * this * this

        private fun decode(v: Float): Float =
            if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

        private fun encode(x: Float): Float {
            val v = x.coerceIn(0f, 1f)
            return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
        }
    }
}

/**
 * Hue rotation, brightness and saturation as one 3×3 matrix (the W3C Filter
 * Effects maths). The glow's slow hue drift goes through it, so the lobes keep
 * moving in colour while the voice holds one level.
 */
internal class GlowColorMatrix(private val m: FloatArray) {
    fun apply(c: GlowRgb) = GlowRgb(
        (m[0] * c.r + m[1] * c.g + m[2] * c.b).coerceIn(0f, 1f),
        (m[3] * c.r + m[4] * c.g + m[5] * c.b).coerceIn(0f, 1f),
        (m[6] * c.r + m[7] * c.g + m[8] * c.b).coerceIn(0f, 1f),
    )

    companion object {
        fun of(hueDegrees: Float, brightness: Float, saturation: Float): GlowColorMatrix {
            val rad = hueDegrees * PI.toFloat() / 180f
            val c = cos(rad)
            val s = sin(rad)
            val hue = floatArrayOf(
                0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f,
                0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f,
                0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f,
            )
            val sat = floatArrayOf(
                0.213f + 0.787f * saturation, 0.715f - 0.715f * saturation, 0.072f - 0.072f * saturation,
                0.213f - 0.213f * saturation, 0.715f + 0.285f * saturation, 0.072f - 0.072f * saturation,
                0.213f - 0.213f * saturation, 0.715f - 0.715f * saturation, 0.072f + 0.928f * saturation,
            )
            val out = FloatArray(9)
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    var sum = 0f
                    for (k in 0 until 3) sum += sat[row * 3 + k] * hue[k * 3 + col] * brightness
                    out[row * 3 + col] = sum
                }
            }
            return GlowColorMatrix(out)
        }
    }
}
