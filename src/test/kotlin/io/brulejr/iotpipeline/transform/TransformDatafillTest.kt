/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class TransformDatafillTest {

    @Test
    fun `a wildcard in a prefix is rejected`() {
        assertFailsWith<IllegalArgumentException> { TransformDatafill.HomeAssistant(discoveryPrefix = "ha/#") }
        assertFailsWith<IllegalArgumentException> { TransformDatafill.HomeAssistant(statePrefix = "a/+/b") }
    }

    @Test
    fun `an edge slash in a prefix is rejected`() {
        assertFailsWith<IllegalArgumentException> { TransformDatafill.HomeAssistant(statePrefix = "/iot") }
        assertFailsWith<IllegalArgumentException> { TransformDatafill.HomeAssistant(statePrefix = "iot/") }
    }

    @Test
    fun `a blank prefix is rejected`() {
        assertFailsWith<IllegalArgumentException> { TransformDatafill.HomeAssistant(discoveryPrefix = " ") }
    }
}
