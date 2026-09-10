package com.gdw2.locationapp

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal JSON-over-HTTP helper (no third-party HTTP/JSON dependencies).
 */
object Http {
    data class Response(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15000
    ): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        return try {
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            Response(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
