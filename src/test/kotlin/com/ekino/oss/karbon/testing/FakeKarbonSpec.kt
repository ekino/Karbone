/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.testing

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.model.ListTemplatesQuery
import com.ekino.oss.karbon.model.Page
import com.ekino.oss.karbon.model.PdfVersion
import com.ekino.oss.karbon.model.RenderData
import com.ekino.oss.karbon.model.RenderId
import com.ekino.oss.karbon.model.RenderOptions
import com.ekino.oss.karbon.model.RenderedDocument
import com.ekino.oss.karbon.model.TemplateId
import com.ekino.oss.karbon.model.TemplateSource
import com.ekino.oss.karbon.model.UploadOptions
import com.ekino.oss.karbon.model.UploadedTemplate
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList

class FakeKarbonSpec :
  ShouldSpec({
    val content = "fake docx".toByteArray()
    val sha = TemplateSource.sha256Hex(content)

    should("store uploaded templates under their SHA-256 like Carbone") {
      val fake = FakeKarbon()
      val uploaded = either {
        fake.templates.upload(TemplateSource.bytes(content), UploadOptions(name = "invoice"))
      }
      uploaded.shouldBeInstanceOf<Either.Right<UploadedTemplate>>().value.id shouldBe
        TemplateId(sha)
      fake.storedTemplates.keys shouldBe setOf(TemplateId(sha))
      fake.calls
        .shouldHaveSize(1)
        .first()
        .shouldBeInstanceOf<FakeKarbon.Call.Upload>()
        .options
        .name shouldBe "invoice"
    }

    should("render from content hash-first, registering the template on the fly") {
      val fake = FakeKarbon()
      val doc = either {
        fake.renders.render(
          TemplateSource.bytes(content),
          RenderData.raw("""{"n":1}"""),
          RenderOptions.pdf(PdfVersion.PDF_A_3),
        )
      }
      val rendered = doc.shouldBeInstanceOf<Either.Right<RenderedDocument>>().value
      rendered.fileName shouldBe "report.pdf"
      rendered.contentType shouldBe "application/pdf"
      rendered.content.toString(Charsets.UTF_8) shouldBe
        """{"templateId":"$sha","data":{"n":1},"convertTo":"pdf"}"""
      fake.storedTemplates.keys shouldBe setOf(TemplateId(sha))
      fake.rendered shouldHaveSize 1
    }

    should("raise TemplateNotFound for an unknown remote id") {
      val fake = FakeKarbon()
      val result = either { fake.renders.render(TemplateSource.id("missing"), RenderData.Empty) }
      result
        .shouldBeInstanceOf<Either.Left<KarbonError>>()
        .value
        .shouldBeInstanceOf<KarbonError.TemplateNotFound>()
        .id shouldBe TemplateId("missing")
    }

    should("inject one failure with failNextWith, then recover") {
      val fake = FakeKarbon()
      fake.failNextWith(KarbonError.Unauthorized("nope"))
      either { fake.status() }
        .shouldBeInstanceOf<Either.Left<KarbonError>>()
        .value
        .shouldBeInstanceOf<KarbonError.Unauthorized>()
      either { fake.status() }.shouldBeInstanceOf<Either.Right<*>>()
    }

    should("support start/download once and list with pagination") {
      val fake = FakeKarbon()
      val id = fake.addTemplate(content)
      val renderId =
        either { fake.renders.start(TemplateSource.id(id), RenderData.Empty) }
          .shouldBeInstanceOf<Either.Right<RenderId>>()
          .value
      either { fake.renders.download(renderId) }.shouldBeInstanceOf<Either.Right<*>>()
      either { fake.renders.download(renderId) }
        .shouldBeInstanceOf<Either.Left<KarbonError>>()
        .value
        .shouldBeInstanceOf<KarbonError.RenderNotFound>()

      fake.addTemplate("second".toByteArray(), UploadOptions(category = "invoices"))
      val page =
        either { fake.templates.list(ListTemplatesQuery(limit = 1)) }
          .shouldBeInstanceOf<Either.Right<Page<*>>>()
          .value
      page.items shouldHaveSize 1
      page.hasMore shouldBe true
      either { fake.templates.listAll().toList() }
        .shouldBeInstanceOf<Either.Right<List<*>>>()
        .value shouldHaveSize 2
      either { fake.templates.categories() }
        .shouldBeInstanceOf<Either.Right<List<String>>>()
        .value shouldBe listOf("invoices")
    }

    should("use a custom renderer") {
      val fake = FakeKarbon(renderer = FakeKarbon.Renderer { "%PDF-fake".toByteArray() })
      val doc = either {
        fake.renders.render(
          TemplateSource.bytes(content),
          RenderData.Empty,
          RenderOptions.build {
            pdf()
            reportName = "facture"
          },
        )
      }
      val rendered = doc.shouldBeInstanceOf<Either.Right<RenderedDocument>>().value
      rendered.fileName shouldBe "facture.pdf"
      rendered.content.toString(Charsets.UTF_8) shouldBe "%PDF-fake"
    }
  })
