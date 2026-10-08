# Coding Conventions

Personal conventions, not specific to this project. Candidates for promotion to
`~/.claude/CLAUDE.md`.

## Kotlin / Spring

- Spring `@ConfigurationProperties` classes are named `*Datafill`, never
  `*Properties`: `MqttIngestionDatafill`, not `MqttIngestionProperties`.
  Constructor parameters and fields that hold one are named `datafill`.
