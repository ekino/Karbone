/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.internal

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.Templates
import com.ekino.oss.karbon.internal.http.FilePart
import com.ekino.oss.karbon.internal.http.HttpBody
import com.ekino.oss.karbon.internal.http.HttpMethod
import com.ekino.oss.karbon.internal.http.HttpRequestSpec
import com.ekino.oss.karbon.internal.wire.NotFoundHint
import com.ekino.oss.karbon.internal.wire.Responses
import com.ekino.oss.karbon.internal.wire.Responses.bool
import com.ekino.oss.karbon.internal.wire.Responses.int
import com.ekino.oss.karbon.internal.wire.Responses.long
import com.ekino.oss.karbon.internal.wire.Responses.objects
import com.ekino.oss.karbon.internal.wire.Responses.str
import com.ekino.oss.karbon.internal.wire.Responses.strings
import com.ekino.oss.karbon.model.ListTemplatesQuery
import com.ekino.oss.karbon.model.Page
import com.ekino.oss.karbon.model.TemplateFile
import com.ekino.oss.karbon.model.TemplateId
import com.ekino.oss.karbon.model.TemplateInfo
import com.ekino.oss.karbon.model.TemplateOrigin
import com.ekino.oss.karbon.model.TemplatePatch
import com.ekino.oss.karbon.model.TemplateSource
import com.ekino.oss.karbon.model.UploadOptions
import com.ekino.oss.karbon.model.UploadedTemplate
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class DefaultTemplates(private val calls: Calls) : Templates {

  context(_: Raise<KarbonError>)
  override suspend fun upload(
    source: TemplateSource.Content,
    options: UploadOptions,
  ): UploadedTemplate {
    validate(options)
    val content = calls.bytesOf(source)
    ensure(content.isNotEmpty()) { KarbonError.InvalidRequest("template content is empty") }
    val fields = buildList {
      options.id?.let { add("id" to it.value) }
      if (options.versioning) add("versioning" to "true")
      options.name?.let { add("name" to it) }
      options.comment?.let { add("comment" to it) }
      options.category?.let { add("category" to it) }
      if (options.tags.isNotEmpty())
        add(
          "tags" to
            calls.json.encodeToString(
              JsonArray.serializer(),
              JsonArray(options.tags.map(::JsonPrimitive)),
            )
        )
      options.expireAt?.let { add("expireAt" to it.epochSecond.toString()) }
      options.deployedAt?.let { add("deployedAt" to it.epochSecond.toString()) }
      options.sample?.let { s ->
        val sample = buildJsonObject {
          s.data?.let { put("data", it) }
          s.complement?.let { put("complement", it) }
          s.translations?.let { put("translations", it) }
          s.enum?.let { put("enum", it) }
        }
        add("sample" to calls.json.encodeToString(JsonObject.serializer(), sample))
      }
    }
    val file =
      FilePart("template", source.fileName ?: "template", "application/octet-stream", content)
    val response =
      calls.execute(
        HttpRequestSpec(
          HttpMethod.POST,
          calls.url("/template"),
          body = HttpBody.Multipart(fields, file),
        )
      )
    val data = Responses.envelope(response, calls.json)
    return parseUploaded(data, response.bodyAsText())
  }

  context(_: Raise<KarbonError>)
  private fun parseUploaded(data: JsonElement, body: String): UploadedTemplate {
    // Versioned responses also carry a backward-compatible `templateId`; prefer the versioned shape
    // when present.
    val id = data.str("id")
    val versionId = data.str("versionId")
    if (id != null && versionId != null) {
      return UploadedTemplate.Versioned(
        id = TemplateId(id),
        versionId = TemplateId(versionId),
        type = data.str("type"),
        size = data.long("size"),
        createdAt = data.long("createdAt")?.let(Instant::ofEpochSecond),
        deployedAt = data.long("deployedAt")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
      )
    }
    val legacy = data.str("templateId")
    ensure(legacy != null) {
      KarbonError.Serialization(IllegalStateException("Missing templateId or id/versionId"), body)
    }
    return UploadedTemplate.Legacy(TemplateId(legacy))
  }

  context(_: Raise<KarbonError>)
  override suspend fun download(id: TemplateId): TemplateFile {
    val response =
      calls.execute(HttpRequestSpec(HttpMethod.GET, calls.url("/template/${id.value}")))
    val ok = Responses.binary(response, calls.json, NotFoundHint.Template(id))
    return TemplateFile(
      ok.body,
      Responses.fileName(ok.header("Content-Disposition")),
      ok.contentType,
    )
  }

  context(_: Raise<KarbonError>)
  override suspend fun update(id: TemplateId, patch: TemplatePatch): TemplateInfo {
    val body = buildJsonObject {
      patch.id?.let { put("id", it.value) }
      patch.name?.let { put("name", it) }
      patch.comment?.let { put("comment", it) }
      patch.category?.let { put("category", it) }
      patch.tags?.let { tags ->
        putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
      }
      patch.expireAt?.let { put("expireAt", it.epochSecond) }
      patch.deployedAt?.let { put("deployedAt", it.epochSecond) }
    }
    ensure(body.isNotEmpty()) { KarbonError.InvalidRequest("empty template patch") }
    val request =
      HttpRequestSpec(
        HttpMethod.PATCH,
        calls.url("/template/${id.value}"),
        body = HttpBody.Json(calls.json.encodeToString(JsonObject.serializer(), body)),
      )
    val data = Responses.envelope(calls.execute(request), calls.json, NotFoundHint.Template(id))
    return templateInfo(data, fallbackId = id)
  }

  context(_: Raise<KarbonError>)
  override suspend fun delete(id: TemplateId) {
    val response =
      calls.execute(HttpRequestSpec(HttpMethod.DELETE, calls.url("/template/${id.value}")))
    Responses.envelope(response, calls.json, NotFoundHint.Template(id))
  }

  context(_: Raise<KarbonError>)
  override suspend fun list(query: ListTemplatesQuery): Page<TemplateInfo> {
    ensure(query.limit in 1..ListTemplatesQuery.MAX_LIMIT) {
      KarbonError.InvalidRequest("limit must be within 1..${ListTemplatesQuery.MAX_LIMIT}")
    }
    val url =
      calls.url(
        "/templates",
        mapOf(
          "id" to query.id?.value,
          "versionId" to query.versionId?.value,
          "category" to query.category,
          "origin" to query.origin?.code?.toString(),
          "includeVersions" to query.includeVersions.takeIf { it }?.toString(),
          "search" to query.search,
          "limit" to query.limit.toString(),
          "cursor" to query.cursor,
        ),
      )
    val response = calls.execute(HttpRequestSpec(HttpMethod.GET, url))
    val root = Responses.envelope(response, calls.json)
    // `hasMore` / `nextCursor` sit next to `data` at the root; re-read them from the body.
    val rootObject = calls.json.parseToJsonElement(response.bodyAsText())
    return Page(
      items = root.objects().map { templateInfo(it) },
      hasMore = rootObject.bool("hasMore") ?: false,
      nextCursor = rootObject.str("nextCursor"),
    )
  }

  context(_: Raise<KarbonError>)
  override fun listAll(query: ListTemplatesQuery): Flow<TemplateInfo> {
    val raise = contextOf<Raise<KarbonError>>()
    return flow {
      var cursor: String? = query.cursor
      do {
        val page = with(raise) { list(query.copy(cursor = cursor)) }
        page.items.forEach { emit(it) }
        cursor = page.nextCursor
      } while (page.hasMore && cursor != null)
    }
  }

  context(_: Raise<KarbonError>)
  override suspend fun categories(): List<String> = names("/templates/categories")

  context(_: Raise<KarbonError>)
  override suspend fun tags(): List<String> = names("/templates/tags")

  context(_: Raise<KarbonError>)
  private suspend fun names(path: String): List<String> {
    val data =
      Responses.envelope(
        calls.execute(HttpRequestSpec(HttpMethod.GET, calls.url(path))),
        calls.json,
      )
    return data.objects().mapNotNull { it.str("name") }
  }

  context(_: Raise<KarbonError>)
  private fun validate(options: UploadOptions) {
    listOf("name" to options.name, "comment" to options.comment, "category" to options.category)
      .forEach { (field, value) ->
        ensure(value == null || value.length <= UploadOptions.MAX_TEXT_LENGTH) {
          KarbonError.InvalidRequest(
            "$field must be at most ${UploadOptions.MAX_TEXT_LENGTH} characters"
          )
        }
      }
  }

  context(_: Raise<KarbonError>)
  private fun templateInfo(o: JsonElement, fallbackId: TemplateId? = null): TemplateInfo {
    val id = o.str("id") ?: o.str("versionId") ?: fallbackId?.value
    ensure(id != null) {
      KarbonError.Serialization(
        IllegalStateException("Template entry without id or versionId"),
        o.toString(),
      )
    }
    return TemplateInfo(
      id = TemplateId(id),
      versionId = o.str("versionId")?.let(::TemplateId),
      name = o.str("name"),
      category = o.str("category"),
      comment = o.str("comment"),
      tags = o.strings("tags"),
      type = o.str("type"),
      size = o.long("size"),
      origin = o.int("origin")?.let(TemplateOrigin::fromCode),
      createdAt = o.long("createdAt")?.let(Instant::ofEpochSecond),
      deployedAt = o.long("deployedAt")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
      expireAt = o.long("expireAt")?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
    )
  }
}
