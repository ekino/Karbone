/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon

import arrow.core.raise.Raise
import com.ekino.oss.karbon.model.AsyncRenderAccepted
import com.ekino.oss.karbon.model.OutputFormat
import com.ekino.oss.karbon.model.RenderData
import com.ekino.oss.karbon.model.RenderId
import com.ekino.oss.karbon.model.RenderOptions
import com.ekino.oss.karbon.model.RenderedDocument
import com.ekino.oss.karbon.model.TemplateSource
import com.ekino.oss.karbon.model.Webhook

/** Document generation: `/render`. */
public interface Renders {

  /**
   * Synchronous, one round-trip: `POST /render/{id}?download=true`.
   *
   * With a [TemplateSource.Content], the template is addressed by its SHA-256; when Carbone does
   * not know it yet, the content is uploaded and the render retried once (legacy, non-versioned
   * template ids only).
   */
  context(_: Raise<KarbonError>)
  public suspend fun render(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument

  context(_: Raise<KarbonError>)
  public suspend fun render(
    template: TemplateSource,
    data: RenderData,
    configure: RenderOptions.Builder.() -> Unit,
  ): RenderedDocument = render(template, data, RenderOptions.build(configure))

  /** Two-step flow: `POST /render/{id}` returning a [RenderId] to pass to [download]. */
  context(_: Raise<KarbonError>)
  public suspend fun start(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions = RenderOptions.None,
  ): RenderId

  /**
   * `GET /render/{renderId}`. No authentication. On the cloud the file is kept one hour and served
   * once.
   */
  context(_: Raise<KarbonError>)
  public suspend fun download(id: RenderId): RenderedDocument

  /**
   * Asynchronous render: Carbone calls [webhook] with `{success, data: {renderId}}` when done.
   * Required for batch.
   */
  context(_: Raise<KarbonError>)
  public suspend fun startAsync(
    template: TemplateSource,
    data: RenderData,
    webhook: Webhook,
    options: RenderOptions = RenderOptions.None,
  ): AsyncRenderAccepted

  /**
   * Pure format conversion through `POST /render/template`: no templating, tags are left untouched.
   */
  context(_: Raise<KarbonError>)
  public suspend fun convert(
    document: TemplateSource.Content,
    to: OutputFormat,
    options: RenderOptions = RenderOptions.None,
  ): RenderedDocument
}
