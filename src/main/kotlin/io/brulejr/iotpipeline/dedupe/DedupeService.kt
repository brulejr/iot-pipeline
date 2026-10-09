/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.dedupe

import com.github.benmanes.caffeine.cache.Caffeine
import io.brulejr.iotpipeline.fingerprint.FingerprintedReading

/**
 * Discards a reading already seen from the same transmitter inside the dedupe window.
 *
 * Keyed on the device fingerprint, holding that device's last event fingerprint. What
 * counts as "the same reading" is therefore decided by the event fingerprint, and so by
 * `pipeline.fingerprint.excluded-event-fields`: while the hash covers receiver metadata
 * such as `rssi` and `time`, two receivers hearing one transmission produce different
 * hashes and both survive.
 */
class DedupeService(private val datafill: DedupeDatafill) {

    private val lastEventByDevice = Caffeine.newBuilder()
        .expireAfterWrite(datafill.window)
        .maximumSize(datafill.maximumDevices)
        .build<String, String>()

    /** True when [reading] should continue down the pipeline. */
    fun isUnique(reading: FingerprintedReading): Boolean {
        if (!datafill.enabled) return true
        val device = reading.fingerprint.device
        val event = reading.fingerprint.event
        if (lastEventByDevice.getIfPresent(device) == event) return false
        lastEventByDevice.put(device, event)
        return true
    }
}
