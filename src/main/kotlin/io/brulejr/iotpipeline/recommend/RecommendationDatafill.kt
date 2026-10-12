/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Defaults are calibrated against observed rtl_433 traffic, and on few samples, so they
 * are worth revisiting as more devices are seen.
 *
 * @property enabled when false, sightings are not recorded.
 * @property windowHours how far back scoring looks.
 * @property minHoursHeard how many distinct hours of the window a device must be heard
 *   in to be recommended. Persistence rather than rate: a door sensor that reports once
 *   an hour qualifies, a car parked nearby for half an hour does not.
 * @property signalField the payload field read as signal strength. `snr` rather than
 *   `rssi`: rtl_433 reports RSSI relative to full scale, so strong signals all read close
 *   to zero, while SNR spreads near and far devices apart and does not depend on the
 *   radio's gain.
 * @property signalFloor signal at or below which a device scores no proximity. Distant
 *   devices were observed around 5-6 dB.
 * @property signalCeiling signal at or above which a device scores full proximity. Nearby
 *   devices were observed around 15-17 dB.
 * @property proximityWeight share of the score given to proximity, the rest going to
 *   persistence. Proximity leads because it is the better sign a device is yours rather
 *   than a neighbour's.
 */
@ConfigurationProperties("pipeline.recommendation")
data class RecommendationDatafill(
    val enabled: Boolean = true,
    val windowHours: Int = 24,
    val minHoursHeard: Int = 6,
    val signalField: String = "snr",
    val signalFloor: Double = 5.0,
    val signalCeiling: Double = 17.0,
    val proximityWeight: Double = 0.6,
) {
    init {
        require(windowHours > 0) { "pipeline.recommendation.window-hours must be positive" }
        require(minHoursHeard in 1..windowHours) {
            "pipeline.recommendation.min-hours-heard must be between 1 and window-hours ($windowHours)"
        }
        require(signalCeiling > signalFloor) {
            "pipeline.recommendation.signal-ceiling must be above signal-floor"
        }
        require(proximityWeight in 0.0..1.0) { "pipeline.recommendation.proximity-weight must be within 0..1" }
    }
}
