/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.testing

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import com.ekino.oss.karbon.KarbonClient
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.Renders
import com.ekino.oss.karbon.Templates
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
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * In-memory [KarbonClient] for consumer tests. No network, no Carbone.
 *
 * - Templates are stored by their SHA-256, exactly like Carbone's legacy ids; the hash-first render
 *   flow works as with the real client.
 * - [render] produces bytes through [renderer]; the default writes a small JSON description of the
 *   request, with a filename derived from `reportName` / `convertTo`.
 * - Every call is recorded in [calls]; [failNextWith] injects one error on the next call.
 */
public class FakeKarbon(
  public var status: ApiStatus = ApiStatus(true, HTTP_OK, "OK", "fake"),
  public var renderer: Renderer = Renderer.Default,
) : KarbonClient {

  /** Produces the bytes of a rendered document. */
  public fun interface Renderer {
    public fun render(request: RenderRequest): ByteArray

    public companion object {
      @JvmField
      public val Default: Renderer = Renderer { request ->
        buildJsonObject {
            put("templateId", request.templateId.value)
            put("data", request.data ?: JsonNull)
            put("convertTo", request.options.convertTo?.formatName ?: "none")
          }
          .toString()
          .toByteArray()
      }
    }
  }

  public data class RenderRequest(
    val templateId: TemplateId,
    val template: ByteArray?,
    val data: JsonElement?,
    val options: RenderOptions,
  )

  public data class StoredTemplate(
    val id: TemplateId,
    val content: ByteArray,
    val options: UploadOptions,
    val createdAt: Instant,
  )

  public sealed interface Call {
    public data class Upload(val id: TemplateId, val options: UploadOptions) : Call

    public data class Download(val id: TemplateId) : Call

    public data class Update(val id: TemplateId, val patch: TemplatePatch) : Call

    public data class Delete(val id: TemplateId) : Call

    public data class List(val query: ListTemplatesQuery) : Call

    public data class Render(val request: RenderRequest, val webhook: Webhook?) : Call

    public data class DownloadRender(val id: RenderId) : Call

    public data object Status : Call
  }

  private val store = ConcurrentHashMap<TemplateId, StoredTemplate>()
  private val pendingRenders = ConcurrentHashMap<RenderId, RenderedDocument>()
  private val renderCounter = AtomicInteger()
  private val nextError = AtomicReference<KarbonError?>(null)
  private val json = Json

  /** Every call made on this fake, in order. */
  public val calls: MutableList<Call> = CopyOnWriteArrayList()

  /** Templates currently stored. */
  public val storedTemplates: Map<TemplateId, StoredTemplate>
    get() = store.toMap()

  /** Rendered documents recorded so far (one per render / start / startAsync). */
  public val rendered: MutableList<RenderedDocument> = CopyOnWriteArrayList()

  /** The next call, whatever it is, raises [error] instead of running. */
  public fun failNextWith(error: KarbonError) {
    nextError.set(error)
  }

  /** Pre-load a template so [TemplateSource.Remote] ids resolve. Returns its id (SHA-256). */
  public fun addTemplate(content: ByteArray, options: UploadOptions = UploadOptions()): TemplateId {
    val id = TemplateId(TemplateSource.sha256Hex(content))
    store[id] = StoredTemplate(id, content, options, Instant.now())
    return id
  }

  public fun reset() {
    store.clear()
    pendingRenders.clear()
    calls.clear()
    rendered.clear()
    nextError.set(null)
  }

  override val templates: Templates = FakeTemplates()
  override val renders: Renders = FakeRenders()

  context(_: Raise<KarbonError>)
  override suspend fun status(): ApiStatus {
    record(Call.Status)
    return status
  }

  context(_: Raise<KarbonError>)
  private fun record(call: Call) {
    calls += call
    nextError.getAndSet(null)?.let { raise(it) }
  }

  private inner class FakeTemplates : Templates {
    context(_: Raise<KarbonError>)
    override suspend fun upload(
      source: TemplateSource.Content,
      options: UploadOptions,
    ): UploadedTemplate {
      val content = source.bytes()
      val id = TemplateId(TemplateSource.sha256Hex(content))
      record(Call.Upload(id, options))
      ensure(content.isNotEmpty()) {
        KarbonError.BadRequest(HTTP_UNPROCESSABLE, "Template file is empty")
      }
      store[id] = StoredTemplate(id, content, options, Instant.now())
      return if (options.versioning) {
        UploadedTemplate.Versioned(
          id,
          id,
          source.fileName?.substringAfterLast('.'),
          content.size.toLong(),
          Instant.now(),
          options.deployedAt,
        )
      } else {
        UploadedTemplate.Legacy(id)
      }
    }

    context(_: Raise<KarbonError>)
    override suspend fun download(id: TemplateId): TemplateFile {
      record(Call.Download(id))
      val stored =
        ensureNotNull(store[id]) { KarbonError.TemplateNotFound(id, "Template not found") }
      return TemplateFile(stored.content, stored.options.name, "application/octet-stream")
    }

    context(_: Raise<KarbonError>)
    override suspend fun update(id: TemplateId, patch: TemplatePatch): TemplateInfo {
      record(Call.Update(id, patch))
      val stored =
        ensureNotNull(store[id]) { KarbonError.TemplateNotFound(id, "Template not found") }
      val updated =
        stored.copy(
          options =
            stored.options.copy(
              name = patch.name ?: stored.options.name,
              comment = patch.comment ?: stored.options.comment,
              category = patch.category ?: stored.options.category,
              tags = patch.tags ?: stored.options.tags,
              expireAt = patch.expireAt ?: stored.options.expireAt,
              deployedAt = patch.deployedAt ?: stored.options.deployedAt,
            )
        )
      store[id] = updated
      return updated.info()
    }

    context(_: Raise<KarbonError>)
    override suspend fun delete(id: TemplateId) {
      record(Call.Delete(id))
      ensureNotNull(store.remove(id)) { KarbonError.TemplateNotFound(id, "Template not found") }
    }

    context(_: Raise<KarbonError>)
    override suspend fun list(query: ListTemplatesQuery): Page<TemplateInfo> {
      record(Call.List(query))
      val all =
        store.values
          .filter { query.id == null || it.id == query.id }
          .filter { query.category == null || it.options.category == query.category }
          .filter {
            query.search == null ||
              it.options.name?.contains(query.search, ignoreCase = true) == true ||
              it.id.value == query.search
          }
          .sortedBy { it.createdAt }
          .map { it.info() }
      val offset = query.cursor?.toIntOrNull() ?: 0
      val items = all.drop(offset).take(query.limit)
      val next = offset + items.size
      return Page(
        items,
        hasMore = next < all.size,
        nextCursor = if (next < all.size) next.toString() else null,
      )
    }

    context(_: Raise<KarbonError>)
    override fun listAll(query: ListTemplatesQuery): Flow<TemplateInfo> {
      val raise = contextOf<Raise<KarbonError>>()
      return with(raise) { store.values.sortedBy { it.createdAt }.map { it.info() } }.asFlow()
    }

    context(_: Raise<KarbonError>)
    override suspend fun categories(): List<String> =
      store.values.mapNotNull { it.options.category }.distinct().sorted()

    context(_: Raise<KarbonError>)
    override suspend fun tags(): List<String> =
      store.values.flatMap { it.options.tags }.distinct().sorted()
  }

  private inner class FakeRenders : Renders {
    context(_: Raise<KarbonError>)
    override suspend fun render(
      template: TemplateSource,
      data: RenderData,
      options: RenderOptions,
    ): RenderedDocument = produce(template, data, options, webhook = null)

    context(_: Raise<KarbonError>)
    override suspend fun start(
      template: TemplateSource,
      data: RenderData,
      options: RenderOptions,
    ): RenderId {
      val document = produce(template, data, options, webhook = null)
      val id = RenderId("fake-render-${renderCounter.incrementAndGet()}")
      pendingRenders[id] = document
      return id
    }

    context(_: Raise<KarbonError>)
    override suspend fun download(id: RenderId): RenderedDocument {
      record(Call.DownloadRender(id))
      return ensureNotNull(pendingRenders.remove(id)) {
        KarbonError.RenderNotFound(id, "Render not found")
      }
    }

    context(_: Raise<KarbonError>)
    override suspend fun startAsync(
      template: TemplateSource,
      data: RenderData,
      webhook: Webhook,
      options: RenderOptions,
    ): AsyncRenderAccepted {
      val document = produce(template, data, options, webhook)
      pendingRenders[RenderId("fake-render-${renderCounter.incrementAndGet()}")] = document
      return AsyncRenderAccepted("A render ID will be sent to your callback URL")
    }

    context(_: Raise<KarbonError>)
    override suspend fun convert(
      document: TemplateSource.Content,
      to: OutputFormat,
      options: RenderOptions,
    ): RenderedDocument {
      val content = document.bytes()
      val request =
        RenderRequest(
          TemplateId(TemplateSource.sha256Hex(content)),
          content,
          null,
          options.copy(convertTo = to),
        )
      record(Call.Render(request, null))
      return emit(request)
    }

    context(_: Raise<KarbonError>)
    private suspend fun produce(
      template: TemplateSource,
      data: RenderData,
      options: RenderOptions,
      webhook: Webhook?,
    ): RenderedDocument {
      val (id, content) =
        when (template) {
          is TemplateSource.Remote -> template.id to store[template.id]?.content
          is TemplateSource.Content -> {
            val bytes = template.bytes()
            val id = TemplateId(TemplateSource.sha256Hex(bytes))
            store.putIfAbsent(
              id,
              StoredTemplate(id, bytes, UploadOptions(name = template.fileName), Instant.now()),
            )
            id to bytes
          }
        }
      val request = RenderRequest(id, content, dataElement(data), options)
      record(Call.Render(request, webhook))
      ensureNotNull(content) { KarbonError.TemplateNotFound(id, "Template not found") }
      return emit(request)
    }

    private fun emit(request: RenderRequest): RenderedDocument {
      val extension = request.options.convertTo?.formatName ?: "bin"
      val name = request.options.reportName ?: "report"
      val document =
        RenderedDocument(renderer.render(request), "$name.$extension", contentTypeOf(extension))
      rendered += document
      return document
    }

    context(_: Raise<KarbonError>)
    private fun dataElement(data: RenderData): JsonElement =
      when (data) {
        is RenderData.Element -> data.json
        is RenderData.Raw ->
          runCatching { json.parseToJsonElement(data.json) }
            .getOrElse {
              raise(KarbonError.InvalidRequest("data is not valid JSON: ${it.message}"))
            }
        is RenderData.Value<*> -> encode(data)
      }

    private fun <T> encode(data: RenderData.Value<T>): JsonElement =
      json.encodeToJsonElement(data.serializer, data.value)
  }

  private fun StoredTemplate.info(): TemplateInfo =
    TemplateInfo(
      id = id,
      versionId = null,
      name = options.name,
      category = options.category,
      comment = options.comment,
      tags = options.tags,
      type = null,
      size = content.size.toLong(),
      origin = null,
      createdAt = createdAt,
      deployedAt = options.deployedAt,
      expireAt = options.expireAt,
    )

  private companion object {
    const val HTTP_OK = 200
    const val HTTP_UNPROCESSABLE = 422

    fun contentTypeOf(extension: String): String =
      when (extension) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "html" -> "text/html"
        "csv" -> "text/csv"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg" -> "image/jpeg"
        else -> "application/octet-stream"
      }
  }
}
