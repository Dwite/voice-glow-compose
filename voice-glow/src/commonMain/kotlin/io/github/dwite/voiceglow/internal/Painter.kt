package io.github.dwite.voiceglow.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.dwite.voiceglow.VoiceGlowHaze
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws one [GlowFrame], bottom to top as voice-glow stacks it: the inner
 * light along the edges, the edge stroke, the bloom, then the band and, on a
 * light background, the white wash at its source.
 *
 * The original is CSS gradients, blurs and masks (web) or a Metal shader
 * (SwiftUI). This is the same picture from plain gradients and layers, which
 * every Compose target draws alike; where the original blurs, the softness is
 * built into the gradients instead.
 */
internal class GlowPainter {
    private val layerPaint = Paint()
    private val cutPaint = Paint().apply { blendMode = BlendMode.DstOut }
    private val piece = Matrix()
    private val ridgeX = FloatArray(MaxPieces + 1)
    private val ridgeY = FloatArray(MaxPieces + 1)
    private val ridgeStretch = FloatArray(MaxPieces + 1)
    private var pieces = 0

    /** The strength in use for the frame being drawn: the type's own times the caller's. */
    private var strength = 1f
    private val host = Path()
    private val ring = Path().apply { fillType = PathFillType.EvenOdd }

    /** Full at the centre, half at 45%, and a long 30% tail before the edge. */
    private val innerMask = fading(0f to 1f, 0.45f to 0.5f, 0.85f to 0.3f, 1f to 0f)
    private val edgeMask = fading(0f to 1f, 0.45f to 0.5f, 1f to 0f)
    private val bloomMask = fading(0f to 1f, 0.35f to 0.5f, 1f to 0f)

    // Rebuilt when the config, the size or the corner radius changes.
    private var builtFor: GlowConfig? = null
    private var builtSize = Size.Unspecified
    private var builtRadius = -1f
    private lateinit var lobeFalloff: Brush
    private lateinit var bloomFalloff: Brush
    private lateinit var coreLight: Brush
    private lateinit var interiorDown: Brush
    private lateinit var interiorAcross: Brush
    private lateinit var edgeDown: Brush
    private lateinit var edgeAcross: Brush
    private lateinit var underLine: Brush
    private var wash: Brush? = null

    private var hazeOf: VoiceGlowHaze? = null
    private var hazeSize = Size.Unspecified
    private var hazeDown: Brush? = null

    /** [strength] is the caller's, 0–1, on top of the type's own; [cornerRadius] is the host's, in px. */
    fun DrawScope.drawGlow(f: GlowFrame, c: GlowConfig, strength: Float, cornerRadius: Float, haze: VoiceGlowHaze?) {
        val radius = cornerRadius.coerceIn(0f, min(size.width, size.height) / 2f)
        if (builtFor !== c || builtSize != size || builtRadius != radius) build(c, radius)
        this@GlowPainter.strength = c.strength * strength.coerceIn(0f, 1f)
        if (this@GlowPainter.strength <= 0.002f) return
        if (radius > 0f) clipPath(host) { drawHazed(f, c, radius, haze) } else clipRect { drawHazed(f, c, radius, haze) }
    }

    private fun DrawScope.build(c: GlowConfig, radius: Float) {
        builtFor = c
        builtSize = size
        builtRadius = radius
        val dp = density
        val w = size.width
        val h = size.height

        val fade = (70f * c.softness).roundToInt().coerceIn(40, 95) / 100f
        lobeFalloff = falloff(fade)
        // The bloom's lobes are gone a little later: the original blurs them.
        bloomFalloff = falloff(min(95f, fade * 100f + 2f) / 100f)

        val t = c.theme
        val ink = Color(t.core.r, t.core.g, t.core.b)
        coreLight = Brush.radialGradient(
            0f to ink.copy(alpha = t.coreAlpha),
            t.coreMid to ink.copy(alpha = t.coreMidAlpha),
            t.coreEnd to ink.copy(alpha = 0f),
            center = Offset.Zero,
            radius = UnitRadius,
        )

        // The inner light lives in a band along each edge: this is its inside, to be cut away.
        val band = 28f * c.scale * dp
        val down = (band / h).coerceAtMost(0.5f)
        val across = (band / w).coerceAtMost(0.5f)
        interiorDown = Brush.verticalGradient(0f to Color.Transparent, down to Color.Black, 1f - down to Color.Black, 1f to Color.Transparent)
        interiorAcross = Brush.horizontalGradient(0f to Color.Transparent, across to Color.Black, 1f - across to Color.Black, 1f to Color.Transparent)

        // The original blurs its bloom, and a blur at an edge has only half its
        // light to gather. That dimmer foot is what lets the hairline on the
        // edge stand out, so it is drawn in: half the light on each edge, all
        // of it 2.5 blur radii in. These are a Gaussian's share on one side of
        // an edge, every half deviation from it.
        val reach = max(0.5f, 10f * c.glowSize) * dp * 2.5f
        val shares = floatArrayOf(0.5f, 0.691f, 0.841f, 0.933f, 0.977f, 1f)
        val last = shares.lastIndex
        fun ramp(inset: Float) = Array(shares.size * 2) { k ->
            if (k <= last) inset * k / last to Color.Black.copy(alpha = shares[k])
            else 1f - inset * (2 * last + 1 - k) / last to Color.Black.copy(alpha = shares[2 * last + 1 - k])
        }
        edgeDown = Brush.verticalGradient(*ramp((reach / h).coerceAtMost(0.5f)))
        edgeAcross = Brush.horizontalGradient(*ramp((reach / w).coerceAtMost(0.5f)))

        host.rewind()
        ring.rewind()
        if (radius > 0f) {
            val line = max(1f, dp)
            host.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius)))
            ring.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius)))
            ring.addRoundRect(RoundRect(line, line, w - line, h - line, CornerRadius(max(0f, radius - line))))
        }

        // What is under the band line, with the soft edge the original's blur of 8 gives a cut:
        // a Gaussian's share on one side of an edge, from two deviations above the line to two below.
        val soft = 16f * c.glowSize * dp
        underLine = Brush.verticalGradient(
            0f to Color.Transparent,
            0.25f to Color.Black.copy(alpha = 0.16f),
            0.5f to Color.Black.copy(alpha = 0.5f),
            0.75f to Color.Black.copy(alpha = 0.84f),
            1f to Color.Black,
            startY = -soft,
            endY = soft,
        )

        // The white wash at the source, on a light background: voice-glow's core light.
        wash = if (c.coreLight > 0f) {
            val boost = (c.coreLight - 1f).coerceIn(0f, 2f)
            val b1 = min(1f, boost)
            val b2 = max(0f, boost - 1f)
            Brush.radialGradient(
                0f to Color.White,
                (45f * b1 + 27f * b2) / 100f to Color.White,
                (40f + 25f * b1 + 15f * b2) / 100f to Color.White.copy(alpha = min(1f, 0.55f + 0.35f * b1 + 0.1f * b2)),
                (72f + 14f * b1 + 8f * b2) / 100f to Color.Transparent,
                center = Offset.Zero,
                radius = UnitRadius,
            )
        } else {
            null
        }
    }

    private fun DrawScope.drawHazed(f: GlowFrame, c: GlowConfig, radius: Float, haze: VoiceGlowHaze?) {
        if (haze == null) return drawLight(f, c, radius)
        // The bloom is the tallest part of the glow: its box holds everything.
        val top = (size.height - (130f * c.rangeHeight * f.h + f.lift) * density).coerceAtLeast(0f)
        val bounds = Rect(0f, top, size.width, size.height)
        layer(bounds, 1f) {
            drawLight(f, c, radius)
            if (hazeOf != haze || hazeSize != size) {
                hazeOf = haze
                hazeSize = size
                // Eased at both ends, so the thinning has no visible start or end line.
                val keep = haze.keep.coerceIn(0f, 1f)
                hazeDown = Brush.verticalGradient(
                    *Array(HazeStops) { k ->
                        val t = k / (HazeStops - 1f)
                        t to Color.Black.copy(alpha = keep + (1f - keep) * t * t * (3f - 2f * t))
                    },
                    startY = size.height - haze.to.toPx(),
                    endY = size.height - haze.from.toPx(),
                )
            }
            drawRect(hazeDown!!, bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn)
        }
    }

    private fun DrawScope.drawLight(f: GlowFrame, c: GlowConfig, radius: Float) {
        val dp = density
        val w = size.width
        val h = size.height
        val base = f.presence * f.glow * strength * c.dim

        // The ellipse the edge light rises in: it grows with the level and humps with the bend.
        val edgeRx = 170f * c.rangeWidth * f.w * dp
        val edgeRy = (64f * c.rangeHeight * f.h + f.lift) * dp
        val edgeBox = Rect(0f, (h - edgeRy).coerceAtLeast(0f), w, h)

        // Inner light: the lobes, kept to a soft band along the edges.
        layer(edgeBox, min(1f, base * c.theme.innerOpacity * c.innerOpacity)) {
            lobes(f, alpha = 0.46f, widthScale = c.glowWidth * 0.9f * c.innerScale, heightScale = c.glowHeight * 0.9f * c.innerScale * c.innerHeight, lift = 0f, falloff = lobeFalloff)
            ellipseMask(innerMask, w / 2f, h, edgeRx, edgeRy)
            drawIntoCanvas { it.saveLayer(edgeBox, cutPaint) }
            drawRect(Color.Black, edgeBox.topLeft, edgeBox.size)
            drawRect(interiorDown, edgeBox.topLeft, edgeBox.size, blendMode = BlendMode.DstIn)
            drawRect(interiorAcross, edgeBox.topLeft, edgeBox.size, blendMode = BlendMode.DstIn)
            drawIntoCanvas { it.restore() }
        }

        // Edge stroke: a hairline of the same light on the very edge, with a hot core at its centre.
        val strokeAlpha = min(1f, base * c.theme.strokeOpacity * c.strokeOpacity)
        if (radius > 0f) {
            layer(edgeBox, strokeAlpha) {
                clipPath(ring) { edgeLight(f, c) }
                ellipseMask(edgeMask, w / 2f, h, edgeRx, edgeRy)
            }
        } else {
            // A square host: the hairline is three thin boxes, far cheaper than one the size of the glow.
            val line = max(1f, dp)
            for (edge in 0 until 3) {
                val box = when (edge) {
                    0 -> Rect(0f, h - line, w, h)
                    1 -> Rect(0f, edgeBox.top, line, h - line)
                    else -> Rect(w - line, edgeBox.top, w, h - line)
                }
                layer(box, strokeAlpha) {
                    edgeLight(f, c)
                    ellipseMask(edgeMask, w / 2f, h, edgeRx, edgeRy)
                }
            }
        }

        // Bloom: the wide light the voice throws up the host.
        val bloomRx = 200f * c.rangeWidth * f.w * dp
        val bloomRy = (130f * c.rangeHeight * f.h + f.lift) * dp
        val bloomBox = Rect(0f, (h - bloomRy).coerceAtLeast(0f), w, h)
        layer(bloomBox, min(1f, base * c.theme.bloomOpacity * c.bloomOpacity)) {
            lobes(f, alpha = c.theme.bloomAlpha, widthScale = c.glowWidth * 1.15f * c.bloomScale, heightScale = c.glowHeight * 1.5f * c.bloomScale * c.bloomHeight, lift = 0f, falloff = bloomFalloff)
            ellipseMask(bloomMask, w / 2f, h, bloomRx, bloomRy)
            drawRect(edgeDown, bloomBox.topLeft, bloomBox.size, blendMode = BlendMode.DstIn)
            drawRect(edgeAcross, bloomBox.topLeft, bloomBox.size, blendMode = BlendMode.DstIn)
        }

        traceRidge(f, c)
        band(f, c, f.presence * strength)
        coreWash(f, c)
    }

    private inline fun DrawScope.layer(bounds: Rect, alpha: Float, block: DrawScope.() -> Unit) {
        if (alpha <= 0.002f || bounds.width <= 0f || bounds.height <= 0f) return
        layerPaint.alpha = alpha
        drawIntoCanvas { it.saveLayer(bounds, layerPaint) }
        block()
        drawIntoCanvas { it.restore() }
    }

    private fun DrawScope.edgeLight(f: GlowFrame, c: GlowConfig) {
        lobes(f, alpha = 1f, widthScale = c.glowWidth * c.strokeScale, heightScale = c.glowHeight * c.strokeScale, lift = 2f, falloff = lobeFalloff)
        // The hot core at the centre of the edge.
        val dp = density
        val rx = c.theme.coreWidth * c.coreSize * f.w * dp
        val ry = 30f * c.coreSize * f.h * dp
        if (rx < 0.5f || ry < 0.5f) return
        val toned = f.tone.apply(c.theme.core)
        withTransform({
            translate(size.width / 2f, size.height + 2f * dp)
            scale(rx / UnitRadius, ry / UnitRadius, Offset.Zero)
        }) {
            drawCircle(coreLight, radius = UnitRadius, center = Offset.Zero, colorFilter = ColorFilter.tint(Color(toned.r, toned.g, toned.b)))
        }
    }

    /**
     * The lobes as soft ellipses rising from the bottom edge, the first on top.
     * Each is the same [falloff], scaled into place and tinted with its colour:
     * one gradient kept for good, in place of seven new ones a frame.
     */
    private fun DrawScope.lobes(f: GlowFrame, alpha: Float, widthScale: Float, heightScale: Float, lift: Float, falloff: Brush) {
        val dp = density
        for (i in GlowLobes.indices.reversed()) {
            val lobe = GlowLobes[i]
            val rx = (lobe.w * widthScale).roundToInt() * f.w * dp
            val ry = (lobe.h * heightScale).roundToInt() * f.h * f.lobeL[i] * dp
            if (rx < 0.5f || ry < 0.5f) continue
            val rgb = f.colors[i]
            withTransform({
                translate(size.width / 2f + f.lobeX[i] * f.w * dp, size.height + lift * dp)
                scale(rx / UnitRadius, ry / UnitRadius, Offset.Zero)
            }) {
                drawCircle(falloff, radius = UnitRadius, center = Offset.Zero, alpha = alpha, colorFilter = ColorFilter.tint(Color(rgb.r, rgb.g, rgb.b)))
            }
        }
    }

    /** Keeps what is inside an ellipse, by one of the mask gradients: full at its centre, gone at its edge. */
    private fun DrawScope.ellipseMask(mask: Brush, cx: Float, cy: Float, rx: Float, ry: Float) {
        if (rx < 0.5f || ry < 0.5f) return
        val sx = rx / UnitRadius
        val sy = ry / UnitRadius
        withTransform({
            translate(cx, cy)
            scale(sx, sy, Offset.Zero)
        }) {
            // The whole canvas, in the ellipse's own coordinates.
            drawRect(
                brush = mask,
                topLeft = Offset(-cx / sx - 1f, -cy / sy - 1f),
                size = Size(size.width / sx + 2f, size.height / sy + 2f),
                blendMode = BlendMode.DstIn,
            )
        }
    }

    /**
     * The band's line: an organic bell along the glow's ceiling. With a tail
     * (the wider types) it runs past both sides of the host and its ends rise
     * again toward the corners; without one it ends inside the host.
     */
    private fun DrawScope.traceRidge(f: GlowFrame, c: GlowConfig) {
        val dp = density
        val cw = size.width / dp
        val ch = size.height / dp
        val centre = cw / 2f
        val half = 170f * c.rangeWidth * f.w
        val apexCap = ch * 0.82f * min(1f, c.scale)
        val apex = min(apexCap, (64f * c.rangeHeight * f.h + f.lift) * c.bandPosition)
        val floor = ch - c.bandOffset
        val tailed = c.bandTail > 0.001f
        val over = if (tailed) c.bandTailOverflow else 0f
        val x0 = if (tailed) -over else centre - half
        val x1 = if (tailed) cw + over else centre + half
        val edge = centre + over
        pieces = ((x1 - x0) / PieceLength).roundToInt().coerceIn(MinPieces, MaxPieces)
        for (i in 0..pieces) {
            val x = x0 + (x1 - x0) * i / pieces
            val t = ((x - centre) / max(1f, half)).coerceIn(-1f, 1f)
            val lift = if (tailed) tailLift(c, abs(x - centre), edge) else 0f
            // Whole pixels, so neighbouring pieces meet without a seam.
            ridgeX[i] = (x * dp).roundToInt().toFloat()
            ridgeY[i] = (floor - apex * (bell(c, t) + lift)) * dp
        }
        // Where the line slopes, the band is stretched downward so that it keeps its thickness across the line.
        for (i in 0..pieces) {
            val before = max(0, i - 1)
            val after = min(pieces, i + 1)
            val slope = (ridgeY[after] - ridgeY[before]) / max(1f, ridgeX[after] - ridgeX[before])
            ridgeStretch[i] = sqrt(1f + slope * slope)
        }
    }

    /**
     * The band: the line traced by a core light with a warm fringe above and a
     * cool one below, which split further and thicken with the voice.
     *
     * The original strokes the line many times and blurs it. Here the line is
     * cut into short pieces, and each piece is one rectangle laid onto the
     * line and filled with the band's cross-section as a gradient: smooth at
     * any size, and no blur to pay for.
     */
    private fun DrawScope.band(f: GlowFrame, c: GlowConfig, opacity: Float) {
        val alpha = min(1f, 0.6f * c.bandStrength * f.bendA) * opacity
        if (alpha < 0.005f || c.bandWidth <= 0f) return
        val dp = density
        val width = c.bandWidth * (1f + 0.35f * f.level)
        val split = c.bandAberration * (0.35f + 0.65f * f.level)
        val thickness = 14f * width * dp
        val reach = thickness * HaloReach
        val section = Brush.verticalGradient(
            *crossSection(f, light = c.theme.bandLight * alpha, fringe = (4f + 12f * split) * c.scale * dp, thickness = thickness, reach = reach),
            startY = -reach,
            endY = reach,
        )
        if (c.bandTail > 0.001f) return bandPieces(section, reach)
        // No tail: the band ends inside the host, and fades out toward both ends instead of stopping in a stub.
        val start = ridgeX[0]
        val end = ridgeX[pieces]
        val box = Rect(start, 0f, end, size.height)
        layer(box, 1f) {
            bandPieces(section, reach)
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent, startX = start, endX = end),
                box.topLeft,
                box.size,
                blendMode = BlendMode.DstIn,
            )
        }
    }

    private fun DrawScope.bandPieces(section: Brush, reach: Float) =
        onEachPiece { length -> drawRect(section, topLeft = Offset(0f, -reach), size = Size(length, 2f * reach)) }

    /**
     * Runs [draw] once for each piece of the line, in that piece's own space:
     * x from 0 to its length along the line, y across it with 0 on the line.
     * The piece's left side lands on its point of the line and its right side
     * on the next, each side stretched by its own slope, so the pieces meet
     * exactly and nothing drawn this way has steps along the line.
     */
    private inline fun DrawScope.onEachPiece(draw: DrawScope.(length: Float) -> Unit) {
        for (i in 0 until pieces) {
            val length = ridgeX[i + 1] - ridgeX[i]
            if (length < 1f || ridgeX[i + 1] <= 0f || ridgeX[i] >= size.width) continue
            val taper = ridgeStretch[i] / ridgeStretch[i + 1]
            piece.reset()
            piece[0, 0] = (ridgeX[i + 1] * taper - ridgeX[i]) / length
            piece[0, 1] = (ridgeY[i + 1] * taper - ridgeY[i]) / length
            piece[0, 3] = (taper - 1f) / length
            piece[1, 1] = ridgeStretch[i]
            piece[3, 0] = ridgeX[i]
            piece[3, 1] = ridgeY[i]
            withTransform({ transform(piece) }) { draw(length) }
        }
    }

    /**
     * The band from its upper edge to its lower one, as gradient stops: a wide
     * hazy halo, then the warm fringe riding above, a faint third between, the
     * cool fringe below and the core on the line, each a soft bump.
     */
    private fun crossSection(f: GlowFrame, light: Float, fringe: Float, thickness: Float, reach: Float): Array<Pair<Float, Color>> {
        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        fun over(color: GlowRgb, alpha: Float) {
            r = color.r * alpha + r * (1f - alpha)
            g = color.g * alpha + g * (1f - alpha)
            b = color.b * alpha + b * (1f - alpha)
            a = alpha + a * (1f - alpha)
        }
        // A triangle of the band's thickness after the original's blur: near enough a bell.
        fun bump(v: Float, centre: Float): Float {
            val u = (v - centre) / (thickness / 2f)
            return 0.833f * exp(-u * u / 0.459f)
        }
        return Array(SectionStops) { k ->
            val at = k / (SectionStops - 1f)
            val v = -reach + 2f * reach * at
            r = 0f
            g = 0f
            b = 0f
            a = 0f
            val haze = ((HaloReach - 0.05f - abs(v) / thickness) / 1.5f).coerceIn(0f, 1f)
            over(f.bandCore, light * 0.3f * haze * haze * (3f - 2f * haze))
            over(f.bandAbove, light * 0.96f * bump(v, -fringe))
            over(f.bandMid, light * 0.96f * 0.55f * bump(v, -fringe * 0.35f))
            over(f.bandBelow, light * 0.96f * bump(v, fringe))
            over(f.bandCore, light * 0.96f * 0.9f * bump(v, 0f))
            if (a > 0.0005f) {
                val toned = f.tone.apply(GlowRgb((r / a).coerceIn(0f, 1f), (g / a).coerceIn(0f, 1f), (b / a).coerceIn(0f, 1f)))
                at to Color(toned.r, toned.g, toned.b, a.coerceIn(0f, 1f))
            } else {
                at to Color.Transparent
            }
        }
    }

    /**
     * The epicentre, on a light background: a soft white wash at the source,
     * over the lower half of the band, so the centre reads lighter than the
     * band. The original clips it at the band line and then blurs it; here it
     * is kept below the line by a mask that is already soft.
     */
    private fun DrawScope.coreWash(f: GlowFrame, c: GlowConfig) {
        val wash = wash ?: return
        val boost = (c.coreLight - 1f).coerceIn(0f, 2f)
        val opacity = f.presence * strength * min(1f, f.glow * min(1f, c.coreLight) * (1.6f + 1.4f * boost))
        if (opacity <= 0.005f) return
        val dp = density
        val grow = 1f + 0.3f * boost
        val rx = 120f * c.coreLightWidth * grow * c.scale * f.w * dp
        val ry = (70f * c.coreLightHeight * grow * c.scale * f.h + f.lift) * dp
        if (rx < 0.5f || ry < 0.5f) return
        val w = size.width
        val h = size.height
        val box = Rect(0f, (h - ry).coerceAtLeast(0f), w, h)
        layer(box, opacity) {
            withTransform({
                translate(w / 2f, h)
                scale(rx / UnitRadius, ry / UnitRadius, Offset.Zero)
            }) {
                drawCircle(wash, radius = UnitRadius, center = Offset.Zero)
            }
            val far = 2f * h
            onEachPiece { length -> drawRect(underLine, topLeft = Offset(0f, -far), size = Size(length, 2f * far), blendMode = BlendMode.DstIn) }
            // Past the ends of the line there is no "under the line": nothing is kept.
            if (ridgeX[0] > 0f) drawRect(Color.Black, box.topLeft, Size(ridgeX[0], box.height), blendMode = BlendMode.Clear)
            if (ridgeX[pieces] < w) drawRect(Color.Black, Offset(ridgeX[pieces], box.top), Size(w - ridgeX[pieces], box.height), blendMode = BlendMode.Clear)
        }
    }

    /** exp(-(|t| / σ)^p): 1 at the centre and exactly 0 at the ends. */
    private fun bell(c: GlowConfig, t: Float): Float {
        val side = if (t < 0f) 1f - c.bandSkew else 1f + c.bandSkew
        val sigma = max(0.05f, c.bandSpread * side)
        val v = exp(-(abs(t) / sigma).pow(c.bandCurve))
        val tail = exp(-(1f / sigma).pow(c.bandCurve))
        return max(0f, (v - tail) / (1f - tail))
    }

    /** The ends rise again toward the corners. */
    private fun tailLift(c: GlowConfig, distance: Float, edge: Float): Float {
        val start = edge * c.bandTailPosition.coerceIn(0f, 0.98f)
        if (distance <= start) return 0f
        val u = min(1f, (distance - start) / max(1f, edge - start))
        return c.bandTail * u.pow(max(0.5f, c.bandTailCurve))
    }

    private companion object {
        /** The lobes, the masks and the cores are drawn once at this radius and scaled into place. */
        const val UnitRadius = 256f

        /** A mask across an ellipse's radius, from pairs of position and what is kept there. */
        fun fading(vararg stops: Pair<Float, Float>): Brush =
            Brush.radialGradient(*Array(stops.size) { i -> stops[i].first to Color.Black.copy(alpha = stops[i].second) }, center = Offset.Zero, radius = UnitRadius)

        /** A lobe's light across its radius: linear to the fade stop, with a softened foot in place of the original's blur. */
        fun falloff(fade: Float): Brush = Brush.radialGradient(
            0f to Color.White,
            fade * 0.82f to Color.White.copy(alpha = 0.18f),
            fade to Color.White.copy(alpha = 0.05f),
            min(1f, fade * 1.12f) to Color.Transparent,
            center = Offset.Zero,
            radius = UnitRadius,
        )

        /**
         * The band is drawn in pieces about this long, in dp. They join
         * exactly, so they only need to be short enough to follow the curve;
         * and each one costs the platform a matrix, so no shorter than that.
         */
        const val PieceLength = 10f
        const val MinPieces = 24
        const val MaxPieces = 160
        const val SectionStops = 33
        const val HazeStops = 7

        /** How far the band's halo reaches from its line, in band thicknesses. */
        const val HaloReach = 1.9f
    }
}
