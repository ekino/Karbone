/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.blocking

import arrow.core.raise.either
import com.ekino.oss.karbon.Karbon
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.KarbonException
import com.ekino.oss.karbon.model.ApiStatus
import com.ekino.oss.karbon.model.AsyncRenderAccepted
import com.ekino.oss.karbon.model.ListTemplatesQuery
import com.ekino.oss.karbon.model.OutputFormat
import com.ekino.oss.karbon.model.Page
import com.ekino.oss.karbon.model.RenderData
import com.ekino.oss.karbon.model.RenderId
import com.ekino.oss.karbon.model.RenderOptions
import com.ekino.oss.karbon.model.RenderedDocument
import com.ekino.oss.karbon.model.TemplateFile
import com.ekino.oss.karbon.model.TemplateId
import com.ekino.oss.karbon.model.TemplateInfo
import com.ekino.oss.karbon.model.TemplatePatch
import com.ekino.oss.karbon.model.TemplateSource
import com.ekino.oss.karbon.model.UploadOptions
import com.ekino.oss.karbon.model.UploadedTemplate
import com.ekino.oss.karbon.model.Webhook
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Blocking facade: every call runs to completion on the calling thread and throws [KarbonException]
 * on failure.
 */
public class KarbonBlocking internal constructor(private val karbon: Karbon) {

  public val templates: TemplatesBlocking = TemplatesBlocking(karbon)
  public val renders: RendersBlocking = RendersBlocking(karbon)

  public fun status(): ApiStatus = call { karbon.status() }
}

public class TemplatesBlocking internal constructor(private val karbon: Karbon) {
  @JvmOverloads
  public fun upload(
    source: TemplateSource.Content,
    options: UploadOptions = UploadOptions(),
  ): UploadedTemplate = call { karbon.templates.upload(source, options) }

  public fun download(id: TemplateId): TemplateFile = call { karbon.templates.download(id) }

  public fun update(id: TemplateId, patch: TemplatePatch): TemplateInfo = call {
    karbon.templates.update(id, patch)
  }

  public fun delete(id: TemplateId): Unit = call { karbon.templates.delete(id) }

  @JvmOverloads
  public fun list(query: ListTemplatesQuery = ListTemplatesQuery()): Page<TemplateInfo> = call {
    karbon.templates.list(query)
  }

  @JvmOverloads
  public fun listAll(query: ListTemplatesQuery = ListTemplatesQuery()): List<TemplateInfo> = call {
    karbon.templates.listAll(query).toList()
  }

  public fun categories(): List<String> = call { karbon.templates.categories() }

  public fun tags(): List<String> = call { karbon.templates.tags() }
}

public class RendersBlocking internal constructor(private val karbon: Karbon) {
  @JvmOverloads
  public fun render(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument = call { karbon.renders.render(template, data, options) }

  @JvmOverloads
  public fun start(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderId = call { karbon.renders.start(template, data, options) }

  public fun download(id: RenderId): RenderedDocument = call { karbon.renders.download(id) }

  @JvmOverloads
  public fun startAsync(
    template: TemplateSource,
    data: RenderData,
    webhook: Webhook,
    options: RenderOptions = RenderOptions.None,
  ): AsyncRenderAccepted = call { karbon.renders.startAsync(template, data, webhook, options) }

  @JvmOverloads
  public fun convert(
    document: TemplateSource.Content,
    to: OutputFormat,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument = call { karbon.renders.convert(document, to, options) }
}

private inline fun <T> call(
  crossinline block: suspend arrow.core.raise.Raise<KarbonError>.() -> T
): T = runBlocking { either { block() } }.fold({ throw KarbonException(it) }, { it })
