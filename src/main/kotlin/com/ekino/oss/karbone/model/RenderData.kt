/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/** The `data` field of a render request. */
public sealed interface RenderData {

  /** Pre-serialized JSON, for callers that already use another JSON library (Jackson, Gson...). */
  public data class Raw(val json: String) : RenderData

  public data class Element(val json: JsonElement) : RenderData

  /** A value serialized by Karbone with kotlinx.serialization. */
  public class Value<T>(public val value: T, public val serializer: KSerializer<T>) : RenderData {
    override fun toString(): String = "RenderData.Value($value)"
  }

  public companion object {
    /** `{}`: templating runs with an empty data-set, tags resolve to empty. */
    @JvmField public val Empty: RenderData = Element(JsonObject(emptyMap()))

    @JvmStatic public fun raw(json: String): RenderData = Raw(json)

    @JvmStatic public fun of(json: JsonElement): RenderData = Element(json)

    public inline fun <reified T> of(value: T): RenderData = Value(value, serializer<T>())

    public fun <T> of(value: T, serializer: KSerializer<T>): RenderData = Value(value, serializer)
  }
}
