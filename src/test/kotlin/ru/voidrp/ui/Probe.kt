package ru.voidrp.ui

import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.page.HomePage
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.render.Rect

object Probe {
    @JvmStatic
    fun main(args: Array<String>) {
        val nodes = Painter.flatten(
            Layout.centred(HomePage().view(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT).nodes,
        )
        val byLevel = sortedMapOf<Int, Int>()
        nodes.filterIsInstance<Rect>().forEach { rect ->
            val level = Glyphs.alphaLevel(rect.paint.alpha)
            byLevel[level] = (byLevel[level] ?: 0) + 1
        }
        println("прямоугольники по ступеням прозрачности:")
        byLevel.forEach { (level, count) ->
            println("  ступень %2d (%.3f) — %d штук".format(level, level / 16.0, count))
        }
        val big = nodes.filterIsInstance<Rect>()
            .filter { it.width > 200 && it.height > 100 }
            .take(8)
        println("крупные заливки:")
        big.forEach {
            println("  %d×%d @ %d,%d  #%06x ступень %d".format(
                it.width, it.height, it.x, it.y, it.paint.rgb, Glyphs.alphaLevel(it.paint.alpha)))
        }
    }
}
