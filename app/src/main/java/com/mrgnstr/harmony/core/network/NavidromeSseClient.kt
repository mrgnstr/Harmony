package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.ScanStatusInfo
import com.mrgnstr.harmony.core.model.ServerAccount
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

sealed interface NavidromeServerEvent {
    data class ScanStatus(val status: ScanStatusInfo) : NavidromeServerEvent
    data class RefreshResource(val resources: Map<String, List<String>>) : NavidromeServerEvent
    data class NowPlayingCount(val count: Int) : NavidromeServerEvent
    data class ServerStart(val version: String, val startTime: String) : NavidromeServerEvent
    data class KeepAlive(val ts: Long) : NavidromeServerEvent
}

/**
 * Connects to Navidrome's Server-Sent Events stream (`GET /api/events`) to receive real-time
 * `scanStatus`, `refreshResource`, and `nowPlayingCount` broadcasts.
 */
@Singleton
class NavidromeSseClient @Inject constructor(
    private val http: NavidromeHttpClient,
) {
    fun events(account: ServerAccount): Flow<NavidromeServerEvent> = flow {
        val jwt = account.jwtToken?.takeIf { it.isNotBlank() } ?: return@flow
        while (currentCoroutineContext().isActive) {
            runCatching {
                val url = "${account.normalizedBaseUrl}/api/events?jwt=${URLEncoder.encode(jwt, "UTF-8")}"
                val conn = http.openConnection(account, url, connectTimeoutMs = 10_000, readTimeoutMs = 90_000)
                try {
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Accept", "text/event-stream")
                    conn.setRequestProperty("Cache-Control", "no-cache")
                    conn.setRequestProperty("X-ND-Authorization", "Bearer $jwt")
                    conn.setRequestProperty("X-ND-Client-Unique-Id", http.clientUniqueId)
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            var eventName = ""
                            val dataBuffer = StringBuilder()
                            while (currentCoroutineContext().isActive) {
                                val line = reader.readLine() ?: break
                                when {
                                    line.isEmpty() -> {
                                        if (eventName.isNotEmpty() && dataBuffer.isNotEmpty()) {
                                            parseEvent(eventName, dataBuffer.toString())?.let { emit(it) }
                                        }
                                        eventName = ""
                                        dataBuffer.setLength(0)
                                    }
                                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                                    line.startsWith("data:") -> {
                                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                                        dataBuffer.append(line.removePrefix("data:").trim())
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            }
            if (currentCoroutineContext().isActive) delay(5_000L)
        }
    }.flowOn(Dispatchers.IO)

    internal fun parseEvent(name: String, data: String): NavidromeServerEvent? = runCatching {
        val obj = MiniJson.parseObject(data)
        when (name) {
            "scanStatus" -> NavidromeServerEvent.ScanStatus(
                ScanStatusInfo(
                    scanning = obj.bool("scanning", false),
                    count = obj.long("count", 0L),
                    folderCount = obj.long("folderCount", 0L),
                    error = obj.strOrNull("error"),
                    scanType = obj.strOrNull("scanType"),
                    elapsedTimeMs = obj.long("elapsedTime", 0L) / 1_000_000L,
                ),
            )
            "refreshResource" -> {
                val map = obj.fields.mapValues { (_, v) ->
                    when (v) {
                        is JsonArr -> v.strings()
                        is JsonStr -> listOf(v.value)
                        else -> listOf("*")
                    }
                }
                NavidromeServerEvent.RefreshResource(map)
            }
            "nowPlayingCount" -> NavidromeServerEvent.NowPlayingCount(obj.int("count", 0))
            "serverStart" -> NavidromeServerEvent.ServerStart(obj.str("version"), obj.str("startTime"))
            "keepAlive" -> NavidromeServerEvent.KeepAlive(obj.long("ts", 0L))
            else -> null
        }
    }.getOrNull()
}
