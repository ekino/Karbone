/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import arrow.core.raise.Raise
import com.ekino.oss.karbone.blocking.KarboneBlocking
import com.ekino.oss.karbone.internal.Calls
import com.ekino.oss.karbone.internal.DefaultRenders
import com.ekino.oss.karbone.internal.DefaultTemplates
import com.ekino.oss.karbone.internal.http.HttpMethod
import com.ekino.oss.karbone.internal.http.HttpRequestSpec
import com.ekino.oss.karbone.internal.http.HttpTransport
import com.ekino.oss.karbone.internal.http.JdkHttpTransport
import com.ekino.oss.karbone.internal.wire.Responses
import com.ekino.oss.karbone.internal.wire.Responses.bool
import com.ekino.oss.karbone.internal.wire.Responses.int
import com.ekino.oss.karbone.internal.wire.Responses.str
import com.ekino.oss.karbone.model.ApiStatus

/**
 * Entry point of the Karbone SDK, a Kotlin client for the Carbone document generation API.
 *
 * Build one with [cloud] or [onPremise]; instances are immutable and thread-safe.
 */
public class Karbone
internal constructor(public val config: KarboneConfig, transport: HttpTransport) : KarboneClient {

  private val calls = Calls(config, transport)

  override val templates: Templates = DefaultTemplates(calls)
  override val renders: Renders = DefaultRenders(calls, templates)

  /** `GET /status`. Works without authentication. */
  context(_: Raise<KarboneError>)
  override suspend fun status(): ApiStatus {
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
  public fun blocking(): KarboneBlocking = KarboneBlocking.of(this)

  public companion object {
    /** Carbone Cloud with an API key. Sends `carbone-version: 5`. */
    @JvmStatic
    @JvmOverloads
    public fun cloud(apiKey: String, configure: KarboneConfig.Builder.() -> Unit = {}): Karbone =
      create(KarboneConfig.cloud(apiKey, configure))

    /**
     * Self-hosted Carbone. [token] is optional: authentication is disabled by default on-premise.
     * No `carbone-version` header.
     */
    @JvmStatic
    @JvmOverloads
    public fun onPremise(
      baseUrl: String,
      token: String? = null,
      configure: KarboneConfig.Builder.() -> Unit = {},
    ): Karbone = create(KarboneConfig.onPremise(baseUrl, token, configure))

    @JvmStatic
    public fun create(config: KarboneConfig): Karbone = Karbone(config, JdkHttpTransport(config))

    internal fun create(config: KarboneConfig, transport: HttpTransport): Karbone =
      Karbone(config, transport)
  }
}
