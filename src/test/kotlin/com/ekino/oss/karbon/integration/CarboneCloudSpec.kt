/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.integration

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbon.Karbon
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.model.ListTemplatesQuery
import com.ekino.oss.karbon.model.OutputFormat
import com.ekino.oss.karbon.model.PdfVersion
import com.ekino.oss.karbon.model.RenderData
import com.ekino.oss.karbon.model.RenderOptions
import com.ekino.oss.karbon.model.TemplatePatch
import com.ekino.oss.karbon.model.TemplateSource
import com.ekino.oss.karbon.model.UploadOptions
import com.ekino.oss.karbon.model.UploadedTemplate
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Against Carbone Cloud with a *test* API key read from `CARBONE_TEST_API_KEY`. Skipped when the
 * variable is absent. Covers what the Community container cannot: `carbone-version`, template
 * versioning, listing, metadata patch.
 */
class CarboneCloudSpec :
  ShouldSpec({
    val apiKey = System.getenv("CARBONE_TEST_API_KEY")?.takeIf { it.isNotBlank() }
    val karbon by lazy { Karbon.cloud(apiKey!!) }
    val template =
      TemplateSource.stream("invoice.docx") {
        CarboneCloudSpec::class.java.getResourceAsStream("/templates/invoice.docx")!!
      }
    val data =
      RenderData.of(
        buildJsonObject {
          put("number", "F-CLOUD-1")
          putJsonObject("customer") { put("name", "ACME") }
          put("total", 42)
          put("status", "PAID")
        }
      )

    context("Carbone Cloud with a test key").config(enabled = apiKey != null) {
      should("report status") {
        either { karbon.status() }.orFail().version.shouldNotBeNull() shouldStartWith "5."
      }

      should("render a PDF/A-3 hash-first from bytes (legacy template id)") {
        either {
          karbon.templates.delete(template.sha256())
        } // ignore the result: forces the upload path if it was known
        val doc =
          either { karbon.renders.render(template, data, RenderOptions.pdf(PdfVersion.PDF_A_3)) }
            .orFail()
        doc.contentType.shouldNotBeNull() shouldStartWith "application/pdf"
        doc.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
        doc.content.toString(Charsets.ISO_8859_1).contains("pdfaid:part>3<") shouldBe true
      }

      should("render in two steps and download once") {
        val renderId =
          either { karbon.renders.start(template, data, RenderOptions.convertTo(OutputFormat.PDF)) }
            .orFail()
        either { karbon.renders.download(renderId) }
          .orFail()
          .content
          .copyOf(5)
          .toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
        val second = either { karbon.renders.download(renderId) }
        second
          .shouldBeInstanceOf<Either.Left<KarbonError>>()
          .value
          .shouldBeInstanceOf<KarbonError.RenderNotFound>()
      }

      should("upload with versioning, list, patch metadata, then delete") {
        val uploaded =
          either {
              karbon.templates.upload(
                template,
                UploadOptions(
                  versioning = true,
                  name = "karbon-it",
                  category = "karbon",
                  tags = listOf("it"),
                ),
              )
            }
            .orFail()
        val versioned = uploaded.shouldBeInstanceOf<UploadedTemplate.Versioned>()
        // In versioned mode versionId is not the plain SHA-256 of the file (metadata is part of the
        // hash).

        val listed =
          either { karbon.templates.list(ListTemplatesQuery(category = "karbon")) }.orFail()
        listed.items.map { it.id } shouldContain versioned.id

        val patched =
          either {
              karbon.templates.update(
                versioned.id,
                TemplatePatch(name = "karbon-it-renamed", tags = listOf("it", "renamed")),
              )
            }
            .orFail()
        patched.name shouldBe "karbon-it-renamed"

        either { karbon.templates.categories() }.orFail() shouldContain "karbon"
        either { karbon.templates.listAll(ListTemplatesQuery(category = "karbon")).toList() }
          .orFail()
          .map { it.id } shouldContain versioned.id

        val rendered =
          either {
              karbon.renders.render(
                TemplateSource.id(versioned.id),
                data,
                RenderOptions.pdf(PdfVersion.PDF_A_3),
              )
            }
            .orFail()
        rendered.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"

        either { karbon.templates.delete(versioned.id) }.orFail()
      }

      should("convert a docx to pdf without data") {
        val doc = either { karbon.renders.convert(template, OutputFormat.PDF) }.orFail()
        doc.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
      }

      should("report TemplateNotFound with the raw API message for an unknown template id") {
        val unknown =
          TemplateSource.id("0000000000000000000000000000000000000000000000000000000000000000")
        val result = either {
          karbon.renders.render(unknown, data, RenderOptions.pdf(PdfVersion.PDF_A_3))
        }
        val error =
          result
            .shouldBeInstanceOf<Either.Left<KarbonError>>()
            .value
            .shouldBeInstanceOf<KarbonError.TemplateNotFound>()
        error.message.shouldNotBeNull()
      }

      should("surface Carbone's error code when a test key is limited to pdf output") {
        val result = either {
          karbon.renders.render(template, data, RenderOptions.convertTo(OutputFormat.DOCX))
        }
        val error =
          result
            .shouldBeInstanceOf<Either.Left<KarbonError>>()
            .value
            .shouldBeInstanceOf<KarbonError.Api>()
        error.code shouldBe "w117"
        error.message.shouldNotBeNull() shouldStartWith "With a test key"
      }
    }
  })

private fun <T> Either<KarbonError, T>.orFail(): T =
  fold({ throw AssertionError("Karbon call failed: ${it.describe()} ($it)") }, { it })
