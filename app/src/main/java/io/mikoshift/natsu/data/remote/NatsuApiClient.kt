package io.mikoshift.natsu.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mikoshift.natsu.data.remote.dto.AuthResponseDto
import io.mikoshift.natsu.data.remote.dto.DocumentPackageResponseDto
import io.mikoshift.natsu.data.remote.dto.DocumentsResponseDto
import io.mikoshift.natsu.data.remote.dto.MessageResponseDto
import io.mikoshift.natsu.data.remote.dto.PackageHeadDto
import io.mikoshift.natsu.data.remote.dto.ReaderSettingsResponseDto
import io.mikoshift.natsu.data.remote.dto.SyncDocumentsRequestDto
import io.mikoshift.natsu.data.remote.dto.UpdateReaderSettingsRequestDto
import io.mikoshift.natsu.data.remote.dto.UserDto
import io.mikoshift.natsu.data.remote.dto.UserEnvelopeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class NatsuApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val gson: Gson = Gson(),
) {
    suspend fun register(
        name: String,
        email: String,
        password: String,
        deviceName: String,
    ): AuthResponseDto = postJson(
        path = "auth/register",
        body = mapOf(
            "name" to name,
            "email" to email,
            "password" to password,
            "device_name" to deviceName,
        ),
        responseType = AuthResponseDto::class.java,
        successCodes = setOf(201),
    )

    suspend fun login(
        email: String,
        password: String,
        deviceName: String,
    ): AuthResponseDto = postJson(
        path = "auth/login",
        body = mapOf(
            "email" to email,
            "password" to password,
            "device_name" to deviceName,
        ),
        responseType = AuthResponseDto::class.java,
    )

    suspend fun logout(): MessageResponseDto = postJson(
        path = "auth/logout",
        body = emptyMap<String, String>(),
        responseType = MessageResponseDto::class.java,
    )

    suspend fun getUser(): UserDto {
        val body = executeRequest(
            Request.Builder()
                .url(url("auth/user"))
                .get()
                .header("Accept", "application/json")
                .build(),
        )
        return gson.fromJson(body, UserEnvelopeDto::class.java).data
    }

    suspend fun deleteAccount(password: String): MessageResponseDto = deleteJson(
        path = "auth/account",
        body = mapOf("password" to password),
        responseType = MessageResponseDto::class.java,
    )

    suspend fun pullDocuments(sinceMs: Long): DocumentsResponseDto = getJson(
        path = "documents?since=$sinceMs",
        responseType = DocumentsResponseDto::class.java,
    )

    suspend fun pushDocuments(request: SyncDocumentsRequestDto): DocumentsResponseDto = postJson(
        path = "documents/sync",
        body = request,
        responseType = DocumentsResponseDto::class.java,
    )

    suspend fun getReaderSettings(): ReaderSettingsResponseDto = getJson(
        path = "settings/reader",
        responseType = ReaderSettingsResponseDto::class.java,
    )

    suspend fun putReaderSettings(request: UpdateReaderSettingsRequestDto): ReaderSettingsResponseDto = putJson(
        path = "settings/reader",
        body = request,
        responseType = ReaderSettingsResponseDto::class.java,
    )

    suspend fun uploadPackage(documentId: String, zipFile: File): DocumentPackageResponseDto =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "package",
                    zipFile.name,
                    zipFile.asRequestBody("application/zip".toMediaType()),
                )
                .build()
            val responseBody = executeRequest(
                Request.Builder()
                    .url(url("documents/$documentId/package"))
                    .put(body)
                    .header("Accept", "application/json")
                    .build(),
            )
            gson.fromJson(responseBody, DocumentPackageResponseDto::class.java)
        }

    suspend fun downloadPackage(documentId: String, destination: File) = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder()
                .url(url("documents/$documentId/package"))
                .get()
                .build(),
        ).execute().use { response ->
            if (response.code != 200) {
                val body = response.body?.string().orEmpty()
                throw ApiException(
                    code = response.code,
                    message = extractErrorMessage(body, response.code),
                    errorBody = body,
                )
            }
            val body = response.body ?: throw ApiException(
                code = response.code,
                message = "Empty package response body",
            )
            destination.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun headPackage(documentId: String): PackageHeadDto = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder()
                .url(url("documents/$documentId/package"))
                .head()
                .build(),
        ).execute().use { response ->
            if (response.code != 200) {
                throw ApiException(
                    code = response.code,
                    message = "Package head failed with HTTP ${response.code}",
                )
            }
            PackageHeadDto(
                contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L,
                sha256 = response.header("X-Package-Sha256"),
                updatedAtMs = response.header("X-Package-Updated-At-Ms")?.toLongOrNull() ?: 0L,
            )
        }
    }

    private suspend fun <T> getJson(
        path: String,
        responseType: Class<T>,
    ): T = withContext(Dispatchers.IO) {
        val body = executeRequest(
            Request.Builder()
                .url(url(path))
                .get()
                .header("Accept", "application/json")
                .build(),
        )
        gson.fromJson(body, responseType)
    }

    private suspend fun <T> postJson(
        path: String,
        body: Any,
        responseType: Class<T>,
        successCodes: Set<Int> = setOf(200),
    ): T = withContext(Dispatchers.IO) {
        val jsonBody = gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
        val responseBody = executeRequest(
            Request.Builder()
                .url(url(path))
                .post(jsonBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build(),
            successCodes = successCodes,
        )
        gson.fromJson(responseBody, responseType)
    }

    private suspend fun <T> putJson(
        path: String,
        body: Any,
        responseType: Class<T>,
    ): T = withContext(Dispatchers.IO) {
        val jsonBody = gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
        val responseBody = executeRequest(
            Request.Builder()
                .url(url(path))
                .put(jsonBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build(),
        )
        gson.fromJson(responseBody, responseType)
    }

    private suspend fun <T> deleteJson(
        path: String,
        body: Map<String, String>,
        responseType: Class<T>,
    ): T = withContext(Dispatchers.IO) {
        val jsonBody = gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
        val responseBody = executeRequest(
            Request.Builder()
                .url(url(path))
                .delete(jsonBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build(),
        )
        gson.fromJson(responseBody, responseType)
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private suspend fun executeRequest(
        request: Request,
        successCodes: Set<Int> = setOf(200),
    ): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code !in successCodes) {
                throw ApiException(
                    code = response.code,
                    message = extractErrorMessage(body, response.code),
                    errorBody = body,
                )
            }
            body
        }
    }

    private fun extractErrorMessage(body: String, code: Int): String {
        if (body.isBlank()) {
            return "Request failed with HTTP $code"
        }
        return runCatching {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.get("message")?.asString
                ?: json.get("error")?.asString
                ?: "Request failed with HTTP $code"
        }.getOrDefault("Request failed with HTTP $code")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
