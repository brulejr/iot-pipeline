# Coding Conventions

Personal conventions, not specific to this project. Candidates for promotion to
`~/.claude/CLAUDE.md`.

## Kotlin

- One top-level type per file, named after it. A file holding several classes,
  interfaces or objects makes it hard to see what lives where. Related top-level
  *functions* may share a file named for their purpose (e.g. `CurationValidation.kt`),
  and an application entry point may keep its `main` beside its class.

## Spring Boot

### 1. Feature-based Package Structure

**Organize ALL code by feature, not by technical layer.**

- **DO** Group all classes for a feature (config, datafill, data access, service, api, etc.) under a single feature package
- **DO NOT** create a flat `controllers/`, `services/`, or `repositories/` top-level package
- Each feature package is self-contained and independently navigable

**Each feature MUST have its own `@Configuration` class**

- **DO** create a `@Configuration` class per feature to instantiate and wire all beans for that feature
- **DO** place cross-cutting concerns (e.g. security, logging, metrics) in a separate package, not mixed with feature packages
- **DO NOT** scatter bean wiring across multiple unrelated classes

**Each feature MUST have its own `*Datafill` class for externalized configuration.**

- **DO** use a `@ConfigurationProperties`-bound `*Datafill` data class for all config values
- **DO** include validation on appropriate properties as part of the `*Datafill` class
- **DO NOT** use `@Value` to inject configuration values directly into classes
- `*Datafill` classes are the only permitted consumers of `application.yml` values
- **EXCEPT** for `@ConditionalOnProperty.` used to enable/disable feature

**Each feature MUST have its own `*Service` class.**

- **DO** create a `*Service` class per feature to encapsulate business logic
- **DO** place all business logic within the `*Service` class
- **DO NOT** annotate the `*Service` class with `@Service`; instead, register the class as a bean explicitly
- **DO NOT** mix business logic with data access or other concerns

**Each feature MUST have its own `*Controller` class.**

- **DO** create a `*Controller` class per feature to handle HTTP requests
- **DO** place all HTTP request handling within the `*Controller` class
- **DO** annotate the `*Controller` class with `@RestController`
- **DO NOT** mix HTTP request handling with business logic or data access

**Each feature MUST expose a Spring Boot Actuator health indicator.**

- **DO** create a `*HealthIndicator` class per feature to expose a Spring Boot Actuator health indicator
- **DO** place all health indicator handling within the `*HealthIndicator` class
- **DO NOT** annotate the `*HealthIndicator` class with `@Component`; instead, register the class as a bean explicitly
- **DO NOT** mix health indicator handling with business logic or data access

**Each feature MUST contribute to the Spring Boot Actuator info endpoint**

- **DO** create a `*InfoContributor` class per feature to contribute to the Spring Boot Actuator info endpoint
- **DO** place all info contribution handling within the `*InfoContributor` class
- **DO NOT** annotate the class with `@Component`; instead, register the class as a bean explicitly
- **DO NOT** mix info contribution handling with business logic or data access

**Wire beans explicitly within `@Configuration` classes - not through class-level annotations**

- **DO** declare beans as `@Bean` methods inside the feature `@Configuration` class
- **EXCEPT** for controller classes, which should be annotated with `@RestController`
- **NEVER** use `@Component` or `@Service`
- Reason: Class-level annotations can lead to unexpected behavior and make it harder to understand the bean wiring.
