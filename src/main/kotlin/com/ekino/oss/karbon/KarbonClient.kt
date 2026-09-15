/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon

import arrow.core.raise.Raise
import com.ekino.oss.karbon.model.ApiStatus

/**
 * What consumers should depend on. [Karbon] is the real client;
 * `com.ekino.oss.karbon.testing.FakeKarbon` is an in-memory double.
 */
public interface KarbonClient {
  public val templates: Templates
  public val renders: Renders

  /** `GET /status`. */
  context(_: Raise<KarbonError>)
  public suspend fun status(): ApiStatus
}
