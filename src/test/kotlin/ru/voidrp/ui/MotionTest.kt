package ru.voidrp.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.input.MotionCodec
import ru.voidrp.ui.input.MotionPlanner
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

    /** Where the shader draws a planned pointer at [clock], as it would on the client. */
    private fun drawn(plan: ru.voidrp.ui.input.MotionPlan, clock: Double): Pair<Double, Double> {
        val elapsed = (clock - plan.tick).coerceIn(MotionPlanner.ELAPSED_MIN, MotionPlanner.ELAPSED_MAX)
        return (plan.x + MotionCodec.speed(plan.vx) * elapsed) to (plan.y + MotionCodec.speed(plan.vy) * elapsed)
    }

    @Test
    fun `a sweep is handed over without a jump and never runs past the hand`() {
        val planner = MotionPlanner()
        val speed = 23.0   // units a tick
        var worstJump = 0.0
        var worstPast = 0.0
        var last: ru.voidrp.ui.input.MotionPlan? = null
        var clock = 100.3
        var hand = 200.0
        repeat(40) {
            hand += speed
            val next = planner.plan(hand, 500.0, clock)
            last?.let { worstJump = maxOf(worstJump, abs(drawn(it, clock).first - drawn(next, clock).first)) }
            // Everywhere until the next reading, the pointer is short of the hand or on it.
            var t = clock
            while (t < clock + 1.0) {
                worstPast = maxOf(worstPast, drawn(next, t).first - hand)
                t += 0.1
            }
            last = next
            clock += 1.0
        }
        assertTrue(worstJump <= 1.5, "jumped $worstJump units at a hand-over")
        assertTrue(worstPast <= 1.0, "ran $worstPast units past the hand")
    }

    @Test
    fun `a stopped hand leaves the pointer exactly where it is`() {
        val planner = MotionPlanner()
        var plan = planner.plan(300.0, 300.0, 50.0)
        plan = planner.plan(411.0, 300.0, 50.6)
        // Wherever it is drawn after the end, it rests short of the hand, never past it.
        assertTrue(drawn(plan, 60.0).first <= 411.0)
        // And once it has stopped short, the next packet puts it on the hand.
        plan = planner.plan(411.0, 300.0, 53.0)
        plan = planner.plan(411.0, 300.0, 56.0)
        assertEquals(411.0, drawn(plan, 60.0).first, 1.5)
    }

    @Test
    fun `a pointer that has arrived is told to rest`() {
        val planner = MotionPlanner()
        planner.plan(300.0, 300.0, 50.0)
        planner.plan(360.0, 330.0, 51.0)
        assertTrue(planner.ended(54.0), "a moving place must not be left to come round again")
        val rest = planner.plan(360.0, 330.0, 54.0)
        assertEquals(0, rest.vx)
        assertEquals(0, rest.vy)
        assertTrue(!planner.ended(60.0))
    }

    @Test
    fun `a speed is never rounded up`() {
        var v = 0.3
        while (v < 650.0) {
            assertTrue(MotionCodec.speed(MotionCodec.codeAtMost(v)) <= v + 1e-9)
            assertTrue(MotionCodec.speed(MotionCodec.codeAtMost(-v)) >= -v - 1e-9)
            v *= 1.05
        }
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
