/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.console.web

import io.brulejr.iotpipeline.classify.ClassificationService
import io.brulejr.iotpipeline.classify.DeviceKey
import io.brulejr.iotpipeline.classify.ModelRecord
import io.brulejr.iotpipeline.ingest.SensorEnvelope
import io.brulejr.iotpipeline.monitor.MonitorService
import io.brulejr.iotpipeline.pipeline.PipelineChannels
import io.brulejr.iotpipeline.promote.PromotedDevice
import io.brulejr.iotpipeline.promote.PromotionService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders every console page through the real application context, so a template that
 * names a property wrongly or a fragment that does not exist fails here rather than in
 * a browser.
 */
@SpringBootTest(
    properties = [
        "pipeline.ingest.mqtt.enabled=false",
        // Keeps the context free of a database. The datasource is still configured, but
        // with in-memory registries and storage off nothing ever connects.
        "pipeline.classification.registry=memory",
        "pipeline.promotion.registry=memory",
        "pipeline.recommendation.registry=memory",
        "pipeline.store.enabled=false",
        // Nothing is published, so no broker is needed or reached.
        "pipeline.publish.enabled=false",
        // Nowhere: 5432 may be a real PostgreSQL, and nothing here should reach it.
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
        // The seeds would fill the registries with the real house; these tests make their own.
        "pipeline.classification.seed.enabled=false",
        "pipeline.promotion.seed.enabled=false",
        // Every test hears the same readings within seconds of the last.
        "pipeline.dedupe.enabled=false",
    ],
)
class ConsolePagesTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var classification: ClassificationService

    @Autowired
    lateinit var promotions: PromotionService

    @Autowired
    lateinit var monitor: MonitorService

    @Autowired
    @Qualifier(PipelineChannels.INGEST)
    lateinit var ingest: MessageChannel

    @Autowired
    lateinit var jsonMapper: JsonMapper

    private lateinit var mvc: MockMvc
    private lateinit var contactModel: ModelRecord

    private val door = DeviceKey("rtl433", "DSC-Security/2328667")
    private val stranger = DeviceKey("rtl433", "Acurite-Tower/A/3064")

    /** Sends a reading down the whole pipeline, as the MQTT adapter would. */
    private fun hear(json: String) {
        val envelope = SensorEnvelope("rtl433", "rtl_433/pi/events", Instant.now(), jsonMapper.readTree(json))
        ingest.send(MessageBuilder.withPayload(envelope).build())
    }

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
        // The context, and its in-memory registries, outlive each test.
        promotions.all().forEach { promotions.demote(it.deviceKey) }
        classification.models().forEach { classification.delete(it.fingerprint) }
        hear("""{"model":"DSC-Security","id":2328667,"closed":1,"event":1,"tamper":0,"snr":15.3}""")
        hear("""{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":21.5,"battery_ok":1,"snr":6.0}""")
        contactModel = checkNotNull(monitor.heard(door)).let { classification.model(it.modelFingerprint)!! }
    }

    @Test
    fun `the root goes to the dashboard`() {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection).andExpect(header().string("Location", "/ui"))
    }

    @Test
    fun `every page renders`() {
        val pages = mapOf(
            "/ui" to "Pipeline health",
            "/ui/overview" to "Top candidates",
            "/ui/models" to "DSC-Security",
            "/ui/models/rows?status=uncurated" to "DSC-Security",
            "/ui/models/${contactModel.fingerprint}" to "Curation",
            "/ui/models/${contactModel.fingerprint}/sensor-row?index=4" to "sensors[4].name",
            "/ui/devices" to "Promoted devices",
            "/ui/devices/rows" to "No promoted devices match",
            "/ui/devices/form" to "Promote a device",
            "/ui/recommendations" to "Recommendations",
            "/ui/recommendations/rows?all=true" to "Acurite-Tower/A/3064",
            "/ui/monitor" to "Heard this past hour",
            "/ui/monitor/feed?scope=UNPROMOTED&changes=true" to "unpromoted devices",
            "/ui/monitor/heard" to "DSC-Security/2328667",
        )
        pages.forEach { (path, text) ->
            mvc.perform(get(path))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString(text)))
        }
    }

    @Test
    fun `an empty state shows only when there is nothing to show`() {
        mvc.perform(get("/ui/overview"))
            .andExpect(content().string(containsString("DSC-Security/2328667")))
            .andExpect(content().string(not(containsString("Nothing heard yet"))))
        mvc.perform(get("/ui/monitor/heard"))
            .andExpect(content().string(not(containsString("Nothing heard yet"))))
    }

    @Test
    fun `any device key has a page, heard or not, promoted or not`() {
        mvc.perform(get("/ui/devices/view").param("source", "rtl433").param("id", door.id))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("not promoted")))
            .andExpect(content().string(containsString("closed")))
        mvc.perform(get("/ui/devices/view").param("id", "Never/Heard/1"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Not heard this past hour")))
    }

    @Test
    fun `promoting from the dialog closes it and tells every device list to reload`() {
        mvc.perform(
            post("/ui/devices")
                .param("source", "rtl433").param("id", door.id)
                .param("name", "Front door").param("type", "door").param("area", "hall"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("HX-Trigger", "closeModal, devicesChanged"))
            .andExpect(content().string(containsString("Promoted Front door")))

        assertEquals("Front door", promotions.find(door)?.name)
        mvc.perform(get("/ui/devices/rows")).andExpect(content().string(containsString("Front door")))
    }

    @Test
    fun `an invalid promotion redraws the dialog with each problem against its field`() {
        mvc.perform(post("/ui/devices").param("source", "rtl433").param("id", door.id).param("name", "").param("type", "door").param("area", "hall"))
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist("HX-Trigger"))
            .andExpect(content().string(containsString("is-invalid")))
            .andExpect(content().string(containsString("must not be blank")))
        assertNull(promotions.find(door))
    }

    @Test
    fun `promoting a key that is already promoted is refused rather than overwriting it`() {
        promotions.promote(PromotedDevice(door, "Front door", "door", "hall", Instant.now()))

        mvc.perform(post("/ui/devices").param("source", "rtl433").param("id", door.id).param("name", "Typo").param("type", "door").param("area", "hall"))
            .andExpect(content().string(containsString("is already promoted")))
        assertEquals("Front door", promotions.find(door)?.name)
    }

    @Test
    fun `editing a promotion keeps when it was first promoted`() {
        val promotedAt = Instant.parse("2026-10-01T00:00:00Z")
        promotions.promote(PromotedDevice(door, "Front door", "door", "hall", promotedAt))

        mvc.perform(
            post("/ui/devices").param("editing", "true")
                .param("source", "rtl433").param("id", door.id)
                .param("name", "Hall door").param("type", "door").param("area", "hall"),
        ).andExpect(header().string("HX-Trigger", "closeModal, devicesChanged"))

        assertEquals("Hall door", promotions.find(door)?.name)
        assertEquals(promotedAt, promotions.find(door)?.promotedAt)
    }

    @Test
    fun `demoting reloads the device lists`() {
        promotions.promote(PromotedDevice(door, "Front door", "door", "hall", Instant.now()))

        mvc.perform(delete("/ui/devices").param("source", "rtl433").param("id", door.id))
            .andExpect(status().isOk)
            .andExpect(header().string("HX-Trigger", "devicesChanged"))
            .andExpect(content().string(containsString("Demoted Front door")))
        assertNull(promotions.find(door))
    }

    @Test
    fun `curation is saved from the form's rows`() {
        mvc.perform(
            post("/ui/models/${contactModel.fingerprint}/curation")
                .param("category", "security")
                .param("sensors[0].name", "closed").param("sensors[0].type", "BINARY")
                .param("sensors[0].classname", "opening").param("sensors[0].inverted", "true")
                // A row the user added and left empty.
                .param("sensors[2].name", "").param("sensors[2].type", "ANALOG"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Saved 1 sensor mapping")))

        val curated = classification.model(contactModel.fingerprint)!!
        assertEquals("security", curated.category)
        assertEquals(listOf("closed"), curated.sensors.map { it.name })
        assertTrue(curated.sensors.single().inverted)
    }

    @Test
    fun `a refused curation shows its problems and saves nothing`() {
        mvc.perform(
            post("/ui/models/${contactModel.fingerprint}/curation")
                .param("category", "security")
                .param("sensors[0].name", "closed").param("sensors[0].type", "BINARY").param("sensors[0].classname", ""),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Nothing was saved")))
            .andExpect(content().string(containsString("table-danger")))

        assertTrue(classification.model(contactModel.fingerprint)!!.sensors.isEmpty())
    }

    @Test
    fun `deleting a model sends the browser back to the catalogue`() {
        mvc.perform(delete("/ui/models/${contactModel.fingerprint}"))
            .andExpect(status().isOk)
            .andExpect(header().string("HX-Redirect", containsString("/ui/models?deleted=DSC-Security")))
        assertNull(classification.model(contactModel.fingerprint))
    }

    @Test
    fun `an unknown model is not found`() {
        mvc.perform(get("/ui/models/nope")).andExpect(status().isNotFound)
    }

    @Test
    fun `the live stream delivers each matching reading as rendered HTML`() {
        val result = mvc.perform(get("/ui/monitor/stream").param("source", "rtl433").param("id", door.id))
            .andExpect(request().asyncStarted())
            .andReturn()

        hear("""{"model":"DSC-Security","id":2328667,"closed":0,"event":1,"tamper":0,"snr":14.9}""")
        hear("""{"model":"Acurite-Tower","id":3064,"channel":"A","temperature_C":21.6,"battery_ok":1,"snr":6.0}""")

        val deadline = System.nanoTime() + 3_000_000_000
        // An event is written in parts; it is whole once its blank line has arrived.
        while (!result.response.contentAsString.contains("</div>\n\n") && System.nanoTime() < deadline) Thread.sleep(20)
        val body = result.response.contentAsString
        assertTrue(body.contains("event:reading"), body)
        assertTrue(body.contains("field-chip changed"), "the contact's closed field changed: $body")
        assertTrue(body.contains("/ui/devices/view?source=rtl433&amp;id=DSC-Security%2F2328667"), body)
        assertTrue(body.lines().filter { it.startsWith("data:") }.all { it.contains("feed-event") }, "one line per event")
        assertTrue(!body.contains("Acurite-Tower/A/3064"), "only the watched device")
        mvc.perform(get("/ui/monitor/heard")).andExpect(content().string(not(containsString("Exception"))))
    }
}
