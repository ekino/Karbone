/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.internal.http

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class MultipartWriterSpec :
  ShouldSpec({
    should("write fields in order before a last file part, ending with the closing boundary") {
      val boundary = "karbone-test-boundary"
      val content = Random(42).nextBytes(256)
      val file =
        FilePart(
          "template",
          "weird \"name\".docx",
          "application/vnd.oasis.opendocument.text",
          content,
        )
      val body =
        HttpBody.Multipart(fields = listOf("name" to "invoice", "category" to "sales"), file = file)

      val bytes = MultipartWriter.write(boundary, body)
      val text = bytes.toString(Charsets.ISO_8859_1)

      val nameIndex = text.indexOf("name=\"name\"")
      val categoryIndex = text.indexOf("name=\"category\"")
      val fileIndex = text.indexOf("name=\"template\"")
      (nameIndex >= 0) shouldBe true
      (nameIndex < categoryIndex) shouldBe true
      (categoryIndex < fileIndex) shouldBe true

      text.contains("filename=\"weird name.docx\"") shouldBe true
      text.contains("Content-Type: application/vnd.oasis.opendocument.text") shouldBe true
      text.endsWith("--$boundary--\r\n") shouldBe true

      val contentTypeMarker = "Content-Type: application/vnd.oasis.opendocument.text\r\n\r\n"
      val start = text.indexOf(contentTypeMarker) + contentTypeMarker.length
      val extracted = bytes.copyOfRange(start, start + content.size)
      extracted shouldBe content

      val afterContent = text.substring(start + content.size, start + content.size + 2)
      afterContent shouldBe "\r\n"
    }

    should("preserve non-UTF8 binary content byte-exactly") {
      val boundary = "b"
      val content = byteArrayOf(0, 1, 2, 127, -1, -2, -128, 10, 13, -100)
      val file = FilePart("template", "raw.bin", "application/octet-stream", content)
      val bytes = MultipartWriter.write(boundary, HttpBody.Multipart(emptyList(), file))
      val text = bytes.toString(Charsets.ISO_8859_1)
      val marker = "Content-Type: application/octet-stream\r\n\r\n"
      val start = text.indexOf(marker) + marker.length
      bytes.copyOfRange(start, start + content.size) shouldBe content
    }

    should("write no field parts when the field list is empty, only the file part") {
      val boundary = "only-file"
      val file = FilePart("template", "f.docx", "application/octet-stream", byteArrayOf(1, 2, 3))
      val bytes = MultipartWriter.write(boundary, HttpBody.Multipart(emptyList(), file))
      val text = bytes.toString(Charsets.ISO_8859_1)
      (text.split("Content-Disposition: form-data;").size - 1) shouldBe 1
    }
  })
