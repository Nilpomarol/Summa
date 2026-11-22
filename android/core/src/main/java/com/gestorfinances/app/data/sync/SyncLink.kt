package com.gestorfinances.app.data.sync

import java.util.concurrent.atomic.AtomicInteger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/*
 * The link between the owner's two devices on the same network. The computer listens while its
 * app is open; the phone finds it, takes its data, merges, and hands back the result. Nothing
 * leaves the local network, and everything on the wire is encrypted with a key both devices
 * derive from the pairing code the owner typed once.
 */

const val SYNC_PORT = 47821

private const val DISCOVERY_QUESTION = "SUMMA-SYNC?"
private const val DISCOVERY_ANSWER = "SUMMA-SYNC!"
private const val TIMEOUT_MILLIS = 15_000
// Many times a database of years, and what a replayed hello could make the computer wait for.
private const val MAX_FRAME = 64 * 1024 * 1024

/** A connection's first message is a hello or a watch: a few bytes. Nothing larger is read from a stranger. */
private const val MAX_FIRST_FRAME = 1024

/** A phone holds a watch and, at times, a round; beyond a few connections it is not the owner's phones. */
private const val MAX_CONNECTIONS = 8

private const val HELLO = 1
private const val SNAPSHOT = 2
private const val PUSH = 3
private const val OK = 4
private const val ERROR = 5
private const val WATCH = 6
private const val CHANGED = 7

/** How long the computer holds a watch before answering "nothing yet"; the phone then asks again. */
private const val WATCH_MILLIS = 50_000L

/** How long after a watch ends the phone still counts as there, while it asks again. */
private const val PRESENCE_GRACE_MILLIS = 5_000L

private val random = SecureRandom()

/** A pairing code: twelve characters with no look-alikes, shown as `XXXX-XXXX-XXXX`. */
fun newPairingCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..12).map { alphabet[random.nextInt(alphabet.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
}

/** The key both devices derive from the pairing code, however the owner typed it. */
class SyncKey(code: String) {
    internal val secret: SecretKeySpec = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(
            PBEKeySpec(code.uppercase().filter { it.isLetterOrDigit() }.toCharArray(), "summa-sync-v1".toByteArray(), 120_000, 256),
        ).encoded,
        "AES",
    )
}

class SyncLinkException(
    val reason: Reason,
    cause: Throwable? = null,
    /** For [Reason.RULES]: the record that does not fit, as the owner knows it. */
    val detail: String? = null,
) : RuntimeException(reason.name, cause) {
    enum class Reason {
        /** No computer answered on this network. */
        NOT_FOUND,

        /** It answered, but not with this pairing code. */
        WRONG_CODE,

        /** The two apps are different versions and their data has a different shape. */
        SCHEMA,

        /** The other side could not complete its part. */
        FAILED,

        /** What the two devices changed does not fit together under a shared account's rules. */
        RULES,
    }
}

/** The failure the other side sent in an error message. */
private fun linkError(body: DataInputStream): SyncLinkException {
    val reason = SyncLinkException.Reason.valueOf(body.readUTF())
    return SyncLinkException(reason, detail = if (reason == SyncLinkException.Reason.RULES) body.readUTF() else null)
}

/** What the link needs from a device's database. */
interface SyncDevice {
    val schemaVersion: String
    val tempDir: File

    /** Writes a consistent copy of the database to [into]. */
    fun snapshot(into: File)

    fun merge(theirs: File, base: File?, choice: ConflictChoice?): MergeResult

    /** A number that is different after the database has been written to, by anything. */
    fun changeCounter(): Long
}

/** Frames of `length | iv | AES-GCM ciphertext` over one socket. */
private class Channel(private val socket: Socket, private val key: SyncKey) {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream().buffered())

    fun send(type: Int, write: DataOutputStream.() -> Unit = {}) {
        val plain = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeByte(type); write() }.flush() }.toByteArray()
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key.secret, GCMParameterSpec(128, iv)) }
        val sealed = cipher.doFinal(plain)
        output.writeInt(sealed.size)
        output.write(iv)
        output.write(sealed)
        output.flush()
    }

    /** The next message's type and body. A frame this key cannot open is a wrong pairing code. */
    fun receive(maxSize: Int = MAX_FRAME): Pair<Int, DataInputStream> {
        val size = input.readInt()
        if (size !in 1..maxSize) throw SyncLinkException(SyncLinkException.Reason.WRONG_CODE)
        val iv = ByteArray(12).also(input::readFully)
        val sealed = input.readGrowing(size)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key.secret, GCMParameterSpec(128, iv)) }.doFinal(sealed)
        } catch (error: Exception) {
            throw SyncLinkException(SyncLinkException.Reason.WRONG_CODE, error)
        }
        val body = DataInputStream(ByteArrayInputStream(plain))
        return body.readUnsignedByte() to body
    }
}

/**
 * [size] bytes, taking memory as they arrive: a length is only a claim until the bytes behind it
 * have been sent, and the key is only checked once they all have.
 */
private fun DataInputStream.readGrowing(size: Int): ByteArray {
    val bytes = ByteArrayOutputStream(minOf(size, 64 * 1024))
    val chunk = ByteArray(64 * 1024)
    var left = size
    while (left > 0) {
        val read = read(chunk, 0, minOf(left, chunk.size))
        if (read < 0) throw EOFException()
        bytes.write(chunk, 0, read)
        left -= read
    }
    return bytes.toByteArray()
}

private fun DataOutputStream.writeFile(file: File) {
    val bytes = file.readBytes()
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readFile(into: File) {
    into.writeBytes(ByteArray(readInt()).also(::readFully))
}

/**
 * The computer's side: answers discovery and serves one round per connection. [onRound] runs
 * after each completed round, saying whether it changed this device's data.
 */
class SyncServer(
    private val key: SyncKey,
    private val device: SyncDevice,
    /** Which computer this is, so a phone paired with several knows this one. Not a secret. */
    private val id: String,
    private val name: String,
    private val onRound: (changed: Boolean) -> Unit,
    port: Int = SYNC_PORT,
    /** Whether a phone is holding a watch open: it is there, in step and waiting for changes. */
    private val onPresence: (present: Boolean) -> Unit = {},
) : AutoCloseable {
    private val watchers = AtomicInteger()
    private val connections = AtomicInteger()
    private val server = ServerSocket(port)
    private val discovery = runCatching { DatagramSocket(port) }.getOrNull()

    val port: Int get() = server.localPort

    init {
        thread(isDaemon = true, name = "summa-sync-server") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                if (connections.incrementAndGet() > MAX_CONNECTIONS) {
                    connections.decrementAndGet()
                    runCatching { socket.close() }
                    continue
                }
                thread(isDaemon = true, name = "summa-sync-round") {
                    try {
                        socket.use { runCatching { serve(it) } }
                    } finally {
                        connections.decrementAndGet()
                    }
                }
            }
        }
        discovery?.let { socket ->
            thread(isDaemon = true, name = "summa-sync-discovery") {
                val buffer = ByteArray(64)
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    runCatching {
                        socket.receive(packet)
                        if (String(packet.data, 0, packet.length) == DISCOVERY_QUESTION) {
                            val answer = "$DISCOVERY_ANSWER${server.localPort}|$id|$name".toByteArray()
                            socket.send(DatagramPacket(answer, answer.size, packet.socketAddress))
                        }
                    }
                }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = TIMEOUT_MILLIS
        val channel = Channel(socket, key)
        val (type, hello) = channel.receive(MAX_FIRST_FRAME)
        if (type == WATCH) return watch(channel)
        if (type != HELLO) return
        if (hello.readUTF() != device.schemaVersion) return channel.send(ERROR) { writeUTF(SyncLinkException.Reason.SCHEMA.name) }
        val served = File(device.tempDir, "sync-served-${System.nanoTime()}.db")
        val pushed = File(device.tempDir, "sync-pushed-${System.nanoTime()}.db")
        try {
            device.snapshot(served)
            val nonce = random.nextLong()
            channel.send(SNAPSHOT) {
                writeLong(nonce)
                writeFile(served)
            }
            // The phone may stop here to ask its owner about a conflict; it starts over afterwards.
            val (next, push) = try {
                channel.receive()
            } catch (_: EOFException) {
                return
            } catch (_: SocketTimeoutException) {
                return
            }
            if (next != PUSH || push.readLong() != nonce) return
            push.readFile(pushed)
            // What the phone sends already holds what it was served, so only what changed here in
            // the meantime can differ from it, and that stays.
            val changed = try {
                device.merge(pushed, served, ConflictChoice.MINE).changedRows > 0
            } catch (error: SyncRuleBreak) {
                return channel.send(ERROR) {
                    writeUTF(SyncLinkException.Reason.RULES.name)
                    writeUTF(error.records.firstOrNull().orEmpty())
                }
            } catch (error: Exception) {
                return channel.send(ERROR) { writeUTF(SyncLinkException.Reason.FAILED.name) }
            }
            channel.send(OK) { writeBoolean(changed) }
            onRound(changed)
        } finally {
            served.delete()
            pushed.delete()
        }
    }

    /** Holds the phone's question "has anything changed?" until something does, so it hears at once. */
    private fun watch(channel: Channel) {
        if (watchers.incrementAndGet() == 1) onPresence(true)
        try {
            val before = device.changeCounter()
            val deadline = System.currentTimeMillis() + WATCH_MILLIS
            while (!server.isClosed && System.currentTimeMillis() < deadline) {
                if (device.changeCounter() != before) return channel.send(CHANGED) { writeBoolean(true) }
                Thread.sleep(500)
            }
            channel.send(CHANGED) { writeBoolean(false) }
        } finally {
            // The phone asks again at once while it is there: only a gap longer than that is an absence.
            thread(isDaemon = true, name = "summa-sync-presence") {
                Thread.sleep(PRESENCE_GRACE_MILLIS)
                if (watchers.decrementAndGet() == 0) onPresence(false)
            }
        }
    }

    override fun close() {
        server.close()
        discovery?.close()
    }
}

/**
 * Waits for the computer at [address] to change: true as soon as its data does, false when it
 * has nothing to say for now. Throws when the computer cannot be reached.
 */
fun awaitSyncChange(address: InetSocketAddress, key: SyncKey): Boolean =
    try {
        Socket().use { socket ->
            socket.connect(address, 4000)
            socket.soTimeout = (WATCH_MILLIS + TIMEOUT_MILLIS).toInt()
            val channel = Channel(socket, key)
            channel.send(WATCH)
            val (type, body) = channel.receive()
            type == CHANGED && body.readBoolean()
        }
    } catch (error: SyncLinkException) {
        throw error
    } catch (error: Exception) {
        throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND, error)
    }

/** A computer running the app on this network. */
data class SyncComputer(val address: InetSocketAddress, val id: String, val name: String)

/** Asks the local network which computers are running the app; empty when none answers in time. */
fun discoverSyncComputers(timeoutMillis: Int = 1500, port: Int = SYNC_PORT): List<SyncComputer> =
    DatagramSocket().use { socket ->
        socket.broadcast = true
        socket.soTimeout = timeoutMillis
        val question = DISCOVERY_QUESTION.toByteArray()
        val targets = NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses.mapNotNull { address -> address.broadcast } } +
            InetAddress.getByName("255.255.255.255")
        targets.distinct().forEach { runCatching { socket.send(DatagramPacket(question, question.size, it, port)) } }
        val found = LinkedHashMap<String, SyncComputer>()
        val buffer = ByteArray(512)
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                break
            }
            val answer = String(packet.data, 0, packet.length)
            if (!answer.startsWith(DISCOVERY_ANSWER)) continue
            val parts = answer.removePrefix(DISCOVERY_ANSWER).split("|", limit = 3)
            val answerPort = parts.getOrNull(0)?.toIntOrNull() ?: continue
            val id = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: continue
            found.getOrPut(id) { SyncComputer(InetSocketAddress(packet.address, answerPort), id, parts.getOrNull(2).orEmpty()) }
            // One has answered: any other on this network answers within moments.
            socket.soTimeout = 250
        }
        found.values.toList()
    }

/** How a round ended on the phone. */
sealed interface SyncRound {
    /** Both devices now hold the same data. */
    data class Done(val changedHere: Boolean, val changedThere: Boolean) : SyncRound

    /** Records changed on both devices: nothing was applied; run the round again with a choice. */
    data class Conflicts(val conflicts: List<SyncConflict>) : SyncRound
}

/**
 * The phone's side of one round with the computer at [address]: take its data, merge it here
 * against [baseFile] (what both held after the last round), hand the result back, and keep it as
 * the new base once the computer has it.
 */
fun syncRound(
    address: InetSocketAddress,
    key: SyncKey,
    device: SyncDevice,
    baseFile: File,
    choice: ConflictChoice?,
): SyncRound {
    val theirs = File(device.tempDir, "sync-theirs-${System.nanoTime()}.db")
    val merged = File(device.tempDir, "sync-merged-${System.nanoTime()}.db")
    try {
        return Socket().use { socket ->
            try {
                socket.connect(address, 4000)
            } catch (error: Exception) {
                throw SyncLinkException(SyncLinkException.Reason.NOT_FOUND, error)
            }
            socket.soTimeout = TIMEOUT_MILLIS
            val channel = Channel(socket, key)
            channel.send(HELLO) { writeUTF(device.schemaVersion) }
            val (type, body) = try {
                channel.receive()
            } catch (error: EOFException) {
                // The computer hangs up on a hello it cannot read.
                throw SyncLinkException(SyncLinkException.Reason.WRONG_CODE, error)
            }
            if (type == ERROR) throw linkError(body)
            if (type != SNAPSHOT) throw SyncLinkException(SyncLinkException.Reason.FAILED)
            val nonce = body.readLong()
            body.readFile(theirs)

            val result = try {
                device.merge(theirs, baseFile.takeIf { it.isFile }, choice)
            } catch (error: SyncSchemaMismatch) {
                throw SyncLinkException(SyncLinkException.Reason.SCHEMA, error)
            } catch (error: SyncRuleBreak) {
                throw SyncLinkException(SyncLinkException.Reason.RULES, error, error.records.firstOrNull())
            }
            if (result.conflicts.isNotEmpty()) return@use SyncRound.Conflicts(result.conflicts)

            device.snapshot(merged)
            channel.send(PUSH) {
                writeLong(nonce)
                writeFile(merged)
            }
            val (answer, reply) = channel.receive()
            if (answer == ERROR) throw linkError(reply)
            if (answer != OK) throw SyncLinkException(SyncLinkException.Reason.FAILED)
            val changedThere = reply.readBoolean()
            // Only now is it what both hold. Until then the old base still gives a correct merge.
            merged.copyTo(baseFile, overwrite = true)
            SyncRound.Done(changedHere = result.changedRows > 0, changedThere = changedThere)
        }
    } catch (error: SyncLinkException) {
        throw error
    } catch (error: Exception) {
        throw SyncLinkException(SyncLinkException.Reason.FAILED, error)
    } finally {
        theirs.delete()
        merged.delete()
    }
}
