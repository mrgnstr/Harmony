package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.ServerAccount
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NavidromeApiException(
    val code: Int,
    override val message: String,
    val isSubsonicError: Boolean = false,
    val retryAfterSeconds: Int? = null,
) : Exception(message)

data class HttpResponseData(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, List<String>>,
    val refreshedJwt: String? = null,
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key != null && it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/**
 * Low-level HTTP transport for both OpenSubsonic (`/rest/`) and Navidrome Native API (`/api/`, `/auth/`).
 * Captures `X-ND-Authorization` token refreshes and supports LAN self-signed TLS when enabled on the account.
 */
@Singleton
class NavidromeHttpClient @Inject constructor() {
    val clientUniqueId: String = UUID.randomUUID().toString()

    /** Listener invoked whenever Navidrome's JWTRefresher sends a fresh `X-ND-Authorization` token. */
    @Volatile var onJwtRefreshed: ((serverId: Long, newJwt: String) -> Unit)? = null

    suspend fun request(
        account: ServerAccount,
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        bodyBytes: ByteArray? = null,
        contentType: String? = null,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 20_000,
    ): HttpResponseData = withContext(Dispatchers.IO) {
        val conn = openConnection(account, url, connectTimeoutMs, readTimeoutMs)
        try {
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "Harmony/0.2.0 (Android)")
            conn.setRequestProperty("X-ND-Client-Unique-Id", clientUniqueId)
            account.jwtToken?.takeIf { it.isNotBlank() }?.let { jwt ->
                conn.setRequestProperty("X-ND-Authorization", "Bearer $jwt")
            }
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (bodyBytes != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(bodyBytes.size)
                conn.outputStream.use { it.write(bodyBytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val respHeaders = conn.headerFields.filterKeys { it != null }
            val refreshed = respHeaders.entries
                .firstOrNull { it.key.equals("X-ND-Authorization", ignoreCase = true) }
                ?.value?.firstOrNull()?.takeIf { it.isNotBlank() }
            if (refreshed != null && account.id > 0L) {
                onJwtRefreshed?.invoke(account.id, refreshed)
            }
            HttpResponseData(code, text, respHeaders, refreshed)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun getBytes(
        account: ServerAccount,
        url: String,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 20_000,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val conn = openConnection(account, url, connectTimeoutMs, readTimeoutMs)
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Harmony/0.2.0 (Android)")
            conn.setRequestProperty("X-ND-Client-Unique-Id", clientUniqueId)
            account.jwtToken?.takeIf { it.isNotBlank() }?.let { jwt ->
                conn.setRequestProperty("X-ND-Authorization", "Bearer $jwt")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val contentType = conn.contentType.orEmpty()
            if (contentType.contains("application/json") || contentType.contains("text/xml") || contentType.contains("application/xml")) {
                // Subsonic returned an error envelope instead of binary bytes.
                return@withContext null
            }
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun streamTo(
        account: ServerAccount,
        url: String,
        onStream: suspend (InputStream, Long, String?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val conn = openConnection(account, url, connectTimeoutMs = 15_000, readTimeoutMs = 60_000)
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Harmony/0.2.0 (Android)")
            conn.setRequestProperty("X-ND-Client-Unique-Id", clientUniqueId)
            account.jwtToken?.takeIf { it.isNotBlank() }?.let { jwt ->
                conn.setRequestProperty("X-ND-Authorization", "Bearer $jwt")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw NavidromeApiException(code, "HTTP $code downloading stream")
            }
            val length = conn.contentLengthLong
            val disposition = conn.getHeaderField("Content-Disposition")
            conn.inputStream.use { input ->
                onStream(input, length, disposition)
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun uploadMultipartImage(
        account: ServerAccount,
        url: String,
        imageBytes: ByteArray,
        filename: String = "cover.jpg",
        mimeType: String = "image/jpeg",
    ): HttpResponseData {
        val boundary = "----HarmonyBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Disposition: form-data; name=\"image\"; filename=\"$filename\"\r\n".toByteArray())
            write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            write(imageBytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        return request(
            account = account,
            url = url,
            method = "POST",
            bodyBytes = body,
            contentType = "multipart/form-data; boundary=$boundary",
        )
    }

    internal fun openConnection(
        account: ServerAccount,
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        if (conn is HttpsURLConnection && account.allowSelfSignedTls) {
            conn.sslSocketFactory = TrustAllSsl.socketFactory
            conn.hostnameVerifier = TrustAllSsl.hostnameVerifier
        }
        return conn
    }
}

private object TrustAllSsl {
    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val socketFactory: javax.net.ssl.SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }.socketFactory
    val hostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }
}
