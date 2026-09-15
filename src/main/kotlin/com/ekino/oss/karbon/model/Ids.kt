/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon.model

/**
 * Identifies a template: legacy SHA-256 id, versioned template id, or a version id. Carbone accepts
 * all three in the same path segment.
 */
@JvmInline
public value class TemplateId(public val value: String) {
  init {
    require(value.isNotBlank()) { "TemplateId must not be blank" }
  }

  override fun toString(): String = value
}

/**
 * Identifies a generated document, returned by `POST /render/{templateId}` when `download=false`.
 */
@JvmInline
public value class RenderId(public val value: String) {
  init {
    require(value.isNotBlank()) { "RenderId must not be blank" }
  }

  override fun toString(): String = value
}
