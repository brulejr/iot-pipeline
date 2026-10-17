/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.transform.homeassistant

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HomeAssistantDeviceClassesTest {

    @Test
    fun `an opening on a door, window or garage door takes that class`() {
        assertEquals("door", homeAssistantBinaryClassFor("opening", "door"))
        assertEquals("window", homeAssistantBinaryClassFor("opening", "window"))
        assertEquals("garage_door", homeAssistantBinaryClassFor("opening", "garage door"))
    }

    @Test
    fun `the device type is matched loosely`() {
        assertEquals("door", homeAssistantBinaryClassFor("opening", " Door "))
        assertEquals("garage_door", homeAssistantBinaryClassFor("opening", "Garage-Door"))
        assertEquals("garage_door", homeAssistantBinaryClassFor("opening", "garage_door"))
    }

    @Test
    fun `an opening on any other type stays an opening`() {
        assertEquals("opening", homeAssistantBinaryClassFor("opening", "contact"))
        assertEquals("opening", homeAssistantBinaryClassFor("opening", "doorbell"))
    }

    @Test
    fun `other classes are never refined`() {
        assertEquals("battery", homeAssistantBinaryClassFor("battery", "door"))
        assertEquals("tamper", homeAssistantBinaryClassFor("tamper", "window"))
    }
}
