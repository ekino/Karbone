/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.internal

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.ekino.oss.karbon.KarbonConfig
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.internal.http.HttpRequestSpec
import com.ekino.oss.karbon.internal.http.HttpResponseSpec
import com.ekino.oss.karbon.internal.http.HttpTransport
import com.ekino.oss.karbon.model.TemplateSource
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

/** Shared plumbing: URL building, transport error mapping, template reading. */
internal class Calls(val config: KarbonConfig, private val transport: HttpTransport) {

  val json = config.json

  fun url(path: String, query: Map<String, String?> = emptyMap()): String {
    val qs =
      query
        .filterValues { it != null }
        .entries
        .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
    return config.normalizedBaseUrl + path + if (qs.isEmpty()) "" else "?$qs"
  }

  context(_: Raise<KarbonError>)
  suspend fun execute(request: HttpRequestSpec): HttpResponseSpec =
    try {
      transport.execute(request)
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      raise(KarbonError.Transport(e))
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      raise(KarbonError.Transport(e))
    }

  context(_: Raise<KarbonError>)
  suspend fun bytesOf(source: TemplateSource.Content): ByteArray =
    try {
      source.bytes()
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      raise(KarbonError.TemplateRead(source, e))
    } catch (e: SecurityException) {
      raise(KarbonError.TemplateRead(source, e))
    }
}
