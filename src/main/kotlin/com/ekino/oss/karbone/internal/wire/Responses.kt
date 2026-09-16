/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.wire

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.internal.http.HttpResponseSpec
import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.TemplateId
import java.net.URLDecoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

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
   * Decodes a 2xx JSON envelope whose `data` is a [T]. Raises the mapped [KarboneError.Api] on a
   * non-2xx status or `success: false`, [KarboneError.Serialization] when the body cannot be
   * decoded.
   */
  context(_: Raise<KarboneError>)
  inline fun <reified T> envelope(
    response: HttpResponseSpec,
    json: Json,
    hint: NotFoundHint = NotFoundHint.None,
    render: Boolean = false,
  ): Envelope<T> = envelope(response, json, serializer<T>(), hint, render)

  context(_: Raise<KarboneError>)
  fun <T> envelope(
    response: HttpResponseSpec,
    json: Json,
    dataSerializer: KSerializer<T>,
    hint: NotFoundHint,
    render: Boolean,
  ): Envelope<T> {
    if (!response.isSuccess)
      raise(apiError(response, parseObjectOrNull(response, json), hint, render))
    val envelope = decodeOrRaise(response, json, Envelope.serializer(dataSerializer))
    ensure(envelope.success) {
      KarboneError.Unexpected(
        response.status,
        envelope.error ?: envelope.message,
        excerpt(response),
        envelope.codeText,
      )
    }
    return envelope
  }

  /** [envelope] then its mandatory `data`. */
  context(_: Raise<KarboneError>)
  inline fun <reified T : Any> data(
    response: HttpResponseSpec,
    json: Json,
    hint: NotFoundHint = NotFoundHint.None,
    render: Boolean = false,
  ): T {
    val envelope = envelope<T>(response, json, hint, render)
    return ensureNotNull(envelope.data) {
      KarboneError.Serialization(
        IllegalStateException("Missing data in response"),
        excerpt(response),
      )
    }
  }

  /** Success check only, for endpoints whose `data` carries nothing useful (delete). */
  context(_: Raise<KarboneError>)
  fun ack(
    response: HttpResponseSpec,
    json: Json,
    hint: NotFoundHint = NotFoundHint.None,
    render: Boolean = false,
  ) {
    envelope<JsonElement>(response, json, hint, render)
  }

  /** Decodes a 2xx body whose fields live at the root rather than under `data` (`GET /status`). */
  context(_: Raise<KarboneError>)
  inline fun <reified T> root(response: HttpResponseSpec, json: Json): T {
    if (!response.isSuccess)
      raise(apiError(response, parseObjectOrNull(response, json), NotFoundHint.None, false))
    return decodeOrRaise(response, json, serializer<T>())
  }

  context(_: Raise<KarboneError>)
  fun <T> decodeOrRaise(response: HttpResponseSpec, json: Json, deserializer: KSerializer<T>): T =
    try {
      json.decodeFromString(deserializer, response.bodyAsText())
    } catch (e: SerializationException) {
      raise(KarboneError.Serialization(e, excerpt(response)))
    } catch (e: IllegalArgumentException) {
      raise(KarboneError.Serialization(e, excerpt(response)))
    }

  fun excerpt(response: HttpResponseSpec): String = response.bodyAsText().take(MAX_BODY_EXCERPT)

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

  private const val MAX_BODY_EXCERPT = 2000
  private const val JWT_ERROR_MARKER = "JSON Web Token"
}
