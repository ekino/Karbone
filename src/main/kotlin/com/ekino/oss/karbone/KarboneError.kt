/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateSource

/** Every way a Karbone call can fail, as a value. */
public sealed interface KarboneError {

  public fun describe(): String

  /**
   * Errors reported by the Carbone API. [message] is the raw `error` text from the response, never
   * rewritten.
   */
  public sealed interface Api : KarboneError {
    public val status: Int
    public val message: String?

    /** Carbone error code when provided, e.g. `w117`. */
    public val code: String?

    override fun describe(): String =
      "Carbone API error HTTP $status${code?.let { " [$it]" }.orEmpty()}${message?.let { ": $it" }.orEmpty()}"
  }

  public data class Unauthorized(
    override val message: String?,
    override val status: Int = HTTP_UNAUTHORIZED,
    override val code: String? = null,
  ) : Api

  public data class TemplateNotFound(
    val id: TemplateId,
    override val message: String?,
    override val status: Int = HTTP_NOT_FOUND,
    override val code: String? = null,
  ) : Api {
    override fun describe(): String = "Template $id not found${message?.let { ": $it" }.orEmpty()}"
  }

  public data class RenderNotFound(
    val id: RenderId,
    override val message: String?,
    override val status: Int = HTTP_NOT_FOUND,
    override val code: String? = null,
  ) : Api {
    override fun describe(): String = "Render $id not found${message?.let { ": $it" }.orEmpty()}"
  }

  public data class PayloadTooLarge(
    override val message: String?,
    override val status: Int = HTTP_PAYLOAD_TOO_LARGE,
    override val code: String? = null,
  ) : Api

  public data class UnsupportedTemplateFormat(
    override val message: String?,
    override val status: Int = HTTP_UNSUPPORTED_MEDIA,
    override val code: String? = null,
  ) : Api

  /** HTTP 400 or 422: rejected metadata, invalid JSON, missing field, empty template... */
  public data class BadRequest(
    override val status: Int,
    override val message: String?,
    override val code: String? = null,
  ) : Api

  /** HTTP 500 on a render endpoint. */
  public data class RenderFailed(
    override val message: String?,
    override val status: Int = HTTP_SERVER_ERROR,
    override val code: String? = null,
  ) : Api

  /** Any other non-2xx status, or a 2xx with `success: false`. */
  public data class Unexpected(
    override val status: Int,
    override val message: String?,
    val body: String?,
    override val code: String? = null,
  ) : Api

  /** A request rejected client-side before any call. */
  public data class InvalidRequest(val reason: String) : KarboneError {
    override fun describe(): String = "Invalid request: $reason"
  }

  /** Connection, timeout or I/O failure. */
  public data class Transport(val cause: Throwable) : KarboneError {
    override fun describe(): String =
      "Transport failure: ${cause.message ?: cause::class.simpleName}"
  }

  /** A response body that could not be parsed. */
  public data class Serialization(val cause: Throwable, val body: String?) : KarboneError {
    override fun describe(): String = "Cannot parse Carbone response: ${cause.message}"
  }

  /** Local template content could not be read. */
  public data class TemplateRead(val source: TemplateSource.Content, val cause: Throwable) :
    KarboneError {
    override fun describe(): String = "Cannot read template $source: ${cause.message}"
  }

  public companion object {
    public const val HTTP_UNAUTHORIZED: Int = 401
    public const val HTTP_NOT_FOUND: Int = 404
    public const val HTTP_PAYLOAD_TOO_LARGE: Int = 413
    public const val HTTP_UNSUPPORTED_MEDIA: Int = 415
    public const val HTTP_SERVER_ERROR: Int = 500
  }
}

/** Thrown by the blocking facade only. Kotlin callers get [KarboneError] through `Raise`. */
public class KarboneException(public val error: KarboneError) :
  RuntimeException(
    error.describe(),
    (error as? KarboneError.Transport)?.cause ?: (error as? KarboneError.Serialization)?.cause,
  )
