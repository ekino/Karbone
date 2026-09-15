/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon

import arrow.core.raise.Raise
import com.ekino.oss.karbon.blocking.KarbonBlocking
import com.ekino.oss.karbon.internal.Calls
import com.ekino.oss.karbon.internal.DefaultRenders
import com.ekino.oss.karbon.internal.DefaultTemplates
import com.ekino.oss.karbon.internal.http.HttpMethod
import com.ekino.oss.karbon.internal.http.HttpRequestSpec
import com.ekino.oss.karbon.internal.http.HttpTransport
import com.ekino.oss.karbon.internal.http.JdkHttpTransport
import com.ekino.oss.karbon.internal.wire.Responses
import com.ekino.oss.karbon.internal.wire.Responses.bool
import com.ekino.oss.karbon.internal.wire.Responses.int
import com.ekino.oss.karbon.internal.wire.Responses.str
import com.ekino.oss.karbon.model.ApiStatus

/**
 * Entry point of the Karbon SDK, a Kotlin client for the Carbone document generation API.
 *
 * Build one with [cloud] or [onPremise]; instances are immutable and thread-safe.
 */
public class Karbon
internal constructor(public val config: KarbonConfig, transport: HttpTransport) {

  private val calls = Calls(config, transport)

  public val templates: Templates = DefaultTemplates(calls)
  public val renders: Renders = DefaultRenders(calls, templates)

  /** `GET /status`. Works without authentication. */
  context(_: Raise<KarbonError>)
  public suspend fun status(): ApiStatus {
    val response = calls.execute(HttpRequestSpec(HttpMethod.GET, calls.url("/status")))
    Responses.envelope(response, calls.json)
    val root = calls.json.parseToJsonElement(response.bodyAsText())
    return ApiStatus(
      root.bool("success") ?: true,
      root.int("code"),
      root.str("message"),
      root.str("version"),
    )
  }

  /** Synchronous, exception-based facade for Java and non-coroutine code. */
  public fun blocking(): KarbonBlocking = KarbonBlocking(this)

  public companion object {
    /** Carbone Cloud with an API key. Sends `carbone-version: 5`. */
    @JvmStatic
    @JvmOverloads
    public fun cloud(apiKey: String, configure: KarbonConfig.Builder.() -> Unit = {}): Karbon =
      create(KarbonConfig.cloud(apiKey, configure))

    /**
     * Self-hosted Carbone. [token] is optional: authentication is disabled by default on-premise.
     * No `carbone-version` header.
     */
    @JvmStatic
    @JvmOverloads
    public fun onPremise(
      baseUrl: String,
      token: String? = null,
      configure: KarbonConfig.Builder.() -> Unit = {},
    ): Karbon = create(KarbonConfig.onPremise(baseUrl, token, configure))

    @JvmStatic
    public fun create(config: KarbonConfig): Karbon = Karbon(config, JdkHttpTransport(config))

    internal fun create(config: KarbonConfig, transport: HttpTransport): Karbon =
      Karbon(config, transport)
  }
}
