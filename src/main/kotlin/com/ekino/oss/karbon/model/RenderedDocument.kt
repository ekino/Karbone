/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.model

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A generated document. */
public class RenderedDocument(
  public val content: ByteArray,
  public val fileName: String?,
  public val contentType: String?,
) {

  public fun inputStream(): InputStream = ByteArrayInputStream(content)

  public suspend fun writeTo(path: Path): Path =
    withContext(Dispatchers.IO) { path.apply { writeBytes(content) } }

  override fun toString(): String =
    "RenderedDocument(size=${content.size}, fileName=$fileName, contentType=$contentType)"
}
