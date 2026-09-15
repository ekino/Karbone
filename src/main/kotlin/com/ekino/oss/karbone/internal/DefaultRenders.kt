/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.Renders
import com.ekino.oss.karbone.Templates
import com.ekino.oss.karbone.internal.http.HttpBody
import com.ekino.oss.karbone.internal.http.HttpMethod
import com.ekino.oss.karbone.internal.http.HttpRequestSpec
import com.ekino.oss.karbone.internal.http.HttpResponseSpec
import com.ekino.oss.karbone.internal.wire.NotFoundHint
import com.ekino.oss.karbone.internal.wire.RenderBody
import com.ekino.oss.karbone.internal.wire.Responses
import com.ekino.oss.karbone.internal.wire.Responses.str
import com.ekino.oss.karbone.model.AsyncRenderAccepted
import com.ekino.oss.karbone.model.OutputFormat
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.RenderedDocument
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.model.Webhook
import io.github.oshai.kotlinlogging.KotlinLogging

internal class DefaultRenders(private val calls: Calls, private val templates: Templates) :
  Renders {

  context(_: Raise<KarboneError>)
  override suspend fun render(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions,
  ): RenderedDocument {
    ensure(options.batch == null) {
      KarboneError.InvalidRequest("batch rendering requires startAsync with a webhook")
    }
    val body = RenderBody.build(data, options, calls.json)
    val response = withTemplate(template) { id -> post(id, body, options, download = true) }
    val ok =
      Responses.binary(
        response,
        calls.json,
        NotFoundHint.Template(templateIdOf(template)),
        render = true,
      )
    return document(ok)
  }

  context(_: Raise<KarboneError>)
  override suspend fun start(
    template: TemplateSource,
    data: RenderData,
    options: RenderOptions,
  ): RenderId {
    ensure(options.batch == null) {
      KarboneError.InvalidRequest("batch rendering requires startAsync with a webhook")
    }
    val body = RenderBody.build(data, options, calls.json)
    val response = withTemplate(template) { id -> post(id, body, options, download = false) }
    val envelope =
      Responses.envelope(
        response,
        calls.json,
        NotFoundHint.Template(templateIdOf(template)),
        render = true,
      )
    val renderId = envelope.str("renderId")
    ensure(renderId != null) {
      KarboneError.Serialization(IllegalStateException("Missing renderId"), response.bodyAsText())
    }
    return RenderId(renderId)
  }

  context(_: Raise<KarboneError>)
  override suspend fun download(id: RenderId): RenderedDocument {
    val response = calls.execute(HttpRequestSpec(HttpMethod.GET, calls.url("/render/${id.value}")))
    return document(Responses.binary(response, calls.json, NotFoundHint.Render(id), render = true))
  }

  context(_: Raise<KarboneError>)
  override suspend fun startAsync(
    template: TemplateSource,
    data: RenderData,
    webhook: Webhook,
    options: RenderOptions,
  ): AsyncRenderAccepted {
    val headers = buildMap {
      put("carbone-webhook-url", webhook.url)
      webhook.headers.forEach { (name, value) ->
        put("carbone-webhook-header-${name.lowercase()}", value)
      }
    }
    val body = RenderBody.build(data, options, calls.json)
    val response =
      withTemplate(template) { id ->
        post(id, body, options.copy(headers = options.headers + headers), download = false)
      }
    Responses.envelope(
      response,
      calls.json,
      NotFoundHint.Template(templateIdOf(template)),
      render = true,
    )
    val message = calls.json.parseToJsonElement(response.bodyAsText()).str("message")
    return AsyncRenderAccepted(message)
  }

  /**
   * Per the v5 spec, omitting `data` skips templating. Older servers (v4 behaviour, e.g. carbone-ee
   * 5.8 on-premise) answer 422 "Missing data": in that case retry once with `data: {}`, which is
   * equivalent for a document without Carbone tags.
   */
  context(_: Raise<KarboneError>)
  override suspend fun convert(
    document: TemplateSource.Content,
    to: OutputFormat,
    options: RenderOptions,
  ): RenderedDocument {
    val content = calls.bytesOf(document)
    val effective = options.copy(convertTo = to)
    val first =
      postTemplate(
        RenderBody.build(data = null, options = effective, json = calls.json, template = content),
        effective,
      )
    val response =
      if (
        first.status == HTTP_UNPROCESSABLE && first.bodyAsText().contains("data", ignoreCase = true)
      ) {
        logger.debug {
          "Server requires a data field for conversion, retrying with an empty data-set"
        }
        postTemplate(
          RenderBody.build(
            data = RenderData.Empty,
            options = effective,
            json = calls.json,
            template = content,
          ),
          effective,
        )
      } else {
        first
      }
    return document(Responses.binary(response, calls.json, render = true))
  }

  context(_: Raise<KarboneError>)
  private suspend fun postTemplate(body: String, options: RenderOptions): HttpResponseSpec =
    calls.execute(
      HttpRequestSpec(
        HttpMethod.POST,
        calls.url("/render/template", mapOf("download" to "true")),
        options.headers,
        HttpBody.Json(body),
      )
    )

  // ---- internals -------------------------------------------------------------------------------

  private suspend fun templateIdOf(template: TemplateSource): TemplateId =
    when (template) {
      is TemplateSource.Remote -> template.id
      is TemplateSource.Content -> template.sha256()
    }

  /**
   * Hash-first flow: try with the local SHA-256, and on [KarboneError.TemplateNotFound] upload then
   * retry once. Remote ids are used as-is.
   */
  context(_: Raise<KarboneError>)
  private suspend fun withTemplate(
    template: TemplateSource,
    call: suspend (TemplateId) -> HttpResponseSpec,
  ): HttpResponseSpec {
    val id = templateIdOf(template)
    val first = call(id)
    if (template is TemplateSource.Remote || first.status != KarboneError.HTTP_NOT_FOUND)
      return first
    val content = template as TemplateSource.Content
    logger.debug {
      "Template $id unknown to Carbone, uploading ${content.fileName ?: "content"} then retrying"
    }
    val uploaded = templates.upload(content)
    ensure(uploaded.id == id) {
      KarboneError.Unexpected(
        first.status,
        "Uploaded template id ${uploaded.id} differs from local SHA-256 $id",
        null,
      )
    }
    return call(id)
  }

  context(_: Raise<KarboneError>)
  private suspend fun post(
    id: TemplateId,
    body: String,
    options: RenderOptions,
    download: Boolean,
  ): HttpResponseSpec =
    calls.execute(
      HttpRequestSpec(
        HttpMethod.POST,
        calls.url("/render/${id.value}", mapOf("download" to download.toString())),
        options.headers,
        HttpBody.Json(body),
      )
    )

  private fun document(response: HttpResponseSpec): RenderedDocument =
    RenderedDocument(
      response.body,
      Responses.fileName(response.header("Content-Disposition")),
      response.contentType,
    )

  private companion object {
    val logger = KotlinLogging.logger {}
    const val HTTP_UNPROCESSABLE = 422
  }
}
