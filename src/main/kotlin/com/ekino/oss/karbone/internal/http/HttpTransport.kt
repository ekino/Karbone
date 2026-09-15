/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.http

/** Minimal HTTP abstraction: bytes in, bytes out. Knows nothing about JSON or Carbone. */
internal interface HttpTransport {
  suspend fun execute(request: HttpRequestSpec): HttpResponseSpec
}

internal enum class HttpMethod {
  GET,
  POST,
  PATCH,
  DELETE,
}

internal sealed interface HttpBody {
  data class Json(val content: String) : HttpBody

  /** Parts are sent in order; Carbone requires the file to be the last one. */
  data class Multipart(val fields: List<Pair<String, String>>, val file: FilePart) : HttpBody

  data object Empty : HttpBody
}

internal data class FilePart(
  val fieldName: String,
  val fileName: String,
  val contentType: String,
  val content: ByteArray,
)

internal data class HttpRequestSpec(
  val method: HttpMethod,
  val url: String,
  val headers: Map<String, String> = emptyMap(),
  val body: HttpBody = HttpBody.Empty,
)

internal class HttpResponseSpec(
  val status: Int,
  private val headers: Map<String, List<String>>,
  val body: ByteArray,
) {
  fun header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

  val contentType: String?
    get() = header("Content-Type")

  val isSuccess: Boolean
    get() = status in SUCCESS_RANGE

  fun bodyAsText(): String = body.toString(Charsets.UTF_8)

  fun isJson(): Boolean = contentType?.contains("json", ignoreCase = true) == true

  private companion object {
    val SUCCESS_RANGE = 200..299
  }
}
