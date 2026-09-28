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
        for (code in 0 until 32) {
            if (code == MotionCodec.NEGATIVE) continue   // minus nothing is written as nothing
            assertEquals(code, MotionCodec.code(MotionCodec.speed(code)))
        }
    }

    @Test
    fun `a speed is sent within a quarter of itself`() {
        var v = 1.0
        while (v < 130.0) {
            val sent = MotionCodec.speed(MotionCodec.code(v))
            // A crawl is judged by how far off it is, the rest by how much of itself.
            assertTrue(if (v < 2.0) abs(sent - v) < 0.4 else abs(sent - v) / v < 0.25, "$v went as $sent")
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
        assertEquals(y.toDouble(), MotionCodec.yOf((data shr 13) and 511).toDouble(), 1.0)
        assertEquals(1234 % MotionCodec.TICK_WRAP, (data shr 10) and 7)
        assertEquals(-17.0, MotionCodec.speed((data shr 5) and 31))
        assertEquals(4.0, MotionCodec.speed(data and 31))
    }

    @Test
    fun `a lifted glyph near the top of the screen still fits`() {
        val colour = GlyphEncoder.packMotion(-57, SpriteMotion(0, 0, 0))
        val mark = colour shr 20
        val data = ((mark - Shaders.MARKER_MOTION_FIRST) shl 20) or (colour and 0xFFFFF)
        assertEquals(-57.0, MotionCodec.yOf((data shr 13) and 511).toDouble(), 1.0)
    }

    /** Where the shader draws a planned pointer at [clock], as it would on the client. */
    private fun drawn(plan: ru.voidrp.ui.input.MotionPlan, clock: Double): Pair<Double, Double> {
        val elapsed = (clock - plan.tick).coerceIn(MotionPlanner.ELAPSED_MIN, MotionPlanner.ELAPSED_MAX)
        return (plan.x + MotionCodec.speed(plan.vx) * elapsed) to (plan.y + MotionCodec.speed(plan.vy) * elapsed)
    }

    @Test
    fun `a steady sweep is handed over without a jump and stays on the hand`() {
        val planner = MotionPlanner()
        val speed = 23.0   // units a tick: not one of the codes, so the planner has to steer
        var worstJump = 0.0
        var worstOff = 0.0
        var last: ru.voidrp.ui.input.MotionPlan? = null
        var clock = 100.3
        repeat(60) {
            val handX = 200.0 + speed * (clock - 100.3)
            last?.let { before ->
                val next = planner.plan(handX, 500.0, speed, 0.0, clock)
                worstJump = maxOf(worstJump, abs(drawn(before, clock).first - drawn(next, clock).first))
                worstOff = maxOf(worstOff, abs(drawn(next, clock).first - handX))
                last = next
            } ?: run { last = planner.plan(handX, 500.0, speed, 0.0, clock) }
            clock += 1.0
        }
        assertTrue(worstJump <= 1.5, "jumped $worstJump units at a hand-over")
        assertTrue(worstOff < 12.0, "fell $worstOff units off the hand")
    }

    @Test
    fun `a stopped hand leaves the pointer exactly where it is`() {
        val planner = MotionPlanner()
        planner.plan(300.0, 300.0, 10.0, 0.0, 50.0)
        val plan = planner.plan(311.0, 300.0, 0.0, 0.0, 51.0)
        assertEquals(0.0, MotionCodec.speed(plan.vx))
        assertEquals(311, plan.x)
        assertTrue(!planner.moving)
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
