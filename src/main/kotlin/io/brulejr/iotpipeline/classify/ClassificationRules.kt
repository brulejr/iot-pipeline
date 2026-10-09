/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.ingest.SensorEnvelope

/**
 * One curated rule for recognising a device model.
 *
 * Classification only identifies the model. [parseRuleSetId] names the rule set that
 * knows how to parse payloads from that model, which is applied later, once the
 * device itself has been promoted to known.
 *
 * @property model the model this rule recognises, e.g. `Acurite-Tower`.
 * @property parseRuleSetId identifies the rule set that parses this model's payloads.
 */
class ClassificationRule(
    val model: String,
    val parseRuleSetId: String,
    private val sources: Set<String>,
    private val predicate: (SensorEnvelope) -> Boolean,
) {
    /** True when [envelope] came from an in-scope source and satisfies every constraint. */
    fun matches(envelope: SensorEnvelope): Boolean =
        (sources.isEmpty() || envelope.source in sources) && predicate(envelope)

    override fun toString() = "$model (parsed by $parseRuleSetId)"
}

/** Source annotation stamped by the MQTT adapter; see `pipeline.ingest.mqtt.source`. */
const val RTL433_SOURCE = "rtl433"

/**
 * Declares a curated rule set. Rules are evaluated in declaration order and the first
 * match wins, so narrow rules belong before broad ones.
 */
fun classificationRules(declare: ClassificationRulesBuilder.() -> Unit): List<ClassificationRule> =
    ClassificationRulesBuilder().apply(declare).build()

/** Receiver of the [classificationRules] block. */
class ClassificationRulesBuilder {
    private val rules = mutableListOf<ClassificationRule>()

    /**
     * A rule recognising [model], whose payloads are parsed by [parseRuleSetId].
     * Constrain it in [declare]; a rule with no constraints matches every reading.
     */
    fun rule(model: String, parseRuleSetId: String, declare: RuleSpec.() -> Unit = {}) {
        val spec = RuleSpec().apply(declare)
        rules += ClassificationRule(model, parseRuleSetId, spec.sources(), spec.predicate())
    }

    /**
     * Shorthand for an rtl_433 model. rtl_433 decodes the model itself and reports it
     * in the payload's own `model` field, so matching that field identifies the device.
     * Any [requiredFields] must also be present, which keeps a truncated or
     * mis-decoded payload from being claimed by the rule.
     */
    fun rtl433(model: String, parseRuleSetId: String, vararg requiredFields: String) =
        rule(model, parseRuleSetId) {
            source(RTL433_SOURCE)
            field("model", model)
            if (requiredFields.isNotEmpty()) requireFields(*requiredFields)
        }

    internal fun build(): List<ClassificationRule> = rules.toList()
}

/** Constraints making up one rule. Every constraint must hold for the rule to match. */
class RuleSpec {
    private val sources = mutableSetOf<String>()
    private val predicates = mutableListOf<(SensorEnvelope) -> Boolean>()

    /** Restrict the rule to these envelope sources. Omit to match any source. */
    fun source(vararg names: String) {
        sources += names
    }

    /** Require a payload field to hold exactly [value]. */
    fun field(name: String, value: String) = where { envelope ->
        envelope.payload.get(name)?.takeIf { it.isValueNode }?.asString() == value
    }

    /** Require every named payload field to be present and non-null. */
    fun requireFields(vararg names: String) = where { envelope ->
        names.all { envelope.payload.get(it)?.isNull == false }
    }

    /** Escape hatch for conditions the helpers above cannot express. */
    fun where(predicate: (SensorEnvelope) -> Boolean) {
        predicates += predicate
    }

    internal fun sources(): Set<String> = sources.toSet()

    internal fun predicate(): (SensorEnvelope) -> Boolean {
        val all = predicates.toList()
        return { envelope -> all.all { it(envelope) } }
    }
}
