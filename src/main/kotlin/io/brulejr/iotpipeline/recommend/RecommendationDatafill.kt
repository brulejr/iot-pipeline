/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.recommend

import io.brulejr.iotpipeline.pipeline.RegistryType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Defaults are calibrated against observed rtl_433 traffic, and on few samples, so they
 * are worth revisiting as more devices are seen.
 *
 * @property registry where hourly sightings are kept.
 * @property enabled when false, sightings are not recorded.
 * @property windowHours how far back scoring looks.
 * @property minHoursHeard how many distinct hours of the window a device must be heard
 *   in to be recommended. Persistence rather than rate: a door sensor that reports once
 *   an hour qualifies, a car parked nearby for half an hour does not. Buckets are clock
 *   hours, so 2 is barely a filter, since two readings a minute apart either side of
 *   the hour satisfy it; 3 requires a device to be around across two hour boundaries,
 *   which is more than an hour, and is the smallest value that means anything.
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
 * @property minProximity proximity a device must reach to be recommended, however long
 *   it is around. Persistence alone would flag a neighbour's sensor, which is around all
 *   day too; proximity is what tells it from yours. 0.5 is about 11 dB on the default
 *   scale: observed nearby devices sat near 16 dB, distant ones near 6-8. Does not apply
 *   to a device whose readings carry no signal, since there is then nothing to judge by.
 */
@ConfigurationProperties("pipeline.recommendation")
data class RecommendationDatafill(
    val registry: RegistryType = RegistryType.POSTGRES,
    val enabled: Boolean = true,
    val windowHours: Int = 24,
    val minHoursHeard: Int = 3,
    val signalField: String = "snr",
    val signalFloor: Double = 5.0,
    val signalCeiling: Double = 17.0,
    val proximityWeight: Double = 0.6,
    val minProximity: Double = 0.5,
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
        require(minProximity in 0.0..1.0) { "pipeline.recommendation.min-proximity must be within 0..1" }
    }
}
