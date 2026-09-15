/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbone

import arrow.core.raise.Raise
import com.ekino.oss.karbone.model.ApiStatus

/**
 * What consumers should depend on. [Karbone] is the real client;
 * `com.ekino.oss.karbone.testing.FakeKarbone` is an in-memory double.
 */
public interface KarboneClient {
  public val templates: Templates
  public val renders: Renders

  /** `GET /status`. */
  context(_: Raise<KarboneError>)
  public suspend fun status(): ApiStatus
}
