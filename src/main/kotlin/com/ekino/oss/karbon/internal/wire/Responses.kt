/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.internal.wire

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.internal.http.HttpResponseSpec
import com.ekino.oss.karbon.model.RenderId
import com.ekino.oss.karbon.model.TemplateId
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

/** Turns raw responses into the `{success, data}` envelope or a [KarbonError]. */
internal object Responses {

  private const val HTTP_BAD_REQUEST = 400
  private const val HTTP_UNPROCESSABLE = 422
  private const val HTTP_SERVER_ERROR = 500

  /**
   * Parses a JSON envelope, raising on non-2xx or `success: false`. Returns `data` (may be JsonNull
   * / absent → empty object).
   */
  context(_: Raise<KarbonError>)
  fun envelope(
    response: HttpResponseSpec,
    json: Json,
    hint: NotFoundHint = NotFoundHint.None,
    render: Boolean = false,
  ): JsonElement {
    val root = parseObjectOrNull(response, json)
    if (!response.isSuccess) raise(apiError(response, root, hint, render))
    ensure(root != null) {
      KarbonError.Serialization(
        IllegalStateException("Expected a JSON body"),
        response.bodyAsText().take(MAX_BODY_EXCERPT),
      )
    }
    val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: true
    ensure(success) {
      KarbonError.Unexpected(
        response.status,
        root.errorMessage(),
        response.bodyAsText().take(MAX_BODY_EXCERPT),
      )
    }
    return root["data"] ?: JsonObject(emptyMap())
  }

  /** For endpoints that return a file stream on success and a JSON error otherwise. */
  context(_: Raise<KarbonError>)
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
      KarbonError.Unexpected(
        response.status,
        root?.errorMessage() ?: "Expected a file, got JSON (success=$success)",
        response.bodyAsText().take(MAX_BODY_EXCERPT),
      )
    )
  }

  fun apiError(
    response: HttpResponseSpec,
    root: JsonObject?,
    hint: NotFoundHint,
    render: Boolean,
  ): KarbonError.Api {
    val message = root?.errorMessage()
    // carbone-ee answers HTTP 500 "Error: Invalid JSON Web Token: ..." to a malformed bearer token;
    // that is an auth failure.
    if (message?.contains(JWT_ERROR_MARKER, ignoreCase = true) == true)
      return KarbonError.Unauthorized(message, response.status)
    return when (response.status) {
      KarbonError.HTTP_UNAUTHORIZED -> KarbonError.Unauthorized(message)
      KarbonError.HTTP_NOT_FOUND ->
        when (hint) {
          is NotFoundHint.Template -> KarbonError.TemplateNotFound(hint.id, message)
          is NotFoundHint.Render -> KarbonError.RenderNotFound(hint.id, message)
          NotFoundHint.None ->
            KarbonError.Unexpected(
              response.status,
              message,
              response.bodyAsText().take(MAX_BODY_EXCERPT),
            )
        }
      KarbonError.HTTP_PAYLOAD_TOO_LARGE -> KarbonError.PayloadTooLarge(message)
      KarbonError.HTTP_UNSUPPORTED_MEDIA -> KarbonError.UnsupportedTemplateFormat(message)
      HTTP_BAD_REQUEST,
      HTTP_UNPROCESSABLE -> KarbonError.BadRequest(response.status, message)
      HTTP_SERVER_ERROR ->
        if (render) KarbonError.RenderFailed(message)
        else
          KarbonError.Unexpected(
            response.status,
            message,
            response.bodyAsText().take(MAX_BODY_EXCERPT),
          )
      else ->
        KarbonError.Unexpected(
          response.status,
          message,
          response.bodyAsText().take(MAX_BODY_EXCERPT),
        )
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
