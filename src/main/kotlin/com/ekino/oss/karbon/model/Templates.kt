/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.model

import java.time.Instant
import kotlinx.serialization.json.JsonElement

/**
 * Metadata sent with `POST /template`. All optional; versioning-related fields are ignored by
 * Community edition.
 */
public data class UploadOptions(
  /** Versioned mode: attach the upload as a new version of this template id. */
  val id: TemplateId? = null,
  val versioning: Boolean = false,
  val name: String? = null,
  val comment: String? = null,
  val category: String? = null,
  val tags: List<String> = emptyList(),
  /** Scheduled deletion. `null` means never. */
  val expireAt: Instant? = null,
  /** Deployment time used to pick the active version. `null` keeps the server default. */
  val deployedAt: Instant? = null,
  val sample: TemplateSample? = null,
) {
  public companion object {
    public const val MAX_TEXT_LENGTH: Int = 200
  }
}

/** Sample data stored with a template for Studio previews. */
public data class TemplateSample(
  val data: JsonElement? = null,
  val complement: JsonElement? = null,
  val translations: JsonElement? = null,
  val enum: JsonElement? = null,
)

/** Result of `POST /template`. Shape depends on whether versioning was requested. */
public sealed interface UploadedTemplate {
  public val id: TemplateId

  /** Versioning off: `id` is the SHA-256 of the uploaded content. */
  public data class Legacy(override val id: TemplateId) : UploadedTemplate

  /** Versioning on: stable template `id` plus the content-hash `versionId`. */
  public data class Versioned(
    override val id: TemplateId,
    val versionId: TemplateId,
    val type: String?,
    val size: Long?,
    val createdAt: Instant?,
    val deployedAt: Instant?,
  ) : UploadedTemplate
}

public enum class TemplateOrigin(public val code: Int) {
  API(0),
  STUDIO(1),
  SALESFORCE(2),
  ODOO(3),
  HUBSPOT(4);

  public companion object {
    public fun fromCode(code: Int): TemplateOrigin? = entries.firstOrNull { it.code == code }
  }
}

public data class TemplateInfo(
  val id: TemplateId,
  val versionId: TemplateId?,
  val name: String?,
  val category: String?,
  val comment: String?,
  val tags: List<String>,
  val type: String?,
  val size: Long?,
  val origin: TemplateOrigin?,
  val createdAt: Instant?,
  val deployedAt: Instant?,
  val expireAt: Instant?,
)

/** Fields accepted by `PATCH /template/{id}`. `null` means "leave unchanged". */
public data class TemplatePatch(
  val id: TemplateId? = null,
  val name: String? = null,
  val comment: String? = null,
  val category: String? = null,
  val tags: List<String>? = null,
  /** Set to [Instant.EPOCH] to cancel a scheduled deletion. */
  val expireAt: Instant? = null,
  val deployedAt: Instant? = null,
)

public data class ListTemplatesQuery(
  val id: TemplateId? = null,
  val versionId: TemplateId? = null,
  val category: String? = null,
  val origin: TemplateOrigin? = null,
  val includeVersions: Boolean = false,
  val search: String? = null,
  val limit: Int = MAX_LIMIT,
  val cursor: String? = null,
) {
  public companion object {
    public const val MAX_LIMIT: Int = 100
  }
}

public data class Page<T>(val items: List<T>, val hasMore: Boolean, val nextCursor: String?)

public class TemplateFile(
  public val content: ByteArray,
  public val fileName: String?,
  public val contentType: String?,
) {
  override fun toString(): String =
    "TemplateFile(size=${content.size}, fileName=$fileName, contentType=$contentType)"
}

public data class ApiStatus(
  val success: Boolean,
  val code: Int?,
  val message: String?,
  val version: String?,
)
