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
     * Five bits a direction: a sign and one of these sixteen. Spaced by about four tenths
     * at most, so a speed is never off by more than a quarter of itself — which the planner
     * then steers out, since it aims at where the pointer should be, not at the speed.
     * 160 a tick is a flick across a full-HD canvas in under a third of a second.
     */
    val SPEEDS = doubleArrayOf(
        0.0, 0.5, 1.0, 1.75, 2.75, 4.0, 6.0, 8.5,
        12.0, 17.0, 24.0, 34.0, 50.0, 75.0, 110.0, 160.0,
    )

    /** The sign bit of a speed's code. */
    const val NEGATIVE = 16

    /** The nearest speed that can be sent, as its five-bit code. */
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

    /** What a code stands for, in units a tick. */
    fun speed(code: Int): Double {
        val magnitude = SPEEDS[code and 15]
        return if (code and NEGATIVE != 0) -magnitude else magnitude
    }

    /** How many ticks a place-and-speed stays readable: the tick travels modulo this. */
    const val TICK_WRAP = 8

    /** The vertical place travels in steps of this many units, to leave bits for the rest. */
    const val Y_STEP = 2

    /** How far above the canvas a moving glyph may be, for the lifted bars below the first. */
    const val Y_SHIFT = 64

    /** And how many steps of it there are. */
    const val Y_STEPS = 512

    /** A y, as the step that carries it. */
    fun yStep(y: Int): Int = ((y + Y_SHIFT).toDouble() / Y_STEP).roundToInt().coerceIn(0, Y_STEPS - 1)

    /** And back: the y the shader will draw at. */
    fun yOf(step: Int): Int = step * Y_STEP - Y_SHIFT

    /**
     * 22 bits: the place's y (9), the tick (3), the speed across (5) and down (5).
     *
     * The top two go into which of four markers the glyph carries, the other twenty into
     * its colour, which is why the pointer is always drawn white.
     */
    fun data(yStep: Int, tick: Int, vx: Int, vy: Int): Int =
        (yStep shl 13) or ((tick and (TICK_WRAP - 1)) shl 10) or ((vx and 31) shl 5) or (vy and 31)
}

/**
 * What to send, as the shader will read it: a place at a whole [tick] and a speed each way.
 */
data class MotionPlan(val x: Int, val y: Int, val tick: Long, val vx: Int, val vy: Int)

/**
 * Chooses what to send so the pointer on the screen follows the pointer we reckon.
 *
 * It keeps a model of what the client is drawing — the last place and speed sent — and each
 * time it is asked it starts from where that model is **now**, not from where the pointer
 * should be: a new packet that starts somewhere else is a jump. Whatever the model is behind
 * or ahead goes into the speed instead, to be made up over [CATCH_UP] ticks. Only when the
 * two have drifted too far apart to steer back ([SNAP] units) is the jump taken.
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

    /** Whether the client is drawing a pointer that is still moving. */
    val moving: Boolean get() = sent && (speedX != 0.0 || speedY != 0.0)

    /**
     * Plans the next packet. [x], [y] is where the pointer should be at [clock] (in ticks of
     * the world's clock, with the part of a tick gone), and [vx], [vy] how fast it is
     * going, in units a tick.
     */
    fun plan(x: Double, y: Double, vx: Double, vy: Double, clock: Double): MotionPlan {
        val shown = shown(clock)
        val fromX: Double
        val fromY: Double
        if (shown == null || abs(shown.first - x) > SNAP || abs(shown.second - y) > SNAP) {
            fromX = x
            fromY = y
        } else {
            fromX = shown.first
            fromY = shown.second
        }
        val still = abs(vx) < STILL && abs(vy) < STILL
        // Come to rest exactly: a pointer the hand has stopped is not steered, it is put there.
        val settle = still && abs(fromX - x) <= SETTLE && abs(fromY - y) <= SETTLE
        val codeX = if (settle) 0 else MotionCodec.code(vx + (x - fromX) / CATCH_UP)
        val codeY = if (settle) 0 else MotionCodec.code(vy + (y - fromY) / CATCH_UP)
        val tick = floor(clock).toLong()
        val part = clock - tick
        val sx = MotionCodec.speed(codeX)
        val sy = MotionCodec.speed(codeY)
        val startX = if (settle) x else fromX
        val startY = if (settle) y else fromY
        // The place travels as the one it had at the start of the tick.
        val px = (startX - sx * part).roundToInt()
        // y is rounded to the encoder's steps only once it is lifted onto its bar; a unit
        // either way is nothing the model needs to know about.
        val py = (startY - sy * part).roundToInt()
        sent = true
        placeX = px.toDouble()
        placeY = py.toDouble()
        speedX = sx
        speedY = sy
        at = tick
        return MotionPlan(px, py, tick, codeX, codeY)
    }

    /** Forgets what the client was drawing: the next packet starts where the pointer is. */
    fun reset() {
        sent = false
    }

    companion object {
        /** Ticks a gap between the model and the pointer is made up over. */
        const val CATCH_UP = 1.5

        /** Further apart than this, and the pointer jumps rather than chases. */
        const val SNAP = 160.0

        /** Slower than this, in units a tick, and the hand has stopped. */
        const val STILL = 0.25

        /** Closer than this to where it belongs, and a stopped pointer is simply put there. */
        const val SETTLE = 3.0

        /**
         * How far from its tick the shader carries a place: a little back, for a client
         * whose clock runs behind ours, and two ticks on, after which a pointer whose
         * packets have stopped stands still rather than sailing off the screen.
         */
        const val ELAPSED_MIN = -1.5
        const val ELAPSED_MAX = 2.0
    }
}
