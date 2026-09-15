/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.blocking

import com.ekino.oss.karbone.Karbone
import com.ekino.oss.karbone.KarboneError
import com.ekino.oss.karbone.KarboneException
import com.ekino.oss.karbone.model.PdfVersion
import com.ekino.oss.karbone.model.RenderData
import com.ekino.oss.karbone.model.RenderOptions
import com.ekino.oss.karbone.model.TemplateId
import com.ekino.oss.karbone.model.TemplateSource
import com.ekino.oss.karbone.testing.FakeKarbone
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs

class KarboneBlockingSpec :
  ShouldSpec({
    val docx = "fake docx".toByteArray()

    should("render through the blocking facade of a FakeKarbone and record the call") {
      val fake = FakeKarbone()
      val blocking = fake.blocking()
      val document =
        blocking.renders.render(
          TemplateSource.bytes(docx, "invoice.docx"),
          RenderData.raw("""{"n":1}"""),
          RenderOptions.pdf(PdfVersion.PDF_A_3),
        )
      document.fileName shouldBe "report.pdf"
      fake.rendered shouldHaveSize 1
      fake.rendered.first() shouldBeSameInstanceAs document
      fake.calls.filterIsInstance<FakeKarbone.Call.Render>() shouldHaveSize 1
    }

    should("turn an injected KarboneError into a KarboneException carrying that very value") {
      val fake = FakeKarbone()
      val error = KarboneError.TemplateNotFound(TemplateId("missing"), "Template not found")
      fake.failNextWith(error)
      val exception =
        shouldThrow<KarboneException> { fake.blocking().templates.delete(TemplateId("missing")) }
      exception.error shouldBe error
    }

    should(
      "expose the same facade type from Karbone.blocking(), KarboneClient.blocking() and KarboneBlocking.of()"
    ) {
      val karbone = Karbone.onPremise("http://localhost:1")
      karbone.blocking().shouldBeInstanceOf<KarboneBlocking>()
      (karbone as com.ekino.oss.karbone.KarboneClient)
        .blocking()
        .shouldBeInstanceOf<KarboneBlocking>()
      KarboneBlocking.of(FakeKarbone()).shouldBeInstanceOf<KarboneBlocking>()
    }
  })
