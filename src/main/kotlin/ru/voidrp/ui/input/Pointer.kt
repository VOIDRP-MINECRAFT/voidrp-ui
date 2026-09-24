package ru.voidrp.ui.input

/**
 * Where the pointer is drawn between two readings of the player's aim.
 *
 * A client reports where it is looking twenty times a second at best, and that is the
 * ceiling on *knowing* where the pointer should be — not on drawing it. The whole question
 * is what to draw in the four frames out of five that have no reading of their own.
 *
 * **It walks the distance at one pace, rather than pouncing on it.** A reading says the hand
 * is 98 units further along than it was 55 ms ago — the median of a real one, recorded with
 * `/vui debug trace` while a player moved the mouse fast and slow. What took the hand 55 ms
 * is drawn over 55 ms, so every frame of that gap is the same size and nothing steps. The
 * filter this replaces — a tracker correcting a fraction of its error per reading — put
 * **45% of a gap's whole movement into its first frame**, where an even walk puts 20%. That
 * is a pointer twitching five times a second, which is what it looked like, and no amount of
 * smoothing on top helped: smoothing hid the twitch by adding lag, and the twitch came back
 * the moment the lag came out.
 *
 * **The lead is a pace, not an offset.** The answer has half a round trip to make before it
 * is on the screen, so the walk aims to arrive that much early — which is the same thing as
 * being thrown forward, with one difference that turned out to be the other half of the
 * problem. An offset added to a position has to appear and disappear: the old one grew when
 * a reading landed and collapsed when the walk ran out of distance, thirty-two units, twenty
 * times a second, on top of everything else. Folded into the pace, it has nothing to
 * collapse.
 *
 * The rest follows:
 *
 *  * **A stop is silence.** A client sends nothing while the hand is still, so nothing
 *    announces a stop — but nothing has to. The walk aims at the last reading and arrives,
 *    and an arrived walk stands still on the aim itself. The tracker before it was clamped
 *    into a window around the reading and left at the edge of it, twelve units to one side
 *    for as long as the hand stayed still.
 *  * **The gap is measured, not assumed.** A tick is what the protocol says; a live client
 *    sent readings 44 to 99 ms apart.
 *  * **It carries a movement on, a little.** Walked evenly, the pointer is a whole reading
 *    behind the hand, and on a normal ping the lead already spends all the room the walk
 *    was given — so the only way closer is to aim past the last reading, by a share of how
 *    far that reading moved ([prediction]). Only while readings keep coming: the first one
 *    after a silence has no pace to carry on, and once a reading is late the hand has
 *    stopped and the pointer walks back onto the aim over one gap rather than snapping.
 *    At the default it is 36 ms behind a moving hand instead of 65, and passes a sudden
 *    stop by about 14 units at an ordinary pace.
 *
 * Nothing here knows about a player or a canvas: it is arithmetic, and it is tested as such,
 * against six invented hands and one recorded one.
 */
class Pointer(
    x: Double,
    y: Double,
    now: Long = System.nanoTime(),
    /**
     * How many gaps the walk is given to cover a reading's distance.
     *
     * The one number worth turning. At 1.0 the pointer arrives exactly as the next reading
     * is due and every gap ends in a stub of a frame; higher gives the walk room to run at
     * one pace all the way through, so the frames come out the same size, and leaves the
     * pointer that much further behind the hand. Measured against a recording of a real
     * hand: at 1.0 the first frame of a gap takes all of its travel and the pointer sits 63
     * units from the hand, at 1.5 it takes a third and sits 90 units back, at 1.8 it takes
     * an even fifth and sits 108 back. 1.5 is where nothing is worse than the filter this
     * replaces and everything about the smoothness is better.
     */
    var smoothing: Double = SMOOTHING,
    /**
     * How far past the last reading the walk aims, as a share of how far that reading
     * moved. The dial between close to the hand and quiet at a stop: at 0 the pointer never
     * passes the aim and sits 65 ms behind, at 0.35 45 ms and 10 units past an ordinary
     * stop, at the default 0.5 36 ms and 14 units, at 0.75 21 ms and up to 56 on a fast
     * flick. Played side by side, 0.35 and 0.5 could not be told apart; 0.5 is closer.
     */
    var prediction: Double = PREDICTION,
) {

    /** What the last reading moved by, when it came straight after the one before. */
    private var stepX = 0.0
    private var stepY = 0.0

    /** The last reading of the aim. */
    var targetX = x
        private set
    var targetY = y
        private set

    /** How far along the walk to it the pointer has got. */
    var estimateX = x
        private set
    var estimateY = y
        private set

    /** And where it is drawn: the walk, thrown forward to cover the trip to the screen. */
    var x = x
        private set
    var y = y
        private set

    /** The pace of the walk, in units a second. */
    var speedX = 0.0
        private set
    var speedY = 0.0
        private set

    /**
     * The gap the readings are really arriving at.
     *
     * A tick is what the protocol says, not what a client does: on a live one they landed 44
     * to 99 ms apart, half of them at 55. Everything counted in gaps here — how long the walk
     * has to cover the distance, how long a reading stays fresh — is counted in this one.
     */
    var gap = SAMPLE_GAP
        private set

    private var sampleAt = now
    private var frameAt = now

    /** A fresh reading of the aim. */
    fun sample(newX: Double, newY: Double, now: Long = System.nanoTime()) {
        val raw = (now - sampleAt) / 1_000_000_000.0
        val interval = raw.coerceIn(0.01, 0.25)
        sampleAt = now
        gap += (interval.coerceIn(SAMPLE_GAP_MIN, SAMPLE_GAP_MAX) - gap) * GAP_EASING
        // A reading after a silence starts a movement: there is no pace to carry on yet.
        val running = raw <= gap * FRESH_GAPS
        stepX = if (running) newX - targetX else 0.0
        stepY = if (running) newY - targetY else 0.0
        targetX = newX
        targetY = newY
    }

    /**
     * Carries the pointer forward one frame.
     *
     * [roundTrip] is the player's ping in milliseconds, and [width] / [height] the canvas it
     * may not leave.
     */
    fun frame(now: Long = System.nanoTime(), roundTrip: Int = 0, width: Int, height: Int) {
        val step = ((now - frameAt) / 1_000_000_000.0).coerceIn(0.001, 0.1)
        frameAt = now
        val age = (now - sampleAt) / 1_000_000_000.0

        // How long is left to cover the distance — and the lead is *in* this, not added to
        // the position afterwards. Arriving half a round trip early is the same thing as
        // being thrown half a round trip forward, with one difference that turns out to be
        // the whole game: an offset added to a position has to appear and disappear. The
        // filter this replaces added one, and it collapsed at the end of every gap and came
        // back at the start of the next — thirty-two units, twenty times a second, on top of
        // whatever else the pointer was doing. In here it is simply a pace.
        // The lead may spend only the slack the walk was given, never the walk itself: on a
        // slow connection it would otherwise eat the whole budget and the pointer would be
        // back to covering a reading's distance in one frame — the very thing this is for.
        val ahead = Math.min(lead(roundTrip), gap * (smoothing - 1.0))
        // A reading overdue means the hand has stopped: the aim is the reading itself again,
        // and the pointer goes back to it over a gap rather than in one frame.
        val fresh = age <= gap * FRESH_GAPS
        val left = if (fresh) Math.max(gap * smoothing - age - ahead, step) else Math.max(gap, step)
        val aimX = targetX + if (fresh) stepX * prediction else 0.0
        val aimY = targetY + if (fresh) stepY * prediction else 0.0
        speedX = (aimX - estimateX) / left
        speedY = (aimY - estimateY) / left
        estimateX = (estimateX + speedX * step).coerceIn(0.0, (width - 1).toDouble())
        estimateY = (estimateY + speedY * step).coerceIn(0.0, (height - 1).toDouble())
        x = estimateX
        y = estimateY
    }

    /** Puts the pointer somewhere outright, readings and all — for a page that opens. */
    fun place(newX: Double, newY: Double) {
        targetX = newX; targetY = newY
        estimateX = newX; estimateY = newY
        x = newX; y = newY
        speedX = 0.0; speedY = 0.0
    }

    /**
     * How early the walk aims to arrive, in seconds.
     *
     * The answer still has half a round trip to make before it is on the screen, and our own
     * frame is half a frame away on average. The wait for the next reading is not in here:
     * the walk already covers that by being paced to the gap.
     */
    fun lead(roundTrip: Int): Double =
        ((roundTrip / 2.0 + FRAME_HALF_MS).coerceIn(0.0, LEAD_LIMIT_MS)) / 1000.0

    /**
     * How fresh the last reading is, from nothing at twice the gap to all of it within one.
     *
     * Nothing in the walk needs this — it aims at the last reading and stops there, so a
     * hand that has stopped is simply a walk that has finished. It is here for `/vui debug
     * cursor`, where it says whether the readings are still coming.
     */
    fun trust(now: Long = System.nanoTime()): Double {
        val age = (now - sampleAt) / 1_000_000_000.0
        return (1.0 - (age - gap) / gap).coerceIn(0.0, 1.0)
    }

    /** How long ago the last reading landed, in milliseconds. */
    fun age(now: Long = System.nanoTime()): Long = (now - sampleAt) / 1_000_000

    companion object {
        /** What the gap between readings is taken to be until one has been measured. */
        const val SAMPLE_GAP = 0.05

        /** And the range a measured one is believed within. */
        const val SAMPLE_GAP_MIN = 0.03
        const val SAMPLE_GAP_MAX = 0.15

        /** How much of a fresh gap goes into the average of them. */
        const val GAP_EASING = 0.25

        /** Half a frame of ours, which is the average wait for the next one. */
        const val FRAME_HALF_MS = 8.0

        /**
         * However bad the connection, the pointer is not thrown this far ahead in time.
         *
         * A pointer that bounces reads as broken where one that trails only reads as slow,
         * so past a certain ping the lead stops being worth what it costs.
         */
        const val LEAD_LIMIT_MS = 75.0

        /** How many gaps the walk is given by default. See the constructor. */
        const val SMOOTHING = 1.5

        /** And the range it is worth setting to: below one it steps, far above it floats. */
        const val SMOOTHING_MIN = 1.0
        const val SMOOTHING_MAX = 3.0

        /** How far past the last reading the walk aims by default. See the constructor. */
        const val PREDICTION = 0.5

        /** And the range it is worth setting to: past one it overshoots every stop. */
        const val PREDICTION_MIN = 0.0
        const val PREDICTION_MAX = 1.0

        /** A reading later than this many gaps means the hand has stopped. */
        const val FRESH_GAPS = 1.5
    }
}
