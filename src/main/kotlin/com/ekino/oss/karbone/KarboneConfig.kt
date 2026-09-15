/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/** Immutable per-client configuration. */
public data class KarboneConfig(
  /**
   * `https://api.carbone.io` or an on-premise URL such as `http://localhost:4000`. Trailing slash
   * optional.
   */
  val baseUrl: String,
  /**
   * Bearer token. `null` sends no `Authorization` header (on-premise with authentication disabled).
   */
  val token: String? = null,
  /**
   * `carbone-version` header. `null` omits it, which is what on-premise expects. Cloud should use
   * [LATEST_API_VERSION].
   */
  val apiVersion: Int? = null,
  val connectTimeout: Duration = 10.seconds,
  /** Per-request timeout. Carbone's synchronous render timeout is 60 s. */
  val requestTimeout: Duration = 60.seconds,
  val retry: RetryPolicy = RetryPolicy.None,
  /** Used for request bodies and response parsing. */
  val json: Json = DefaultJson,
  val userAgent: String = "karbone/$VERSION",
) {
  init {
    require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
      "baseUrl must start with http:// or https://"
    }
  }

  public val normalizedBaseUrl: String = baseUrl.trimEnd('/')

  public class Builder
  internal constructor(
    private var baseUrl: String,
    private var token: String?,
    private var apiVersion: Int?,
  ) {
    public var connectTimeout: Duration = 10.seconds
    public var requestTimeout: Duration = 60.seconds
    public var retry: RetryPolicy = RetryPolicy.None
    public var json: Json = DefaultJson
    public var userAgent: String = "karbone/$VERSION"

    public fun baseUrl(value: String): Builder = apply { baseUrl = value }

    public fun token(value: String?): Builder = apply { token = value }

    public fun apiVersion(value: Int?): Builder = apply { apiVersion = value }

    public fun build(): KarboneConfig =
      KarboneConfig(
        baseUrl,
        token,
        apiVersion,
        connectTimeout,
        requestTimeout,
        retry,
        json,
        userAgent,
      )
  }

  public companion object {
    public const val CLOUD_BASE_URL: String = "https://api.carbone.io"
    public const val LATEST_API_VERSION: Int = 5
    public const val VERSION: String = "0.1.0"

    @JvmField
    public val DefaultJson: Json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
      encodeDefaults = false
    }

    public fun cloud(apiKey: String, configure: Builder.() -> Unit = {}): KarboneConfig =
      Builder(CLOUD_BASE_URL, apiKey, LATEST_API_VERSION).apply(configure).build()

    public fun onPremise(
      baseUrl: String,
      token: String? = null,
      configure: Builder.() -> Unit = {},
    ): KarboneConfig = Builder(baseUrl, token, null).apply(configure).build()
  }
}

/** Retry on transport failures only (connection reset, timeout). API errors are never retried. */
public sealed interface RetryPolicy {
  public data object None : RetryPolicy

  public data class OnTransportError(
    val maxAttempts: Int = 2,
    val backoff: Duration = 200.milliseconds,
  ) : RetryPolicy {
    init {
      require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }
  }
}
