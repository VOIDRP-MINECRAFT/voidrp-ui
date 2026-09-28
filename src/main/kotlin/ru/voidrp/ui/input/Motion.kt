package ru.voidrp.ui.input

import kotlin.math.abs
import kotlin.math.ceil
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

    /** Minus nothing, both ways: a hold rather than a segment. */
    const val HOLD = NEGATIVE

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

    /**
     * The speed to cover [distance] in [ticks]: the nearest there is, unless that would end
     * more than [OVERSHOOT] units past it, then the next one down. Rounding always down made
     * a stop a string of small hops, each short by up to a quarter of what was left.
     */
    fun codeToward(distance: Double, ticks: Double): Int {
        val near = code(distance / ticks)
        val past = Math.abs(speed(near) * ticks) - Math.abs(distance)
        return if (past > OVERSHOOT) codeAtMost(distance / ticks) else near
    }

    /** Units a pointer may end past where it was sent. */
    const val OVERSHOOT = 1.0

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
 * One glyph of a moving pointer, as the shader reads it: a place at a whole [tick], and
 * how far it goes in the one tick after — or, for [hold], where it stays from then on.
 */
data class MotionGlyph(val x: Int, val y: Int, val tick: Long, val vx: Int, val vy: Int) {
    val hold: Boolean get() = vx == MotionCodec.HOLD && vy == MotionCodec.HOLD
}

/**
 * The pointer as a schedule the client plays by its own clock.
 *
 * Sent as a place and a speed, the pointer changed course whenever a packet happened to be
 * applied — a network's and a frame's worth after it was planned, different every time —
 * and every change of speed moved it by that change times that delay: ten units, twenty
 * times a second, measured from a recording of a real hand. No amount of steering helps,
 * because the delay is not ours to know.
 *
 * So the course is changed at a time the client knows instead. Each reading of the aim
 * becomes a segment: from where the last one ended to the new reading, in the one tick
 * after a whole tick of the world's clock a little ahead of now. Every segment is visible
 * in its own tick only, and the last one is followed by a [hold] at its end; the shader
 * shows whichever one's time it is. Each packet carries the segments still to be played,
 * so when it lands does not matter as long as it lands before its first segment starts:
 * the pointer goes through the readings joined end to end, with no jump anywhere — the
 * way the client moves an entity between two updates, a tick and a bit behind the hand.
 */
class MotionTimeline {

    private data class Segment(val start: Long, val x: Int, val y: Int, val vx: Int, val vy: Int)

    private val segments = ArrayDeque<Segment>()

    /** Where the schedule ends: the tick, and the place the pointer stays at after it. */
    private var endTick: Long? = null
    var endX = 0.0
        private set
    var endY = 0.0
        private set
    private var placed = false

    /** Whether the pointer is at rest, sent the ordinary way rather than as a schedule. */
    var resting = true
        private set

    /** The pointer put somewhere outright, at rest — a page that opens. */
    fun place(x: Double, y: Double) {
        segments.clear()
        endTick = null
        endX = x
        endY = y
        placed = true
        resting = true
    }

    /**
     * A new reading: a segment from where the schedule ends to [x], [y]. [yAsSent] is the
     * encoder's rounding of a height, so the schedule knows where the client really starts.
     */
    fun reading(x: Double, y: Double, clock: Double, yAsSent: (Double) -> Int = { Math.round(it).toInt() }): List<MotionGlyph> {
        if (!placed) {
            place(x, y)
            return emptyList()
        }
        val now = floor(clock).toLong()
        val earliest = ceil(clock + MARGIN).toLong()
        // A segment that has not started yet is aimed at the new reading instead of being
        // queued behind: readings a little closer than a tick apart would otherwise build
        // a backlog, the pointer falling further behind and the schedule reaching so far
        // ahead that the shader, whose clock goes round every four ticks, read it as past.
        segments.lastOrNull()?.takeIf { it.start >= earliest }?.let { pending ->
            segments.removeLast()
            endX = pending.x.toDouble()
            endY = pending.y.toDouble()
            endTick = pending.start
        }
        // From rest, or after a gap, the pointer stands where it is until the new segment:
        // as one-tick segments of no speed, so nothing is left over once it moves on.
        var fill = maxOf(endTick ?: (now - 1), now - 1)
        val start = maxOf(endTick ?: earliest, earliest)
        while (fill < start) {
            segments.addLast(Segment(fill, Math.round(endX).toInt(), yAsSent(endY), 0, 0))
            fill++
        }
        val fromX = Math.round(endX).toInt()
        val fromY = yAsSent(endY)
        val vx = MotionCodec.code(x - fromX)
        val vy = MotionCodec.code(y - fromY)
        segments.addLast(Segment(start, fromX, fromY, vx, vy))
        endX = fromX + MotionCodec.speed(vx)
        endY = fromY + MotionCodec.speed(vy)
        endTick = start + 1
        resting = false
        prune(now)
        return glyphs()
    }

    /**
     * Whether it is time to send the pointer at rest: the schedule played out and the hold
     * has been showing a while. It must be replaced within [MotionCodec.TICK_WRAP] ticks or
     * the hold comes round again.
     */
    fun settled(clock: Double): Boolean = !resting && endTick?.let { clock >= it + REST_AFTER } == true

    /** Rest at the end of the schedule. */
    fun rest() {
        segments.clear()
        endTick = null
        resting = true
    }

    /** The glyphs of the schedule as it stands, for a packet that has to carry the pointer. */
    fun glyphs(): List<MotionGlyph> {
        val end = endTick ?: return emptyList()
        // The hold carries the last segment's tick and shows from the end of it: given a
        // tick of its own, a tick further on, it was far enough ahead to read as the past.
        return segments.map { MotionGlyph(it.x, it.y, it.start, it.vx, it.vy) } +
            MotionGlyph(Math.round(endX).toInt(), Math.round(endY).toInt(), end - 1, MotionCodec.HOLD, MotionCodec.HOLD)
    }

    /**
     * Only this tick's segment and the one before — for a client whose clock runs a little
     * behind — go out. Anything older is not merely useless: the shader's clock goes round
     * every four ticks, and a packet held on screen long enough brought it back into view.
     */
    private fun prune(now: Long) {
        while (segments.isNotEmpty() && segments.first().start < now - 1) segments.removeFirst()
    }

    companion object {
        /**
         * Ticks between a reading and the start of its segment, at the least: time for the
         * packet to land and for the client's clock to be a little off ours.
         */
        const val MARGIN = 0.3

        /**
         * Ticks the hold shows before the pointer is sent at rest — well inside [WRAP_AT],
         * past which the hold would read as a tick still to come and vanish.
         */
        const val REST_AFTER = 1.1

        /**
         * Where the shader turns an elapsed time read modulo [MotionCodec.TICK_WRAP] into a
         * tick still to come: a segment starts at most 1.3 ticks ahead, and a hold shows
         * until [REST_AFTER] past the end of its segment and a packet's way beyond.
         */
        const val WRAP_AT = 2.7
    }
}
