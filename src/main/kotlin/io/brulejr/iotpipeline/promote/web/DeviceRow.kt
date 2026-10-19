/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.promote.web

import io.brulejr.iotpipeline.monitor.HeardDevice
import io.brulejr.iotpipeline.promote.PromotedDevice

/** A promoted device, with when it was last heard if it was heard recently. */
data class DeviceRow(val device: PromotedDevice, val heard: HeardDevice?)
