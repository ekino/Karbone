/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.integration

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbon.Karbon
import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.model.PdfVersion
import com.ekino.oss.karbon.model.RenderData
import com.ekino.oss.karbon.model.RenderOptions
import com.ekino.oss.karbon.model.TemplateSource
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
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
 * Same container as [CarboneContainerSpec] but with `CARBONE_AUTHENTICATION=true` and a JWT, as
 * iperia-back runs it.
 */
class CarboneAuthContainerSpec :
  ShouldSpec({
    val dockerAvailable =
      runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
    val publicKey = resource("/auth/carbone-test.pub").replace("\n", "\\n")
    val token = resource("/auth/carbone-test.jwt").trim()

    val container =
      GenericContainer(CarboneContainerSpec.IMAGE)
        .withExposedPorts(CarboneContainerSpec.PORT)
        .withEnv("CARBONE_AUTHENTICATION", "true")
        .withEnv("CARBONE_AUTHENTICATION_PUBLIC_KEY", publicKey)
        .waitingFor(Wait.forHttp("/status").forPort(CarboneContainerSpec.PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3))

    lateinit var baseUrl: String
    val template =
      TemplateSource.stream("invoice.docx") {
        CarboneAuthContainerSpec::class.java.getResourceAsStream("/templates/invoice.docx")!!
      }
    val data =
      RenderData.of(
        buildJsonObject {
          put("number", "F-1")
          putJsonObject("customer") { put("name", "ACME") }
          put("total", 10)
          put("status", "PAID")
        }
      )

    beforeSpec {
      if (dockerAvailable) {
        container.start()
        baseUrl = "http://${container.host}:${container.getMappedPort(CarboneContainerSpec.PORT)}"
      }
    }
    afterSpec { if (dockerAvailable) container.stop() }

    context("carbone-ee container with authentication").config(enabled = dockerAvailable) {
      should("answer /status without a token") {
        val status = either { Karbon.onPremise(baseUrl).status() }
        status.shouldBeInstanceOf<Either.Right<*>>()
      }

      should("reject a render without a token as Unauthorized, keeping the server message") {
        val result = either { Karbon.onPremise(baseUrl).renders.render(template, data) }
        val error =
          result
            .shouldBeInstanceOf<Either.Left<KarbonError>>()
            .value
            .shouldBeInstanceOf<KarbonError.Unauthorized>()
        error.status shouldBe 401
      }

      should("reject a forged token as Unauthorized") {
        val result = either {
          Karbon.onPremise(baseUrl, token = "not-a-jwt").renders.render(template, data)
        }
        result
          .shouldBeInstanceOf<Either.Left<KarbonError>>()
          .value
          .shouldBeInstanceOf<KarbonError.Unauthorized>()
      }

      should("upload and render with a valid JWT") {
        val karbon = Karbon.onPremise(baseUrl, token = token)
        val doc = either {
          karbon.renders.render(template, data, RenderOptions.pdf(PdfVersion.PDF_A_3))
        }
        val pdf = doc.fold({ throw AssertionError("render failed: ${it.describe()}") }, { it })
        pdf.content.copyOf(5).toString(Charsets.ISO_8859_1) shouldStartWith "%PDF-"
        either { karbon.templates.delete(template.sha256()) }.shouldBeInstanceOf<Either.Right<*>>()
      }
    }
  })

private fun resource(path: String): String =
  CarboneAuthContainerSpec::class.java.getResourceAsStream(path)?.use {
    it.readAllBytes().toString(Charsets.UTF_8)
  } ?: error("missing test resource $path")
