package ru.voidrp.ui.input

/**
 * Where the pointer is drawn between two readings of the player's aim.
 *
 * A client reports where it is looking twenty times a second at best, and that is the
 * ceiling on *knowing* where the pointer should be — not on drawing it. Holding the last
 * reading makes the pointer step; easing towards it makes the pointer lag. This is the
 * third answer, the filter a radar uses to draw a smooth track from a dish that sweeps:
 * carry on at the speed the readings have been showing, and when the next one lands,
 * correct both the position and the speed by a fraction of how wrong they turned out to be.
 *
 * Three things here are not in the textbook, and each of them answers something a recording
 * of a live pointer showed ([ru.voidrp.ui.page.PageSession.trace]):
 *
 *  * **A stop is silence.** A client sends nothing while the hand is still, so the last
 *    reading before a stop is a reading at full speed and the news that it has ended never
 *    arrives. So belief in the reckoning fades with the age of the last reading — full while
 *    it is younger than the gap they arrive at, gone by twice that — rather than falling off
 *    a threshold two gaps later, by which time the pointer had flown 45 units past.
 *  * **The lead is taken from a speed that falls at once and rises slowly.** A pointer
 *    thrown forward by the speed of a hand that has already stopped sails past the thing it
 *    was aimed at and walks back, which is the whole of what a bouncing pointer is.
 *  * **With the readings stopped, the reckoning comes home to the aim.** It used to be
 *    clamped into a window around the last reading and left at the edge of it, twelve units
 *    to one side for as long as the hand stayed still: a pointer that comes to rest beside
 *    the button it is pointed at.
 *
 * The numbers were found by simulation, not by feel: six hands — a steady sweep, a flick
 * that stops dead, a hand easing into its target, an arc, a slow nudge onto a small button,
 * and a hand merely resting on the mouse — each sampled the way a client really reports and
 * drawn at our frame rate, over four connections. Against the numbers before them the worst
 * overshoot is down 61%, the worst jolt between two frames down 39%, and a pointer that came
 * to rest twelve units from the aim now rests on it. Fast sweeps sit about 20% further
 * behind, which is the price and was worth paying.
 *
 * Nothing here knows about a player or a canvas: it is arithmetic, and it is tested as such.
 */
class Pointer(x: Double, y: Double, now: Long = System.nanoTime()) {

    /** The last reading of the aim. */
    var targetX = x
        private set
    var targetY = y
        private set

    /** Where the reckoning has the pointer between readings. */
    var estimateX = x
        private set
    var estimateY = y
        private set

    /** And where it is drawn: the reckoning, led forward and lightly smoothed. */
    var x = x
        private set
    var y = y
        private set

    /** The speed the readings have been showing, in units a second. */
    var speedX = 0.0
        private set
    var speedY = 0.0
        private set

    /**
     * The speed the lead is taken from, which is not quite the speed measured: a fall in it
     * is believed the moment a reading shows one, a rise has to be shown three times.
     */
    var leadX = 0.0
        private set
    var leadY = 0.0
        private set

    /**
     * The gap the readings are really arriving at.
     *
     * A tick is what the protocol says, not what a client does: measured on a live one they
     * landed between 66 and 110 ms apart. Everything counted in gaps here — how long a
     * reading stays fresh, how far the reckoning may run — is counted in this one.
     */
    var gap = SAMPLE_GAP
        private set

    private var sampleAt = now
    private var frameAt = now

    /** A fresh reading of the aim. */
    fun sample(newX: Double, newY: Double, now: Long = System.nanoTime()) {
        val interval = ((now - sampleAt) / 1_000_000_000.0).coerceIn(0.01, 0.25)
        sampleAt = now
        gap += (interval.coerceIn(SAMPLE_GAP_MIN, SAMPLE_GAP_MAX) - gap) * GAP_EASING
        targetX = newX
        targetY = newY
        val offX = targetX - estimateX
        val offY = targetY - estimateY
        // A hand that turns round is not a hand that was going faster: when the reading
        // lands on the other side of where we were heading, the speed we believed in was
        // wrong rather than short, and carrying it on is what sails a pointer past things.
        if (offX * speedX < 0) speedX = 0.0
        if (offY * speedY < 0) speedY = 0.0
        estimateX += offX * CATCH_UP
        estimateY += offY * CATCH_UP
        speedX += offX * SPEED_CATCH_UP / interval
        speedY += offY * SPEED_CATCH_UP / interval
        leadX = follow(leadX, speedX)
        leadY = follow(leadY, speedY)
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
        val trust = trust(now)

        // Carry on at the speed we think the hand is going, for as long as that is worth
        // believing.
        estimateX += speedX * step * trust
        estimateY += speedY * step * trust

        if (trust <= 0.0) {
            // Silence for two gaps: the hand has stopped. The speed dies away and the
            // reckoning comes home to where the player is actually looking.
            speedX *= SPEED_DECAY
            speedY *= SPEED_DECAY
            leadX *= SPEED_DECAY
            leadY *= SPEED_DECAY
            estimateX += (targetX - estimateX) * REST_CATCH_UP
            estimateY += (targetY - estimateY) * REST_CATCH_UP
        }

        // How far forward the pointer is thrown, and the ceiling on it.
        //
        // One reading showing a huge step is ambiguous: it is either a hand moving very
        // fast or a single jump, and thrown forward by the speed it implies the pointer
        // leaves the screen. Measured against a rig whose mouse teleports — 450 units
        // between two readings — a ceiling here is the only thing that helps, because the
        // speed it computes is real arithmetic on a real reading and there is nothing wrong
        // with it to correct. Thirty-two units is about three per cent of the screen's
        // height: enough for the lead a steady hand earns, too little to matter when it is
        // wrong.
        val ahead = lead(roundTrip) * trust
        val throwX = (leadX * ahead).coerceIn(-LEAD_MAX_UNITS, LEAD_MAX_UNITS)
        val throwY = (leadY * ahead).coerceIn(-LEAD_MAX_UNITS, LEAD_MAX_UNITS)

        // Never further ahead of the last reading than the hand could have gone since it:
        // what the speed covers in the gap the readings arrive at, faded out with the belief
        // in that speed — so at rest the pointer is on the aim itself, not beside it.
        val windowX = (Math.abs(throwX) + Math.abs(leadX) * gap * trust) * trust
        val windowY = (Math.abs(throwY) + Math.abs(leadY) * gap * trust) * trust
        estimateX = estimateX.coerceIn(targetX - windowX, targetX + windowX)
        estimateY = estimateY.coerceIn(targetY - windowY, targetY + windowY)
        estimateX = estimateX.coerceIn(0.0, (width - 1).toDouble())
        estimateY = estimateY.coerceIn(0.0, (height - 1).toDouble())

        // Where it has to be drawn to arrive under the player's hand rather than behind it.
        val wantX = (estimateX + throwX).coerceIn(0.0, (width - 1).toDouble())
        val wantY = (estimateY + throwY).coerceIn(0.0, (height - 1).toDouble())

        // A light smoothing over the top, which takes out the jitter in the reckoning
        // without adding any of the lag that hiding the steps used to cost.
        x += (wantX - x) * EASING
        y += (wantY - y) * EASING
        if (Math.abs(wantX - x) < 0.5) x = wantX
        if (Math.abs(wantY - y) < 0.5) y = wantY
    }

    /** Puts the pointer somewhere outright, readings and all — for a page that opens. */
    fun place(newX: Double, newY: Double) {
        targetX = newX; targetY = newY
        estimateX = newX; estimateY = newY
        x = newX; y = newY
        speedX = 0.0; speedY = 0.0
        leadX = 0.0; leadY = 0.0
    }

    /**
     * How far ahead of the last reading the pointer is drawn, in seconds.
     *
     * Everything in the chain costs time: the client reports its aim in gaps (half of one on
     * average before a turn is even sent), the packet takes half a round trip to arrive, our
     * frame takes up to half a frame to go out, and the answer takes the other half of the
     * round trip to be drawn. Drawn where the player *was* looking, a pointer lags by all of
     * it at once. So it is drawn where they will be looking by the time it lands — for a
     * hand moving steadily, which is most of the way to anything, the lag cancels out.
     */
    fun lead(roundTrip: Int): Double =
        ((roundTrip / 2.0 + gap * 1000.0 / 2.0 + FRAME_HALF_MS).coerceIn(0.0, LEAD_LIMIT_MS)) / 1000.0

    /**
     * How much of the reckoning is still worth believing, from the age of the last reading.
     *
     * A reading no older than the gap they arrive at is fresh and carried forward in full.
     * Past that the hand has most likely stopped, and by twice the gap there is nothing left
     * to carry. Fading over that second gap is what a threshold could not do: the old one
     * waited 120 ms, longer than two gaps of a live client, so a stop was noticed a tenth of
     * a second after it happened.
     */
    fun trust(now: Long = System.nanoTime()): Double {
        val age = (now - sampleAt) / 1_000_000_000.0
        return (1.0 - (age - gap) / gap).coerceIn(0.0, 1.0)
    }

    /** How long ago the last reading landed, in milliseconds. */
    fun age(now: Long = System.nanoTime()): Long = (now - sampleAt) / 1_000_000

    /** A fall in speed is news at once; a rise has to be shown three readings running. */
    private fun follow(lead: Double, speed: Double): Double =
        if (Math.abs(speed) < Math.abs(lead) || speed * lead < 0) speed
        else lead + (speed - lead) * LEAD_RISE

    companion object {
        /** How much of the way to where it should be the pointer moves each frame. */
        const val EASING = 0.60

        /** How much of the gap a fresh reading closes at once. Gentler is smoother. */
        const val CATCH_UP = 0.55

        /**
         * And how much of it is taken as news about the speed.
         *
         * These two are not free of each other. Correct the speed harder than the position
         * can settle and the tracker rings: every reading tells it that it overshot, so it
         * turns around, overshoots the other way, and the pointer flies about. The bound is
         * the critically damped one — the speed term is the square of the position term over
         * two minus it — and this sits on it.
         */
        const val SPEED_CATCH_UP = CATCH_UP * CATCH_UP / (2 - CATCH_UP)

        /** What the gap between readings is taken to be until one has been measured. */
        const val SAMPLE_GAP = 0.05

        /** And the range a measured one is believed within. */
        const val SAMPLE_GAP_MIN = 0.03
        const val SAMPLE_GAP_MAX = 0.15

        /** How much of a fresh gap goes into the average of them. */
        const val GAP_EASING = 0.25

        /**
         * How quickly the speed the lead is taken from climbs to the speed measured: three
         * readings agreeing before a hand is thrown forward at its full speed. Falls are not
         * eased at all.
         */
        const val LEAD_RISE = 0.35

        /** How much of the way home the reckoning comes each frame once the hand is still. */
        const val REST_CATCH_UP = 0.30

        /** Half a frame of ours, which is the average wait for the next one. */
        const val FRAME_HALF_MS = 8.0

        /**
         * However bad the connection, the pointer is not thrown this far ahead.
         *
         * Below about 80 ms of ping the chain does not add up to this and the cap is inert:
         * it decides nothing for a player on the same continent. It is here for the far-away
         * one, where carrying the pointer forward by the whole of a long round trip buys back
         * some of the lag and pays for it in overshoot on every stop. Measured on a flick at
         * 150 ms of ping, the trade runs 44 units of overshoot at a 50 ms cap, 65 at 75 ms
         * and 86 at 100 ms, against 166 with no useful cap at all; the lag it buys back runs
         * the other way, 64, 54 and 50 units behind a steady sweep. This sits in the middle,
         * because a pointer that bounces reads as broken and one that trails reads as slow.
         */
        const val LEAD_LIMIT_MS = 75.0

        /**
         * The ceiling on how far ahead of the last reading the pointer may be thrown.
         *
         * The cap in milliseconds above answers a slow connection; this one answers a fast
         * hand, where the lead is large because the speed really is. Against a mouse that
         * teleports it takes the overshoot from 49 units to 35 and costs one unit of lag.
         */
        const val LEAD_MAX_UNITS = 32.0

        /** How quickly the speed dies away once the hand has stopped. */
        const val SPEED_DECAY = 0.85
    }
}
