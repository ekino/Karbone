/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.blocking

import arrow.core.raise.either
import com.ekino.oss.karbone.Karbone
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.KarboneException
import com.ekino.oss.karbone.model.ApiStatus
import com.ekino.oss.karbone.model.AsyncRenderAccepted
import com.ekino.oss.karbone.model.ListTemplatesQuery
import com.ekino.oss.karbone.model.OutputFormat
import com.ekino.oss.karbone.model.Page
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.RenderedDocument
import com.ekino.oss.karbone.model.TemplateFile
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateInfo
import com.ekino.oss.karbone.model.TemplatePatch
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.model.UploadOptions
import com.ekino.oss.karbone.model.UploadedTemplate
import com.ekino.oss.karbone.model.Webhook
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Blocking facade: every call runs to completion on the calling thread and throws
 * [KarboneException] on failure.
 */
public class KarboneBlocking internal constructor(private val karbone: Karbone) {

  public val templates: TemplatesBlocking = TemplatesBlocking(karbone)
  public val renders: RendersBlocking = RendersBlocking(karbone)

  public fun status(): ApiStatus = call { karbone.status() }
}

public class TemplatesBlocking internal constructor(private val karbone: Karbone) {
  @JvmOverloads
  public fun upload(
    source: TemplateSource.Content,
    options: UploadOptions = UploadOptions(),
  ): UploadedTemplate = call { karbone.templates.upload(source, options) }

  public fun download(id: TemplateId): TemplateFile = call { karbone.templates.download(id) }

  public fun update(id: TemplateId, patch: TemplatePatch): TemplateInfo = call {
    karbone.templates.update(id, patch)
  }

  public fun delete(id: TemplateId): Unit = call { karbone.templates.delete(id) }

  @JvmOverloads
  public fun list(query: ListTemplatesQuery = ListTemplatesQuery()): Page<TemplateInfo> = call {
    karbone.templates.list(query)
  }

  @JvmOverloads
  public fun listAll(query: ListTemplatesQuery = ListTemplatesQuery()): List<TemplateInfo> = call {
    karbone.templates.listAll(query).toList()
  }

  public fun categories(): List<String> = call { karbone.templates.categories() }

  public fun tags(): List<String> = call { karbone.templates.tags() }
}

public class RendersBlocking internal constructor(private val karbone: Karbone) {
  @JvmOverloads
  public fun render(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument = call { karbone.renders.render(template, data, options) }

  @JvmOverloads
  public fun start(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderId = call { karbone.renders.start(template, data, options) }

  public fun download(id: RenderId): RenderedDocument = call { karbone.renders.download(id) }

  @JvmOverloads
  public fun startAsync(
    template: TemplateSource,
    data: RenderData,
    webhook: Webhook,
    options: RenderOptions = RenderOptions.None,
  ): AsyncRenderAccepted = call { karbone.renders.startAsync(template, data, webhook, options) }

  @JvmOverloads
  public fun convert(
    document: TemplateSource.Content,
    to: OutputFormat,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument = call { karbone.renders.convert(document, to, options) }
}

private inline fun <T> call(
  crossinline block: suspend arrow.core.raise.Raise<KarboneError>.() -> T
): T = runBlocking { either { block() } }.fold({ throw KarboneException(it) }, { it })
