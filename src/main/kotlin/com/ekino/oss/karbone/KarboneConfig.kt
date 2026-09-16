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
  /** Time allowed to establish the TCP/TLS connection. Defaults to 10 seconds. */
  val connectTimeout: Duration = 10.seconds,
  /** Per-request timeout. Carbone's synchronous render timeout is 60 s. */
  val requestTimeout: Duration = 60.seconds,
  /** Retry behaviour on transport failures. Defaults to no retry. */
  val retry: RetryPolicy = RetryPolicy.None,
  /** Used for request bodies and response parsing. */
  val json: Json = DefaultJson,
  /** Sent as the `User-Agent` header. Defaults to `karbone/<version>`. */
  val userAgent: String = "karbone/$VERSION",
) {
  init {
    require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
      "baseUrl must start with http:// or https://"
    }
  }

  /** [baseUrl] with any trailing slash removed. */
  public val normalizedBaseUrl: String = baseUrl.trimEnd('/')

  /** Mutable builder backing [Karbone.cloud] and [Karbone.onPremise]; not constructed directly. */
  public class Builder
  internal constructor(
    private var baseUrl: String,
    private var token: String?,
    private var apiVersion: Int?,
  ) {
    /** See [KarboneConfig.connectTimeout]. Defaults to 10 seconds. */
    public var connectTimeout: Duration = 10.seconds

    /** See [KarboneConfig.requestTimeout]. Defaults to 60 seconds. */
    public var requestTimeout: Duration = 60.seconds

    /** See [KarboneConfig.retry]. Defaults to no retry. */
    public var retry: RetryPolicy = RetryPolicy.None

    /** See [KarboneConfig.json]. */
    public var json: Json = DefaultJson

    /** See [KarboneConfig.userAgent]. */
    public var userAgent: String = "karbone/$VERSION"

    /** Overrides the base URL set by [Karbone.cloud] or [Karbone.onPremise]. */
    public fun baseUrl(value: String): Builder = apply { baseUrl = value }

    /** Overrides the bearer token; `null` sends no `Authorization` header. */
    public fun token(value: String?): Builder = apply { token = value }

    /** Overrides the `carbone-version` header; `null` omits it. */
    public fun apiVersion(value: Int?): Builder = apply { apiVersion = value }

    /** Builds the immutable [KarboneConfig]. */
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
    /** Default [baseUrl] for [cloud]. */
    public const val CLOUD_BASE_URL: String = "https://api.carbone.io"

    /** `carbone-version` header value used by [cloud]. */
    public const val LATEST_API_VERSION: Int = 5

    /**
     * SDK version as recorded in the jar manifest (`Implementation-Version`), used in the default
     * `User-Agent`. `dev` when Karbone is not loaded from its jar (tests, IDE).
     */
    @JvmField
    public val VERSION: String = KarboneConfig::class.java.`package`?.implementationVersion ?: "dev"

    /**
     * [Json] instance used by default: unknown keys ignored, nulls and defaults omitted on encode.
     */
    @JvmField
    public val DefaultJson: Json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
      encodeDefaults = false
    }

    /** Config for Carbone Cloud, authenticated with [apiKey] and sending [LATEST_API_VERSION]. */
    public fun cloud(apiKey: String, configure: Builder.() -> Unit = {}): KarboneConfig =
      Builder(CLOUD_BASE_URL, apiKey, LATEST_API_VERSION).apply(configure).build()

    /**
     * Config for a self-hosted Carbone instance at [baseUrl]. [token] is optional since
     * authentication is disabled by default on-premise; no `carbone-version` header is sent.
     */
    public fun onPremise(
      baseUrl: String,
      token: String? = null,
      configure: Builder.() -> Unit = {},
    ): KarboneConfig = Builder(baseUrl, token, null).apply(configure).build()
  }
}

/** Retry on transport failures only (connection reset, timeout). API errors are never retried. */
public sealed interface RetryPolicy {
  /** No retry: any transport failure is raised immediately. */
  public data object None : RetryPolicy

  /**
   * Retries a failed request on transport (I/O) failures only, waiting `backoff * attempt` between
   * attempts.
   *
   * @property maxAttempts total number of attempts, including the first one. Defaults to 2.
   * @property backoff base delay multiplied by the attempt number before each retry. Defaults to
   *   200 ms.
   */
  public data class OnTransportError(
    val maxAttempts: Int = 2,
    val backoff: Duration = 200.milliseconds,
  ) : RetryPolicy {
    init {
      require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    }
  }
}
