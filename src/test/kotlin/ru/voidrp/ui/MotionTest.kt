package ru.voidrp.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.input.MotionCodec
import ru.voidrp.ui.input.MotionTimeline
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.SpriteMotion

class MotionTest {

    @Test
    fun `every speed code reads back as itself`() {
        for (code in 0 until 64) {
            if (code == MotionCodec.NEGATIVE) continue   // minus nothing is written as nothing
            assertEquals(code, MotionCodec.code(MotionCodec.speed(code)))
        }
    }

    @Test
    fun `a speed is sent within an eighth of itself`() {
        var v = 1.0
        while (v < 650.0) {
            val sent = MotionCodec.speed(MotionCodec.code(v))
            // A crawl is judged by how far off it is, the rest by how much of itself.
            assertTrue(if (v < 2.0) abs(sent - v) < 0.4 else abs(sent - v) / v < 0.13, "$v went as $sent")
            assertEquals(-sent, MotionCodec.speed(MotionCodec.code(-v)))
            v *= 1.07
        }
        assertEquals(0.0, MotionCodec.speed(MotionCodec.code(0.2)))
    }

    @Test
    fun `the colour carries what the shader decodes`() {
        val y = 437
        val colour = GlyphEncoder.packMotion(y, SpriteMotion(tick = 1234, vx = MotionCodec.code(-17.0), vy = MotionCodec.code(4.0)))
        // As the shader reads it: marker from the red nibble, twenty bits of colour.
        val mark = colour shr 20
        assertTrue(mark in Shaders.MARKER_MOTION_FIRST..Shaders.MARKER_MOTION_LAST)
        val data = ((mark - Shaders.MARKER_MOTION_FIRST) shl 20) or (colour and 0xFFFFF)
        assertEquals(y.toDouble(), MotionCodec.yOf((data shr 14) and 255).toDouble(), 2.0)
        assertEquals(1234 % MotionCodec.TICK_WRAP, (data shr 12) and 3)
        assertEquals(MotionCodec.speed(MotionCodec.code(-17.0)), MotionCodec.speed((data shr 6) and 63))
        assertEquals(MotionCodec.speed(MotionCodec.code(4.0)), MotionCodec.speed(data and 63))
    }

    @Test
    fun `a lifted glyph near the top of the screen still fits`() {
        val colour = GlyphEncoder.packMotion(-57, SpriteMotion(0, 0, 0))
        val mark = colour shr 20
        val data = ((mark - Shaders.MARKER_MOTION_FIRST) shl 20) or (colour and 0xFFFFF)
        assertEquals(-57.0, MotionCodec.yOf((data shr 14) and 255).toDouble(), 2.0)
    }

    /** What the shader draws from a packet's glyphs at [clock]: the one whose time it is. */
    private fun drawn(glyphs: List<ru.voidrp.ui.input.MotionGlyph>, clock: Double): List<Double> {
        val out = mutableListOf<Double>()
        for (g in glyphs) {
            var e = (clock - g.tick).mod(MotionCodec.TICK_WRAP.toDouble())
            if (e >= MotionTimeline.WRAP_AT) e -= MotionCodec.TICK_WRAP
            if (if (g.hold) e < 1 else (e < 0 || e >= 1)) continue
            out += if (g.hold) g.x.toDouble() else g.x + MotionCodec.speed(g.vx) * e
        }
        return out
    }

    @Test
    fun `segments join end to end, one tick each, and end in a hold`() {
        val timeline = MotionTimeline()
        timeline.place(100.0, 400.0)
        var glyphs = emptyList<ru.voidrp.ui.input.MotionGlyph>()
        var clock = 10.72
        repeat(6) { k ->
            glyphs = timeline.reading(100.0 + 37.0 * (k + 1), 400.0 + 11.0 * (k + 1), clock)
            clock += 1.0
        }
        val moving = glyphs.filter { !it.hold }
        for ((a, b) in moving.zipWithNext()) {
            assertEquals(a.tick + 1, b.tick)
            assertEquals((a.x + MotionCodec.speed(a.vx)), b.x.toDouble(), 1.0)
        }
        assertTrue(glyphs.last().hold)
        assertEquals(moving.last().tick, glyphs.last().tick)
    }

    @Test
    fun `whenever packets land, one pointer is drawn and it never jumps or goes back`() {
        val timeline = MotionTimeline()
        timeline.place(100.0, 400.0)
        val random = java.util.Random(7)
        // Packets: (the client clock they land at, their glyphs).
        val packets = mutableListOf<Pair<Double, List<ru.voidrp.ui.input.MotionGlyph>>>()
        var clock = 20.72
        for (k in 1..30) {
            val glyphs = timeline.reading(100.0 + 30.0 * k, 400.0, clock)
            packets += (clock + random.nextDouble() * MotionTimeline.MARGIN * 0.9) to glyphs
            clock += 0.9 + random.nextDouble() * 0.2
        }
        var shown: List<ru.voidrp.ui.input.MotionGlyph> = emptyList()
        var last: Double? = null
        var worst = 0.0
        var c = 21.0
        var p = 0
        while (c < clock) {
            while (p < packets.size && packets[p].first <= c) shown = packets[p++].second
            val at = drawn(shown, c)
            if (shown.isNotEmpty()) assertEquals(1, at.size, "pointers drawn at $c: $at from $shown")
            at.firstOrNull()?.let { x ->
                last?.let { worst = maxOf(worst, Math.abs(x - it)); assertTrue(x >= it - 0.5, "went back at $c") }
                last = x
            }
            c += 1.0 / 3   // sixty frames a second
        }
        assertTrue(worst <= 12.0, "a frame moved $worst units")
    }

    @Test
    fun `a schedule played out comes to rest`() {
        val timeline = MotionTimeline()
        timeline.place(100.0, 400.0)
        timeline.reading(160.0, 400.0, 30.7)
        assertTrue(!timeline.settled(31.0))
        assertTrue(timeline.settled(35.0), "a hold must be replaced before it comes round again")
        timeline.rest()
        assertTrue(timeline.resting)
    }

    @Test
    fun `the motion shader is built only when asked for`() {
        Shaders.motion = false
        assertTrue("VOIDRP_MOTION 1" !in Shaders.TEXT_VSH_MODERN)
        Shaders.motion = true
        try {
            val source = Shaders.TEXT_VSH_MODERN
            assertTrue("#define VOIDRP_MOTION 1" in source)
            assertTrue("globals.glsl" in source)
            assertTrue("VOIDRP_MOTION 1" !in Shaders.TEXT_VSH_LEGACY)
            // For checking with glslang by hand: build/shaders/text.vsh
            java.io.File("build/shaders").mkdirs()
            java.io.File("build/shaders/text.vsh").writeText(source)
        } finally {
            Shaders.motion = false
        }
    }
}
