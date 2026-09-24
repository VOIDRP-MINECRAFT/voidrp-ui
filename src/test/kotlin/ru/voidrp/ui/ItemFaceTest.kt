package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.voidrp.ui.pack.Icons

/**
 * Items whose picture is not named after them.
 *
 * Found in the shop on a live client: ancient debris came out as an empty square, because
 * there is no ancient_debris.png — only a side and a top. A hundred-odd blocks were the same,
 * furnaces and crafting tables among them. The face is read from the client's own models
 * rather than guessed from file names, which would have picked a glass pane's thin edge.
 */
class ItemFaceTest {

    @Test
    fun `blocks with no single texture have a picture`() {
        listOf("ancient_debris", "crafting_table", "furnace", "barrel", "tnt", "bee_nest", "glass_pane")
            .forEach { assertTrue(Icons.has(it), "$it is still an empty square") }
    }

    @Test
    fun `the face is the one the inventory shows`() {
        // The same glyph as the texture named outright means the lookup went to that face.
        assertEquals(Icons.glyph("block/crafting_table_front"), Icons.glyph("crafting_table"))
        assertEquals(Icons.glyph("block/furnace_front"), Icons.glyph("furnace"))
        assertEquals(Icons.glyph("block/ancient_debris_side"), Icons.glyph("ancient_debris"))
        // Not the edge a guess from the name would take.
        assertEquals(Icons.glyph("block/glass"), Icons.glyph("glass_pane"))
        // A model that carries its own namesake keeps it: a beacon is its core, not the
        // glass its particle falls back to.
        assertEquals(Icons.glyph("block/beacon"), Icons.glyph("beacon"))
    }

    @Test
    fun `what the client paints is painted here too`() {
        // Grey in the texture, green only because the client colours them.
        assertEquals(0x48B518, Icons.tint("oak_leaves"))
        assertEquals(0x80A755, Icons.tint("birch_leaves"))
        assertEquals(0x619961, Icons.tint("spruce_leaves"))
        assertNotNull(Icons.tint("vine").takeIf { it != 0xFFFFFF }, "vines stay grey")
    }

    @Test
    fun `and what it does not paint is left alone`() {
        // A grass block's side already has its green; only the top is tinted in the game.
        assertEquals(0xFFFFFF, Icons.tint("grass_block"))
        assertEquals(0xFFFFFF, Icons.tint("diamond"))
        assertEquals(0xFFFFFF, Icons.tint("minecraft:stone"))
    }

    @Test
    fun `names are found however they are written`() {
        assertEquals(Icons.glyph("furnace"), Icons.glyph("minecraft:furnace"))
        assertEquals(Icons.glyph("furnace"), Icons.glyph("FURNACE"))
    }
}
