/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.integration

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbone.Karbone
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.model.OutputFormat
import com.ekino.oss.karbone.model.PdfVersion
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.model.UploadedTemplate
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * End-to-end against a real Carbone on-premise container (Community features, authentication
 * disabled). Skipped when Docker is not available.
 */
class CarboneContainerSpec :
  ShouldSpec({
    val dockerAvailable =
      runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

    val container =
      GenericContainer(IMAGE)
        .withExposedPorts(PORT)
        .withEnv("CARBONE_LANG", "fr")
        .withEnv("CARBONE_TIMEZONE", "Europe/Paris")
        .waitingFor(Wait.forHttp("/status").forPort(PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3))

    lateinit var karbone: Karbone
    val template =
      TemplateSource.stream("invoice.docx") {
        CarboneContainerSpec::class.java.getResourceAsStream("/templates/invoice.docx")!!
      }
    val data =
      RenderData.of(
        buildJsonObject {
          put("number", "F-2026-001")
          putJsonObject("customer") { put("name", "ACME") }
          put("total", 1234.5)
          put("status", "PAID")
        }
      )
    val options = RenderOptions.build {
      pdf { version = PdfVersion.PDF_A_3 }
      lang = "fr-fr"
      enum = buildJsonObject { putJsonObject("STATUS") { put("PAID", "Payée") } }
    }

    beforeSpec {
      if (dockerAvailable) {
        container.start()
        karbone = Karbone.onPremise("http://${container.host}:${container.getMappedPort(PORT)}")
      }
    }
    afterSpec { if (dockerAvailable) container.stop() }

    context("carbone-ee container").config(enabled = dockerAvailable) {
      should("report status and version") {
        val status = either { karbone.status() }.orFail()
        status.success shouldBe true
        status.version.shouldNotBeNull() shouldStartWith "5."
      }

      should("upload a template and get its SHA-256 back as legacy id") {
        val uploaded = either { karbone.templates.upload(template) }.orFail()
        uploaded.shouldBeInstanceOf<UploadedTemplate.Legacy>()
        uploaded.id shouldBe template.sha256()
      }

      should("render from bytes hash-first (upload on demand) into a PDF/A-3") {
        either {
          karbone.templates.delete(template.sha256())
        } // make sure the hash-first path has to upload
        val doc = either { karbone.renders.render(template, data, options) }.orFail()
        doc.contentType.shouldNotBeNull() shouldContain "pdf"
        doc.fileName.shouldNotBeNull() shouldContain ".pdf"
        val head = doc.content.copyOf(8).toString(Charsets.ISO_8859_1)
        head shouldStartWith "%PDF-"
        doc.content.toString(Charsets.ISO_8859_1) shouldContain "pdfaid:part>3<"
      }

      should("render a known remote template in two steps") {
        val id = either { karbone.templates.upload(template) }.orFail().id
        val renderId =
          either {
              karbone.renders.start(
                TemplateSource.id(id),
                data,
                RenderOptions.convertTo(OutputFormat.DOCX),
              )
            }
            .orFail()
        val doc = either { karbone.renders.download(renderId) }.orFail()
        doc.content.copyOf(2).toString(Charsets.ISO_8859_1) shouldBe "PK"
      }

      should("convert a document without templating") {
        val doc = either { karbone.renders.convert(template, OutputFormat.PDF) }.orFail()
        doc.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldBe "%PDF-"
      }

      should("download then delete a template, and report TemplateNotFound afterwards") {
        val id = either { karbone.templates.upload(template) }.orFail().id
        either { karbone.templates.download(id) }
          .orFail()
          .content
          .copyOf(2)
          .toString(Charsets.ISO_8859_1) shouldBe "PK"
        either { karbone.templates.delete(id) }.orFail()
        val error = either { karbone.renders.render(TemplateSource.id(id), data) }
        error
          .shouldBeInstanceOf<Either.Left<KarboneError>>()
          .value
          .shouldBeInstanceOf<KarboneError.TemplateNotFound>()
          .id shouldBe id
      }

      should("list templates when template management is available, or fail explicitly") {
        // Community edition may not expose GET /templates; both outcomes are acceptable, but never
        // a crash.
        when (val result = either { karbone.templates.list() }) {
          is Either.Right -> result.value.items.map { it.id }.shouldContain(template.sha256())
          is Either.Left -> result.value.shouldBeInstanceOf<KarboneError.Api>()
        }
      }
    }
  }) {
  companion object {
    const val IMAGE = "carbone/carbone-ee:full-5.8.0-fonts"
    const val PORT = 4000
  }
}

private fun <T> Either<KarboneError, T>.orFail(): T =
  fold({ throw AssertionError("Karbone call failed: ${it.describe()} ($it)") }, { it })
