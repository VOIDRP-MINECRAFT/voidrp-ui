package ru.voidrp.ui

import java.io.File
import ru.voidrp.ui.pack.PackBuilder

/**
 * Builds the pack and says what it weighs: `./gradlew packWeight`.
 *
 * Every player downloads this file once, so its size is a number worth being able to check
 * before and after a change, rather than guessing at it.
 */
object PackWeight {

    @JvmStatic
    fun main(args: Array<String>) {
        val target = File(args.firstOrNull() ?: "build/pack/voidrp-ui.zip")
        target.parentFile?.mkdirs()
        val hash = PackBuilder().build(target)
        println("${target.path}: ${target.length() / 1024} KB, sha1 $hash")
    }
}
