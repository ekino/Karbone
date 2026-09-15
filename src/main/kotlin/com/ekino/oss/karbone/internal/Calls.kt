/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.ekino.oss.karbone.KarboneConfig
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.internal.http.HttpRequestSpec
import com.ekino.oss.karbone.internal.http.HttpResponseSpec
import com.ekino.oss.karbone.internal.http.HttpTransport
import com.ekino.oss.karbone.model.TemplateSource
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

/** Shared plumbing: URL building, transport error mapping, template reading. */
internal class Calls(val config: KarboneConfig, private val transport: HttpTransport) {

  val json = config.json

  fun url(path: String, query: Map<String, String?> = emptyMap()): String {
    val qs =
      query
        .filterValues { it != null }
        .entries
        .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
    return config.normalizedBaseUrl + path + if (qs.isEmpty()) "" else "?$qs"
  }

  context(_: Raise<KarboneError>)
  suspend fun execute(request: HttpRequestSpec): HttpResponseSpec =
    try {
      transport.execute(request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      raise(KarboneError.Transport(e))
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      raise(KarboneError.Transport(e))
    }

  context(_: Raise<KarboneError>)
  suspend fun bytesOf(source: TemplateSource.Content): ByteArray =
    try {
      source.bytes()
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      raise(KarboneError.TemplateRead(source, e))
    } catch (e: SecurityException) {
      raise(KarboneError.TemplateRead(source, e))
    }
}
