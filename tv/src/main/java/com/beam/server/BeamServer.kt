package com.beam.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * BeamServer runs a tiny HTTP server on the TV (port 8765).
 * The phone companion app sends commands to this server over local WiFi.
 *
 * Supported commands (POST requests):
 * - /beam     → receive a URL to analyze and display
 * - /key      → receive an API key to save
 * - /ping     → phone checks if TV is reachable
 */
class BeamServer(
    private val onUrlReceived: (String) -> Unit,
    private val onKeyReceived: (String, String) -> Unit  // provider, key
) {

    companion object {
        const val PORT = 8765
        private const val TAG = "BeamServer"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "Beam server started on port $PORT")

                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        Log.i(TAG, "Beam server stopped")
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            // Read HTTP request
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1) ?: "/"

            // Read headers to find Content-Length
            var contentLength = 0
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.split(":")[1].trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            // Read body
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                reader.read(chars)
                String(chars)
            } else ""

            // Handle routes
            when {
                path == "/ping" -> {
                    sendResponse(writer, 200, """{"status":"ok","app":"Beam"}""")
                }

                path == "/beam" && body.isNotBlank() -> {
                    val json = JSONObject(body)
                    val url = json.optString("url", "")
                    if (url.isNotBlank()) {
                        Log.i(TAG, "Received URL from phone: $url")
                        onUrlReceived(url)
                        sendResponse(writer, 200, """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 400, """{"error":"missing url"}""")
                    }
                }

                path == "/key" && body.isNotBlank() -> {
                    val json = JSONObject(body)
                    val provider = json.optString("provider", "gemini")
                    val key = json.optString("key", "")
                    if (key.isNotBlank()) {
                        Log.i(TAG, "Received API key from phone for provider: $provider")
                        onKeyReceived(provider, key)
                        sendResponse(writer, 200, """{"status":"ok"}""")
                    } else {
                        sendResponse(writer, 400, """{"error":"missing key"}""")
                    }
                }

                else -> sendResponse(writer, 404, """{"error":"not found"}""")
            }

            client.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        }
    }

    private fun sendResponse(writer: PrintWriter, code: Int, body: String) {
        val status = if (code == 200) "OK" else if (code == 400) "Bad Request" else "Not Found"
        writer.println("HTTP/1.1 $code $status")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${body.length}")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println()
        writer.println(body)
    }
}
