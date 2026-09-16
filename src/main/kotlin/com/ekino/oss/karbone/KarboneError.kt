/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateSource

/** Every way a Karbone call can fail, as a value. */
public sealed interface KarboneError {

  /** Human-readable summary of this error, used as the message of [KarboneException]. */
  public fun describe(): String

  /**
   * Errors reported by the Carbone API. [message] is the raw `error` text from the response, never
   * rewritten.
   */
  public sealed interface Api : KarboneError {
    /** HTTP status returned by Carbone. */
    public val status: Int

    /** Raw `error` text from the Carbone response, if any, never rewritten. */
    public val message: String?

    /** Carbone error code when provided, e.g. `w117`. */
    public val code: String?

    override fun describe(): String =
      "Carbone API error HTTP $status${code?.let { " [$it]" }.orEmpty()}${message?.let { ": $it" }.orEmpty()}"
  }

  /**
   * HTTP 401, or a malformed bearer token (carbone-ee answers HTTP 500 "Invalid JSON Web Token").
   */
  public data class Unauthorized(
    override val message: String?,
    override val status: Int = HTTP_UNAUTHORIZED,
    override val code: String? = null,
  ) : Api

  /** HTTP 404 when the requested template id is unknown to Carbone. */
  public data class TemplateNotFound(
    /** Id of the template that was not found. */
    val id: TemplateId,
    override val message: String?,
    override val status: Int = HTTP_NOT_FOUND,
    override val code: String? = null,
  ) : Api {
    override fun describe(): String = "Template $id not found${message?.let { ": $it" }.orEmpty()}"
  }

  /** HTTP 404 when the requested render id is unknown, expired, or already downloaded once. */
  public data class RenderNotFound(
    /** Id of the render that was not found. */
    val id: RenderId,
    override val message: String?,
    override val status: Int = HTTP_NOT_FOUND,
    override val code: String? = null,
  ) : Api {
    override fun describe(): String = "Render $id not found${message?.let { ": $it" }.orEmpty()}"
  }

  /** HTTP 413: the template or document sent exceeds Carbone's size limit. */
  public data class PayloadTooLarge(
    override val message: String?,
    override val status: Int = HTTP_PAYLOAD_TOO_LARGE,
    override val code: String? = null,
  ) : Api

  /** HTTP 415: Carbone cannot process the template's file format. */
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
    /** Raw response body, if it could be read, for diagnostics. */
    val body: String?,
    override val code: String? = null,
  ) : Api

  /** A request rejected client-side before any call. */
  public data class InvalidRequest(
    /** Why the request was rejected. */
    val reason: String
  ) : KarboneError {
    override fun describe(): String = "Invalid request: $reason"
  }

  /** Connection, timeout or I/O failure. */
  public data class Transport(
    /** The underlying `IOException` or similar, kept as [KarboneException]'s cause. */
    val cause: Throwable
  ) : KarboneError {
    override fun describe(): String =
      "Transport failure: ${cause.message ?: cause::class.simpleName}"
  }

  /** A response body that could not be parsed. */
  public data class Serialization(
    /** The underlying parsing exception, kept as [KarboneException]'s cause. */
    val cause: Throwable,
    /** Raw response body, if it could be read, for diagnostics. */
    val body: String?,
  ) : KarboneError {
    override fun describe(): String = "Cannot parse Carbone response: ${cause.message}"
  }

  /** Local template content could not be read. */
  public data class TemplateRead(
    /** The template source that failed to read. */
    val source: TemplateSource.Content,
    /** The underlying I/O exception. */
    val cause: Throwable,
  ) : KarboneError {
    override fun describe(): String = "Cannot read template $source: ${cause.message}"
  }

  public companion object {
    /** HTTP status mapped to [Unauthorized]. */
    public const val HTTP_UNAUTHORIZED: Int = 401

    /** HTTP status mapped to [TemplateNotFound] or [RenderNotFound]. */
    public const val HTTP_NOT_FOUND: Int = 404

    /** HTTP status mapped to [PayloadTooLarge]. */
    public const val HTTP_PAYLOAD_TOO_LARGE: Int = 413

    /** HTTP status mapped to [UnsupportedTemplateFormat]. */
    public const val HTTP_UNSUPPORTED_MEDIA: Int = 415

    /** HTTP status mapped to [RenderFailed]. */
    public const val HTTP_SERVER_ERROR: Int = 500
  }
}

/** Thrown by the blocking facade only. Kotlin callers get [KarboneError] through `Raise`. */
public class KarboneException(
  /** The [KarboneError] value that caused this exception. */
  public val error: KarboneError
) :
  RuntimeException(
    error.describe(),
    (error as? KarboneError.Transport)?.cause ?: (error as? KarboneError.Serialization)?.cause,
  )
