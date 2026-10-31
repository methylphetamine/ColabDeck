package dev.colabdeck.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RemoteEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val lastModified: String,
    val mimeType: String,
    val writable: Boolean
) {
    val isDirectory: Boolean get() = type == "directory"
}

data class RemoteFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * Jupyter Contents API on the Colab runtime, using the runtime proxy token
 * as a query parameter (the same transport the official Colab CLI uses).
 */
class ColabFilesApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun list(session: ColabSession, path: String): List<RemoteEntry> =
        withContext(Dispatchers.IO) {
            val body = execute(session, path, "GET")
            if (body.isBlank()) return@withContext emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val content = root["content"]?.jsonArray ?: return@withContext emptyList()
            content.mapNotNull { element ->
                runCatching { parseEntry(element.jsonObject) }.getOrNull()
            }.sortedWith(
                compareByDescending<RemoteEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        }

    suspend fun download(session: ColabSession, path: String): RemoteFile =
        withContext(Dispatchers.IO) {
            val body = execute(session, path, "GET", extraParams = mapOf("content" to "1"))
            val obj = json.parseToJsonElement(body).jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "directory") {
                throw IsADirectoryException("$path is a directory.")
            }

            val content = obj["content"]?.jsonPrimitive?.contentOrNull
                ?: throw IOException("The runtime returned no file content.")
            val format = obj["format"]?.jsonPrimitive?.contentOrNull

            val bytes = if (format == "base64") {
                val estimated = content.length * 3L / 4
                if (estimated > MAX_TRANSFER_BYTES) {
                    throw IOException(
                        "File is larger than the ${MAX_TRANSFER_BYTES / (1024 * 1024)} MB in-app limit."
                    )
                }
                Base64.decode(content, Base64.DEFAULT)
            } else {
                content.toByteArray(Charsets.UTF_8)
            }
            if (bytes.size > MAX_TRANSFER_BYTES) {
                throw IOException("File is larger than the ${MAX_TRANSFER_BYTES / (1024 * 1024)} MB in-app limit.")
            }

            RemoteFile(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: path.substringAfterLast('/'),
                mimeType = obj["mimetype"]?.jsonPrimitive?.contentOrNull ?: "application/octet-stream",
                bytes = bytes
            )
        }

    suspend fun upload(
        session: ColabSession,
        path: String,
        bytes: ByteArray,
        mimeType: String
    ): RemoteEntry = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_TRANSFER_BYTES) {
            throw IOException("File is larger than the ${MAX_TRANSFER_BYTES / (1024 * 1024)} MB in-app limit.")
        }
        val payload = JSONObject()
            .put("name", path.substringAfterLast('/'))
            .put("path", path)
            .put("type", "file")
            .put("format", "base64")
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("chunk", 1)
            .toString()
            .toRequestBody(JSON_MEDIA)

        val body = execute(session, path, "PUT", body = payload)
        if (body.isBlank()) {
            RemoteEntry(
                name = path.substringAfterLast('/'),
                path = path,
                type = "file",
                size = bytes.size.toLong(),
                lastModified = "",
                mimeType = mimeType,
                writable = true
            )
        } else {
            parseEntry(json.parseToJsonElement(body).jsonObject)
        }
    }

    suspend fun createDirectory(session: ColabSession, path: String): RemoteEntry =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("name", path.substringAfterLast('/'))
                .put("path", path)
                .put("type", "directory")
                .toString()
                .toRequestBody(JSON_MEDIA)
            val body = execute(session, path, "PUT", body = payload)
            if (body.isBlank()) {
                RemoteEntry(
                    name = path.substringAfterLast('/'),
                    path = path,
                    type = "directory",
                    size = 0,
                    lastModified = "",
                    mimeType = "",
                    writable = true
                )
            } else {
                parseEntry(json.parseToJsonElement(body).jsonObject)
            }
        }

    suspend fun delete(session: ColabSession, path: String) = withContext(Dispatchers.IO) {
        execute(session, path, "DELETE")
        Unit
    }

    private fun execute(
        session: ColabSession,
        path: String,
        method: String,
        extraParams: Map<String, String> = emptyMap(),
        body: okhttp3.RequestBody? = null
    ): String {
        val base = session.proxyUrl.toHttpUrlOrNull()
            ?: throw IOException("Colab returned an invalid runtime URL.")

        val builder = base.newBuilder()
            .encodedPath("/")
            .addPathSegment("api")
            .addPathSegment("contents")

        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { segment ->
            builder.addPathSegment(segment)
        }

        builder.addQueryParameter("authuser", "0")
        builder.addQueryParameter("colab-runtime-proxy-token", session.proxyToken)
        extraParams.forEach { (key, value) -> builder.addQueryParameter(key, value) }

        val request = Request.Builder()
            .url(builder.build())
            .header("X-Colab-Client-Agent", "colabdeck")
            .header("Accept", "application/json")
            .apply {
                when (method.uppercase()) {
                    "PUT" -> put(body ?: EMPTY_BODY)
                    "DELETE" -> delete()
                    else -> get()
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 404) {
                throw FileNotFoundException("Not found on the runtime: $path")
            }
            if (!response.isSuccessful) {
                throw IOException(friendlyError(response.code, responseBody, path))
            }
            return responseBody
        }
    }

    private fun parseEntry(obj: JsonObject): RemoteEntry {
        return RemoteEntry(
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
            path = obj["path"]?.jsonPrimitive?.contentOrNull ?: "",
            type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "file",
            size = obj["size"]?.jsonPrimitive?.longOrNull
                ?: obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: 0L,
            lastModified = obj["last_modified"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mimeType = obj["mimetype"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            writable = obj["writable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        )
    }

    private fun friendlyError(code: Int, body: String, path: String): String = when (code) {
        401, 403 -> "The runtime rejected the request. Refresh sessions and retry."
        404 -> "Not found on the runtime: $path"
        413 -> "The runtime rejected the file because it is too large."
        else -> "Runtime file request failed (HTTP $code)${body.take(160).let { if (it.isBlank()) "" else ": $it" }}"
    }

    companion object {
        const val MAX_TRANSFER_BYTES = 100L * 1024 * 1024
        private val JSON_MEDIA = "application/json".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

class IsADirectoryException(message: String) : IOException(message)
