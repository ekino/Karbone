/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a template comes from: local content (hashable, uploadable) or a template already known to
 * Carbone.
 */
public sealed interface TemplateSource {

  /**
   * A template whose bytes are available locally. Its SHA-256 is the legacy Carbone template id.
   */
  public sealed interface Content : TemplateSource {
    /** File name sent with the upload, when known. */
    public val fileName: String?

    /** Reads the content. Implementations read once and cache the result. */
    public suspend fun bytes(): ByteArray

    /** Legacy template id: SHA-256 of the content, hex encoded. */
    public suspend fun sha256(): TemplateId = TemplateId(sha256Hex(bytes()))
  }

  /** Content already held in memory. */
  public class Bytes(private val content: ByteArray, override val fileName: String? = null) :
    Content {
    override suspend fun bytes(): ByteArray = content

    override fun toString(): String =
      "TemplateSource.Bytes(size=${content.size}, fileName=$fileName)"
  }

  /** Content read from a local file; [fileName] is the file's own name. */
  public class File(public val path: Path) : Content {
    override val fileName: String = path.name
    private val cached: ByteArray by lazy { path.readBytes() }

    override suspend fun bytes(): ByteArray = withContext(Dispatchers.IO) { cached }

    override fun toString(): String = "TemplateSource.File($path)"
  }

  /** Content read lazily from a caller-supplied [InputStream], opened once and cached. */
  public class Stream(override val fileName: String? = null, private val open: () -> InputStream) :
    Content {
    private val cached: ByteArray by lazy { open().use { it.readAllBytes() } }

    override suspend fun bytes(): ByteArray = withContext(Dispatchers.IO) { cached }

    override fun toString(): String = "TemplateSource.Stream(fileName=$fileName)"
  }

  /** A template already stored on Carbone, referenced by id. */
  public data class Remote(
    /** Legacy SHA-256, versioned template id, or version id: Carbone accepts all three. */
    val id: TemplateId
  ) : TemplateSource

  /** Factory functions for each [TemplateSource] variant. */
  public companion object {
    /** Content held in memory; see [Bytes]. */
    @JvmStatic
    @JvmOverloads
    public fun bytes(content: ByteArray, fileName: String? = null): Content =
      Bytes(content, fileName)

    /** Content read from a local file; see [File]. */
    @JvmStatic public fun file(path: Path): Content = File(path)

    /** Content read lazily from a stream opened by [open]; see [Stream]. */
    @JvmStatic
    @JvmOverloads
    public fun stream(fileName: String? = null, open: () -> InputStream): Content =
      Stream(fileName, open)

    /** References an existing Carbone template by its string id; see [Remote]. */
    @JvmStatic public fun id(id: String): Remote = Remote(TemplateId(id))

    /** References an existing Carbone template by [TemplateId]; see [Remote]. */
    @JvmStatic public fun id(id: TemplateId): Remote = Remote(id)

    /** SHA-256 of [bytes], hex encoded (lowercase). */
    @JvmStatic
    public fun sha256Hex(bytes: ByteArray): String =
      MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        String.format(java.util.Locale.ROOT, "%02x", it)
      }
  }
}
