package ru.voidrp.ui.pack

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.logging.Logger

/**
 * Hands the pack to clients, so installing this plugin is installing this plugin.
 *
 * A resource pack has to be fetched over HTTP, which normally means finding somewhere to
 * put a zip and keeping it in step with the plugin. That is a lot to ask of someone who
 * only wanted to try an interface, so the plugin serves the file itself from a small
 * server of its own — one file, one route, nothing else answered.
 *
 * Anyone who would rather host it elsewhere sets `pack.url` and this never starts.
 */
class PackServer(
    private val file: File,
    private val port: Int,
    private val log: Logger,
) {

    companion object {
        const val PATH = "/voidrp-ui.zip"
    }

    private var server: HttpServer? = null

    fun start(): Boolean {
        return try {
            val http = HttpServer.create(InetSocketAddress(port), 0)
            http.createContext(PATH) { exchange ->
                val bytes = file.readBytes()
                exchange.responseHeaders.add("Content-Type", "application/zip")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            http.executor = null
            http.start()
            server = http
            log.info("Раздаю ресурспак на порту $port$PATH")
            true
        } catch (error: Exception) {
            log.warning(
                "Не удалось занять порт $port для раздачи пака (${error.message}). " +
                    "Укажите другой в pack.serve.port или выложите архив сами и пропишите pack.url."
            )
            false
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    /**
     * The address to send a particular player.
     *
     * Players reach a server by a name that is often not the one the machine knows itself
     * by, so the address they typed is the one to hand back — that way a pack link works
     * both for someone on the same network and someone across the internet, without
     * anything to configure.
     */
    fun urlFor(host: String): String = "http://$host:$port$PATH"
}
