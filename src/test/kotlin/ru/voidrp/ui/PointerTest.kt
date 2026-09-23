package ru.voidrp.ui

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue
import ru.voidrp.ui.input.Pointer

/**
 * The pointer, put through the hands a player has.
 *
 * Every case here is something a recording of a live pointer showed, and the numbers they
 * assert are the ones the recording said were wrong. A client sends its aim only when the
 * aim has changed, so a still hand is silence rather than a stream of identical readings —
 * that is the detail the first simulation of this got wrong, and it is the one that hid the
 * worst of the defects: a pointer that came to rest twelve units from the button it was
 * pointed at and stayed there.
 */
class PointerTest {

    private val width = 1820
    private val height = 1024
    private val frame = 1_000_000_000L / 85

    /** Runs a hand past the pointer and reports every frame of it. */
    private fun run(
        seconds: Double,
        ping: Int = 40,
        gap: Long = 50_000_000L,
        aim: (Double) -> Double,
    ): List<Triple<Double, Double, Double>> {
        var now = 0L
        val pointer = Pointer(aim(0.0), 512.0, now)
        var nextSample = 0L
        var sent: Double? = null
        val frames = mutableListOf<Triple<Double, Double, Double>>()
        while (now < (seconds * 1_000_000_000L).toLong()) {
            if (now >= nextSample) {
                val at = aim(now / 1_000_000_000.0)
                // Silence while the hand is still: a client sends nothing it has not moved.
                if (sent == null || abs(at - sent!!) > 1e-9) {
                    pointer.sample(at, 512.0, now)
                    sent = at
                }
                nextSample = now + gap
            }
            pointer.frame(now, ping, width, height)
            // Where the hand is by the time the answer is on screen, half a trip later.
            val seen = aim(now / 1_000_000_000.0 + ping / 2000.0)
            frames.add(Triple(now / 1_000_000_000.0, seen, pointer.x))
            now += frame
        }
        return frames
    }

    @Test
    fun `comes to rest on the aim and not beside it`() {
        // A sweep that stops dead: the readings stop with it, and nothing ever tells the
        // pointer the hand has finished. It used to be clamped into a window around the last
        // reading and left at the edge of it — twelve units off, for ever.
        val frames = run(1.5) { t -> 400 + 900 * minOf(t, 0.6) }
        val settled = frames.filter { it.first > 1.1 }
        assertTrue(settled.isNotEmpty())
        settled.forEach { (at, hand, drawn) ->
            assertTrue(
                abs(hand - drawn) < 1.0,
                "at ${at}s the hand is at $hand and the pointer is drawn at $drawn",
            )
        }
    }

    @Test
    fun `does not sail far past a hand that stops`() {
        // The recording had it 45 units past the aim, and 110 ms before it even started
        // back, because a stop was noticed by a threshold two gaps later.
        listOf(40, 150, 250).forEach { ping ->
            val frames = run(1.5, ping = ping) { t -> 400 + 1600 * minOf(t, 0.25) }
            val target = 400 + 1600 * 0.25
            val worst = frames.filter { it.first >= 0.25 }.maxOf { it.third - target }
            // A far-away player keeps more of the lead and pays for it here; the ceiling on
            // the throw is what holds it. See Pointer.LEAD_MAX_UNITS.
            val allowed = if (ping <= 80) 50.0 else 70.0
            assertTrue(worst < allowed, "on ${ping}ms of ping it went $worst units past")
        }
    }

    @Test
    fun `comes back from an overshoot without a lurch`() {
        // Coming home is the other half of it: the recording had the pointer walk back from
        // 45 units to 12 over three hundred milliseconds, which is what a bouncing pointer
        // looks like. Measured as the worst step between two frames after the hand has
        // stopped, the tracker this replaces ran 60 units at the stop itself, 18 fifty
        // milliseconds later and was still moving a unit a frame after a tenth of a second.
        val frames = run(1.5) { t -> 400 + 1600 * minOf(t, 0.25) }
        fun worstStepFrom(delay: Double) = (1 until frames.size)
            .filter { frames[it].first >= 0.25 + delay }
            .maxOf { abs(frames[it].third - frames[it - 1].third) }
        // The frame of the stop itself still carries the hand's own 19 units of travel.
        assertTrue(worstStepFrom(0.0) < 35.0, "at the stop it moved ${worstStepFrom(0.0)} in a frame")
        assertTrue(worstStepFrom(0.05) < 14.0, "50ms later: ${worstStepFrom(0.05)} in a frame")
        // By a tenth of a second there is nothing left but a gentle settle onto the aim —
        // a unit or so a frame, which is a pixel. Both trackers are quiet by here; this
        // holds it to arriving rather than snapping the last of the distance.
        assertTrue(worstStepFrom(0.10) < 3.0, "100ms later: ${worstStepFrom(0.10)} in a frame")
    }

    @Test
    fun `follows a slow nudge onto a small button`() {
        // The case that matters most and is easiest to forget: a hand creeping the last few
        // units onto something the size of a button. Anything thrown forward here is a
        // pointer that will not sit still on a small target.
        val frames = run(1.4) { t -> 400 + 60 * minOf(t, 0.5) }
        val moving = frames.filter { it.first < 0.5 }
        assertTrue(moving.map { abs(it.second - it.third) }.average() < 8.0)
        val worst = frames.filter { it.first >= 0.5 }.maxOf { it.third - 460.0 }
        assertTrue(worst < 8.0, "it went $worst units past a 60-unit nudge")
    }

    @Test
    fun `a hand resting on the mouse does not make the pointer wander`() {
        // Two units of jitter, twenty times a second. A tracker that takes each of those for
        // news about the speed rings on it.
        val frames = run(1.0) { t -> 900 + if ((t * 40).toInt() % 2 == 1) 2.0 else 0.0 }
        frames.forEach { (at, hand, drawn) ->
            assertTrue(abs(hand - drawn) < 6.0, "at ${at}s: hand $hand, pointer $drawn")
        }
    }

    @Test
    fun `keeps up with a hand that keeps moving`() {
        // The lead earns its place here, and the whole trade is in this number: the pointer
        // is drawn where the hand will be by the time the answer lands, so a steady sweep
        // should sit close to it rather than a round trip behind.
        val frames = run(1.5) { t -> 400 + 900 * minOf(t, 0.6) }
        val moving = frames.filter { it.first in 0.15..0.55 }
        val lag = moving.map { abs(it.second - it.third) }.average()
        // A gap and a half of the hand's travel, less the lead — that is the walk's whole
        // trade, and at 900 units a second it comes to about sixty. Lower `smoothing` and
        // this number falls while the pointer starts stepping again; the recorded-hand tests
        // are the other side of the same dial.
        assertTrue(lag < 70.0, "a steady sweep sat $lag units behind the hand")
    }

    @Test
    fun `measures the gap the readings really arrive at`() {
        // A tick is what the protocol says; a live client sent them 66 to 110 ms apart, and
        // everything counted in gaps has to be counted in the real one.
        val pointer = Pointer(900.0, 512.0, 0L)
        var now = 0L
        repeat(12) {
            now += 90_000_000L
            pointer.sample(900.0 + it * 30, 512.0, now)
        }
        assertTrue(abs(pointer.gap - 0.09) < 0.01, "measured ${pointer.gap}s")
        // And a reading a gap and a half old is already half doubted.
        assertTrue(pointer.trust(now + 135_000_000L) in 0.3..0.7)
        assertTrue(pointer.trust(now + 200_000_000L) == 0.0)
    }

    @Test
    fun `stays on the canvas`() {
        val frames = run(1.5) { t -> 400 + 4000 * t }
        frames.forEach { (_, _, drawn) ->
            assertTrue(drawn in 0.0..(width - 1).toDouble(), "drawn at $drawn")
        }
    }

    @Test
    fun `walks a gap evenly instead of pouncing on it`() {
        // The defect this filter was written for. A reading says the hand is somewhere new;
        // what matters is not only arriving there but how the five frames of that gap divide
        // the distance. Evenly is 20% in the first one. The tracker this replaces put 44% of
        // it there, five times a second, and that is what a player sees as the pointer
        // twitching rather than moving.
        val readings = recordedHand()
        val frames = replay(readings)
        val shares = gapShares(frames)
        val median = shares.sorted()[shares.size / 2]
        assertTrue(median < 0.28, "the first frame of a gap took ${median * 100}% of its travel")
    }

    @Test
    fun `follows a recorded hand more closely than it steps`() {
        // The same recording, measured for accuracy rather than smoothness: against the line
        // the hand actually walked between two readings, and the worst jump between two
        // frames. The tracker this replaces sat 88 units from that line and jumped 308 in a
        // single frame.
        val readings = recordedHand()
        val frames = replay(readings)
        val error = frames.map { (at, px, py) ->
            val (hx, hy) = handAt(readings, at)
            hypot(px - hx, py - hy)
        }.average()
        // The tracker this replaces sat 88 units from that line while jumping 308 units in
        // a single frame. This one is further back and never lurches; `smoothing` is the
        // dial between those, and 1.5 is where it ships.
        assertTrue(error < 115.0, "it sat $error units from the hand")
        val worst = (1 until frames.size).maxOf { i ->
            hypot(frames[i].second - frames[i - 1].second, frames[i].third - frames[i - 1].third)
        }
        assertTrue(worst < 160.0, "it jumped $worst units in one frame")
    }

    // ── the recorded hand ────────────────────────────────────────────────────────────────

    /** Every reading of the aim a real player's ten seconds of mouse gave the server. */
    private fun recordedHand(): List<Triple<Long, Double, Double>> =
        javaClass.getResourceAsStream("/hand.csv")!!.bufferedReader().readLines()
            .filterNot { it.startsWith("#") || it.startsWith("ms") || it.isBlank() }
            .map { line ->
                val (ms, x, y) = line.split(",")
                Triple(ms.toLong(), x.toDouble(), y.toDouble())
            }

    private fun replay(
        readings: List<Triple<Long, Double, Double>>,
        ping: Int = 40,
    ): List<Triple<Double, Double, Double>> {
        val pointer = Pointer(readings[0].second, readings[0].third, 0L)
        val out = mutableListOf<Triple<Double, Double, Double>>()
        var now = 0L
        var next = 0
        val end = readings.last().first * 1_000_000L
        while (now < end) {
            while (next < readings.size && readings[next].first * 1_000_000L <= now) {
                pointer.sample(readings[next].second, readings[next].third, now)
                next++
            }
            pointer.frame(now, ping, width, height)
            out.add(Triple(now / 1_000_000_000.0, pointer.x, pointer.y))
            now += frame
        }
        return out
    }

    /**
     * Where the hand really was between two readings.
     *
     * Its path is unknown — that is the whole problem — but over 55 ms a hand goes in a
     * straight line, and it is the same stand-in for every filter measured against it.
     */
    private fun handAt(readings: List<Triple<Long, Double, Double>>, at: Double): Pair<Double, Double> {
        val ms = at * 1000.0
        val after = readings.indexOfFirst { it.first > ms }
        if (after <= 0) return readings.first().let { it.second to it.third }
        val before = readings[after - 1]
        val next = readings[after]
        val span = (next.first - before.first).toDouble()
        val f = if (span > 0) (ms - before.first) / span else 0.0
        return (before.second + (next.second - before.second) * f) to
            (before.third + (next.third - before.third) * f)
    }

    /** What share of each gap's travel its first frame took. */
    private fun gapShares(frames: List<Triple<Double, Double, Double>>): List<Double> {
        val readings = recordedHand()
        val marks = readings.map { r -> frames.indexOfFirst { it.first * 1000.0 >= r.first } }
            .filter { it > 0 }.distinct()
        val steps = (1 until frames.size).map { i ->
            hypot(frames[i].second - frames[i - 1].second, frames[i].third - frames[i - 1].third)
        }
        return marks.zipWithNext().mapNotNull { (a, b) ->
            val seg = steps.subList(a - 1, (b - 1).coerceAtLeast(a))
            if (seg.size < 3 || seg.sum() < 5.0) null else seg.first() / seg.sum()
        }
    }

    @Test
    fun `a slow connection does not turn the walk back into a jump`() {
        // The lead is time taken out of the walk, so on a long round trip it could take all
        // of it — and a walk with no time left covers a reading's distance in one frame,
        // which is the defect this filter exists to remove. It may only spend the slack.
        listOf(40, 150, 400).forEach { ping ->
            val readings = recordedHand()
            val frames = replay(readings, ping)
            val shares = gapShares(frames).sorted()
            val median = shares[shares.size / 2]
            assertTrue(median < 0.30, "on ${ping}ms of ping the first frame took ${median * 100}%")
        }
    }
}
