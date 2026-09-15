/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.wire

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbone.KarboneConfig
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.model.BatchOptions
import com.ekino.oss.karbone.model.BatchOutput
import com.ekino.oss.karbone.model.ColorMode
import com.ekino.oss.karbone.model.CsvOptions
import com.ekino.oss.karbone.model.CurrencyConversion
import com.ekino.oss.karbone.model.ImageOptions
import com.ekino.oss.karbone.model.OutputFormat
import com.ekino.oss.karbone.model.PdfConverter
import com.ekino.oss.karbone.model.PdfOptions
import com.ekino.oss.karbone.model.PdfVersion
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.Watermark
import com.ekino.oss.karbone.model.WatermarkAnchor
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val parser = Json { ignoreUnknownKeys = true }

@Serializable private data class Invoice(val id: Int, val total: Double)

private fun buildBody(
  data: RenderData?,
  options: RenderOptions = RenderOptions.None,
  template: ByteArray? = null,
): JsonObject {
  val result = either {
    RenderBody.build(data, options, KarboneConfig.DefaultJson, template)
  }
  val right = result.shouldBeInstanceOf<Either.Right<String>>()
  return parser.parseToJsonElement(right.value).jsonObject
}

private fun buildError(
  data: RenderData?,
  options: RenderOptions = RenderOptions.None,
  template: ByteArray? = null,
): KarboneError {
  val result = either {
    RenderBody.build(data, options, KarboneConfig.DefaultJson, template)
  }
  val left = result.shouldBeInstanceOf<Either.Left<KarboneError>>()
  return left.value
}

class RenderBodySpec :
  ShouldSpec({
    context("data") {
      should("encode raw JSON data as-is") {
        val body = buildBody(RenderData.raw("""{"id":42}"""))
        body["data"]!!.jsonObject["id"]!!.jsonPrimitive.intOrNull shouldBe 42
      }

      should("encode an enum object and a pdf convertTo with an integer SelectPdfVersion") {
        val enum = buildJsonObject {
          put("YES", "Yes")
          put("NO", "No")
        }
        val body =
          buildBody(
            RenderData.raw("""{"id":42}"""),
            RenderOptions(convertTo = OutputFormat.pdf(PdfVersion.PDF_A_3), enum = enum),
          )
        body["data"]!!.jsonObject["id"]!!.jsonPrimitive.intOrNull shouldBe 42
        body["enum"]!!.jsonObject shouldBe enum
        val convertTo = body["convertTo"]!!.jsonObject
        convertTo["formatName"]!!.jsonPrimitive.contentOrNull shouldBe "pdf"
        val formatOptions = convertTo["formatOptions"]!!.jsonObject
        formatOptions["SelectPdfVersion"]!!.jsonPrimitive.intOrNull shouldBe 3
      }

      should("encode RenderData.of(value) through kotlinx.serialization") {
        val body = buildBody(RenderData.of(Invoice(1, 9.99)))
        body["data"]!!.jsonObject["id"]!!.jsonPrimitive.intOrNull shouldBe 1
      }

      should("encode RenderData.Element as the raw JsonElement") {
        val element = buildJsonObject { put("a", "b") }
        val body = buildBody(RenderData.of(element))
        body["data"]!!.jsonObject shouldBe element
      }

      should("encode RenderData.Empty as an empty object") {
        val body = buildBody(RenderData.Empty)
        body["data"]!!.jsonObject shouldBe JsonObject(emptyMap())
      }

      should("omit the data key when data is null") {
        val body = buildBody(data = null, template = byteArrayOf(1, 2, 3))
        body.containsKey("data") shouldBe false
      }

      should("encode template bytes as base64") {
        val bytes = byteArrayOf(10, 20, 30, 40, 0, -1)
        val body = buildBody(data = null, template = bytes)
        body["template"]!!.jsonPrimitive.contentOrNull shouldBe
          Base64.getEncoder().encodeToString(bytes)
      }
    }

    context("convertTo") {
      should("encode a plain format with no options as a bare string") {
        buildBody(RenderData.Empty, RenderOptions(convertTo = OutputFormat.PDF))["convertTo"]!!
          .jsonPrimitive
          .contentOrNull shouldBe "pdf"
        buildBody(RenderData.Empty, RenderOptions(convertTo = OutputFormat.DOCX))["convertTo"]!!
          .jsonPrimitive
          .contentOrNull shouldBe "docx"
      }

      should("encode pdf watermarks and encryption with Carbone's wire names") {
        val body =
          buildBody(
            RenderData.Empty,
            RenderOptions(
              convertTo =
                OutputFormat.Pdf(
                  PdfOptions(
                    encryptFile = true,
                    documentOpenPassword = "secret",
                    watermarks =
                      listOf(
                        Watermark(
                          text = "Confidential",
                          anchor = WatermarkAnchor.CENTER,
                          opacity = 0.5,
                        )
                      ),
                  )
                )
            ),
          )
        val formatOptions = body["convertTo"]!!.jsonObject["formatOptions"]!!.jsonObject
        formatOptions["EncryptFile"]!!.jsonPrimitive.content shouldBe "true"
        formatOptions["DocumentOpenPassword"]!!.jsonPrimitive.contentOrNull shouldBe "secret"
        val watermarks = formatOptions["Watermarks"]!!.jsonArray
        watermarks shouldHaveSize 1
        val watermark = watermarks[0].jsonObject
        watermark["text"]!!.jsonPrimitive.contentOrNull shouldBe "Confidential"
        watermark["anchor"]!!.jsonPrimitive.contentOrNull shouldBe "center"
        watermark["opacity"]!!.jsonPrimitive.content shouldBe "0.5"
      }

      should("encode jpg Quality/ColorMode, png Interlaced and csv fieldSeparator") {
        val jpgBody =
          buildBody(
            RenderData.Empty,
            RenderOptions(
              convertTo =
                OutputFormat.Jpg(ImageOptions(quality = 80, colorMode = ColorMode.GREYSCALE))
            ),
          )
        val jpgOptions = jpgBody["convertTo"]!!.jsonObject["formatOptions"]!!.jsonObject
        jpgOptions["Quality"]!!.jsonPrimitive.intOrNull shouldBe 80
        jpgOptions["ColorMode"]!!.jsonPrimitive.intOrNull shouldBe ColorMode.GREYSCALE.code

        val pngOnBody =
          buildBody(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Png(ImageOptions(interlaced = true))),
          )
        pngOnBody["convertTo"]!!
          .jsonObject["formatOptions"]!!
          .jsonObject["Interlaced"]!!
          .jsonPrimitive
          .intOrNull shouldBe 1

        val pngOffBody =
          buildBody(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Png(ImageOptions(interlaced = false))),
          )
        pngOffBody["convertTo"]!!
          .jsonObject["formatOptions"]!!
          .jsonObject["Interlaced"]!!
          .jsonPrimitive
          .intOrNull shouldBe 0

        val csvBody =
          buildBody(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Csv(CsvOptions(fieldSeparator = ";"))),
          )
        csvBody["convertTo"]!!
          .jsonObject["formatOptions"]!!
          .jsonObject["fieldSeparator"]!!
          .jsonPrimitive
          .contentOrNull shouldBe ";"
      }
    }

    context("top-level options") {
      should("encode timezone, lang, reportName, complement and translations") {
        val complement = buildJsonObject { put("company", "ekino") }
        val translations = buildJsonObject { put("en", buildJsonObject { put("hello", "Hello") }) }
        val body =
          buildBody(
            RenderData.Empty,
            RenderOptions(
              timezone = "Europe/Paris",
              lang = "fr-fr",
              reportName = "report-{d.id}",
              complement = complement,
              translations = translations,
            ),
          )
        body["timezone"]!!.jsonPrimitive.contentOrNull shouldBe "Europe/Paris"
        body["lang"]!!.jsonPrimitive.contentOrNull shouldBe "fr-fr"
        body["reportName"]!!.jsonPrimitive.contentOrNull shouldBe "report-{d.id}"
        body["complement"]!!.jsonObject shouldBe complement
        body["translations"]!!.jsonObject shouldBe translations
      }

      should("encode currency conversion fields") {
        val body =
          buildBody(
            RenderData.Empty,
            RenderOptions(
              currency =
                CurrencyConversion(source = "EUR", target = "USD", rates = mapOf("USD" to 1.1))
            ),
          )
        body["currencySource"]!!.jsonPrimitive.contentOrNull shouldBe "EUR"
        body["currencyTarget"]!!.jsonPrimitive.contentOrNull shouldBe "USD"
        body["currencyRates"]!!.jsonObject["USD"]!!.jsonPrimitive.content shouldBe "1.1"
      }

      should("encode hardRefresh only when true, and require convertTo") {
        val body =
          buildBody(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.PDF, hardRefresh = true),
          )
        body["hardRefresh"]!!.jsonPrimitive.content shouldBe "true"
      }

      should("encode batch options with the zip output name") {
        val body =
          buildBody(
            RenderData.Empty,
            RenderOptions(
              batch =
                BatchOptions(splitBy = "d.items", output = BatchOutput.ZIP, reportName = "r-{d.id}")
            ),
          )
        body["batchSplitBy"]!!.jsonPrimitive.contentOrNull shouldBe "d.items"
        body["batchOutput"]!!.jsonPrimitive.contentOrNull shouldBe "zip"
        body["batchReportName"]!!.jsonPrimitive.contentOrNull shouldBe "r-{d.id}"
      }

      should("encode the converter code for a pdf convertTo") {
        val body =
          buildBody(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.PDF, converter = PdfConverter.LIBRE_OFFICE),
          )
        body["converter"]!!.jsonPrimitive.contentOrNull shouldBe "L"
      }
    }

    context("validation") {
      should("reject hardRefresh without convertTo") {
        buildError(RenderData.Empty, RenderOptions(hardRefresh = true))
          .shouldBeInstanceOf<KarboneError.InvalidRequest>()
      }

      should("reject a converter with a non-pdf convertTo") {
        buildError(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.DOCX, converter = PdfConverter.LIBRE_OFFICE),
          )
          .shouldBeInstanceOf<KarboneError.InvalidRequest>()
      }

      should("reject more than 5 watermarks") {
        val watermarks = (1..6).map { Watermark("wm$it") }
        buildError(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Pdf(PdfOptions(watermarks = watermarks))),
          )
          .shouldBeInstanceOf<KarboneError.InvalidRequest>()
      }

      should("reject a jpg quality of 0 or 101") {
        buildError(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Jpg(ImageOptions(quality = 0))),
          )
          .shouldBeInstanceOf<KarboneError.InvalidRequest>()
        buildError(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Jpg(ImageOptions(quality = 101))),
          )
          .shouldBeInstanceOf<KarboneError.InvalidRequest>()
      }

      should("reject a png compression of 10") {
        buildError(
            RenderData.Empty,
            RenderOptions(convertTo = OutputFormat.Png(ImageOptions(compression = 10))),
          )
          .shouldBeInstanceOf<KarboneError.InvalidRequest>()
      }

      should("reject data that is not valid JSON") {
        buildError(RenderData.raw("not json")).shouldBeInstanceOf<KarboneError.InvalidRequest>()
      }
    }
  })
