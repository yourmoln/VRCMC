package com.vrcmc.app

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.util.date.GMTDate
import io.ktor.util.hex
import io.ktor.websocket.*
import kotlin.random.Random
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

// Protocol reference: https://github.com/yourmoln/vrctts/blob/main/src-tauri/src/tts.rs
internal const val edgeTtsToken = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
private const val edgeTtsBase = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
private const val edgeTtsVersion = "1-143.0.3650.75"
private const val edgeTtsUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"

internal data class EdgeTtsVoice(val shortName: String, val locale: String, val gender: String)

internal expect fun speechSha256(data: ByteArray): ByteArray

internal fun edgeTtsGec(unixMillis: Long): String {
    val ticks = (unixMillis / 1_000 + 11_644_473_600L) * 10_000_000
    val rounded = ticks - ticks % 3_000_000_000L
    return hex(speechSha256("$rounded$edgeTtsToken".encodeToByteArray())).uppercase()
}

internal fun escapeSpeechXml(text: String): String = buildString {
    text.forEach { char ->
        append(when (char) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '\'' -> "&apos;"
            '"' -> "&quot;"
            else -> if (char < ' ' && char !in "\n\r\t") " " else char.toString()
        })
    }
}

internal fun edgeTtsSsml(text: String, voice: String): String =
    "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
        "<voice name='${escapeSpeechXml(voice)}'><prosody rate='+0%' pitch='+0Hz' volume='+0%'>" +
        escapeSpeechXml(text) + "</prosody></voice></speak>"

internal fun edgeTtsAudioPayload(data: ByteArray): ByteArray? {
    require(data.size >= 2) { "Invalid Edge TTS frame" }
    val headerSize = ((data[0].toInt() and 255) shl 8) or (data[1].toInt() and 255)
    require(headerSize <= data.size - 2) { "Invalid Edge TTS header length" }
    val headers = data.decodeToString(2, 2 + headerSize)
    return if (headers.lineSequence().any { it.trim() == "Path:audio" })
        data.copyOfRange(2 + headerSize, data.size) else null
}

internal object EdgeTtsService {
    private class Forbidden(val serverDate: String?) : IllegalStateException("Edge TTS HTTP 403")
    private val client by lazy {
        createVrcmcHttpClient {
            install(WebSockets) { maxFrameSize = 4 * 1024 * 1024 }
            install(HttpTimeout) { connectTimeoutMillis = 15_000; requestTimeoutMillis = 30_000 }
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Forbidden &&
                        response.call.request.url.encodedPath.endsWith("/edge/v1")) {
                        throw Forbidden(response.headers[HttpHeaders.Date])
                    }
                }
            }
        }
    }
    private var clockSkewMillis = 0L

    private fun updateClock(date: String?) {
        date ?: return
        runCatching { date.fromHttpToGmtDate().timestamp }.getOrNull()?.let {
            clockSkewMillis = it - currentTimeMillis()
        }
    }

    private fun HttpRequestBuilder.edgeHeaders() {
        header(HttpHeaders.UserAgent, edgeTtsUserAgent)
        header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        header(HttpHeaders.Cookie, "muid=${requestId().uppercase()}")
        header(HttpHeaders.CacheControl, "no-cache")
    }

    suspend fun voices(): List<EdgeTtsVoice> = withTimeout(30_000) {
        suspend fun request() = client.get("https://$edgeTtsBase/voices/list") {
            parameter("trustedclienttoken", edgeTtsToken)
            edgeHeaders()
            header("Sec-MS-GEC", edgeTtsGec(currentTimeMillis() + clockSkewMillis))
            header("Sec-MS-GEC-Version", edgeTtsVersion)
        }
        var response = request()
        updateClock(response.headers[HttpHeaders.Date])
        if (response.status == HttpStatusCode.Forbidden) {
            response.bodyAsText()
            response = request()
        }
        check(response.status.isSuccess()) { "Edge TTS voice list HTTP ${response.status.value}" }
        Json.parseToJsonElement(response.bodyAsText()).jsonArray.mapNotNull { element ->
            val voice = element.jsonObject
            val name = voice["ShortName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            EdgeTtsVoice(name, voice["Locale"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                voice["Gender"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }.distinctBy { it.shortName }.sortedBy { it.shortName }.also { check(it.isNotEmpty()) }
    }

    suspend fun synthesize(text: String, voice: String): ByteArray = withTimeout(60_000) {
        require(text.isNotBlank() && text.length <= 10_000)
        // Rebuild the URL after a 403 so a corrected clock produces a fresh GEC.
        repeat(2) { attempt ->
            try {
                return@withTimeout synthesizeOnce(text, voice)
            } catch (error: Forbidden) {
                if (attempt != 0) throw error
                updateClock(error.serverDate)
            }
        }
        error("Edge TTS connection failed")
    }

    private suspend fun synthesizeOnce(text: String, voice: String): ByteArray {
        val requestId = requestId()
        val gec = edgeTtsGec(currentTimeMillis() + clockSkewMillis)
        val chunks = mutableListOf<ByteArray>()
        var size = 0
        var completed = false
        client.webSocket(
            urlString = "wss://$edgeTtsBase/edge/v1?TrustedClientToken=$edgeTtsToken" +
                "&ConnectionId=$requestId&Sec-MS-GEC=$gec&Sec-MS-GEC-Version=$edgeTtsVersion",
            request = {
                edgeHeaders()
                header(HttpHeaders.Origin, "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            },
        ) {
            val date = GMTDate(currentTimeMillis() + clockSkewMillis)
            fun Int.pad() = toString().padStart(2, '0')
            val timestamp = "${date.dayOfWeek.value} ${date.month.value} ${date.dayOfMonth.pad()} " +
                "${date.year} ${date.hours.pad()}:${date.minutes.pad()}:${date.seconds.pad()} GMT+0000 (Coordinated Universal Time)"
            send(Frame.Text("X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":false,"wordBoundaryEnabled":false},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""))
            send(Frame.Text("X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n${edgeTtsSsml(text, voice)}"))
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> edgeTtsAudioPayload(frame.data)?.let { payload ->
                        size += payload.size
                        check(size <= 8 * 1024 * 1024) { "Edge TTS audio exceeds limit" }
                        chunks.add(payload)
                    }
                    is Frame.Text -> if (frame.readText().lineSequence().any { it.trim() == "Path:turn.end" }) {
                        completed = true
                        break
                    }
                    else -> Unit
                }
            }
        }
        check(completed && size > 0) { "Edge TTS returned incomplete or empty audio" }
        return ByteArray(size).also { result ->
            var offset = 0
            chunks.forEach { it.copyInto(result, offset); offset += it.size }
        }
    }

    private fun requestId(): String = hex(Random.nextBytes(16))
}
