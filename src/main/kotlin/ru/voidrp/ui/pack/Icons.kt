package ru.voidrp.ui.pack

/**
 * Pictures of items and blocks, borrowed from the client's own textures.
 *
 * A shop, a quest list or an inventory screen is mostly pictures of items, and the client
 * already has every one of them. A bitmap glyph can point at any texture in the assets, so
 * the pack simply declares `minecraft:item/diamond.png` as a glyph and the client draws it
 * — the pack carries none of Mojang's artwork and grows by nothing but a list of names.
 *
 * The colour bits are still spent on the vertical position, so an icon is drawn white:
 * white leaves the texture exactly as it is, while everything else on the page is tinted.
 *
 * Blocks are included because a shop is mostly blocks, and a shop that cannot show stone
 * is not a shop. They are drawn flat — one face, not the little cube the inventory draws —
 * since a glyph is a picture and nothing here can turn a model into one. Which face is not
 * guessed from file names: ancient debris has no ancient_debris.png at all, only a side and
 * a top, and a glass pane's picture is the glass rather than the thin edge its own texture
 * shows. [faces] says, for every item where the name does not lead to the right picture,
 * the texture the client's own model puts on the face you see — and, for leaves, vines and
 * grass, the colour the client paints them, since in the texture they are grey.
 */
object Icons {

    /** The sizes an icon can be drawn at, in canvas units. Item textures are 16×16. */
    val SIZES = listOf(16, 32)

    private const val BASE = 0xF000

    /**
     * Item texture names and how wide the picture in each one actually is.
     *
     * The width matters because the client advances a glyph by the width of its ink, and
     * an item texture is 16 pixels of canvas with the drawing somewhere inside it — a
     * diamond is fourteen wide, a door thirteen. Assuming all sixteen made every page with
     * icons on it drift sideways. The numbers are measured once from the client's own
     * textures; only the numbers travel with us.
     *
     * Textures that are not 16×16, and animated ones, are left out: a glyph would draw
     * every frame of an animation stacked on top of each other.
     */
    private val table: List<Pair<String, Int>> by lazy {
        Icons::class.java.getResourceAsStream("/icons/vanilla_items.txt")
            ?.bufferedReader()
            ?.readLines()
            ?.mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 16) else null
            }
            ?: emptyList()
    }

    val NAMES: List<String> get() = table.map { it.first }

    /**
     * Items whose picture is not the texture named after them, and the tint the client
     * gives them. Worked out from the client's models by `tools/item-faces.py`: only names
     * and numbers, nothing of the artwork.
     */
    private val faces: Map<String, Pair<String, Int?>> by lazy {
        Icons::class.java.getResourceAsStream("/icons/item_faces.txt")
            ?.bufferedReader()
            ?.readLines()
            ?.mapNotNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                val parts = line.trim().split(' ')
                if (parts.size < 2) return@mapNotNull null
                val tint = parts.getOrNull(2)?.removePrefix("#")?.toIntOrNull(16)
                parts[0] to (parts[1] to tint)
            }
            ?.toMap()
            ?: emptyMap()
    }

    /**
     * The colour to draw an item's picture in: white for nearly everything, which leaves
     * the texture as it is, and the client's own paint for what the client paints.
     */
    fun tint(name: String): Int = faces[plain(name)]?.second ?: 0xFFFFFF

    private val index: Map<String, Int> by lazy {
        table.withIndex().associate { (i, entry) -> entry.first to i }
    }

    fun fontName(size: Int): String = "icons_$size"

    fun nearestSize(size: Int): Int = SIZES.minByOrNull { Math.abs(it - size) } ?: SIZES.first()

    fun has(name: String): Boolean = lookup(name) != null

    /** The glyph for an item, or null if the client has no picture of it. */
    fun glyph(name: String): String? = lookup(name)?.let { String(Character.toChars(BASE + it)) }

    /**
     * How far the pen moves past an icon: the ink of that picture, scaled to the size it
     * is drawn at, plus the pixel every bitmap glyph adds.
     */
    fun advance(name: String, size: Int): Int {
        val drawn = nearestSize(size)
        val ink = lookup(name)?.let { table[it].second } ?: 16
        return Math.round(ink.toDouble() * drawn / 16).toInt() + 1
    }

    /**
     * `minecraft:diamond`, `diamond` and `DIAMOND` all name the same picture, and a plain
     * name finds the item before the block — which is what anyone naming `stone` means.
     */
    private fun lookup(name: String): Int? {
        val plain = plain(name)
        if (plain.startsWith("item/") || plain.startsWith("block/")) return index[plain]
        faces[plain]?.let { (texture, _) -> index[texture]?.let { return it } }
        return index["item/$plain"] ?: index["block/$plain"]
    }

    private fun plain(name: String): String = name.substringAfter(':').lowercase().trim()

    /**
     * One font per size, each naming every item texture the client already has.
     *
     * The spacers come along for the ride: the step to where an icon goes is written in
     * the same run as the icon, so both have to live in the same font.
     */
    fun fontJson(size: Int): String {
        val advances = Glyphs.spacers().entries.joinToString(", ") { (char, advance) ->
            "\"${Fonts.escapeJson(char)}\": $advance"
        }
        val providers = table.mapIndexed { i, (name, _) ->
            """{"type": "bitmap", "file": "minecraft:$name.png", "ascent": 0,
                "height": $size, "chars": ["${Fonts.escapeJson(String(Character.toChars(BASE + i)))}"]}"""
        }
        return Fonts.compact(
            """{"providers": [{"type": "space", "advances": { $advances }},
            ${providers.joinToString(", ")}]}"""
        )
    }
}
