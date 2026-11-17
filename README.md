# g5w-parkcast

Screen mirroring from an iPhone to the screen of a Hyundai, Kia or Genesis **Gen5W** head unit,
for watching phone apps such as YouTube **while parked**. It's built on the open-source AirPlay
receiver [UxPlay](https://github.com/FDH2/UxPlay) and ships as a
[NEMESIS](https://github.com/xwtk/hmg_nemesis) package for head units that run NEMESIS.

> Not affiliated with Apple, Hyundai, Kia, Genesis or Mobis. AirPlay is a trademark of Apple Inc.
> This is a personal project for the owner's own car; use it at your own risk. Video is only ever
> meant to show while the car is in Park.

## Status

| Slice | What it covers | State |
|---|---|---|
| S1 | PC feasibility test: does an iPhone mirror H.264 to UxPlay set up like the head unit? | **Done, GO** (iPhone16,2, iOS 27.3, UxPlay 1.74; `docs/evidence/`) |
| S2 | The parking gate: video only in Park, read from the stock Park getter; sleep, logging, config | **Done offline**: the gate in plain Java 7 (`receiver/gate/`), 433 Cucumber runs pass, mutation-checked. The `@unit` evidence checks (feature 12) come with the on-unit receiver (OD-137) |
| S3 | The receiver on the head unit: UxPlay's AirPlay library cross-built for Android 4.4 ARMv7, video through the unit's H.264 decoder, with the S2 gate | **Now**: owner decisions OD-138 to OD-145; first milestone video only, GO/NO-GO (OD-139) |
| S4 | The NEMESIS package: launched from NEMESIS Manager, config and logs | Not started |
| S5 | Coexisting with wireless CarPlay (ports, mDNS, who owns the screen) | Decisions OD-76, OD-77 and OD-97 taken; not specified yet |

There is **no runnable receiver app yet**. The repository holds the S1 PC test tool (`tools/airplay_s1/`),
read-only head-unit probes (`tools/unit-probes/`), the specifications, and S2's parking gate: plain Java in
`receiver/gate/`, with the first piece of Android glue (`receiver/app/`, not built yet).

## Roadmap

1. **S2, the parking gate** (done offline, merged): plain Java 7, tested on the PC with Cucumber-JVM
   (OD-131 to OD-135). Its on-unit evidence checks (feature 12) are bound in the next slice (OD-137).
2. **S3, the receiver on the head unit** (now, OD-99, OD-139 to OD-145): cross-build UxPlay's AirPlay core for the unit
   (Android 4.4, ARMv7) and show mirrored video through its hardware H.264 decoder. This is the
   biggest untested risk.
3. **S4, the NEMESIS package**: the app, launched from NEMESIS Manager (OD-95), with its config and logs.
4. **S5, coexisting with CarPlay**: fixed ports, mDNS, screen and audio ownership (OD-76, OD-77, OD-97).

Everything is developed spec-first: owner decisions, then requirements, then Gherkin scenarios,
then tests, then code. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Layout

| Path | What's there |
|---|---|
| `AGENTS.md` | Rules for people and coding agents working here: the development loop, git, docs |
| `CONTRIBUTING.md` | How to contribute, step by step |
| `docs/spec/DECISIONS.md` | Owner decisions (`OD-nn`), the source of truth for policy |
| `docs/spec/AMBIGUITY_REGISTER.md` | Questions and proposals (`AMB-nn`), open or decided |
| `docs/spec/REQUIREMENTS.md` | Requirements (`AIR-nnn`) and acceptance tests (`AT-nn`) |
| `docs/spec/reviews/` | Requirement drafts and coverage reviews, saved verbatim |
| `docs/spec/TRACEABILITY.md` | Generated: which scenarios cover which requirements |
| `docs/design/` | How the code is built: `S2_GATE.md` covers the gate's ports, components and test harness; `S3_RECEIVER.md` covers the on-unit receiver's modules, staged native start, build and harness |
| `docs/procedures/` | Steps the owner follows on the unit: `S3_FIRST_UNIT_RUN.md` is the first on-unit S3 run, ending with the CarPlay-after-exit check |
| `docs/evidence/` | Facts from the head unit and PC tests that the specs rely on, redacted |
| `specs/features/` | Gherkin scenarios; tag rules in `specs/README.md` |
| `tests/` | pytest-bdd bindings and step definitions for S1, plus the tag and traceability checks |
| `receiver/gate/` | The parking gate, plain Java 7 (`src/main/java`), and its Cucumber-JVM S2 steps and harness (`src/testFixtures/java`, shared with core under OD-189) |
| `settings.gradle`, `gradlew` | The Gradle wrapper build for the Java code |
| `tools/airplay_s1/`, `tools/s1.py` | The S1 PC test: build UxPlay, run it, analyse and judge the log |
| `tools/unit-probes/` | Read-only probes run on the head unit from a USB stick |
| `tools/astra/` | Runner and role prompts for the external requirements author and reviewer |
| `tools/trace_specs.py` | Tag-rule check and traceability generator |
| `package/` | The NEMESIS package (from a later slice) |

## Quick start

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/Scripts/python.exe -r requirements-dev.txt
.venv/Scripts/python -m pytest          # S1 offline scenarios, plus tag and traceability checks
.venv/Scripts/python tools/trace_specs.py --check
./gradlew test                          # S2 offline scenarios (Cucumber-JVM), needs a JDK 17
```

The S1 PC test (`--run-pc`) needs MSYS2 with UxPlay's build dependencies and an iPhone on the same
network. `./gradlew test` needs a JDK 17 (set `JAVA_HOME`); the wrapper downloads Gradle and the pinned
Cucumber jars on first use. Head-unit (`@unit`) scenarios never run from
the PC: their evidence comes from the probes and logs on the USB stick.

## Licence

[AGPL-3.0](LICENSE), the same as NEMESIS and compatible with UxPlay's GPL-3.0.
