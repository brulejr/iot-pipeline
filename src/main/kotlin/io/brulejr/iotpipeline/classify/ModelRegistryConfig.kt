/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.classify

import io.brulejr.iotpipeline.classify.mongo.MongoModelRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoOperations

/**
 * Chooses where the model registry lives.
 *
 * The two conditions are mutually exclusive, so exactly one bean is defined. That
 * matters: `@ConditionalOnMissingBean` between two beans declared in application code
 * resolves in definition order, which Spring only guarantees for auto-configuration.
 */
@Configuration
class ModelRegistryConfig {

    /**
     * The [uri] parameter is a deliberate startup guard, not configuration.
     *
     * Spring leaves an unresolved placeholder in place when binding
     * `@ConfigurationProperties`, and the MongoDB driver accepts the result: a URI still
     * containing the literal text `$`+`{MONGO_USERNAME}` parses, and the driver connects
     * to localhost:27017 using that text as the username. On a host already running
     * another MongoDB that is a silent connection to the wrong database. `@Value`
     * resolves strictly, so reading the property here turns a missing credential into a
     * startup failure that names the placeholder.
     */
    @Bean
    @ConditionalOnProperty("pipeline.model-registry.type", havingValue = "mongo", matchIfMissing = true)
    fun mongoModelRegistry(
        mongo: MongoOperations,
        @Value("\${spring.mongodb.uri}") uri: String,
    ): ModelRegistryPort {
        require(!uri.contains("\${")) {
            "spring.mongodb.uri has unresolved placeholders: $uri. Export the " +
                "variables from .env with `set -a; . ./.env; set +a`, or set the uri in " +
                "application-local.yml."
        }
        return MongoModelRegistry(mongo)
    }

    /** Holds models for the lifetime of the process; needs no database. */
    @Bean
    @ConditionalOnProperty("pipeline.model-registry.type", havingValue = "memory")
    fun inMemoryModelRegistry(): ModelRegistryPort = InMemoryModelRegistry()
}
