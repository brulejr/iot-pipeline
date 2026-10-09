# Coding Conventions

Personal conventions, not specific to this project. Candidates for promotion to
`~/.claude/CLAUDE.md`.

## Kotlin

- One top-level type per file, named after it. A file holding several classes,
  interfaces or objects makes it hard to see what lives where. Related top-level
  *functions* may share a file named for their purpose (e.g. `CurationValidation.kt`),
  and an application entry point may keep its `main` beside its class.

## Kotlin / Spring

- Spring `@ConfigurationProperties` classes are named `*Datafill`, never
  `*Properties`: `MqttIngestionDatafill`, not `MqttIngestionProperties`.
  Constructor parameters and fields that hold one are named `datafill`.
