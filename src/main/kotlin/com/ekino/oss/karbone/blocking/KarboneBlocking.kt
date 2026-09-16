/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.blocking

import arrow.core.raise.either
import com.ekino.oss.karbone.KarboneClient
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
public class KarboneBlocking internal constructor(private val karbone: KarboneClient) {

  /** Blocking view of template operations. */
  public val templates: TemplatesBlocking = TemplatesBlocking(karbone)

  /** Blocking view of render operations. */
  public val renders: RendersBlocking = RendersBlocking(karbone)

  /**
   * Delegates to [KarboneClient.status], blocking the calling thread and throwing
   * [KarboneException] on failure.
   *
   * @see com.ekino.oss.karbone.KarboneClient.status
   */
  public fun status(): ApiStatus = call { karbone.status() }

  public companion object {
    /**
     * Java entry point: `KarboneBlocking.of(client)`. Kotlin callers use [KarboneClient.blocking].
     */
    @JvmStatic public fun of(client: KarboneClient): KarboneBlocking = KarboneBlocking(client)
  }
}

/**
 * Blocking, exception-based view of any [KarboneClient]: the real [com.ekino.oss.karbone.Karbone]
 * or a test double.
 */
public fun KarboneClient.blocking(): KarboneBlocking = KarboneBlocking(this)

/**
 * Blocking, exception-based view of [com.ekino.oss.karbone.Templates]; every method throws
 * [KarboneException] on failure.
 */
public class TemplatesBlocking internal constructor(private val karbone: KarboneClient) {
  /**
   * Delegates to [com.ekino.oss.karbone.Templates.upload].
   *
   * @see com.ekino.oss.karbone.Templates.upload
   */
  @JvmOverloads
  public fun upload(
    source: TemplateSource.Content,
    options: UploadOptions = UploadOptions(),
  ): UploadedTemplate = call { karbone.templates.upload(source, options) }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.download].
   *
   * @see com.ekino.oss.karbone.Templates.download
   */
  public fun download(id: TemplateId): TemplateFile = call { karbone.templates.download(id) }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.update].
   *
   * @see com.ekino.oss.karbone.Templates.update
   */
  public fun update(id: TemplateId, patch: TemplatePatch): TemplateInfo = call {
    karbone.templates.update(id, patch)
  }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.delete].
   *
   * @see com.ekino.oss.karbone.Templates.delete
   */
  public fun delete(id: TemplateId): Unit = call { karbone.templates.delete(id) }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.list].
   *
   * @see com.ekino.oss.karbone.Templates.list
   */
  @JvmOverloads
  public fun list(query: ListTemplatesQuery = ListTemplatesQuery()): Page<TemplateInfo> = call {
    karbone.templates.list(query)
  }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.listAll], collecting the whole flow into a list.
   *
   * @see com.ekino.oss.karbone.Templates.listAll
   */
  @JvmOverloads
  public fun listAll(query: ListTemplatesQuery = ListTemplatesQuery()): List<TemplateInfo> = call {
    karbone.templates.listAll(query).toList()
  }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.categories].
   *
   * @see com.ekino.oss.karbone.Templates.categories
   */
  public fun categories(): List<String> = call { karbone.templates.categories() }

  /**
   * Delegates to [com.ekino.oss.karbone.Templates.tags].
   *
   * @see com.ekino.oss.karbone.Templates.tags
   */
  public fun tags(): List<String> = call { karbone.templates.tags() }
}

/**
 * Blocking, exception-based view of [com.ekino.oss.karbone.Renders]; every method throws
 * [KarboneException] on failure.
 */
public class RendersBlocking internal constructor(private val karbone: KarboneClient) {
  /**
   * Delegates to [com.ekino.oss.karbone.Renders.render].
   *
   * @see com.ekino.oss.karbone.Renders.render
   */
  @JvmOverloads
  public fun render(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument = call { karbone.renders.render(template, data, options) }

  /**
   * Delegates to [com.ekino.oss.karbone.Renders.start].
   *
   * @see com.ekino.oss.karbone.Renders.start
   */
  @JvmOverloads
  public fun start(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderId = call { karbone.renders.start(template, data, options) }

  /**
   * Delegates to [com.ekino.oss.karbone.Renders.download].
   *
   * @see com.ekino.oss.karbone.Renders.download
   */
  public fun download(id: RenderId): RenderedDocument = call { karbone.renders.download(id) }

  /**
   * Delegates to [com.ekino.oss.karbone.Renders.startAsync].
   *
   * @see com.ekino.oss.karbone.Renders.startAsync
   */
  @JvmOverloads
  public fun startAsync(
    template: TemplateSource,
    data: RenderData,
    webhook: Webhook,
    options: RenderOptions = RenderOptions.None,
  ): AsyncRenderAccepted = call { karbone.renders.startAsync(template, data, webhook, options) }

  /**
   * Delegates to [com.ekino.oss.karbone.Renders.convert].
   *
   * @see com.ekino.oss.karbone.Renders.convert
   */
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
