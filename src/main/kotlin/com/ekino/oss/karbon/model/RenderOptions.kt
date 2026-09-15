/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Everything in a render body except `data` and `template`, plus extra request headers. */
public data class RenderOptions(
  val convertTo: OutputFormat? = null,
  /** PDF engine. Only meaningful when [convertTo] is [OutputFormat.Pdf]. */
  val converter: PdfConverter? = null,
  /** IANA timezone, server default `Europe/Paris`. */
  val timezone: String? = null,
  /** Locale such as `fr-fr`, drives `{t()}`, `:formatN`, `:formatC`. */
  val lang: String? = null,
  /** Extra data reachable through `{c.}` tags. */
  val complement: JsonElement? = null,
  val variableStr: String? = null,
  /** File name in `Content-Disposition`; may contain Carbone tags. */
  val reportName: String? = null,
  /** Enumerations for the `convEnum` formatter. */
  val enum: JsonObject? = null,
  /** Translations keyed by locale, used with `{t()}` and [lang]. */
  val translations: JsonObject? = null,
  val currency: CurrencyConversion? = null,
  /** Recompute pagination and TOC at the end of rendering. Requires [convertTo]. */
  val hardRefresh: Boolean = false,
  /** Multi-report generation. Requires an async render (webhook). */
  val batch: BatchOptions? = null,
  val preReleaseFeatureIn: Int? = null,
  /** Extra HTTP headers (e.g. `carbone-egress-header-authorization`). */
  val headers: Map<String, String> = emptyMap(),
) {
  public class Builder {
    public var convertTo: OutputFormat? = null
    public var converter: PdfConverter? = null
    public var timezone: String? = null
    public var lang: String? = null
    public var complement: JsonElement? = null
    public var variableStr: String? = null
    public var reportName: String? = null
    public var enum: JsonObject? = null
    public var translations: JsonObject? = null
    public var currency: CurrencyConversion? = null
    public var hardRefresh: Boolean = false
    public var batch: BatchOptions? = null
    public var preReleaseFeatureIn: Int? = null
    public val headers: MutableMap<String, String> = mutableMapOf()

    public fun pdf(configure: PdfOptions.Builder.() -> Unit = {}): Builder = apply {
      convertTo = OutputFormat.pdf(configure)
    }

    public fun jpg(options: ImageOptions = ImageOptions()): Builder = apply {
      convertTo = OutputFormat.Jpg(options)
    }

    public fun png(options: ImageOptions = ImageOptions()): Builder = apply {
      convertTo = OutputFormat.Png(options)
    }

    public fun csv(options: CsvOptions = CsvOptions()): Builder = apply {
      convertTo = OutputFormat.Csv(options)
    }

    public fun header(name: String, value: String): Builder = apply { headers[name] = value }

    public fun build(): RenderOptions =
      RenderOptions(
        convertTo,
        converter,
        timezone,
        lang,
        complement,
        variableStr,
        reportName,
        enum,
        translations,
        currency,
        hardRefresh,
        batch,
        preReleaseFeatureIn,
        headers.toMap(),
      )
  }

  public companion object {
    @JvmField public val None: RenderOptions = RenderOptions()

    @JvmStatic
    public fun pdf(version: PdfVersion): RenderOptions =
      RenderOptions(convertTo = OutputFormat.pdf(version))

    @JvmStatic
    public fun convertTo(format: OutputFormat): RenderOptions = RenderOptions(convertTo = format)

    public fun build(configure: Builder.() -> Unit): RenderOptions =
      Builder().apply(configure).build()
  }
}

public enum class PdfConverter(public val code: String) {
  LIBRE_OFFICE("L"),
  ONLY_OFFICE("O"),
  CHROMIUM("C"),
}

public data class CurrencyConversion(
  val source: String,
  val target: String,
  val rates: Map<String, Double> = emptyMap(),
)

public enum class BatchOutput(public val wire: String) {
  ZIP("zip"),
  PDF("pdf"),
}

/** `batchSplitBy` / `batchOutput` / `batchReportName`. */
public data class BatchOptions(
  /** JSON path of the array to split on, e.g. `d.items`. */
  val splitBy: String,
  val output: BatchOutput = BatchOutput.ZIP,
  /** File name template for each entry of the ZIP, e.g. `report-{d.id}`. */
  val reportName: String? = null,
)

/** Asynchronous rendering target. Headers become `carbone-webhook-header-<name>`. */
public data class Webhook(val url: String, val headers: Map<String, String> = emptyMap())

public data class AsyncRenderAccepted(val message: String?)
