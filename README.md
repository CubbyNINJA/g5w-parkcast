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
| S2 | The parking gate: video only in Park, read from the stock Park getter; sleep, logging, config | Requirements settled; scenarios written and reviewed; step definitions and the Java gate next |
| S3 | Coexisting with wireless CarPlay (ports, mDNS, who owns the screen) | Decisions OD-76 and OD-77 taken; not specified yet |

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
| `docs/evidence/` | Facts from the head unit and PC tests that the specs rely on, redacted |
| `specs/features/` | Gherkin scenarios; tag rules in `specs/README.md` |
| `tests/` | pytest-bdd bindings and step definitions |
| `tools/airplay_s1/`, `tools/s1.py` | The S1 PC test: build UxPlay, run it, analyse and judge the log |
| `tools/unit-probes/` | Read-only probes run on the head unit from a USB stick |
| `tools/astra/` | Runner and role prompts for the external requirements author and reviewer |
| `tools/trace_specs.py` | Tag-rule check and traceability generator |
| `package/` | The NEMESIS package (from a later slice) |

## Quick start

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/Scripts/python.exe -r requirements-dev.txt
.venv/Scripts/python -m pytest          # offline scenarios (synthetic, no hardware)
.venv/Scripts/python tools/trace_specs.py --check
```

The S1 PC test (`--run-pc`) needs MSYS2 with UxPlay's build dependencies and an iPhone on the same
network; the S2 gate tests need a JDK 17 (`javac`). Head-unit (`@unit`) scenarios never run from
the PC: their evidence comes from the probes and logs on the USB stick.

## Licence

[AGPL-3.0](LICENSE), the same as NEMESIS and compatible with UxPlay's GPL-3.0.
