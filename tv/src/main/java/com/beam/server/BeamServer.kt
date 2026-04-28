package com.beam.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

class BeamServer(
    private val onUrlReceived: (String) -> Unit,
    private val onKeyReceived: (String, String) -> Unit
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
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))

            // Read request line
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1) ?: "/"

            // Read headers
            var contentLength = 0
            var line = reader.readLine()
            while (line != null && line.isNotBlank()) {
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.split(":")[1].trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            // Read body
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                reader.read(chars, 0, contentLength)
                String(chars)
            } else ""

            Log.d(TAG, "Request: $path body: $body")

            // Handle routes
            val responseBody = when {
                path == "/ping" -> """{"status":"ok","app":"Beam"}"""

                path == "/beam" && body.isNotBlank() -> {
                    val json = JSONObject(body)
                    val url = json.optString("url", "")
                    if (url.isNotBlank()) {
                        Log.i(TAG, "Received URL: $url")
                        onUrlReceived(url)
                        """{"status":"ok"}"""
                    } else """{"error":"missing url"}"""
                }

                path == "/key" && body.isNotBlank() -> {
                    val json = JSONObject(body)
                    val provider = json.optString("provider", "gemini")
                    val key = json.optString("key", "")
                    if (key.isNotBlank()) {
                        Log.i(TAG, "Received API key for: $provider")
                        onKeyReceived(provider, key)
                        """{"status":"ok"}"""
                    } else """{"error":"missing key"}"""
                }

                else -> """{"error":"not found"}"""
            }

            // Send response with proper HTTP formatting
            val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${responseBody.length}\r\nConnection: close\r\n\r\n$responseBody"
            writer.write(response)
            writer.flush()
            Log.d(TAG, "Response sent: $responseBody")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try { client.close() } catch (e: Exception) { }
        }
    }
}
