package com.gestorfinances.desktop

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Started by Windows at sign-in: go straight to the tray. */
internal const val START_HIDDEN_ARGUMENT = "--tray"

// A trial profile (SUMMA_PROFILE) is another app as far as this goes: it runs beside the real one.
// A few ports, tried in order: another program may have taken the first.
private val INSTANCE_PORTS = (if (System.getenv("SUMMA_PROFILE").isNullOrEmpty()) 47822 else 47823).let { listOf(it, it + 2, it + 4) }

/** What the app already running answers with, so a port held by some other program is not mistaken for it. */
private val GREETING = "SUMMA".toByteArray()

/**
 * One app per user session: two would share the database and fight over the sync port. The first
 * holds a loopback port and gets a count of the times it was asked to show itself; a later one
 * asks it to, and gets null: it has nothing left to do. A port some other program holds is passed
 * over for the next; with all of them taken the app opens anyway, unguarded.
 */
internal fun singleInstance(ports: List<Int> = INSTANCE_PORTS): StateFlow<Int>? {
    val loopback = InetAddress.getLoopbackAddress()
    val requests = MutableStateFlow(0)
    for (port in ports) {
        val server = try {
            ServerSocket(port, 4, loopback)
        } catch (_: Exception) {
            if (isRunningAppAt(loopback, port)) return null
            continue
        }
        thread(isDaemon = true, name = "summa-single-instance") {
            while (true) {
                runCatching { server.accept().use { it.getOutputStream().write(GREETING) } }.onFailure { return@thread }
                requests.update { it + 1 }
            }
        }
        break
    }
    return requests
}

/** Asks whatever holds [port] to show itself; true when it answers as this app does. */
private fun isRunningAppAt(loopback: InetAddress, port: Int): Boolean =
    runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(loopback, port), 1000)
            socket.soTimeout = 1000
            socket.getInputStream().readNBytes(GREETING.size).contentEquals(GREETING)
        }
    }.getOrDefault(false)

/**
 * Whether Windows starts the app at sign-in, hidden in the tray. Kept where Windows keeps it, the
 * user's Run key, so what Settings shows is what will happen. Only the installed app can be
 * started this way.
 */
internal object StartWithWindows {
    private const val RUN_KEY = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    private const val VALUE = "Summa"

    /** The installed app's launcher, or null in a development run. */
    private val launcher: String? = System.getProperty("jpackage.app-path")

    val available: Boolean get() = launcher != null

    fun isOn(): Boolean = run("reg", "query", RUN_KEY, "/v", VALUE)

    fun set(on: Boolean): Boolean {
        val path = launcher ?: return false
        return if (on) {
            run("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"$path\" $START_HIDDEN_ARGUMENT", "/f")
        } else {
            run("reg", "delete", RUN_KEY, "/v", VALUE, "/f")
        }
    }

    private fun run(vararg command: String): Boolean =
        runCatching {
            ProcessBuilder(*command).redirectErrorStream(true).start().let { process ->
                process.inputStream.readBytes()
                process.waitFor() == 0
            }
        }.getOrDefault(false)
}
