/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.internal.wire

import com.ekino.oss.karbon.KarbonError
import com.ekino.oss.karbon.internal.http.HttpResponseSpec
import com.ekino.oss.karbon.model.RenderId
import com.ekino.oss.karbon.model.TemplateId
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun httpResponse(status: Int, body: String = "") =
  HttpResponseSpec(status, emptyMap(), body.toByteArray(Charsets.UTF_8))

class ResponsesSpec :
  ShouldSpec({
    context("fileName") {
      should("extract a plain quoted filename") {
        Responses.fileName("attachment; filename=\"report.pdf\"") shouldBe "report.pdf"
      }

      should("prefer the RFC 5987 extended filename over the plain one") {
        Responses.fileName(
          "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"
        ) shouldBe "résumé.pdf"
      }

      should("extract a bare, unquoted filename") {
        Responses.fileName("attachment; filename=report.pdf") shouldBe "report.pdf"
      }

      should("return null when there is no Content-Disposition header") {
        Responses.fileName(null).shouldBeNull()
      }
    }

    context("apiError") {
      should("map 401 to Unauthorized with the raw message") {
        val root = buildJsonObject { put("error", "Invalid token") }
        val error = Responses.apiError(httpResponse(401), root, NotFoundHint.None, render = false)
        error.shouldBeInstanceOf<KarbonError.Unauthorized>()
        error.message shouldBe "Invalid token"
      }

      should("map 404 with a Template hint to TemplateNotFound") {
        val id = TemplateId("tpl-1")
        val root = buildJsonObject { put("error", "not found") }
        val error =
          Responses.apiError(httpResponse(404), root, NotFoundHint.Template(id), render = false)
        error.shouldBeInstanceOf<KarbonError.TemplateNotFound>()
        error.id shouldBe id
        error.message shouldBe "not found"
      }

      should("map 404 with a Render hint to RenderNotFound") {
        val id = RenderId("r1.pdf")
        val root = buildJsonObject { put("error", "gone") }
        val error =
          Responses.apiError(httpResponse(404), root, NotFoundHint.Render(id), render = false)
        error.shouldBeInstanceOf<KarbonError.RenderNotFound>()
        error.id shouldBe id
      }

      should("map 404 with no hint to Unexpected") {
        val root: JsonObject? = null
        val error =
          Responses.apiError(httpResponse(404, "not found"), root, NotFoundHint.None, false)
        error.shouldBeInstanceOf<KarbonError.Unexpected>()
        error.status shouldBe 404
      }

      should("map 413 to PayloadTooLarge") {
        Responses.apiError(httpResponse(413), null, NotFoundHint.None, false)
          .shouldBeInstanceOf<KarbonError.PayloadTooLarge>()
      }

      should("map 415 to UnsupportedTemplateFormat") {
        Responses.apiError(httpResponse(415), null, NotFoundHint.None, false)
          .shouldBeInstanceOf<KarbonError.UnsupportedTemplateFormat>()
      }

      should("map 400 and 422 to BadRequest") {
        val badRequest400 = Responses.apiError(httpResponse(400), null, NotFoundHint.None, false)
        badRequest400.shouldBeInstanceOf<KarbonError.BadRequest>()
        badRequest400.status shouldBe 400

        val badRequest422 = Responses.apiError(httpResponse(422), null, NotFoundHint.None, false)
        badRequest422.shouldBeInstanceOf<KarbonError.BadRequest>()
        badRequest422.status shouldBe 422
      }

      should("map 500 to RenderFailed when render is true, Unexpected otherwise") {
        Responses.apiError(httpResponse(500), null, NotFoundHint.None, render = true)
          .shouldBeInstanceOf<KarbonError.RenderFailed>()
        Responses.apiError(httpResponse(500), null, NotFoundHint.None, render = false)
          .shouldBeInstanceOf<KarbonError.Unexpected>()
      }

      should("map 503 to Unexpected") {
        Responses.apiError(httpResponse(503), null, NotFoundHint.None, false)
          .shouldBeInstanceOf<KarbonError.Unexpected>()
      }
    }
  })
