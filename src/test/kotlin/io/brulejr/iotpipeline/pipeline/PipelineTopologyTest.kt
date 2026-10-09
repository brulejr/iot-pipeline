/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.integration.channel.AbstractSubscribableChannel
import org.springframework.integration.graph.IntegrationGraphServer
import kotlin.test.assertEquals

/**
 * Guards the shape of the pipeline.
 *
 * No single class declares the workflow: each stage is an independent
 * `@Configuration` that subscribes to one [PipelineChannels] constant and publishes to
 * another, and Spring Integration joins them by name at startup. Nothing checks that
 * the result is the chain anyone intended, so this test asserts it against the graph
 * the framework actually built.
 *
 * The MQTT adapter is disabled here, as it needs a broker, so the edge from the adapter
 * into [PipelineChannels.INGEST] is the one part of the chain left uncovered.
 */
@SpringBootTest(properties = ["pipeline.ingest.mqtt.enabled=false"])
class PipelineTopologyTest {

    @Autowired
    lateinit var graphServer: IntegrationGraphServer

    @Autowired
    lateinit var context: ApplicationContext

    @Value("\${spring.application.name}")
    lateinit var applicationName: String

    /** Which flows subscribe to a channel, and which publish into it. */
    private data class Wiring(
        val subscribers: Set<String> = emptySet(),
        val producers: Set<String> = emptySet(),
    )

    private val pipelineChannels = listOf(
        PipelineChannels.INGEST,
        PipelineChannels.FINGERPRINTED,
        PipelineChannels.DEDUPED,
        PipelineChannels.DUPLICATES,
        PipelineChannels.KNOWN_DEVICE,
        PipelineChannels.UNKNOWN_DEVICE,
        PipelineChannels.INGEST_ERRORS,
    )

    @Test
    fun `the stages form the intended chain`() {
        val expected = mapOf(
            // Produced by the MQTT adapter, which is disabled in this test.
            PipelineChannels.INGEST to Wiring(subscribers = setOf("fingerprintFlow")),
            PipelineChannels.FINGERPRINTED to Wiring(setOf("dedupeFlow"), setOf("fingerprintFlow")),
            PipelineChannels.DEDUPED to Wiring(setOf("classificationFlow"), setOf("dedupeFlow")),
            // Dedupe's discard path.
            PipelineChannels.DUPLICATES to Wiring(setOf("duplicateFlow"), setOf("dedupeFlow")),
            PipelineChannels.UNKNOWN_DEVICE to Wiring(setOf("unknownDeviceStubFlow"), setOf("classificationFlow")),
            // Nothing produces here until the stage 3 promotion gate exists. When it
            // does, this becomes setOf("classificationFlow") and the test should say so.
            PipelineChannels.KNOWN_DEVICE to Wiring(subscribers = setOf("knownDeviceStubFlow")),
            // Fed by the MQTT adapter's error channel, so also uncovered here.
            PipelineChannels.INGEST_ERRORS to Wiring(subscribers = setOf("ingestErrorFlow")),
        )

        assertEquals(expected, actualWiring())
    }

    @Test
    fun `every pipeline channel has exactly one subscriber`() {
        pipelineChannels.forEach { name ->
            val channel = context.getBean(name, AbstractSubscribableChannel::class.java)
            assertEquals(
                1,
                channel.subscriberCount,
                "$name has ${channel.subscriberCount} subscribers. A DirectChannel " +
                    "load-balances rather than broadcasts, so a second subscriber would " +
                    "silently take every other message instead of seeing all of them.",
            )
        }
    }

    /** Reduces the integration graph to the edges between named pipeline channels. */
    private fun actualWiring(): Map<String, Wiring> {
        val graph = graphServer.graph
        val nodesById = graph.nodes.associateBy({ it.nodeId }, { label(it.name) })

        return pipelineChannels.associateWith { channel ->
            val subscribers = mutableSetOf<String>()
            val producers = mutableSetOf<String>()
            graph.links.forEach { link ->
                val from = nodesById[link.from] ?: return@forEach
                val to = nodesById[link.to] ?: return@forEach
                if (from == channel) subscribers += to
                if (to == channel) producers += from
            }
            Wiring(subscribers, producers)
        }
    }

    /**
     * Reduces a graph node name to the bean that owns it: channels keep their own name,
     * while an endpoint such as
     * `iot-pipeline.dedupeFlow.org.springframework.integration...#0` becomes
     * `dedupeFlow`.
     */
    private fun label(name: String): String = name
        .removePrefix("$applicationName.")
        .substringBefore(".org.springframework")
}
