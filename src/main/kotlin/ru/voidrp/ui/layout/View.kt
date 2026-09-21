package ru.voidrp.ui.layout

import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * What a page is written with.
 *
 * A page describes what it contains, not where each piece goes: a panel holds a row or a
 * column of things, with a gap between them and a rule for lining them up, and works out
 * its own size from what is inside it. That is the difference between porting a design
 * from the site and re-typing its coordinates — the same structure the stylesheet has
 * survives the trip.
 *
 * [Layout] turns a view into the shapes on the canvas.
 */
sealed interface View

/** How big something wants to be along one axis. */
sealed interface Size {
    /** As big as the content needs. */
    data object Auto : Size

    /** Everything the parent can spare. */
    data object Fill : Size

    /** Exactly this many canvas units. */
    data class Fixed(val value: Int) : Size
}

/** Which way a panel stacks its children. */
enum class Direction { ROW, COLUMN }

/** Where the children sit along the direction they stack in. */
enum class Justify { START, CENTER, END, SPACE_BETWEEN }

/** Where a child sits across that direction. */
enum class Align { START, CENTER, END, STRETCH }

/**
 * A styled container. Give it children and it lays them out; give it a size and it keeps
 * to it, otherwise it takes the size of what it holds plus its padding.
 */
data class Panel(
    val children: List<View> = emptyList(),
    val style: Style = Style(),
    val direction: Direction = Direction.COLUMN,
    val gap: Int = 0,
    val justify: Justify = Justify.START,
    val align: Align = Align.START,
    val width: Size = Size.Auto,
    val height: Size = Size.Auto,
) : View

/** A line of text. It measures itself, so a panel around it fits it exactly. */
data class Text(
    val value: String,
    val size: Int = Theme.TEXT_BODY,
    val colour: Int = Theme.INK,
    val weight: TextFonts.Weight = TextFonts.Weight.REGULAR,
) : View

/** Empty space, for when a gap is not enough — the flexible kind pushes things apart. */
data class Gap(val size: Int = 0, val grow: Boolean = false) : View

/** An escape hatch: something already positioned, for what the layout has no word for yet. */
data class Raw(val node: Node) : View
