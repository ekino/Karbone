/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

import java.time.Instant
import kotlinx.serialization.json.JsonElement

/**
 * Metadata sent with `POST /template`. All optional; versioning-related fields are ignored by
 * Community edition.
 */
public data class UploadOptions(
  /** Versioned mode: attach the upload as a new version of this template id. */
  val id: TemplateId? = null,
  /** Store the upload with version history instead of replacing the template in place. */
  val versioning: Boolean = false,
  /** Display name, at most [MAX_TEXT_LENGTH] characters. */
  val name: String? = null,
  /** Free-form comment, at most [MAX_TEXT_LENGTH] characters. */
  val comment: String? = null,
  /** Category name, at most [MAX_TEXT_LENGTH] characters. */
  val category: String? = null,
  /** Free-form tags used to search and filter templates. */
  val tags: List<String> = emptyList(),
  /** Scheduled deletion. `null` means never. */
  val expireAt: Instant? = null,
  /** Deployment time used to pick the active version. `null` keeps the server default. */
  val deployedAt: Instant? = null,
  /** Sample data stored alongside the template for Studio previews. */
  val sample: TemplateSample? = null,
) {
  public companion object {
    /** Maximum length accepted for [name], [comment] and [category]. */
    public const val MAX_TEXT_LENGTH: Int = 200
  }
}

/** Sample data stored with a template for Studio previews. */
public data class TemplateSample(
  /** Sample `data` used to preview the template. */
  val data: JsonElement? = null,
  /** Sample `complement`, reachable through `{c.}` tags. */
  val complement: JsonElement? = null,
  /** Sample translations, used with `{t()}`. */
  val translations: JsonElement? = null,
  /** Sample enumerations for the `convEnum` formatter. */
  val enum: JsonElement? = null,
)

/** Result of `POST /template`. Shape depends on whether versioning was requested. */
public sealed interface UploadedTemplate {
  /** Template id to use for subsequent renders and lookups. */
  public val id: TemplateId

  /** Versioning off: `id` is the SHA-256 of the uploaded content. */
  public data class Legacy(override val id: TemplateId) : UploadedTemplate

  /** Versioning on: stable template `id` plus the content-hash `versionId`. */
  public data class Versioned(
    override val id: TemplateId,
    /** Content hash of this upload; not the plain SHA-256 of the file in versioned mode. */
    val versionId: TemplateId,
    /** File type/extension reported by Carbone, when available. */
    val type: String?,
    /** Content size in bytes, when reported by Carbone. */
    val size: Long?,
    /** Upload timestamp, when reported by Carbone. */
    val createdAt: Instant?,
    /** Deployment timestamp used to pick the active version, when reported by Carbone. */
    val deployedAt: Instant?,
  ) : UploadedTemplate
}

/** How a template was created; mirrors `GET /templates` `origin`. */
public enum class TemplateOrigin(public val code: Int) {
  API(0),
  STUDIO(1),
  SALESFORCE(2),
  ODOO(3),
  HUBSPOT(4);

  public companion object {
    /** Maps a wire `origin` code to a [TemplateOrigin], or `null` if unrecognized. */
    public fun fromCode(code: Int): TemplateOrigin? = entries.firstOrNull { it.code == code }
  }
}

/** One entry of `GET /templates`. */
public data class TemplateInfo(
  val id: TemplateId,
  /** Content hash of the currently deployed version, when versioning is used. */
  val versionId: TemplateId?,
  val name: String?,
  val category: String?,
  val comment: String?,
  val tags: List<String>,
  /** File type/extension reported by Carbone, when available. */
  val type: String?,
  /** Content size in bytes, when reported by Carbone. */
  val size: Long?,
  val origin: TemplateOrigin?,
  /** Upload timestamp, when reported by Carbone. */
  val createdAt: Instant?,
  /** Deployment timestamp used to pick the active version, when reported by Carbone. */
  val deployedAt: Instant?,
  /** Scheduled deletion, when one is set. */
  val expireAt: Instant?,
)

/** Fields accepted by `PATCH /template/{id}`. `null` means "leave unchanged". */
public data class TemplatePatch(
  /** Moves the template to a different (or new) template id. */
  val id: TemplateId? = null,
  val name: String? = null,
  val comment: String? = null,
  val category: String? = null,
  val tags: List<String>? = null,
  /** Set to [Instant.EPOCH] to cancel a scheduled deletion. */
  val expireAt: Instant? = null,
  val deployedAt: Instant? = null,
)

/** Filters and pagination for `GET /templates`. */
public data class ListTemplatesQuery(
  val id: TemplateId? = null,
  val versionId: TemplateId? = null,
  val category: String? = null,
  val origin: TemplateOrigin? = null,
  /** Include every version of a template, not just the deployed one. */
  val includeVersions: Boolean = false,
  /** Free-text search across name, comment and tags. */
  val search: String? = null,
  /** Page size, 1..[MAX_LIMIT]. */
  val limit: Int = MAX_LIMIT,
  /** Opaque cursor from a previous [Page.nextCursor], to fetch the next page. */
  val cursor: String? = null,
) {
  public companion object {
    /** Maximum accepted value for [limit]. */
    public const val MAX_LIMIT: Int = 100
  }
}

/** One page of a paginated Carbone listing. */
public data class Page<T>(
  val items: List<T>,
  /** `true` if a further page can be fetched using [nextCursor]. */
  val hasMore: Boolean,
  /** Cursor for the next page; `null` when [hasMore] is `false`. */
  val nextCursor: String?,
)

/** A downloaded template document. */
public class TemplateFile(
  /** Raw bytes of the template file as stored by Carbone. */
  public val content: ByteArray,
  /** File name from `Content-Disposition`, when Carbone provided one. */
  public val fileName: String?,
  /** MIME type from the response, when Carbone provided one. */
  public val contentType: String?,
) {
  override fun toString(): String =
    "TemplateFile(size=${content.size}, fileName=$fileName, contentType=$contentType)"
}

/** Mirrors `GET /status`. */
public data class ApiStatus(
  val success: Boolean,
  /** HTTP-like status code reported by Carbone, when present. */
  val code: Int?,
  /** Human-readable status message, when present. */
  val message: String?,
  /** Carbone server version, when present. */
  val version: String?,
)
