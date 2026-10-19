/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify.web

import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.classify.CurationProblem
import io.brulejr.iotpipeline.classify.CurationResult
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.console.web.Htmx
import io.brulejr.iotpipeline.console.web.Toast
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.parse.ParseService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriUtils

/**
 * The console's model pages: the catalogue, and curating one model's sensor mappings
 * against its structure and what its devices are sending.
 */
@Controller
@RequestMapping("/ui/models")
class ModelPageController(
    private val classification: ClassificationService,
    private val monitor: MonitorService,
    private val parse: ParseService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) deleted: String?,
        model: Model,
    ): String {
        rows(q, status, model)
        model.addAttribute("total", classification.models().size)
        deleted?.let { model.addAttribute("toast", Toast.success("Deleted model $it")) }
        return "ui/models/list"
    }

    /** The catalogue's rows alone, for the filter. */
    @GetMapping("/rows")
    fun rows(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        model: Model,
    ): String {
        val heardByModel = monitor.heard().groupingBy { it.modelFingerprint }.eachCount()
        val models = classification.models()
            .filter { matches(it, q) }
            .filter {
                when (status) {
                    "curated" -> it.recognised
                    "uncurated" -> !it.recognised
                    else -> true
                }
            }
            .sortedWith(compareBy({ !it.recognised }, { it.name?.lowercase() ?: "~" }))
        model.addAttribute("models", models.toMutableList())
        model.addAttribute("heardByModel", heardByModel)
        model.addAttribute("q", q.orEmpty())
        model.addAttribute("status", status ?: "all")
        return "ui/models/list :: rows"
    }

    @GetMapping("/{fingerprint}")
    fun detail(@PathVariable fingerprint: String, model: Model): String {
        val record = find(fingerprint)
        populate(model, record, record.sensors.mapIndexed(SensorRow::of), emptyList())
        return "ui/models/detail"
    }

    /**
     * Saves the curation as submitted, or shows why it was refused. Either way the whole
     * detail is redrawn, since the structure and preview depend on what was saved.
     */
    @PostMapping("/{fingerprint}/curation")
    fun curate(
        @PathVariable fingerprint: String,
        @RequestParam(defaultValue = "") category: String,
        request: HttpServletRequest,
        model: Model,
    ): String {
        val record = find(fingerprint)
        val rows = SensorRow.fromParameters(request.parameterMap).filterNot { it.blank }
        when (val result = classification.curate(fingerprint, category.trim(), rows.map { it.toMapping() })) {
            is CurationResult.Curated -> {
                val saved = result.model
                populate(model, saved, saved.sensors.mapIndexed(SensorRow::of), emptyList())
                val mappings = saved.sensors.size
                model.addAttribute(
                    "toast",
                    Toast.success(
                        if (mappings == 0) "Saved; with no mappings the model is not curated"
                        else "Saved $mappings sensor mapping${if (mappings == 1) "" else "s"}",
                    ),
                )
            }
            is CurationResult.Invalid -> {
                populate(model, record.copy(category = category), rows, result.problems)
                model.addAttribute("toast", Toast.danger("Not saved: fix the problems shown"))
            }
            CurationResult.ModelNotFound -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return "ui/models/detail :: detail"
    }

    /** A blank mapping row, appended to the form by its Add button. */
    @GetMapping("/{fingerprint}/sensor-row")
    fun sensorRow(@PathVariable fingerprint: String, @RequestParam index: Int, model: Model): String {
        val record = find(fingerprint)
        model.addAttribute("row", SensorRow(index))
        model.addAttribute("fields", fields(record))
        model.addAttribute("problemFields", HashSet<String>())
        return "ui/models/detail :: sensorRow"
    }

    /** Forgets the model, then sends the browser back to the catalogue. */
    @DeleteMapping("/{fingerprint}")
    @ResponseBody
    fun delete(@PathVariable fingerprint: String, request: HttpServletRequest, response: HttpServletResponse) {
        val record = find(fingerprint)
        classification.delete(fingerprint)
        val name = UriUtils.encodeQueryParam(record.name ?: fingerprint.take(12), Charsets.UTF_8)
        Htmx.redirect(response, "${request.contextPath}/ui/models?deleted=$name")
    }

    private fun find(fingerprint: String): ModelRecord =
        classification.model(fingerprint) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No model $fingerprint")

    private fun populate(model: Model, record: ModelRecord, rows: List<SensorRow>, problems: List<CurationProblem>) {
        val heard = monitor.heardOfModel(record.fingerprint)
        model.addAttribute("model", record)
        model.addAttribute("rows", rows.toMutableList())
        model.addAttribute("nextIndex", (rows.maxOfOrNull { it.index } ?: -1) + 1)
        model.addAttribute("fields", fields(record))
        // Java collections throughout: SpEL cannot call methods on Kotlin's empty singletons.
        model.addAttribute("problems", problems.toMutableList())
        model.addAttribute("problemFields", problems.map { it.field }.toHashSet())
        model.addAttribute("heard", heard.toMutableList())
        model.addAttribute("preview", heard.firstOrNull()?.let { parse.preview(record.fingerprint, it.payload) }?.toMutableList())
        model.addAttribute("categories", classification.models().map { it.category }
            .filter { it != ModelRecord.UNCATEGORISED }.distinct().sorted())
        model.addAttribute("classnames", (CLASSNAMES + classification.models().flatMap { m -> m.sensors.map { it.classname } })
            .distinct().sorted())
    }

    private fun fields(record: ModelRecord): List<StructureField> {
        val example = monitor.heardOfModel(record.fingerprint).firstOrNull()?.payload
        val mapped = record.sensors.associate { it.name to it.classname }
        return classification.fieldTypes(record).map { (name, type) ->
            StructureField(
                name = name,
                type = type,
                example = example?.findValue(name)?.let { if (it.isValueNode) it.asString() else it.toString() },
                mappedAs = mapped[name],
            )
        }
    }

    private fun matches(model: ModelRecord, q: String?): Boolean {
        if (q.isNullOrBlank()) return true
        val needle = q.trim()
        return listOfNotNull(model.name, model.category, model.fingerprint).any { it.contains(needle, ignoreCase = true) }
    }

    private companion object {
        /** Home Assistant's own classes, offered while typing; any other string is allowed. */
        val CLASSNAMES = listOf(
            "temperature", "humidity", "pressure", "battery", "illuminance", "wind_speed", "wind_direction",
            "precipitation", "moisture", "voltage", "power", "energy", "carbon_dioxide", "signal_strength",
            "opening", "door", "window", "garage_door", "motion", "occupancy", "smoke", "tamper", "problem",
            "vibration", "cold", "heat", "gas", "safety", "sound", "connectivity", "lock", "presence",
        )
    }
}
