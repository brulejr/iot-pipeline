plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "io.brulejr"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.integration:spring-integration-mqtt")
    // Paho is an optional dependency of spring-integration-mqtt, so it must be declared explicitly.
    implementation(libs.paho.mqttv3)
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Expiring cache for the dedupe window; version managed by Spring Boot.
    implementation("com.github.ben-manes.caffeine:caffeine")
    // Flyway without Spring Boot's auto-configuration, which would migrate at startup
    // even with every registry in memory; DatabaseMigrator runs it only when needed.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.integration:spring-integration-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // A throwaway PostgreSQL for the SQL-backed registries; skipped without Docker.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The container image copies this one jar, so it has a fixed name and no plain-jar
// sibling to choose between.
tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "iot-pipeline.jar"
    // Development settings, with their credentials, stay with the source tree. The IDE
    // still reads the file from build/resources; only the deployable jar leaves it out.
    exclude("application-local.yml")
}
