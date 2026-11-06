package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.AuthMethod
import com.mrgnstr.harmony.core.model.ServerAccount
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OpenSubsonic / Navidrome authentication helper.
 * Handles salted MD5 tokens (`u`, `t`, `s`), `apiKeyAuthentication` (`apiKey=nds_...`),
 * and Native JWT (`X-ND-Authorization: Bearer ...` / `jwt=...`).
 */
object SubsonicAuth {
    const val SUBSONIC_VERSION = "1.16.1"
    const val CLIENT_NAME = "Harmony"

    private val random = SecureRandom()

    fun generateSalt(bytes: Int = 6): String {
        val b = ByteArray(bytes)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun computeToken(password: String, salt: String): String = md5Hex(password + salt)

    fun hexEncodePassword(password: String): String =
        "enc:" + password.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    fun hexDecodePassword(encoded: String): String {
        if (!encoded.startsWith("enc:")) return encoded
        val hex = encoded.removePrefix("enc:")
        if (hex.length % 2 != 0) return encoded
        return runCatching {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
                .toString(Charsets.UTF_8)
        }.getOrDefault(encoded)
    }

    /**
     * Builds the authentication parameters for a `/rest/` OpenSubsonic request.
     * Per Navidrome's `authenticateAPIKey`, `apiKey` must never be combined with `u`, `p`, `t`, `s`, or `jwt`.
     */
    fun authParams(account: ServerAccount, jsonFormat: Boolean = true): List<Pair<String, String>> {
        val params = ArrayList<Pair<String, String>>(8)
        val key = account.apiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: account.passwordOrToken.trim().takeIf { account.authMethod == AuthMethod.ApiKey && it.isNotEmpty() }

        if (key != null && account.authMethod == AuthMethod.ApiKey) {
            params += "apiKey" to key
        } else if (account.subsonicToken.isNotBlank() && account.subsonicSalt.isNotBlank()) {
            params += "u" to account.username
            params += "t" to account.subsonicToken
            params += "s" to account.subsonicSalt
        } else if (account.passwordOrToken.isNotEmpty()) {
            val salt = account.subsonicSalt.ifBlank { generateSalt() }
            params += "u" to account.username
            params += "t" to computeToken(account.passwordOrToken, salt)
            params += "s" to salt
        } else if (!account.jwtToken.isNullOrBlank()) {
            params += "u" to account.username
            params += "jwt" to account.jwtToken
        } else {
            params += "u" to account.username
        }
        params += "v" to SUBSONIC_VERSION
        params += "c" to CLIENT_NAME
        if (jsonFormat) params += "f" to "json"
        return params
    }

    fun encodeQuery(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    fun buildRestUrl(
        account: ServerAccount,
        endpoint: String,
        extraParams: List<Pair<String, String>> = emptyList(),
        jsonFormat: Boolean = true,
    ): String {
        val base = account.normalizedBaseUrl
        val cleanEndpoint = endpoint.removePrefix("/").removePrefix("rest/").removeSuffix(".view")
        val allParams = authParams(account, jsonFormat = jsonFormat) + extraParams
        return "$base/rest/$cleanEndpoint?${encodeQuery(allParams)}"
    }
}
