/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.wire

import com.ekino.oss.karbone.model.ApiStatus
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateInfo
import com.ekino.oss.karbone.model.TemplateOrigin
import com.ekino.oss.karbone.model.UploadedTemplate
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Carbone's JSON envelope: `{success, data}` on success, `{success: false, error, code}` on
 * failure. `hasMore` / `nextCursor` sit next to `data` on list endpoints, `message` on asynchronous
 * renders.
 */
@Serializable
internal data class Envelope<T>(
  val success: Boolean = true,
  val data: T? = null,
  val error: String? = null,
  /** `"w117"` on errors, but a number on `/status`: kept as a primitive and read as text. */
  val code: JsonPrimitive? = null,
  val message: String? = null,
  val hasMore: Boolean? = null,
  val nextCursor: String? = null,
) {
  val codeText: String?
    get() = code?.contentOrNull
}

/** `GET /status`: fields live at the root, not under `data`. */
@Serializable
internal data class StatusDto(
  val success: Boolean = true,
  val code: Int? = null,
  val message: String? = null,
  val version: String? = null,
) {
  fun toModel(): ApiStatus = ApiStatus(success, code, message, version)
}

/**
 * `POST /template` data: versioned shape (`id`, `versionId`, ...) with a compatibility
 * `templateId`, or legacy (`templateId` only).
 */
@Serializable
internal data class UploadDto(
  val id: String? = null,
  val versionId: String? = null,
  val templateId: String? = null,
  val type: String? = null,
  val size: Long? = null,
  val createdAt: Long? = null,
  val deployedAt: Long? = null,
) {
  /** `null` when neither shape is recognisable. */
  fun toModel(): UploadedTemplate? =
    when {
      id != null && versionId != null ->
        UploadedTemplate.Versioned(
          TemplateId(id),
          TemplateId(versionId),
          type,
          size,
          createdAt?.epoch(),
          deployedAt?.epochIfSet(),
        )
      templateId != null -> UploadedTemplate.Legacy(TemplateId(templateId))
      else -> null
    }
}

/** One entry of `GET /templates`, also the `data` of `PATCH /template/{id}`. */
@Serializable
internal data class TemplateInfoDto(
  val id: String? = null,
  val versionId: String? = null,
  val name: String? = null,
  val category: String? = null,
  val comment: String? = null,
  val tags: List<String>? = null,
  val type: String? = null,
  val size: Long? = null,
  val origin: Int? = null,
  val createdAt: Long? = null,
  val deployedAt: Long? = null,
  val expireAt: Long? = null,
) {
  /** `null` when no identifier is present, not even [fallbackId]. */
  fun toModel(fallbackId: TemplateId? = null): TemplateInfo? {
    val resolvedId = id ?: versionId ?: fallbackId?.value ?: return null
    return TemplateInfo(
      id = TemplateId(resolvedId),
      versionId = versionId?.let(::TemplateId),
      name = name,
      category = category,
      comment = comment,
      tags = tags.orEmpty(),
      type = type,
      size = size,
      origin = origin?.let(TemplateOrigin::fromCode),
      createdAt = createdAt?.epoch(),
      deployedAt = deployedAt?.epochIfSet(),
      expireAt = expireAt?.epochIfSet(),
    )
  }
}

/** `POST /render/{id}` data when `download=false`. */
@Serializable internal data class RenderStartedDto(val renderId: String? = null)

/** Entries of `GET /templates/categories` and `GET /templates/tags`. */
@Serializable internal data class NamedDto(val name: String? = null)

private fun Long.epoch(): Instant = Instant.ofEpochSecond(this)

/** Carbone uses `0` for "not set" on timestamps. */
private fun Long.epochIfSet(): Instant? = takeIf { it > 0 }?.let(Instant::ofEpochSecond)
