# AirPlay receiver for the head unit

An AirPlay screen-mirroring receiver for the owner's Gen5W head unit, so phone apps such as
YouTube can be watched on the car's screen while parked (OD-01). It will ship as a NEMESIS
package built from `package/`.

**Status:** slice S1, the PC feasibility test, is being specified. No receiver code exists yet.

| Path | What's there |
|---|---|
| `AGENTS.md` | The development loop: SDD and BDD, reviewed by Astra via Codex (OD-02) |
| `docs/spec/DECISIONS.md` | Owner decisions (`OD-nn`) |
| `docs/spec/AMBIGUITY_REGISTER.md` | Open questions and proposals (`AMB-nn`) |
| `docs/spec/REQUIREMENTS.md` | Requirements (`AIR-nnn`) and acceptance tests (`AT-nn`) |
| `docs/spec/reviews/` | Astra requirement drafts and coverage reviews, saved verbatim |
| `docs/spec/TRACEABILITY.md` | Generated: which scenarios cover which requirements |
| `docs/evidence/` | Facts and redacted results that the specs rely on |
| `specs/features/` | Gherkin scenarios; tag rules are in `specs/README.md` |
| `tests/` | pytest-bdd bindings and step definitions |
| `tools/` | `trace_specs.py` (tag rules and traceability), `astra/` (the Codex runner and role prompts) |
| `package/` | The NEMESIS package (empty until a later slice) |
