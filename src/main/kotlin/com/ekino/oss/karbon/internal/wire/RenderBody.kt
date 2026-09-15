/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.internal.wire

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.model.CsvOptions
import com.ekino.oss.karbon.model.ImageOptions
import com.ekino.oss.karbon.model.OutputFormat
import com.ekino.oss.karbon.model.PdfOptions
import com.ekino.oss.karbon.model.RenderData
import com.ekino.oss.karbon.model.RenderOptions
import com.ekino.oss.karbon.model.Watermark
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Builds the JSON body of `POST /render/...` from the public models, with Carbone's wire names. */
internal object RenderBody {

  context(_: Raise<KarbonError>)
  fun build(
    data: RenderData?,
    options: RenderOptions,
    json: Json,
    template: ByteArray? = null,
  ): String {
    validate(options)
    val body = buildJsonObject {
      data?.let { put("data", dataElement(it, json)) }
      template?.let { put("template", java.util.Base64.getEncoder().encodeToString(it)) }
      options.convertTo?.let { put("convertTo", convertTo(it)) }
      options.converter?.let { put("converter", it.code) }
      options.timezone?.let { put("timezone", it) }
      options.lang?.let { put("lang", it) }
      options.complement?.let { put("complement", it) }
      options.variableStr?.let { put("variableStr", it) }
      options.reportName?.let { put("reportName", it) }
      options.enum?.let { put("enum", it) }
      options.translations?.let { put("translations", it) }
      options.currency?.let {
        put("currencySource", it.source)
        put("currencyTarget", it.target)
        if (it.rates.isNotEmpty())
          putJsonObject("currencyRates") { it.rates.forEach { (k, v) -> put(k, v) } }
      }
      if (options.hardRefresh) put("hardRefresh", true)
      options.batch?.let {
        put("batchSplitBy", it.splitBy)
        put("batchOutput", it.output.wire)
        it.reportName?.let { name -> put("batchReportName", name) }
      }
      options.preReleaseFeatureIn?.let { put("preReleaseFeatureIn", it) }
    }
    return json.encodeToString(JsonObject.serializer(), body)
  }

  context(_: Raise<KarbonError>)
  fun validate(options: RenderOptions) {
    ensure(!options.hardRefresh || options.convertTo != null) {
      KarbonError.InvalidRequest("hardRefresh requires convertTo")
    }
    ensure(options.converter == null || options.convertTo is OutputFormat.Pdf) {
      KarbonError.InvalidRequest("converter requires convertTo = pdf")
    }
    (options.convertTo as? OutputFormat.Pdf)?.options?.let { pdf ->
      ensure(pdf.watermarks.size <= PdfOptions.MAX_WATERMARKS) {
        KarbonError.InvalidRequest("at most ${PdfOptions.MAX_WATERMARKS} watermarks")
      }
      pdf.watermarks.forEach { w ->
        ensure(w.opacity == null || w.opacity in 0.0..1.0) {
          KarbonError.InvalidRequest("watermark opacity must be within 0..1")
        }
      }
    }
    (options.convertTo as? OutputFormat.Jpg)?.options?.quality?.let {
      ensure(it in 1..MAX_QUALITY) {
        KarbonError.InvalidRequest("jpg quality must be within 1..$MAX_QUALITY")
      }
    }
    (options.convertTo as? OutputFormat.Png)?.options?.compression?.let {
      ensure(it in 0..MAX_PNG_COMPRESSION) {
        KarbonError.InvalidRequest("png compression must be within 0..$MAX_PNG_COMPRESSION")
      }
    }
  }

  context(_: Raise<KarbonError>)
  private fun dataElement(data: RenderData, json: Json): JsonElement =
    when (data) {
      is RenderData.Element -> data.json
      is RenderData.Raw ->
        try {
          json.parseToJsonElement(data.json)
        } catch (e: SerializationException) {
          raise(KarbonError.InvalidRequest("data is not valid JSON: ${e.message}"))
        }
      is RenderData.Value<*> -> encodeValue(data, json)
    }

  @Suppress("UNCHECKED_CAST")
  private fun <T> encodeValue(data: RenderData.Value<T>, json: Json): JsonElement =
    json.encodeToJsonElement(data.serializer, data.value)

  fun convertTo(format: OutputFormat): JsonElement =
    when (format) {
      is OutputFormat.Simple -> JsonPrimitive(format.formatName)
      is OutputFormat.Pdf -> withOptions(format.formatName, pdfOptions(format.options))
      is OutputFormat.Jpg -> withOptions(format.formatName, imageOptions(format.options))
      is OutputFormat.Png -> withOptions(format.formatName, imageOptions(format.options))
      is OutputFormat.Csv -> withOptions(format.formatName, csvOptions(format.options))
    }

  private fun withOptions(name: String, options: JsonObject): JsonElement =
    if (options.isEmpty()) JsonPrimitive(name)
    else
      buildJsonObject {
        put("formatName", name)
        put("formatOptions", options)
      }

  private fun pdfOptions(o: PdfOptions): JsonObject = buildJsonObject {
    o.version?.let { put("SelectPdfVersion", it.code) }
    o.encryptFile?.let { put("EncryptFile", it) }
    o.documentOpenPassword?.let { put("DocumentOpenPassword", it) }
    o.permissionPassword?.let { put("PermissionPassword", it) }
    o.restrictPermissions?.let { put("RestrictPermissions", it) }
    o.printing?.let { put("Printing", it.code) }
    o.changes?.let { put("Changes", it.code) }
    o.watermark?.let { put("Watermark", it) }
    if (o.watermarks.isNotEmpty())
      put("Watermarks", kotlinx.serialization.json.JsonArray(o.watermarks.map(::watermark)))
    o.pdfUaCompliance?.let { put("PDFUACompliance", it) }
    o.useTaggedPdf?.let { put("UseTaggedPDF", it) }
    o.pageRange?.let { put("PageRange", it) }
    o.useLosslessCompression?.let { put("UseLosslessCompression", it) }
    o.reduceImageResolution?.let { put("ReduceImageResolution", it) }
    o.maxImageResolution?.let { put("MaxImageResolution", it.dpi) }
    o.exportFormFields?.let { put("ExportFormFields", it) }
    o.exportNotes?.let { put("ExportNotes", it) }
  }

  private fun watermark(w: Watermark): JsonObject = buildJsonObject {
    put("text", w.text)
    w.anchor?.let { put("anchor", it.wire) }
    w.offsetX?.let { put("offsetX", it) }
    w.offsetY?.let { put("offsetY", it) }
    w.rotation?.let { put("rotation", it) }
    w.color?.let { put("color", it) }
    w.size?.let { put("size", it) }
    w.opacity?.let { put("opacity", it) }
    w.font?.let { put("font", it) }
    w.fromPage?.let { put("fromPage", it) }
    w.toPage?.let { put("toPage", it) }
  }

  private fun imageOptions(o: ImageOptions): JsonObject = buildJsonObject {
    o.pixelWidth?.let { put("PixelWidth", it) }
    o.pixelHeight?.let { put("PixelHeight", it) }
    o.colorMode?.let { put("ColorMode", it.code) }
    o.quality?.let { put("Quality", it) }
    o.compression?.let { put("Compression", it) }
    o.interlaced?.let { put("Interlaced", if (it) 1 else 0) }
    o.translucent?.let { put("Translucent", if (it) 1 else 0) }
  }

  private fun csvOptions(o: CsvOptions): JsonObject = buildJsonObject {
    o.fieldSeparator?.let { put("fieldSeparator", it) }
    o.textDelimiter?.let { put("textDelimiter", it) }
    o.characterSet?.let { put("characterSet", it) }
  }

  private const val MAX_QUALITY = 100
  private const val MAX_PNG_COMPRESSION = 9
}
