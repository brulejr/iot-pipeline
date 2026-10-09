/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.fingerprint

import io.brulejr.iotpipeline.ingest.SensorEnvelope
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Derives a reading's [Fingerprint].
 *
 * Every hash is taken over JSON with object keys sorted, so two readings that differ
 * only in field order hash alike. Field exclusions are matched by name at any depth.
 */
class FingerprintService(
    private val datafill: FingerprintDatafill,
    private val jsonMapper: JsonMapper,
) {

    fun fingerprint(envelope: SensorEnvelope): Fingerprint {
        val payload = envelope.payload
        val modelStructure = modelStructure(payload)
        return Fingerprint(
            event = sha256Hex(canonicalJson(exclude(payload, datafill.excludedEventFields))),
            device = deviceHash(envelope),
            model = sha256Hex(modelStructure),
            modelStructure = modelStructure,
        )
    }

    /**
     * The transmitter's identity. Deliberately excludes the receiving antenna, so one
     * device heard by two receivers hashes the same.
     */
    private fun deviceHash(envelope: SensorEnvelope): String {
        val identity = jsonMapper.createObjectNode()
        identity.put("source", envelope.source)
        identity.put("model", envelope.payload.stringField("model"))
        identity.put("channel", envelope.payload.stringField("channel"))
        identity.put("id", envelope.payload.stringField("id"))
        return sha256Hex(jsonMapper.writeValueAsString(identity))
    }

    /**
     * The payload with values replaced by the name of their type, so the result
     * describes the shape of a reading rather than its content.
     */
    internal fun modelStructure(payload: JsonNode): String =
        canonicalJson(toStructure(exclude(payload, datafill.excludedModelFields)))

    /** Drops every field named in [excluded], at any depth. Arrays are left alone. */
    internal fun exclude(node: JsonNode, excluded: Set<String>): JsonNode {
        if (!datafill.enabled || excluded.isEmpty() || !node.isObject) return node
        val out = jsonMapper.createObjectNode()
        node.properties()
            .filterNot { (name, _) -> name in excluded }
            .forEach { (name, child) -> out.set(name, exclude(child, excluded)) }
        return out
    }

    private fun toStructure(node: JsonNode): JsonNode = when {
        node.isObject -> jsonMapper.createObjectNode().also { out ->
            node.properties().forEach { (name, child) -> out.set(name, toStructure(child)) }
        }
        // The element type of a heterogeneous array is not part of the model's shape.
        node.isArray -> jsonMapper.nodeFactory.textNode("array")
        node.isNull -> jsonMapper.nodeFactory.textNode("null")
        node.isBoolean -> jsonMapper.nodeFactory.textNode("boolean")
        node.isNumber -> jsonMapper.nodeFactory.textNode("number")
        node.isTextual -> jsonMapper.nodeFactory.textNode("string")
        else -> jsonMapper.nodeFactory.textNode("unknown")
    }

    /** Serialises with object keys sorted at every depth, so hashing is order-stable. */
    private fun canonicalJson(node: JsonNode): String = jsonMapper.writeValueAsString(sortKeys(node))

    private fun sortKeys(node: JsonNode): JsonNode = when {
        node.isObject -> jsonMapper.createObjectNode().also { out ->
            node.properties()
                .sortedBy { (name, _) -> name }
                .forEach { (name, child) -> out.set(name, sortKeys(child)) }
        }
        else -> node
    }

    private fun JsonNode.stringField(name: String): String? =
        get(name)?.takeIf { it.isValueNode }?.asString()

}
