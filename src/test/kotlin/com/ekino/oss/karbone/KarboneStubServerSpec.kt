/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import arrow.core.Either
import arrow.core.raise.either
import com.ekino.oss.karbone.model.ApiStatus
import com.ekino.oss.karbone.model.AsyncRenderAccepted
import com.ekino.oss.karbone.model.OutputFormat
import com.ekino.oss.karbone.model.Page
import com.ekino.oss.karbone.model.PdfVersion
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderId
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.RenderedDocument
import com.ekino.oss.karbone.model.TemplateFile
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateInfo
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.model.UploadOptions
import com.ekino.oss.karbone.model.UploadedTemplate
import com.ekino.oss.karbone.model.Webhook
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val parser = Json { ignoreUnknownKeys = true }

private fun sha256Hex(bytes: ByteArray): String =
  MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private data class Recorded(
  val method: String,
  val path: String,
  val query: String?,
  val headers: Map<String, List<String>>,
  val body: ByteArray,
) {
  fun header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

  fun bodyAsJson(): JsonObject = parser.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
}

private fun HttpExchange.respondJson(status: Int, body: String) {
  val bytes = body.toByteArray(Charsets.UTF_8)
  responseHeaders.set("Content-Type", "application/json")
  sendResponseHeaders(status, bytes.size.toLong())
  responseBody.use { it.write(bytes) }
}

private fun HttpExchange.respondBinary(
  status: Int,
  contentType: String,
  fileName: String?,
  content: ByteArray,
) {
  responseHeaders.set("Content-Type", contentType)
  fileName?.let { responseHeaders.set("Content-Disposition", "attachment; filename=\"$it\"") }
  sendResponseHeaders(status, content.size.toLong())
  responseBody.use { it.write(content) }
}

/** A stub Carbone server, bound to an ephemeral port, recording every request it receives. */
private class StubServer {
  private val server: HttpServer =
    HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

  val requests: CopyOnWriteArrayList<Recorded> = CopyOnWriteArrayList()
  var handler: HttpHandler = HttpHandler { it.sendResponseHeaders(500, -1) }

  init {
    server.createContext("/") { exchange ->
      exchange.use { exchange ->
        val body = exchange.requestBody.readBytes()
        requests.add(
          Recorded(
            exchange.requestMethod,
            exchange.requestURI.path,
            exchange.requestURI.rawQuery,
            exchange.requestHeaders,
            body,
          )
        )
        handler.handle(exchange)
      }
    }
    server.start()
  }

  val baseUrl: String
    get() = "http://127.0.0.1:${server.address.port}"

  fun reset() {
    requests.clear()
    handler = HttpHandler { it.sendResponseHeaders(500, -1) }
  }

  fun stop() = server.stop(0)
}

private const val STATUS_BODY = """{"success":true,"code":200,"message":"OK","version":"5.8.0"}"""

class KarboneStubServerSpec :
  ShouldSpec({
    val stub = StubServer()

    beforeSpec {}

    afterSpec { stub.stop() }

    beforeEach { stub.reset() }

    should("parse status() from a plain JSON envelope") {
      stub.handler = HttpHandler { ex -> ex.respondJson(200, STATUS_BODY) }
      val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
      val result = either { karbone.status() }
      result.shouldBeInstanceOf<Either.Right<ApiStatus>>().value shouldBe
        ApiStatus(true, 200, "OK", "5.8.0")
    }

    context("headers") {
      should(
        "send Authorization: Bearer and no carbone-version on-premise, and a karbone/ User-Agent"
      ) {
        stub.handler = HttpHandler { ex -> ex.respondJson(200, STATUS_BODY) }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        either { karbone.status() }
        val request = stub.requests.single()
        request.header("Authorization") shouldBe "Bearer tok"
        request.header("carbone-version").shouldBeNull()
        request.header("User-Agent").shouldNotBeNull().shouldStartWith("karbone/")
      }

      should("send carbone-version: 5 for cloud clients") {
        stub.handler = HttpHandler { ex -> ex.respondJson(200, STATUS_BODY) }
        val karbone = Karbone.cloud("api-key") { baseUrl(stub.baseUrl) }
        either { karbone.status() }
        stub.requests.single().header("carbone-version") shouldBe "5"
      }

      should("send no Authorization header when the token is null") {
        stub.handler = HttpHandler { ex -> ex.respondJson(200, STATUS_BODY) }
        val karbone = Karbone.onPremise(stub.baseUrl, token = null)
        either { karbone.status() }
        stub.requests.single().header("Authorization").shouldBeNull()
      }
    }

    context("templates.upload") {
      should("send a multipart request with name before the file part, legacy response") {
        val sha = "deadbeefcafebabefeedface"
        stub.handler = HttpHandler { ex ->
          ex.respondJson(200, """{"success":true,"data":{"templateId":"$sha"}}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val bytes = "hello docx content".toByteArray()
        val result = either {
          karbone.templates.upload(
            TemplateSource.bytes(bytes, "invoice.docx"),
            UploadOptions(name = "x"),
          )
        }
        val uploaded = result.shouldBeInstanceOf<Either.Right<UploadedTemplate>>().value
        uploaded.shouldBeInstanceOf<UploadedTemplate.Legacy>()
        uploaded.id shouldBe TemplateId(sha)

        val request = stub.requests.single()
        request.method shouldBe "POST"
        request.path shouldBe "/template"
        val text = request.body.toString(Charsets.ISO_8859_1)
        (text.indexOf("name=\"name\"") in 0 until text.indexOf("name=\"template\"")) shouldBe true
      }

      should("parse a versioned upload response, mapping deployedAt=0 to null") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(
            200,
            """{"success":true,"data":{"id":"abc","versionId":"def","type":"docx","size":10,"createdAt":1700000000,"deployedAt":0}}""",
          )
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.templates.upload(TemplateSource.bytes("x".toByteArray(), "t.docx"))
        }
        val uploaded = result.shouldBeInstanceOf<Either.Right<UploadedTemplate>>().value
        val versioned = uploaded.shouldBeInstanceOf<UploadedTemplate.Versioned>()
        versioned.id shouldBe TemplateId("abc")
        versioned.versionId shouldBe TemplateId("def")
        versioned.deployedAt.shouldBeNull()
      }
    }

    context("renders.render") {
      should("POST /render/{id}?download=true, send the JSON body and return the file") {
        val pdfBytes = byteArrayOf(1, 2, 3, 4)
        stub.handler = HttpHandler { ex ->
          ex.respondBinary(200, "application/pdf", "report.pdf", pdfBytes)
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.renders.render(
            TemplateSource.id("abc"),
            RenderData.Empty,
            RenderOptions.pdf(PdfVersion.PDF_A_3),
          )
        }
        val document = result.shouldBeInstanceOf<Either.Right<RenderedDocument>>().value
        document.content shouldBe pdfBytes
        document.fileName shouldBe "report.pdf"
        document.contentType shouldBe "application/pdf"

        val request = stub.requests.single()
        request.method shouldBe "POST"
        request.path shouldBe "/render/abc"
        request.query shouldBe "download=true"
        val body = request.bodyAsJson()
        body["data"]!!.jsonObject shouldBe JsonObject(emptyMap())
        body["convertTo"]!!
          .jsonObject["formatOptions"]!!
          .jsonObject["SelectPdfVersion"]!!
          .jsonPrimitive
          .content shouldBe "3"
      }

      should("upload then retry once when the template is unknown, hash-first flow") {
        val content = "some template content".toByteArray()
        val sha = sha256Hex(content)
        val pdfBytes = byteArrayOf(9, 9, 9)
        val renderAttempts = AtomicInteger(0)
        stub.handler = HttpHandler { ex ->
          when (ex.requestURI.path) {
            "/render/$sha" ->
              if (renderAttempts.getAndIncrement() == 0) {
                ex.respondJson(404, """{"success":false,"error":"Template not found"}""")
              } else {
                ex.respondBinary(200, "application/pdf", "out.pdf", pdfBytes)
              }

            "/template" -> ex.respondJson(200, """{"success":true,"data":{"templateId":"$sha"}}""")

            else -> ex.sendResponseHeaders(500, -1)
          }
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.renders.render(TemplateSource.bytes(content), RenderData.Empty)
        }
        result.shouldBeInstanceOf<Either.Right<RenderedDocument>>()
        stub.requests shouldHaveSize 3
        stub.requests[0].path shouldBe "/render/$sha"
        stub.requests[0].method shouldBe "POST"
        stub.requests[1].path shouldBe "/template"
        stub.requests[2].path shouldBe "/render/$sha"
      }

      should("not retry a 404 for a remote TemplateSource.id, raising TemplateNotFound") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(404, """{"success":false,"error":"no such template"}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.renders.render(TemplateSource.id("missing"), RenderData.Empty)
        }
        val error = result.shouldBeInstanceOf<Either.Left<KarboneError>>().value
        val notFound = error.shouldBeInstanceOf<KarboneError.TemplateNotFound>()
        notFound.message shouldBe "no such template"
        stub.requests shouldHaveSize 1
      }

      should("map a 401 to Unauthorized with the raw message") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(401, """{"success":false,"error":"Invalid token"}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.renders.render(TemplateSource.id("abc"), RenderData.Empty)
        }
        val error = result.shouldBeInstanceOf<Either.Left<KarboneError>>().value
        val unauthorized = error.shouldBeInstanceOf<KarboneError.Unauthorized>()
        unauthorized.message shouldBe "Invalid token"
      }

      should("map a 2xx JSON success:false where a file was expected to Unexpected") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(200, """{"success":false,"error":"boom"}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.renders.render(TemplateSource.id("abc"), RenderData.Empty)
        }
        val error = result.shouldBeInstanceOf<Either.Left<KarboneError>>().value
        error.shouldBeInstanceOf<KarboneError.Unexpected>()
      }
    }

    context("renders.start / download") {
      should("start a render returning a RenderId, with download=false") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(200, """{"success":true,"data":{"renderId":"r1.pdf"}}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either {
          karbone.renders.start(TemplateSource.id("xyz"), RenderData.Empty)
        }
        result.shouldBeInstanceOf<Either.Right<RenderId>>().value shouldBe RenderId("r1.pdf")
        stub.requests.single().query shouldBe "download=false"
      }

      should("download a render result via GET /render/{id}") {
        val pdfBytes = byteArrayOf(5, 6, 7)
        stub.handler = HttpHandler { ex ->
          ex.respondBinary(200, "application/pdf", null, pdfBytes)
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either { karbone.renders.download(RenderId("r1.pdf")) }
        val document = result.shouldBeInstanceOf<Either.Right<RenderedDocument>>().value
        document.content shouldBe pdfBytes
        val request = stub.requests.single()
        request.method shouldBe "GET"
        request.path shouldBe "/render/r1.pdf"
      }

      should("map a 404 on download to RenderNotFound") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(404, """{"success":false,"error":"expired"}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either { karbone.renders.download(RenderId("gone.pdf")) }
        result
          .shouldBeInstanceOf<Either.Left<KarboneError>>()
          .value
          .shouldBeInstanceOf<KarboneError.RenderNotFound>()
      }
    }

    should("send carbone-webhook-url and carbone-webhook-header-<name> for startAsync") {
      stub.handler = HttpHandler { ex ->
        ex.respondJson(200, """{"success":true,"message":"queued"}""")
      }
      val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
      val result = either {
        karbone.renders.startAsync(
          TemplateSource.id("xyz"),
          RenderData.Empty,
          Webhook("https://cb", mapOf("Authorization" to "secret")),
        )
      }
      result.shouldBeInstanceOf<Either.Right<AsyncRenderAccepted>>()
      val request = stub.requests.single()
      request.header("carbone-webhook-url") shouldBe "https://cb"
      request.header("carbone-webhook-header-authorization") shouldBe "secret"
    }

    should("convert via POST /render/template with a base64 template field and no data key") {
      val pdfBytes = byteArrayOf(1)
      val doc = "raw document bytes".toByteArray()
      stub.handler = HttpHandler { ex ->
        ex.respondBinary(200, "application/pdf", "out.pdf", pdfBytes)
      }
      val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
      val result = either {
        karbone.renders.convert(TemplateSource.bytes(doc), OutputFormat.PDF)
      }
      result.shouldBeInstanceOf<Either.Right<RenderedDocument>>()
      val request = stub.requests.single()
      request.path shouldBe "/render/template"
      request.query shouldBe "download=true"
      val body = request.bodyAsJson()
      body.containsKey("data") shouldBe false
      body["template"]!!.jsonPrimitive.content shouldBe Base64.getEncoder().encodeToString(doc)
    }

    context("templates") {
      should("delete a template via DELETE /template/{id}") {
        stub.handler = HttpHandler { ex -> ex.respondJson(200, """{"success":true,"data":{}}""") }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either { karbone.templates.delete(TemplateId("t1")) }
        result.shouldBeInstanceOf<Either.Right<Unit>>()
        val request = stub.requests.single()
        request.method shouldBe "DELETE"
        request.path shouldBe "/template/t1"
      }

      should("download a template file, exposing its filename") {
        val bytes = byteArrayOf(1, 2, 3)
        stub.handler = HttpHandler { ex ->
          ex.respondBinary(200, "application/pdf", "modele.pdf", bytes)
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either { karbone.templates.download(TemplateId("t1")) }
        val file = result.shouldBeInstanceOf<Either.Right<TemplateFile>>().value
        file.content shouldBe bytes
        file.fileName shouldBe "modele.pdf"
      }

      should("list templates, reading hasMore/nextCursor at the root, with limit=100") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(
            200,
            """{"success":true,"data":[{"id":"a","name":"A"}],"hasMore":true,"nextCursor":"cursor-1"}""",
          )
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either { karbone.templates.list() }
        val page = result.shouldBeInstanceOf<Either.Right<Page<TemplateInfo>>>().value
        page.items.map { it.id } shouldBe listOf(TemplateId("a"))
        page.hasMore shouldBe true
        page.nextCursor shouldBe "cursor-1"
        stub.requests.single().query.orEmpty().contains("limit=100") shouldBe true
      }

      should("follow the cursor across pages with listAll") {
        stub.handler = HttpHandler { ex ->
          if (ex.requestURI.rawQuery.orEmpty().contains("cursor=page2")) {
            ex.respondJson(
              200,
              """{"success":true,"data":[{"id":"b","name":"B"}],"hasMore":false}""",
            )
          } else {
            ex.respondJson(
              200,
              """{"success":true,"data":[{"id":"a","name":"A"}],"hasMore":true,"nextCursor":"page2"}""",
            )
          }
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val result = either { karbone.templates.listAll().toList() }
        val items = result.shouldBeInstanceOf<Either.Right<List<TemplateInfo>>>().value
        items.map { it.id } shouldBe listOf(TemplateId("a"), TemplateId("b"))
        stub.requests shouldHaveSize 2
      }

      should("map categories() and tags() from [{name}] arrays") {
        stub.handler = HttpHandler { ex ->
          when (ex.requestURI.path) {
            "/templates/categories" ->
              ex.respondJson(200, """{"success":true,"data":[{"name":"Sales"},{"name":"HR"}]}""")
            "/templates/tags" ->
              ex.respondJson(200, """{"success":true,"data":[{"name":"invoice"}]}""")
            else -> ex.sendResponseHeaders(500, -1)
          }
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val categories = either { karbone.templates.categories() }
        val tags = either { karbone.templates.tags() }
        categories.shouldBeInstanceOf<Either.Right<List<String>>>().value shouldBe
          listOf("Sales", "HR")
        tags.shouldBeInstanceOf<Either.Right<List<String>>>().value shouldBe listOf("invoice")
      }
    }

    should("expose baseUrl and apiVersion, and nothing else from the configuration") {
      val cloud = Karbone.cloud("secret-key") { baseUrl("https://carbone.example.com/") }
      cloud.baseUrl shouldBe "https://carbone.example.com"
      cloud.apiVersion shouldBe 5
      val onPremise = Karbone.onPremise("http://localhost:4000", token = "jwt")
      onPremise.apiVersion.shouldBeNull()
      Karbone::class.java.methods.map { it.name } shouldNotContain "getConfig"
    }

    should("map an unparsable 2xx body to KarboneError.Serialization instead of throwing") {
      stub.handler = HttpHandler { ex -> ex.respondJson(200, "<html>not json</html>") }
      val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
      val result = either { karbone.status() }
      result
        .shouldBeInstanceOf<Either.Left<KarboneError>>()
        .value
        .shouldBeInstanceOf<KarboneError.Serialization>()
    }

    should("map a connection failure to KarboneError.Transport") {
      val karbone = Karbone.onPremise("http://127.0.0.1:1") { connectTimeout = 1.seconds }
      val result = either { karbone.status() }
      result
        .shouldBeInstanceOf<Either.Left<KarboneError>>()
        .value
        .shouldBeInstanceOf<KarboneError.Transport>()
    }

    context("blocking facade") {
      should("return successfully for a 2xx response") {
        stub.handler = HttpHandler { ex -> ex.respondJson(200, STATUS_BODY) }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        karbone.blocking().status() shouldBe ApiStatus(true, 200, "OK", "5.8.0")
      }

      should("throw KarboneException wrapping Unauthorized on a 401") {
        stub.handler = HttpHandler { ex ->
          ex.respondJson(401, """{"success":false,"error":"Invalid token"}""")
        }
        val karbone = Karbone.onPremise(stub.baseUrl, token = "tok")
        val exception =
          shouldThrow<KarboneException> {
            karbone.blocking().renders.render(TemplateSource.id("abc"), RenderData.Empty)
          }
        exception.error.shouldBeInstanceOf<KarboneError.Unauthorized>()
      }
    }
  })
