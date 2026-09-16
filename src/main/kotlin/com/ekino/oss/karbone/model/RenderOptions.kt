/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Everything in a render body except `data` and `template`, plus extra request headers. */
public data class RenderOptions(
  /** Output format; `null` renders in the template's own format. */
  val convertTo: OutputFormat? = null,
  /** PDF engine. Only meaningful when [convertTo] is [OutputFormat.Pdf]. */
  val converter: PdfConverter? = null,
  /** IANA timezone, server default `Europe/Paris`. */
  val timezone: String? = null,
  /** Locale such as `fr-fr`, drives `{t()}`, `:formatN`, `:formatC`. */
  val lang: String? = null,
  /** Extra data reachable through `{c.}` tags. */
  val complement: JsonElement? = null,
  /** `variableStr`: extra data available to the template as free-form text. */
  val variableStr: String? = null,
  /** File name in `Content-Disposition`; may contain Carbone tags. */
  val reportName: String? = null,
  /** Enumerations for the `convEnum` formatter. */
  val enum: JsonObject? = null,
  /** Translations keyed by locale, used with `{t()}` and [lang]. */
  val translations: JsonObject? = null,
  /** `currencySource` / `currencyTarget` / `currencyRates`. */
  val currency: CurrencyConversion? = null,
  /** Recompute pagination and TOC at the end of rendering. Requires [convertTo]. */
  val hardRefresh: Boolean = false,
  /** Multi-report generation. Requires an async render (webhook). */
  val batch: BatchOptions? = null,
  /** Opts into a pre-release Carbone feature, by its numeric id. */
  val preReleaseFeatureIn: Int? = null,
  /** Extra HTTP headers (e.g. `carbone-egress-header-authorization`). */
  val headers: Map<String, String> = emptyMap(),
) {
  /** Builder for [RenderOptions]. */
  public class Builder {
    /** See [RenderOptions.convertTo]. */
    public var convertTo: OutputFormat? = null
    /** See [RenderOptions.converter]. */
    public var converter: PdfConverter? = null
    /** See [RenderOptions.timezone]. */
    public var timezone: String? = null
    /** See [RenderOptions.lang]. */
    public var lang: String? = null
    /** See [RenderOptions.complement]. */
    public var complement: JsonElement? = null
    /** See [RenderOptions.variableStr]. */
    public var variableStr: String? = null
    /** See [RenderOptions.reportName]. */
    public var reportName: String? = null
    /** See [RenderOptions.enum]. */
    public var enum: JsonObject? = null
    /** See [RenderOptions.translations]. */
    public var translations: JsonObject? = null
    /** See [RenderOptions.currency]. */
    public var currency: CurrencyConversion? = null
    /** See [RenderOptions.hardRefresh]. */
    public var hardRefresh: Boolean = false
    /** See [RenderOptions.batch]. */
    public var batch: BatchOptions? = null
    /** See [RenderOptions.preReleaseFeatureIn]. */
    public var preReleaseFeatureIn: Int? = null
    /** See [RenderOptions.headers]. */
    public val headers: MutableMap<String, String> = mutableMapOf()

    /**
     * Sets [RenderOptions.convertTo] to a PDF output configured through the [PdfOptions.Builder]
     * DSL.
     */
    public fun pdf(configure: PdfOptions.Builder.() -> Unit = {}): Builder = apply {
      convertTo = OutputFormat.pdf(configure)
    }

    /** Sets [RenderOptions.convertTo] to a JPG output with the given [ImageOptions]. */
    public fun jpg(options: ImageOptions = ImageOptions()): Builder = apply {
      convertTo = OutputFormat.Jpg(options)
    }

    /** Sets [RenderOptions.convertTo] to a PNG output with the given [ImageOptions]. */
    public fun png(options: ImageOptions = ImageOptions()): Builder = apply {
      convertTo = OutputFormat.Png(options)
    }

    /** Sets [RenderOptions.convertTo] to a CSV output with the given [CsvOptions]. */
    public fun csv(options: CsvOptions = CsvOptions()): Builder = apply {
      convertTo = OutputFormat.Csv(options)
    }

    /** Adds one extra HTTP request header (see [RenderOptions.headers]). */
    public fun header(name: String, value: String): Builder = apply { headers[name] = value }

    /**
     * Builds the immutable [RenderOptions]; request invariants are checked when the options are
     * used.
     */
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

  /** Ready-made [RenderOptions] and a builder entry point. */
  public companion object {
    /** No options: render in the template's own format with every other setting at default. */
    @JvmField public val None: RenderOptions = RenderOptions()

    /** Renders to PDF, targeting the given [PdfVersion]. */
    @JvmStatic
    public fun pdf(version: PdfVersion): RenderOptions =
      RenderOptions(convertTo = OutputFormat.pdf(version))

    /** Renders to the given output [format], every other setting at default. */
    @JvmStatic
    public fun convertTo(format: OutputFormat): RenderOptions = RenderOptions(convertTo = format)

    /** Builds a [RenderOptions] through the [Builder] DSL. */
    public fun build(configure: Builder.() -> Unit): RenderOptions =
      Builder().apply(configure).build()
  }
}

/** `converter`: the engine used to produce PDF output. `LIBRE_OFFICE` is the server default. */
public enum class PdfConverter(public val code: String) {
  LIBRE_OFFICE("L"),
  ONLY_OFFICE("O"),
  CHROMIUM("C"),
}

/** `currencySource` / `currencyTarget` / `currencyRates`. */
public data class CurrencyConversion(
  /** Source currency code the document's amounts are expressed in. */
  val source: String,
  /** Target currency code amounts are converted to. */
  val target: String,
  /** Exchange rates keyed by currency code; empty uses Carbone's built-in rates. */
  val rates: Map<String, Double> = emptyMap(),
)

/** `batchOutput`. */
public enum class BatchOutput(public val wire: String) {
  ZIP("zip"),
  PDF("pdf"),
}

/** `batchSplitBy` / `batchOutput` / `batchReportName`. */
public data class BatchOptions(
  /** JSON path of the array to split on, e.g. `d.items`. */
  val splitBy: String,
  /** Shape of the batch result. */
  val output: BatchOutput = BatchOutput.ZIP,
  /** File name template for each entry of the ZIP, e.g. `report-{d.id}`. */
  val reportName: String? = null,
)

/** Asynchronous rendering target. Headers become `carbone-webhook-header-<name>`. */
public data class Webhook(
  /** URL Carbone posts the generated document (or batch) to once rendering completes. */
  val url: String,
  val headers: Map<String, String> = emptyMap(),
)

/** Response to an accepted asynchronous render request. */
public data class AsyncRenderAccepted(
  /** Optional acknowledgement message returned by Carbone. */
  val message: String?
)
