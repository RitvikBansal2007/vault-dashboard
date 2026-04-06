package com.vault.commandcenter.util

import java.security.MessageDigest

object CryptoUtils {

    fun hashPin(rawPin: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(rawPin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun safeHashEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}