/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.integration

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbone.Karbone
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.model.ListTemplatesQuery
import com.ekino.oss.karbone.model.OutputFormat
import com.ekino.oss.karbone.model.PdfVersion
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.TemplatePatch
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.model.UploadOptions
import com.ekino.oss.karbone.model.UploadedTemplate
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
    val karbone by lazy { Karbone.cloud(apiKey!!) }
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
        either { karbone.status() }.orFail().version.shouldNotBeNull() shouldStartWith "5."
      }

      should("render a PDF/A-3 hash-first from bytes (legacy template id)") {
        either {
          karbone.templates.delete(template.sha256())
        } // ignore the result: forces the upload path if it was known
        val doc =
          either { karbone.renders.render(template, data, RenderOptions.pdf(PdfVersion.PDF_A_3)) }
            .orFail()
        doc.contentType.shouldNotBeNull() shouldStartWith "application/pdf"
        doc.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
        doc.content.toString(Charsets.ISO_8859_1).contains("pdfaid:part>3<") shouldBe true
      }

      should("render in two steps and download once") {
        val renderId =
          either {
              karbone.renders.start(template, data, RenderOptions.convertTo(OutputFormat.PDF))
            }
            .orFail()
        either { karbone.renders.download(renderId) }
          .orFail()
          .content
          .copyOf(5)
          .toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
        val second = either { karbone.renders.download(renderId) }
        second
          .shouldBeInstanceOf<Either.Left<KarboneError>>()
          .value
          .shouldBeInstanceOf<KarboneError.RenderNotFound>()
      }

      should("upload with versioning, list, patch metadata, then delete") {
        val uploaded =
          either {
              karbone.templates.upload(
                template,
                UploadOptions(
                  versioning = true,
                  name = "karbone-it",
                  category = "karbone",
                  tags = listOf("it"),
                ),
              )
            }
            .orFail()
        val versioned = uploaded.shouldBeInstanceOf<UploadedTemplate.Versioned>()
        // In versioned mode versionId is not the plain SHA-256 of the file (metadata is part of the
        // hash).

        val listed =
          either { karbone.templates.list(ListTemplatesQuery(category = "karbone")) }.orFail()
        listed.items.map { it.id } shouldContain versioned.id

        val patched =
          either {
              karbone.templates.update(
                versioned.id,
                TemplatePatch(name = "karbone-it-renamed", tags = listOf("it", "renamed")),
              )
            }
            .orFail()
        patched.name shouldBe "karbone-it-renamed"

        either { karbone.templates.categories() }.orFail() shouldContain "karbone"
        either { karbone.templates.listAll(ListTemplatesQuery(category = "karbone")).toList() }
          .orFail()
          .map { it.id } shouldContain versioned.id

        val rendered =
          either {
              karbone.renders.render(
                TemplateSource.id(versioned.id),
                data,
                RenderOptions.pdf(PdfVersion.PDF_A_3),
              )
            }
            .orFail()
        rendered.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"

        either { karbone.templates.delete(versioned.id) }.orFail()
      }

      should("convert a docx to pdf without data") {
        val doc = either { karbone.renders.convert(template, OutputFormat.PDF) }.orFail()
        doc.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
      }

      should("report TemplateNotFound with the raw API message for an unknown template id") {
        val unknown =
          TemplateSource.id("0000000000000000000000000000000000000000000000000000000000000000")
        val result = either {
          karbone.renders.render(unknown, data, RenderOptions.pdf(PdfVersion.PDF_A_3))
        }
        val error =
          result
            .shouldBeInstanceOf<Either.Left<KarboneError>>()
            .value
            .shouldBeInstanceOf<KarboneError.TemplateNotFound>()
        error.message.shouldNotBeNull()
      }

      should("surface Carbone's error code when a test key is limited to pdf output") {
        val result = either {
          karbone.renders.render(template, data, RenderOptions.convertTo(OutputFormat.DOCX))
        }
        val error =
          result
            .shouldBeInstanceOf<Either.Left<KarboneError>>()
            .value
            .shouldBeInstanceOf<KarboneError.Api>()
        error.code shouldBe "w117"
        error.message.shouldNotBeNull() shouldStartWith "With a test key"
      }
    }
  })

private fun <T> Either<KarboneError, T>.orFail(): T =
  fold({ throw AssertionError("Karbone call failed: ${it.describe()} ($it)") }, { it })
