/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import arrow.core.raise.Raise
import com.ekino.oss.karbone.model.ListTemplatesQuery
import com.ekino.oss.karbone.model.Page
import com.ekino.oss.karbone.model.TemplateFile
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateInfo
import com.ekino.oss.karbone.model.TemplatePatch
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.model.UploadOptions
import com.ekino.oss.karbone.model.UploadedTemplate
import kotlinx.coroutines.flow.Flow

/** Template management: `/template` and `/templates`. */
public interface Templates {

  /** `POST /template`. Multipart upload, metadata fields before the file. */
  context(_: Raise<KarboneError>)
  public suspend fun upload(
    source: TemplateSource.Content,
    options: UploadOptions = UploadOptions(),
  ): UploadedTemplate

  /** `GET /template/{id}`. */
  context(_: Raise<KarboneError>)
  public suspend fun download(id: TemplateId): TemplateFile

  /** `PATCH /template/{id}`. Requires template management (cloud / enterprise). */
  context(_: Raise<KarboneError>)
  public suspend fun update(id: TemplateId, patch: TemplatePatch): TemplateInfo

  /** `DELETE /template/{id}`. */
  context(_: Raise<KarboneError>)
  public suspend fun delete(id: TemplateId)

  /** `GET /templates`, one page. */
  context(_: Raise<KarboneError>)
  public suspend fun list(query: ListTemplatesQuery = ListTemplatesQuery()): Page<TemplateInfo>

  /** Every template, following `nextCursor`. Errors are raised on the collecting `Raise`. */
  context(_: Raise<KarboneError>)
  public fun listAll(query: ListTemplatesQuery = ListTemplatesQuery()): Flow<TemplateInfo>

  /** `GET /templates/categories`. */
  context(_: Raise<KarboneError>)
  public suspend fun categories(): List<String>

  /** `GET /templates/tags`. */
  context(_: Raise<KarboneError>)
  public suspend fun tags(): List<String>
}
