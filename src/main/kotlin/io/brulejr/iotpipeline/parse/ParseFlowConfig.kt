/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.parse

import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.promote.PromotedReading
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/**
 * Reads every admitted reading through its model's sensor mappings, turning a payload
 * the pipeline does not understand into values it does.
 *
 * This is the last step before storage, which the design calls the neutral handoff
 * boundary: nothing past here needs to know the reading came from rtl_433.
 */
@Configuration
class ParseFlowConfig {

    @Bean
    fun readingParser() = ReadingParser()

    @Bean
    fun parseFlow(parser: ReadingParser) = integrationFlow(PipelineChannels.KNOWN_DEVICE) {
        transform<PromotedReading> { parser.parse(it) }
        log<ParsedReading>(LoggingHandler.Level.INFO, "pipeline.parse") { message ->
            val reading = message.payload
            val values = reading.values.joinToString(", ") { value ->
                when (value) {
                    is SensorValue.Analog -> "${value.mapping.classname}=${value.value}"
                    is SensorValue.Binary -> "${value.mapping.classname}=${value.on}"
                }
            }
            val expected = reading.model.sensors.size
            val short = if (reading.values.size < expected) {
                " (${reading.values.size} of $expected mappings yielded a value)"
            } else {
                ""
            }
            "${reading.device.name} in ${reading.device.area}: $values$short"
        }
        channel(PipelineChannels.PARSED)
    }
}
