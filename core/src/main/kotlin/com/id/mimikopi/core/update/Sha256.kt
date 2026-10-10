package com.id.mimikopi.core.update

import java.io.InputStream
import java.security.MessageDigest

/** Streaming SHA-256 used to verify downloaded APKs before they are installed. */
class Sha256 {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        digest.update(bytes, offset, length)
    }

    fun hex(): String = digest.digest().joinToString("") { "%02x".format(it) }

    companion object {
        fun of(input: InputStream): String {
            val sha = Sha256()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                sha.update(buffer, 0, n)
            }
            return sha.hex()
        }
    }
}
