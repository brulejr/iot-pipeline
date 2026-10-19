/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.monitor

import io.brulejr.iotpipeline.classify.DeviceKey

/**
 * What one live stream wants to see.
 *
 * @property deviceKey only this device, when set. Any key may be watched, promoted or
 *   not, heard before or not.
 * @property text only devices whose id or model name contains this, ignoring case.
 * @property scope only promoted devices, only unpromoted ones, or both.
 * @property changesOnly only readings where something the device sensed changed since its
 *   previous transmission, or that are the first heard from it. Leaves out the steady
 *   reports of everything nobody is touching.
 */
data class MonitorFilter(
    val deviceKey: DeviceKey? = null,
    val text: String? = null,
    val scope: MonitorScope = MonitorScope.ALL,
    val changesOnly: Boolean = false,
) {
    fun matches(event: MonitorEvent): Boolean {
        if (deviceKey != null && event.deviceKey != deviceKey) return false
        if (!text.isNullOrBlank()) {
            val haystack = listOfNotNull(event.deviceKey?.id, event.modelName, event.promotion?.name)
            if (haystack.none { it.contains(text.trim(), ignoreCase = true) }) return false
        }
        when (scope) {
            MonitorScope.ALL -> Unit
            MonitorScope.PROMOTED -> if (event.promotion == null) return false
            MonitorScope.UNPROMOTED -> if (event.promotion != null) return false
        }
        return !changesOnly || event.changed || event.firstHeard
    }
}
