/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A generated document. */
public class RenderedDocument(
  /** Document bytes. */
  public val content: ByteArray,
  /** File name from `Content-Disposition`, when Carbone provided one. */
  public val fileName: String?,
  /** MIME type from the response, when Carbone provided one. */
  public val contentType: String?,
) {

  /** Wraps [content] in a fresh [InputStream]. */
  public fun inputStream(): InputStream = ByteArrayInputStream(content)

  /** Writes [content] to [path], overwriting it if it exists. */
  public suspend fun writeTo(path: Path): Path =
    withContext(Dispatchers.IO) { path.apply { writeBytes(content) } }

  override fun toString(): String =
    "RenderedDocument(size=${content.size}, fileName=$fileName, contentType=$contentType)"
}
