/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.wire

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.internal.http.HttpResponseSpec
import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.TemplateId
import java.net.URLDecoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What a 404 means for the endpoint being called. */
internal sealed interface NotFoundHint {
  data class Template(val id: TemplateId) : NotFoundHint

  data class Render(val id: RenderId) : NotFoundHint

  data object None : NotFoundHint
}

/** Turns raw responses into the `{success, data}` envelope or a [KarboneError]. */
internal object Responses {

  private const val HTTP_BAD_REQUEST = 400
  private const val HTTP_UNPROCESSABLE = 422
  private const val HTTP_SERVER_ERROR = 500

  /**
   * Parses a JSON envelope, raising on non-2xx or `success: false`. Returns `data` (may be JsonNull
   * / absent → empty object).
   */
  context(_: Raise<KarboneError>)
  fun envelope(
    response: HttpResponseSpec,
    json: Json,
    hint: NotFoundHint = NotFoundHint.None,
    render: Boolean = false,
  ): JsonElement {
    val root = parseObjectOrNull(response, json)
    if (!response.isSuccess) raise(apiError(response, root, hint, render))
    ensure(root != null) {
      KarboneError.Serialization(
        IllegalStateException("Expected a JSON body"),
        response.bodyAsText().take(MAX_BODY_EXCERPT),
      )
    }
    val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: true
    ensure(success) {
      KarboneError.Unexpected(
        response.status,
        root.errorMessage(),
        response.bodyAsText().take(MAX_BODY_EXCERPT),
        root.errorCode(),
      )
    }
    return root["data"] ?: JsonObject(emptyMap())
  }

  /** For endpoints that return a file stream on success and a JSON error otherwise. */
  context(_: Raise<KarboneError>)
  fun binary(
    response: HttpResponseSpec,
    json: Json,
    hint: NotFoundHint = NotFoundHint.None,
    render: Boolean = false,
  ): HttpResponseSpec {
    if (response.isSuccess && !response.isJson()) return response
    val root = parseObjectOrNull(response, json)
    if (!response.isSuccess) raise(apiError(response, root, hint, render))
    // 2xx with a JSON body: either an envelope error, or an unexpected JSON answer where a file was
    // expected
    val success = root?.get("success")?.jsonPrimitive?.booleanOrNull
    raise(
      KarboneError.Unexpected(
        response.status,
        root?.errorMessage() ?: "Expected a file, got JSON (success=$success)",
        response.bodyAsText().take(MAX_BODY_EXCERPT),
        root?.errorCode(),
      )
    )
  }

  fun apiError(
    response: HttpResponseSpec,
    root: JsonObject?,
    hint: NotFoundHint,
    render: Boolean,
  ): KarboneError.Api {
    val message = root?.errorMessage()
    val code = root?.errorCode()
    val excerpt = response.bodyAsText().take(MAX_BODY_EXCERPT)
    // carbone-ee answers HTTP 500 "Error: Invalid JSON Web Token: ..." to a malformed bearer token;
    // that is an auth failure.
    if (message?.contains(JWT_ERROR_MARKER, ignoreCase = true) == true)
      return KarboneError.Unauthorized(message, response.status, code)
    return when (response.status) {
      KarboneError.HTTP_UNAUTHORIZED -> KarboneError.Unauthorized(message, code = code)
      KarboneError.HTTP_NOT_FOUND ->
        when (hint) {
          is NotFoundHint.Template -> KarboneError.TemplateNotFound(hint.id, message, code = code)
          is NotFoundHint.Render -> KarboneError.RenderNotFound(hint.id, message, code = code)
          NotFoundHint.None -> KarboneError.Unexpected(response.status, message, excerpt, code)
        }
      KarboneError.HTTP_PAYLOAD_TOO_LARGE -> KarboneError.PayloadTooLarge(message, code = code)
      KarboneError.HTTP_UNSUPPORTED_MEDIA ->
        KarboneError.UnsupportedTemplateFormat(message, code = code)
      HTTP_BAD_REQUEST,
      HTTP_UNPROCESSABLE -> KarboneError.BadRequest(response.status, message, code)
      HTTP_SERVER_ERROR ->
        if (render) KarboneError.RenderFailed(message, code = code)
        else KarboneError.Unexpected(response.status, message, excerpt, code)
      else -> KarboneError.Unexpected(response.status, message, excerpt, code)
    }
  }

  private fun parseObjectOrNull(response: HttpResponseSpec, json: Json): JsonObject? {
    if (response.body.isEmpty()) return null
    return try {
      json.parseToJsonElement(response.bodyAsText()) as? JsonObject
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private fun JsonObject.errorCode(): String? = (this["code"] as? JsonPrimitive)?.contentOrNull

  private fun JsonObject.errorMessage(): String? =
    (this["error"] as? JsonPrimitive)?.contentOrNull
      ?: (this["message"] as? JsonPrimitive)?.contentOrNull

  /** `filename*=UTF-8''...` wins over `filename="..."`. */
  fun fileName(contentDisposition: String?): String? {
    if (contentDisposition == null) return null
    val extended =
      Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""")
        .find(contentDisposition)
        ?.groupValues
        ?.get(1)
    if (extended != null) return URLDecoder.decode(extended.trim(), Charsets.UTF_8)
    val quoted = Regex("""filename\s*=\s*"([^"]*)"""").find(contentDisposition)?.groupValues?.get(1)
    if (quoted != null) return quoted
    return Regex("""filename\s*=\s*([^;\s]+)""").find(contentDisposition)?.groupValues?.get(1)
  }

  // ---- typed accessors on `data` -------------------------------------------------------------

  fun JsonElement.str(key: String): String? =
    (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }

  fun JsonElement.long(key: String): Long? =
    (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.longOrNull }

  fun JsonElement.int(key: String): Int? =
    (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.intOrNull }

  fun JsonElement.bool(key: String): Boolean? =
    (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.booleanOrNull }

  fun JsonElement.strings(key: String): List<String> =
    (this as? JsonObject)
      ?.get(key)
      ?.let { arr ->
        runCatching { arr.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull()
      }
      .orEmpty()

  fun JsonElement.objects(): List<JsonObject> =
    runCatching { jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())

  private const val MAX_BODY_EXCERPT = 2000
  private const val JWT_ERROR_MARKER = "JSON Web Token"
}
