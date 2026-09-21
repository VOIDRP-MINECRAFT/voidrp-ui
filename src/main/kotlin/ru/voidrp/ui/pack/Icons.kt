package ru.voidrp.ui.pack

/**
 * Item pictures, borrowed from the client's own textures.
 *
 * A shop, a quest list or an inventory screen is mostly pictures of items, and the client
 * already has every one of them. A bitmap glyph can point at any texture in the assets, so
 * the pack simply declares `minecraft:item/diamond.png` as a glyph and the client draws it
 * — the pack carries none of Mojang's artwork and grows by nothing but a list of names.
 *
 * The colour bits are still spent on the vertical position, so an icon is drawn white:
 * white leaves the texture exactly as it is, while everything else on the page is tinted.
 */
object Icons {

    /** The sizes an icon can be drawn at, in canvas units. Item textures are 16×16. */
    val SIZES = listOf(16, 32)

    private const val BASE = 0xF000

    /** Item texture names, as the client has them (`diamond`, `oak_planks`, …). */
    val NAMES: List<String> by lazy {
        Icons::class.java.getResourceAsStream("/icons/vanilla_items.txt")
            ?.bufferedReader()
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    private val index: Map<String, Int> by lazy { NAMES.withIndex().associate { (i, name) -> name to i } }

    fun fontName(size: Int): String = "icons_$size"

    fun nearestSize(size: Int): Int = SIZES.minByOrNull { Math.abs(it - size) } ?: SIZES.first()

    fun has(name: String): Boolean = index.containsKey(normalise(name))

    /** The glyph for an item, or null if the client has no picture of it. */
    fun glyph(name: String): String? =
        index[normalise(name)]?.let { String(Character.toChars(BASE + it)) }

    /** A square icon advances by its own width plus the pixel every bitmap glyph adds. */
    fun advance(size: Int): Int = nearestSize(size) + 1

    /** `minecraft:diamond`, `diamond` and `DIAMOND` all name the same picture. */
    private fun normalise(name: String): String =
        name.substringAfter(':').lowercase().trim()

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
        val providers = NAMES.mapIndexed { i, name ->
            """{"type": "bitmap", "file": "minecraft:item/$name.png", "ascent": 0,
                "height": $size, "chars": ["${Fonts.escapeJson(String(Character.toChars(BASE + i)))}"]}"""
        }
        return """{"providers": [{"type": "space", "advances": { $advances }},
            ${providers.joinToString(", ")}]}"""
    }
}
