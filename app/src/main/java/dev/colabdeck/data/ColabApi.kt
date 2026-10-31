package dev.colabdeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Client for the Colab runtime control plane used by Colab's own CLI:
 *   GET  /tun/m/assignments
 *   GET  /tun/m/assign?nbh=...&variant=...&accelerator=...
 *   POST /tun/m/assign              (X-Goog-Colab-Token)
 *   GET  /tun/m/unassign/<endpoint>
 *   POST /tun/m/unassign/<endpoint> (X-Goog-Colab-Token)
 *
 * All calls are authenticated with a Google OAuth token carrying the
 * https://www.googleapis.com/auth/colaboratory scope.
 */
class ColabApi(private val auth: AuthRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun listAssignments(): List<ColabSession> {
        val body = execute(base("tun/m/assignments").build(), "GET")
        if (body.isBlank()) return emptyList()
        val root = parseObject(body)
        val array = root["assignments"]?.jsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { parseAssignment(element.jsonObject) }.getOrNull()
        }
    }

    suspend fun createSession(
        accelerator: Accelerator,
        highMemory: Boolean,
        notebookHash: String = newNotebookHash()
    ): ColabSession {
        val url = assignUrl(accelerator, highMemory, notebookHash).build()
        val first = execute(url, "GET")
        val firstObject = parseObject(first)

        if (firstObject["runtimeProxyInfo"] != null) {
            return parseAssignment(firstObject)
                ?: throw ApiException(500, "Colab returned an unreadable session.")
        }

        val xsrf = firstObject["token"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException(500, "Colab did not return an allocation token.")

        val posted = execute(
            url,
            "POST",
            headers = mapOf("X-Goog-Colab-Token" to xsrf)
        )

        if (posted.isNotBlank()) {
            runCatching { parseAssignment(parseObject(posted)) }.getOrNull()?.let { return it }
        }

        // The POST can return an empty body on some backends. Reconcile.
        return listAssignments().firstOrNull {
            it.accelerator.equals(accelerator.accelerator, true)
        } ?: throw ApiException(502, "The runtime was requested but did not appear. Refresh in a moment.")
    }

    suspend fun stopSession(endpoint: String) {
        val url = base("tun/m/unassign")
            .addPathSegment(endpoint)
            .build()

        val first = execute(url, "GET")
        val xsrf = parseObject(first)["token"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException(500, "Colab did not return a termination token.")

        execute(
            url,
            "POST",
            headers = mapOf("X-Goog-Colab-Token" to xsrf)
        )
    }

    private fun assignUrl(
        accelerator: Accelerator,
        highMemory: Boolean,
        notebookHash: String
    ): HttpUrl.Builder {
        val builder = base("tun/m/assign")
            .addQueryParameter("nbh", notebookHash)
            .addQueryParameter("variant", accelerator.variant)

        if (accelerator.accelerator != "NONE") {
            builder.addQueryParameter("accelerator", accelerator.accelerator)
        }
        if (highMemory && !accelerator.highMemoryOnly) {
            builder.addQueryParameter("shape", "hm")
        }
        return builder
    }

    private fun base(path: String): HttpUrl.Builder =
        BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .addQueryParameter("authuser", "0")

    private suspend fun execute(
        url: HttpUrl,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: RequestBody? = null
    ): String {
        val token = auth.accessToken()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("X-Colab-Client-Agent", "colabdeck")

        headers.forEach { (key, value) -> requestBuilder.header(key, value) }

        when (method.uppercase()) {
            "POST" -> requestBuilder.post(body ?: EMPTY_BODY)
            else -> requestBuilder.get()
        }

        return withContext(Dispatchers.IO) {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) auth.markExpired()
                    throw ApiException(
                        response.code,
                        friendlyError(response.code, responseBody, url.toString())
                    )
                }
                stripXssi(responseBody)
            }
        }
    }

    private fun parseAssignment(obj: JsonObject): ColabSession? {
        val endpoint = obj["endpoint"]?.jsonPrimitive?.contentOrNull ?: return null
        val accelerator = obj["accelerator"]?.jsonPrimitive?.contentOrNull ?: "NONE"
        val variant = variantName(obj["variant"])
        val shape = shapeName(obj["machineShape"])
        val proxy = obj["runtimeProxyInfo"]?.jsonObject ?: return null
        val proxyUrl = proxy["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val proxyToken = proxy["token"]?.jsonPrimitive?.contentOrNull ?: return null
        val expiresIn = proxy["tokenExpiresInSeconds"]?.jsonPrimitive?.longOrNull
            ?: proxy["tokenExpiresInSeconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: 3600L

        return ColabSession(
            endpoint = endpoint,
            accelerator = accelerator,
            variant = variant,
            machineShape = shape,
            proxyUrl = proxyUrl,
            proxyToken = proxyToken,
            tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L
        )
    }

    private fun variantName(element: JsonElement?): String {
        val primitive = element as? JsonPrimitive ?: return "DEFAULT"
        primitive.contentOrNull?.let { text ->
            if (!text.toIntOrNull().let { it != null }) return text.uppercase()
        }
        return when (primitive.intOrNull) {
            1 -> "GPU"
            2 -> "TPU"
            else -> "DEFAULT"
        }
    }

    private fun shapeName(element: JsonElement?): String {
        val primitive = element as? JsonPrimitive ?: return "STANDARD"
        primitive.contentOrNull?.let { text ->
            if (text.toIntOrNull() == null) {
                return if (text.equals("hm", true) || text.equals("HIGH_RAM", true)) {
                    "HIGH_RAM"
                } else {
                    "STANDARD"
                }
            }
        }
        return if (primitive.intOrNull == 1) "HIGH_RAM" else "STANDARD"
    }

    private fun parseObject(body: String): JsonObject {
        val cleaned = stripXssi(body).trim()
        if (cleaned.isEmpty()) return JsonObject(emptyMap())
        return json.parseToJsonElement(cleaned).jsonObject
    }

    private fun stripXssi(body: String): String {
        var cleaned = body
        if (cleaned.startsWith(")]}'")) {
            cleaned = cleaned.substringAfter('\n', "")
        }
        return cleaned.trimStart()
    }

    private fun friendlyError(code: Int, body: String, url: String): String {
        val detail = extractError(body)
        return when (code) {
            400 -> buildString {
                append("Colab rejected the request")
                if (url.contains("/assign")) append(" — you may not have quota for this accelerator")
                if (detail.isNotBlank()) append(": $detail")
            }
            401 -> "Google sign-in expired. Sign in again."
            403 -> "This Google account is not allowed to perform that action."
            404 -> "Colab endpoint not found. The API may have changed."
            412 -> "Allocation refused. You may have too many active sessions, or the requested GPU/TPU is at capacity."
            429 -> "Colab is rate-limiting requests. Wait a moment and retry."
            in 500..599 -> "Colab backend error (HTTP $code). Try again shortly."
            else -> "Colab request failed (HTTP $code)${if (detail.isNotBlank()) ": $detail" else "."}"
        }
    }

    private fun extractError(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val obj = json.parseToJsonElement(stripXssi(body)).jsonObject
            val error = obj["error"]
            when (error) {
                is JsonPrimitive -> error.contentOrNull.orEmpty()
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                else -> ""
            }
        }.getOrDefault("").take(220)
    }

    companion object {
        private const val BASE_URL = "https://colab.research.google.com"
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private val JSON_BODY = "{}".toRequestBody("application/json".toMediaType())

        fun newNotebookHash(): String =
            UUID.randomUUID().toString().replace('-', '_') + ".".repeat(8)
    }
}

class ApiException(val code: Int, override val message: String) : IOException(message)
