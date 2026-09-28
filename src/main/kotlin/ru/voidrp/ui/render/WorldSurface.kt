package ru.voidrp.ui.render

import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import ru.voidrp.ui.pack.Shaders

/**
 * A page drawn in the world rather than on the screen: an upright plane in front of the
 * player, facing them, seen by them alone.
 *
 * The point of it is the pointer. On the screen the pointer is whatever the server says,
 * twenty times a second at best; here the page stands still in the world and the player
 * turns the camera over it, so the pointer is simply the middle of the view — moved by the
 * client itself at the screen's own rate, with no delay and nothing sent.
 *
 * Three displays, back to front: a dark backdrop wider than the view, so the world does not
 * show round the page; the page itself; and what the pointer is over, a little in front.
 * The page is drawn by the same glyphs and the same shader as on the screen — the text
 * shader's world variant lays them on the display's plane instead of across the screen.
 */
class WorldSurface(
    private val plugin: Plugin,
    val player: Player,
    /** The canvas the page is laid out on: its width, and the middle x the line starts at. */
    private val canvasWidth: Int,
) {
    /** Blocks between the eyes and the page. */
    private val distance = DISTANCE

    /** The point the page line starts from, at its top: the displays' own position. */
    val origin: Location

    /** Which way the page faces (towards the player), and its rightward direction. */
    private val normal: Vector
    private val right: Vector

    private val page: TextDisplay
    private val hover: TextDisplay
    private val backdrop: List<BlockDisplay>

    init {
        val eye = player.eyeLocation
        val flat = Vector(eye.direction.x, 0.0, eye.direction.z).let {
            if (it.lengthSquared() < 1e-6) Vector(0.0, 0.0, 1.0) else it.normalize()
        }
        normal = flat.clone().multiply(-1)
        // The player's right, looking along flat: text runs this way across the page.
        right = Vector(-flat.z, 0.0, flat.x)
        origin = eye.clone().add(flat.clone().multiply(distance)).add(0.0, HALF_HEIGHT, 0.0)
        origin.yaw = eye.yaw + 180f
        origin.pitch = 0f

        backdrop = spawnBackdrop(eye, flat)
        page = spawnText(origin.clone())
        // A little nearer than anything on the page, which is itself drawn a hair nearer
        // glyph by glyph (see the shader) — so the highlight always lands on top.
        hover = spawnText(origin.clone().add(flat.clone().multiply(-HOVER_AHEAD)))
    }

    private fun spawnText(at: Location): TextDisplay {
        val display = at.world.spawn(at, TextDisplay::class.java) { d ->
            d.text(Component.empty())
            d.lineWidth = Int.MAX_VALUE
            d.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            d.isShadowed = false
            d.billboard = Display.Billboard.FIXED
            d.brightness = Display.Brightness(15, 15)
            val s = Shaders.WORLD_DISPLAY_SCALE
            d.transformation = Transformation(Vector3f(), AxisAngle4f(), Vector3f(s, s, s), AxisAngle4f())
            d.isPersistent = false
            d.isVisibleByDefault = false
            d.viewRange = 4f
        }
        player.showEntity(plugin, display)
        return display
    }

    /**
     * A dark room round the player: six thin walls, so wherever they turn there is no world
     * to see. Each wall is a flattened block whose face towards the player is the one drawn.
     */
    private fun spawnBackdrop(eye: Location, flat: Vector): List<BlockDisplay> {
        val r = ROOM
        val t = 0.02f
        // Offset of the wall's near corner from the eye, and its size, per wall.
        val walls = listOf(
            Vector3f(-r, -r, r) to Vector3f(2 * r, 2 * r, t),          // south
            Vector3f(-r, -r, -r - t) to Vector3f(2 * r, 2 * r, t),     // north
            Vector3f(r, -r, -r) to Vector3f(t, 2 * r, 2 * r),          // east
            Vector3f(-r - t, -r, -r) to Vector3f(t, 2 * r, 2 * r),     // west
            Vector3f(-r, r, -r) to Vector3f(2 * r, t, 2 * r),          // up
            // The floor just under the player's feet rather than a room's depth below them,
            // where the ground they stand on would cover it.
            Vector3f(-r, -FLOOR, -r) to Vector3f(2 * r, t, 2 * r),     // down
        )
        val at = eye.clone()
        at.yaw = 0f
        at.pitch = 0f
        return walls.map { (offset, size) ->
            val display = at.world.spawn(at, BlockDisplay::class.java) { d ->
                d.block = Material.BLACK_CONCRETE.createBlockData()
                d.brightness = Display.Brightness(3, 3)
                d.transformation = Transformation(offset, AxisAngle4f(), size, AxisAngle4f())
                d.isPersistent = false
                d.isVisibleByDefault = false
                d.viewRange = 4f
            }
            player.showEntity(plugin, display)
            display
        }
    }

    /** The latest of each, set on the server thread: entities may be touched nowhere else. */
    @Volatile private var pendingPage: Component? = null
    @Volatile private var pendingHover: Component? = null
    @Volatile private var removed = false

    fun page(text: Component) {
        pendingPage = text
        onMain { pendingPage?.let { pendingPage = null; if (!removed) page.text(it) } }
    }

    fun hover(text: Component) {
        pendingHover = text
        onMain { pendingHover?.let { pendingHover = null; if (!removed) hover.text(it) } }
    }

    fun remove() {
        removed = true
        onMain {
            runCatching { page.remove() }
            runCatching { hover.remove() }
            backdrop.forEach { runCatching { it.remove() } }
        }
    }

    private fun onMain(work: () -> Unit) {
        if (org.bukkit.Bukkit.isPrimaryThread()) work()
        else runCatching { org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { work() }) }
    }

    /**
     * Where on the canvas the player is looking, or null if not at the page's plane at all.
     *
     * The ray from the eyes along [direction] meets the page's plane; how far along the
     * page and down from its top that is, in canvas units, is the pointer.
     */
    fun aim(eye: Vector, direction: Vector): Pair<Double, Double>? {
        val o = origin.toVector()
        val facing = direction.dot(normal)
        if (facing >= -1e-6) return null   // looking away from the page
        val t = o.clone().subtract(eye).dot(normal) / facing
        if (t <= 0) return null
        val hit = eye.clone().add(direction.clone().multiply(t))
        val along = hit.clone().subtract(o).dot(right)
        val down = o.y - hit.y
        val unit = WORLD_UNIT
        return (canvasWidth / 2.0 + along / unit) to (down / unit + TOP_OFFSET)
    }

    companion object {
        /** Blocks between the eyes and the page. */
        const val DISTANCE = 2.0

        /** World units per canvas unit: a text pixel is 1/40 of a block, times the scale. */
        val WORLD_UNIT: Double = 0.025 * Shaders.WORLD_DISPLAY_SCALE

        /** Half the canvas's height in blocks: the display sits that far above the eyes. */
        val HALF_HEIGHT: Double = 512 * WORLD_UNIT

        /** Canvas units between the display's position and the top of what it draws. */
        const val TOP_OFFSET = 0.0

        /** How much nearer the highlight is than the page. */
        const val HOVER_AHEAD = 0.12

        /** Half the size of the dark room round the player, in blocks: the page is inside it. */
        const val ROOM = 2.6f

        /** Blocks from the eyes down to the room's floor: just above the ground under the feet. */
        const val FLOOR = 1.55f
    }
}
