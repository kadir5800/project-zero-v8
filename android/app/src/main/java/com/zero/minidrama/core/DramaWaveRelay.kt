package com.zero.minidrama.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DramaWaveRelay(context: Context) : NanoHTTPD(RELAY_PORT) {
    private val appContext = context.applicationContext

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method == Method.GET && session.uri == "/health") {
                val cellular = NetworkHolder.hasCellular(appContext)
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"ok":true,"cellular":$cellular,"port":$RELAY_PORT}"""
                )
            }

            if (!session.uri.startsWith("/dm-api")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }

            val suffix = session.uri.removePrefix("/dm-api")
            val targetUrl = "$TARGET_HOST/dm-api$suffix"
            val method = session.method.name
            val bodyBytes = if (method == "GET" || method == "HEAD") {
                ByteArray(0)
            } else {
                val files = HashMap<String, String>()
                session.parseBody(files)
                files["postData"]?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            }

            val headers = linkedMapOf<String, String>()
            session.headers.forEach { (key, value) ->
                if (key.lowercase() !in SKIP_HEADERS) headers[key] = value
            }

            val result = NetworkHolder.execute(appContext, targetUrl, method, headers, bodyBytes)
            newFixedLengthResponse(
                Response.Status.OK,
                result.contentType,
                result.body
            )
        } catch (e: Exception) {
            val msg = (e.message ?: "relay error").replace("\"", "'")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"ok":false,"error":"$msg"}"""
            )
        }
    }

    private data class HttpResult(val contentType: String, val body: String)

    private object NetworkHolder {
        @Volatile
        private var cellularNetwork: Network? = null

        fun init(context: Context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cellularNetwork = network
                    Log.i(TAG, "Cellular network baglandi")
                }

                override fun onLost(network: Network) {
                    if (cellularNetwork == network) {
                        cellularNetwork = null
                        Log.i(TAG, "Cellular network kaybedildi")
                    }
                }
            })

            val latch = CountDownLatch(1)
            cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cellularNetwork = network
                    latch.countDown()
                }

                override fun onUnavailable() {
                    latch.countDown()
                }
            })
            Thread { latch.await(5, TimeUnit.SECONDS) }.start()
        }

        fun hasCellular(context: Context): Boolean {
            if (cellularNetwork != null) return true
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.let { caps ->
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }
        }

        private fun pickCellular(context: Context): Network? {
            cellularNetwork?.let { return it }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)?.let { caps ->
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }
        }

        fun execute(
            context: Context,
            targetUrl: String,
            method: String,
            headers: Map<String, String>,
            bodyBytes: ByteArray
        ): HttpResult {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return executeOnNetwork(context, targetUrl, method, headers, bodyBytes)
            }
            val result = AtomicReference<HttpResult>()
            val error = AtomicReference<Exception>()
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try {
                    result.set(executeOnNetwork(context, targetUrl, method, headers, bodyBytes))
                } catch (e: Exception) {
                    error.set(e)
                } finally {
                    latch.countDown()
                }
            }
            if (!latch.await(90, TimeUnit.SECONDS)) {
                throw IllegalStateException("Relay istegi zaman asimina ugradi")
            }
            error.get()?.let { throw it }
            return result.get() ?: throw IllegalStateException("Relay yaniti bos")
        }

        private fun executeOnNetwork(
            context: Context,
            targetUrl: String,
            method: String,
            headers: Map<String, String>,
            bodyBytes: ByteArray
        ): HttpResult {
            val cellular = pickCellular(context)
            val url = URL(targetUrl)
            val conn = try {
                (cellular?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            } catch (e: Exception) {
                Log.w(TAG, "Cellular baglanti basarisiz, wifi deneniyor", e)
                (url.openConnection() as HttpURLConnection)
            }
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true
            headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
            if (bodyBytes.isNotEmpty()) {
                conn.doOutput = true
                conn.outputStream.use { it.write(bodyBytes) }
            }
            val httpCode = conn.responseCode
            val stream = if (httpCode >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val contentType = conn.contentType?.substringBefore(';') ?: "application/json"
            conn.disconnect()
            return HttpResult(contentType, body)
        }
    }

    companion object {
        private const val RELAY_PORT = 8899
        const val PORT = RELAY_PORT
        private const val TARGET_HOST = "https://api.mydramawave.com"
        private const val TAG = "DramaWaveRelay"
        private val SKIP_HEADERS = setOf("host", "connection", "content-length", "transfer-encoding", "accept-encoding")

        @Volatile
        private var instance: DramaWaveRelay? = null

        fun start(context: Context) {
            if (instance != null) return
            synchronized(this) {
                if (instance != null) return
                try {
                    NetworkHolder.init(context.applicationContext)
                    val relay = DramaWaveRelay(context)
                    relay.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    instance = relay
                    Log.i(TAG, "Listening on port $RELAY_PORT")
                } catch (e: Exception) {
                    if (isPortInUse(e) && probeHealth()) {
                        Log.w(TAG, "Port $RELAY_PORT zaten acik; mevcut relay kullaniliyor")
                        return
                    }
                    Log.e(TAG, "Relay start failed", e)
                    throw e
                }
            }
        }

        private fun isPortInUse(e: Exception): Boolean {
            val msg = e.message.orEmpty()
            return msg.contains("EADDRINUSE", ignoreCase = true) ||
                msg.contains("Address already in use", ignoreCase = true)
        }

        private fun probeHealth(): Boolean {
            return runCatching {
                val conn = URL("http://127.0.0.1:$RELAY_PORT/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.requestMethod = "GET"
                val ok = conn.responseCode == 200
                conn.disconnect()
                ok
            }.getOrDefault(false)
        }
    }
}
