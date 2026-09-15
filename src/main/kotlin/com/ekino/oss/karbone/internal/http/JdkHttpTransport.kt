/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.http

import com.ekino.oss.karbone.KarboneConfig
import com.ekino.oss.karbone.RetryPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.util.UUID
import kotlin.time.toJavaDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await

/**
 * [HttpTransport] backed by `java.net.http.HttpClient`. Applies auth, version header, timeouts and
 * the retry policy.
 */
internal class JdkHttpTransport(private val config: KarboneConfig, client: HttpClient? = null) :
  HttpTransport {

  private val client: HttpClient =
    client
      ?: HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

  override suspend fun execute(request: HttpRequestSpec): HttpResponseSpec {
    val attempts =
      when (val policy = config.retry) {
        RetryPolicy.None -> 1
        is RetryPolicy.OnTransportError -> policy.maxAttempts
      }
    var attempt = 0
    while (true) {
      attempt++
      try {
        return send(request)
      } catch (e: IOException) {
        if (attempt >= attempts) throw e
        val backoff = (config.retry as RetryPolicy.OnTransportError).backoff * attempt
        logger.debug(e) {
          "Transport failure on ${request.method} ${request.url}, retrying in $backoff (attempt $attempt/$attempts)"
        }
        delay(backoff)
      }
    }
  }

  private suspend fun send(spec: HttpRequestSpec): HttpResponseSpec {
    val request = toJdkRequest(spec)
    logger.debug { "${spec.method} ${spec.url}" }
    val response =
      try {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
      } catch (e: HttpTimeoutException) {
        throw IOException("Request timed out after ${config.requestTimeout}", e)
      }
    logger.debug { "${spec.method} ${spec.url} -> HTTP ${response.statusCode()}" }
    return HttpResponseSpec(response.statusCode(), response.headers().map(), response.body())
  }

  private fun toJdkRequest(spec: HttpRequestSpec): HttpRequest {
    val builder =
      HttpRequest.newBuilder(URI.create(spec.url)).timeout(config.requestTimeout.toJavaDuration())
    builder.header("User-Agent", config.userAgent)
    builder.header("Accept", "*/*")
    config.token?.let { builder.header("Authorization", "Bearer $it") }
    config.apiVersion?.let { builder.header("carbone-version", it.toString()) }
    spec.headers.forEach { (name, value) -> builder.header(name, value) }

    val publisher =
      when (val body = spec.body) {
        HttpBody.Empty -> HttpRequest.BodyPublishers.noBody()
        is HttpBody.Json -> {
          builder.header("Content-Type", "application/json")
          HttpRequest.BodyPublishers.ofString(body.content)
        }
        is HttpBody.Multipart -> {
          val boundary = "----karbone-${UUID.randomUUID()}"
          builder.header("Content-Type", "multipart/form-data; boundary=$boundary")
          HttpRequest.BodyPublishers.ofByteArray(MultipartWriter.write(boundary, body))
        }
      }
    return builder.method(spec.method.name, publisher).build()
  }

  private companion object {
    val logger = KotlinLogging.logger {}
  }
}

/** Encodes a multipart/form-data body. Text fields first, file last, as Carbone requires. */
internal object MultipartWriter {
  private const val CRLF = "\r\n"

  fun write(boundary: String, body: HttpBody.Multipart): ByteArray {
    val out = ByteArrayOutputStream()
    fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
    body.fields.forEach { (name, value) ->
      text("--$boundary$CRLF")
      text("Content-Disposition: form-data; name=\"$name\"$CRLF$CRLF")
      text(value)
      text(CRLF)
    }
    val file = body.file
    text("--$boundary$CRLF")
    text(
      "Content-Disposition: form-data; name=\"${file.fieldName}\"; filename=\"${file.fileName.replace("\"", "")}\"$CRLF"
    )
    text("Content-Type: ${file.contentType}$CRLF$CRLF")
    out.write(file.content)
    text(CRLF)
    text("--$boundary--$CRLF")
    return out.toByteArray()
  }
}
