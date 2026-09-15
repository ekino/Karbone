/*
 * Copyright (c) 2026 ekino (https://www.ekino.com/)
 */
package com.ekino.oss.karbon

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class KarbonSpec :
  ShouldSpec({
    should("expose the SDK name") { Karbon.NAME shouldBe "Karbon" }
  })
