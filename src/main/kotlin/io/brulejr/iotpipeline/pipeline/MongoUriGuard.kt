/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.iotpipeline.pipeline

import org.springframework.boot.mongodb.autoconfigure.MongoProperties

/**
 * Fails when the MongoDB URI still holds an unresolved placeholder.
 *
 * Spring leaves an unresolved placeholder in place when binding configuration
 * properties, and the MongoDB driver accepts the result: a URI still containing the
 * literal text `$`+`{MONGO_USERNAME}` parses, and the driver connects to
 * localhost:27017 using that text as the username. On a host already running another
 * MongoDB that is a silent connection to the wrong database.
 *
 * Read from Spring Boot's own bound [MongoProperties], which keeps the unresolved text
 * exactly so that this check can see it. Called by every feature that chooses a MongoDB
 * registry, before it touches the database.
 */
fun requireResolvedMongoUri(properties: MongoProperties) {
    val uri = properties.uri ?: return
    require(!uri.contains("\${")) {
        "spring.mongodb.uri has unresolved placeholders: $uri. Export the variables from " +
            ".env with `set -a; . ./.env; set +a`, import .env in application-local.yml, " +
            "or set the uri there."
    }
}
