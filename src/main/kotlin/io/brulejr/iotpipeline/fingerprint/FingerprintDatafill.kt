/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property enabled when false, field exclusions are skipped and every field counts
 *   towards both hashes.
 * @property excludedModelFields field names ignored when deriving the model structure,
 *   matched by name at any depth. Receiver and transport metadata belongs here: it says
 *   nothing about what kind of device sent the reading, and a receiver that omits one of
 *   these fields would otherwise look like a different model.
 * @property excludedEventFields field names ignored when hashing the reading itself.
 *   Anything that varies between two deliveries of one transmission belongs here,
 *   otherwise the duplicate is not recognised as one: rtl_433 repeats a decode with
 *   slightly different `rssi` and `time`, and two receivers differ by more. Measured
 *   against a live stream, excluding these roughly doubled the duplicates caught.
 *   Set to empty to make every field significant, which catches only byte-identical
 *   repeats.
 */
@ConfigurationProperties("pipeline.fingerprint")
data class FingerprintDatafill(
    val enabled: Boolean = true,
    val excludedModelFields: Set<String> = DEFAULT_EXCLUDED_MODEL_FIELDS,
    val excludedEventFields: Set<String> = DEFAULT_RADIO_METADATA_FIELDS,
) {
    companion object {
        /**
         * rtl_433 receiver and demodulation metadata, present on every event. None of
         * it describes the reading or the kind of device that sent it.
         *
         * Safe to exclude from the event hash only because the dedupe window is short:
         * two genuinely separate transmissions carrying identical values would collapse
         * if the window outlived the sensor's reporting interval.
         */
        val DEFAULT_RADIO_METADATA_FIELDS = setOf(
            "time", "rssi", "snr", "noise", "freq", "freq1", "freq2", "mod",
        )

        val DEFAULT_EXCLUDED_MODEL_FIELDS = DEFAULT_RADIO_METADATA_FIELDS
    }
}
