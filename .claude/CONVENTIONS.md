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

**Each feature MYST have its own `*Datafill` class for externalized configuration.**

- **DO** use a `@ConfigurationProperties`-bound `*Datafill` data class for all config values
- **DO** include validation on appropriate properties as part of the `*Datafill` class
- **DO NOT** use `@Value` to inject configuration values directly into classes
- `*Datafill` classes are the only permitted consumers of `application.yml` values


