package com.id.mimikopi.core.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Why a Wi-Fi sync stopped. */
class SyncException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { WRONG_CODE, NO_CONNECTION, TIMEOUT, BAD_DATA, VERSION }
}

/**
 * Phone-to-PC sync over the home Wi-Fi, with JDK classes only (no server, no internet, no account). The phone waits
 * for one connection on a free port and shows its address and a 6-digit code; the PC connects (outbound, so Windows
 * shows no firewall prompt), both send their [SyncPayload] and each merges the other's.
 *
 * Each sync makes fresh P-256 keys (ECDH), so someone listening on the Wi-Fi cannot read the exchange; the code is
 * mixed into the AES-GCM key, so a device without the code cannot take part. The code is used once and stored
 * nowhere.
 */
object LanSync {
    private const val MAGIC = "MMKP-SYNC-1"
    private const val MAX_FRAME = 16 * 1024 * 1024
    private val CONFIRM = "mimikopi-confirm".toByteArray()
    private val random = SecureRandom()

    /** A fresh one-time code ("042917"). */
    fun newCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }

    /**
     * The phone's side: waits up to [waitMs] for the PC, then exchanges payloads ([mine] is read when the PC has
     * connected, so it is fresh). Returns the PC's payload; the caller merges it.
     */
    fun serve(server: ServerSocket, code: String, waitMs: Int, mine: () -> SyncPayload): SyncPayload {
        server.soTimeout = waitMs
        val socket = try {
            server.accept()
        } catch (e: SocketTimeoutException) {
            throw SyncException(SyncException.Kind.TIMEOUT, "no connection within ${waitMs / 1000} s")
        }
        socket.use { s ->
            s.soTimeout = IO_TIMEOUT_MS
            return exchange(s.getInputStream(), s.getOutputStream(), code, server = true, mine = mine)
        }
    }

    /** The PC's side: connects to the address the phone shows and exchanges payloads. Returns the phone's payload. */
    fun connect(host: String, port: Int, code: String, mine: () -> SyncPayload, timeoutMs: Int = CONNECT_TIMEOUT_MS): SyncPayload {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        } catch (e: IOException) {
            socket.close()
            throw SyncException(SyncException.Kind.NO_CONNECTION, e.message ?: "cannot connect")
        }
        socket.use { s ->
            s.soTimeout = IO_TIMEOUT_MS
            return exchange(s.getInputStream(), s.getOutputStream(), code, server = false, mine = mine)
        }
    }

    /**
     * The protocol on any pair of streams (tests use it over loopback): hello with public keys, a key from ECDH and
     * the code, a confirmation each way (a wrong code fails here, before any data is sent), then the client's payload
     * and the server's payload.
     */
    fun exchange(input: InputStream, output: OutputStream, code: String, server: Boolean, mine: () -> SyncPayload): SyncPayload {
        val inp = DataInputStream(input)
        val out = DataOutputStream(output)
        try {
            val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }
            val keys = kpg.generateKeyPair()
            val myPub = keys.public.encoded
            write(out, MAGIC.toByteArray() + myPub)
            val hello = read(inp)
            val magic = MAGIC.toByteArray()
            if (hello.size <= magic.size || !hello.copyOfRange(0, magic.size).contentEquals(magic)) {
                throw SyncException(SyncException.Kind.VERSION, "the other device runs a different sync version")
            }
            val peerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(hello.copyOfRange(magic.size, hello.size)))
            val shared = KeyAgreement.getInstance("ECDH").run { init(keys.private); doPhase(peerPub, true); generateSecret() }
            val (clientPub, serverPub) = if (server) hello.copyOfRange(magic.size, hello.size) to myPub else myPub to hello.copyOfRange(magic.size, hello.size)
            val key = deriveKey(shared, clientPub, serverPub, code)

            if (server) {
                val c = runCatching { open(key, read(inp)) }.getOrNull()
                if (c == null || !c.contentEquals(CONFIRM)) {
                    write(out, NO.toByteArray())
                    throw SyncException(SyncException.Kind.WRONG_CODE, "wrong code")
                }
                write(out, seal(key, CONFIRM))
                val theirs = decode(open(key, read(inp)))
                write(out, seal(key, mine().toJson().toByteArray()))
                return theirs
            } else {
                write(out, seal(key, CONFIRM))
                val reply = read(inp)
                if (reply.contentEquals(NO.toByteArray())) throw SyncException(SyncException.Kind.WRONG_CODE, "wrong code")
                val c = runCatching { open(key, reply) }.getOrNull()
                if (c == null || !c.contentEquals(CONFIRM)) throw SyncException(SyncException.Kind.WRONG_CODE, "wrong code")
                write(out, seal(key, mine().toJson().toByteArray()))
                return decode(open(key, read(inp)))
            }
        } catch (e: SyncException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw SyncException(SyncException.Kind.TIMEOUT, "the other device stopped answering")
        } catch (e: AEADBadTagException) {
            throw SyncException(SyncException.Kind.WRONG_CODE, "wrong code")
        } catch (e: IOException) {
            throw SyncException(SyncException.Kind.NO_CONNECTION, e.message ?: "connection lost")
        }
    }

    /**
     * This device's Wi-Fi (or Ethernet) IPv4 addresses for the PC to type, most likely first: private-range
     * addresses of interfaces that are up, Wi-Fi-looking names first. Needs no permission beyond network access.
     */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { nif.name to it.hostAddress } }
            .sortedBy { (name, _) -> if (name.startsWith("wlan") || name.startsWith("wl") || name.startsWith("eth") || name.startsWith("en")) 0 else 1 }
            .map { it.second }
            .distinct()
    }.getOrDefault(emptyList())

    /** "192.168.1.23:48123" or "192.168.1.23 48123" as host and port; null when it cannot be read. */
    fun parseAddress(text: String): Pair<String, Int>? {
        val parts = text.trim().split(':', ' ', '：').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val host = parts[0]
        val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return null
        return host to port
    }

    const val IO_TIMEOUT_MS = 20_000
    const val CONNECT_TIMEOUT_MS = 8_000
    private const val NO = "NO"

    private fun decode(bytes: ByteArray): SyncPayload =
        SyncPayload.fromJson(bytes.toString(Charsets.UTF_8)) ?: throw SyncException(SyncException.Kind.BAD_DATA, "unreadable progress data")

    private fun deriveKey(shared: ByteArray, clientPub: ByteArray, serverPub: ByteArray, code: String): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256").digest(clientPub + serverPub)
        val prk = hmac(salt, shared)
        return hmac(prk, "mimikopi-sync-v1|$code".toByteArray() + byteArrayOf(1))
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    private fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)) }
        return nonce + c.doFinal(plain)
    }

    private fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        if (sealed.size < 12 + 16) throw SyncException(SyncException.Kind.BAD_DATA, "short message")
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        }
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    private fun write(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun read(inp: DataInputStream): ByteArray {
        val n = inp.readInt()
        if (n !in 0..MAX_FRAME) throw SyncException(SyncException.Kind.BAD_DATA, "bad frame size $n")
        return ByteArray(n).also { inp.readFully(it) }
    }
}
