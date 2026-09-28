package ru.voidrp.ui.input

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A pointer the client moves by itself.
 *
 * Sent frame by frame, the pointer can only be as smooth as packets arrive, and they do not
 * arrive in step with the player's screen: at 85 a second on a 60 Hz screen one frame took
 * two of ours and the next none, and the pointer went in uneven steps however smooth the
 * reckoning behind it was. The client already knows how to move something smoothly between
 * two things a server says — that is how it draws display entities, interpolated at its own
 * frame rate from twenty updates a second — and its text shader can do the same, because
 * the time it is given (`GameTime`) runs on through a tick rather than stepping at its end.
 *
 * So instead of a position a frame, the pointer is sent as **a place at a tick and a speed**:
 * where it is when the world's clock reads [tick], and how many canvas units it covers per
 * tick. The shader draws it at `place + speed × (now − tick)` on every frame of the screen,
 * whatever the screen's rate. Packets go out only when the pointer changes course.
 *
 * Nothing here needs the client's clock to agree with ours. While the speed holds, the old
 * and the new packet put the pointer in the same place at every moment, so however far the
 * two clocks are apart the hand-over is seamless; only a change of speed shows the gap, and
 * then only as that change times the part of a tick the clocks differ by.
 */
object MotionCodec {

    /**
     * The speeds a pointer can be sent at, in canvas units per tick, fastest last.
     *
     * Six bits a direction: a sign and one of these thirty-two — nothing, then from half a
     * unit a tick up to about seven hundred, each a quarter faster than the one before, so
     * a speed is never off by more than an eighth of itself, which the planner steers out.
     * The top of it is a flick across a full-HD canvas in three ticks; an earlier table
     * stopped at 160, the pointer fell behind a fast hand and caught up in one jump.
     */
    val SPEEDS: DoubleArray = DoubleArray(32) { i ->
        if (i == 0) 0.0 else Math.round(0.5 * Math.pow(SPEED_RATIO, (i - 1).toDouble()) * 100) / 100.0
    }

    private const val SPEED_RATIO = 1.273

    /** The sign bit of a speed's code. */
    const val NEGATIVE = 32

    /** The nearest speed that can be sent, as its six-bit code. */
    fun code(speed: Double): Int {
        val magnitude = abs(speed)
        var best = 0
        var error = Double.MAX_VALUE
        for (i in SPEEDS.indices) {
            // Measured as a ratio above a unit a tick, so the steps are judged the way they
            // are spaced; below it, as a plain difference, so a crawl rounds to a stop.
            val e = if (magnitude < 1.0 || SPEEDS[i] < 1.0) abs(magnitude - SPEEDS[i])
            else abs(Math.log(magnitude / SPEEDS[i]))
            if (e < error) {
                error = e
                best = i
            }
        }
        return if (best != 0 && speed < 0) best or NEGATIVE else best
    }

    /** The fastest speed that can be sent without going faster than [speed]: never past. */
    fun codeAtMost(speed: Double): Int {
        val magnitude = abs(speed)
        var best = 0
        for (i in SPEEDS.indices) if (SPEEDS[i] <= magnitude + 1e-9) best = i
        return if (best != 0 && speed < 0) best or NEGATIVE else best
    }

    /** What a code stands for, in units a tick. */
    fun speed(code: Int): Double {
        val magnitude = SPEEDS[code and 31]
        return if (code and NEGATIVE != 0) -magnitude else magnitude
    }

    /** How many ticks a place-and-speed stays readable: the tick travels modulo this. */
    const val TICK_WRAP = 4

    /**
     * The vertical place of a moving pointer travels in steps of this many units, to leave
     * bits for the speed; nobody sees four units on something moving. A pointer at rest is
     * sent the ordinary way, to the unit.
     */
    const val Y_STEP = 4

    /** How far above the canvas a moving glyph may be, for the lifted bars below the first. */
    const val Y_SHIFT = 64

    /** And how many steps of it there are. */
    const val Y_STEPS = 256

    /** A y, as the step that carries it. */
    fun yStep(y: Int): Int = ((y + Y_SHIFT).toDouble() / Y_STEP).roundToInt().coerceIn(0, Y_STEPS - 1)

    /** And back: the y the shader will draw at. */
    fun yOf(step: Int): Int = step * Y_STEP - Y_SHIFT

    /**
     * 22 bits: the place's y (8), the tick (2), the speed across (6) and down (6).
     *
     * The top two go into which of four markers the glyph carries, the other twenty into
     * its colour, which is why the pointer is always drawn white.
     */
    fun data(yStep: Int, tick: Int, vx: Int, vy: Int): Int =
        ((yStep and 255) shl 14) or ((tick and (TICK_WRAP - 1)) shl 12) or ((vx and 63) shl 6) or (vy and 63)
}

/**
 * What to send, as the shader will read it: a place at a whole [tick] and a speed each way.
 */
data class MotionPlan(val x: Int, val y: Int, val tick: Long, val vx: Int, val vy: Int)

/**
 * Chooses what to send so the pointer on the screen goes to where the hand last was, and
 * stops there.
 *
 * The client's pointer never runs ahead of the last reading of the aim. An earlier planner
 * carried the hand on by its speed so the pointer would not trail it, and every time the
 * hand stopped the pointer ran on until the stop was heard, then came back: tens of units,
 * the one thing left wrong once it was otherwise smooth. This is how the client moves an
 * entity between two updates instead — it goes to where it was last told, and no further.
 *
 * The shader carries a place on for at most [ELAPSED_MAX] ticks, so the end of that is
 * where the pointer comes to rest. Each packet is laid out to put that end on the target:
 * the place is where the client's pointer is now (so nothing jumps), and the speed is the
 * one that covers the rest in the time left before the end. Speeds are rounded towards
 * zero, so the pointer may stop a little short, which the next packet makes up — it never
 * overshoots. Where it stops does not depend on the client's clock at all: only when.
 */
class MotionPlanner {

    private var sent = false
    private var placeX = 0.0
    private var placeY = 0.0
    private var speedX = 0.0
    private var speedY = 0.0
    private var at = 0L

    /** Where the client is drawing the pointer at [clock], by our model of it. */
    fun shown(clock: Double): Pair<Double, Double>? {
        if (!sent) return null
        val elapsed = (clock - at).coerceIn(ELAPSED_MIN, ELAPSED_MAX)
        return (placeX + speedX * elapsed) to (placeY + speedY * elapsed)
    }

    /** Where the client's pointer will come to rest. */
    fun resting(): Pair<Double, Double>? =
        if (!sent) null else (placeX + speedX * ELAPSED_MAX) to (placeY + speedY * ELAPSED_MAX)

    /** Whether the client is drawing a pointer that is still on its way at [clock]. */
    fun moving(clock: Double): Boolean = sent && (speedX != 0.0 || speedY != 0.0) && clock - at < ELAPSED_MAX

    /** Plans the next packet: the pointer to go to [x], [y] from wherever it is at [clock]. */
    fun plan(x: Double, y: Double, clock: Double): MotionPlan {
        val shown = shown(clock) ?: (x to y)
        val dx = x - shown.first
        val dy = y - shown.second
        if (Math.abs(dx) < SETTLE && Math.abs(dy) < SETTLE) return rest(x, y, clock)
        // A tick for the place such that between 1.2 and 2.2 ticks are left before the end.
        // Readings come about a tick apart, so while the hand moves the next one always
        // lands before the pointer arrives and it never stands waiting for it; an earlier
        // half to one and a half ticks let it arrive first, stop, and go again every tick.
        val whole = floor(clock).toLong()
        val part = clock - whole
        val tick = if (part <= 0.8) whole else whole + 1
        val elapsed = clock - tick
        val left = ELAPSED_MAX - elapsed
        val codeX = MotionCodec.codeAtMost(dx / left)
        val codeY = MotionCodec.codeAtMost(dy / left)
        val sx = MotionCodec.speed(codeX)
        val sy = MotionCodec.speed(codeY)
        val px = (shown.first - sx * elapsed).roundToInt()
        // y is rounded to the encoder's steps only once it is lifted onto its bar.
        val py = (shown.second - sy * elapsed).roundToInt()
        remember(px.toDouble(), py.toDouble(), sx, sy, tick)
        return MotionPlan(px, py, tick, codeX, codeY)
    }

    /** At rest exactly on [x], [y]. */
    private fun rest(x: Double, y: Double, clock: Double): MotionPlan {
        val tick = floor(clock).toLong()
        remember(x.roundToInt().toDouble(), y.roundToInt().toDouble(), 0.0, 0.0, tick)
        return MotionPlan(x.roundToInt(), y.roundToInt(), tick, 0, 0)
    }

    private fun remember(x: Double, y: Double, vx: Double, vy: Double, tick: Long) {
        sent = true
        placeX = x
        placeY = y
        speedX = vx
        speedY = vy
        at = tick
    }

    /** Forgets what the client was drawing: the next packet starts where the pointer is. */
    fun reset() {
        sent = false
    }

    companion object {
        /** Closer than this to where it belongs, and the pointer is simply put there. */
        const val SETTLE = 1.5

        /**
         * How far from its tick the shader carries a place: a little back, for a client
         * whose clock runs behind ours, and two ticks on — where the pointer stops.
         */
        const val ELAPSED_MIN = -1.0
        const val ELAPSED_MAX = 2.0

        /**
         * Where the shader turns an elapsed time read modulo [MotionCodec.TICK_WRAP] into
         * one before the tick instead: past three ticks is a place from just ahead.
         */
        const val ELAPSED_WRAP = 3.0
    }
}
