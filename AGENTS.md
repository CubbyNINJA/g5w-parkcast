# AGENTS.md: g5w-parkcast

This project builds an AirPlay screen-mirroring receiver for Hyundai, Kia and Genesis Gen5W head
units, shipped as a NEMESIS package from `package/`, so phone apps can be watched on the car's
screen while parked. It is a standalone repository (OD-89); it started inside hmg_nemesis and its
history came with it. Read `CONTRIBUTING.md` for how to contribute, and this file for the rules.

## Development process: SDD and BDD only

All work is spec-driven and behaviour-driven. Don't write production code or step definitions
for behaviour that has no reviewed scenario. The loop is the owner's spec-first process (OD-02):

1. **Owner decisions.** Record every owner decision for the slice in `docs/spec/DECISIONS.md`
   (`OD-nn`). Anything still open goes to `docs/spec/AMBIGUITY_REGISTER.md` (`AMB-nn`) and is
   asked of the owner. The agent never decides for the owner.
2. **Requirements, drafted by Astra.** GPT-6 Astra drafts the requirement and acceptance-test
   rows through the Codex CLI, at medium reasoning in a read-only sandbox:
   `python tools/astra/run.py --kind requirements ...` with the role prompt
   `tools/astra/roles/astra-requirements-author.md`.
   - It runs on a staged copy holding `AGENTS.md`, `docs/`, `specs/`, `tests/`, `tools/`,
     `package/` and the evidence the prompt names. Never stage credentials, Wi-Fi secrets,
     pulled OEM binaries, or anything from the owner's iPhone.
   - Its draft is saved verbatim as
     `docs/spec/reviews/ASTRA_REQUIREMENTS_<topic>_<YYYY-MM-DD>.md`, with a header and the line
     "Drafted specification, not an owner decision."
   - Put the rows into `docs/spec/REQUIREMENTS.md`, and note any wording you changed and why.
     Open questions go to the owner, never into the requirements.
3. **Scenarios, written by the parent agent.** Write Gherkin in `specs/features/*.feature`
   following the tag rules in `specs/README.md`, then regenerate traceability
   (`python tools/trace_specs.py`). The suite's `test_tag_rules.py` fails on any tag-rule break.
4. **Coverage review by GPT-6.1 Sol (mandatory, OD-205).** Run `--kind review` with the role prompt
   `astra-scenario-reviewer.md` on the requirements and scenarios; the runner uses `gpt-6.1-sol`. Save the
   review verbatim as `docs/spec/reviews/SOL_REVIEW_<topic>_<YYYY-MM-DD>.md`, with the line "External
   critique, not an owner decision."
5. **Apply the review before any test is written.** Fold its findings into the requirements and
   features, regenerate traceability, and note in the review file what was applied. Ask the
   owner only when a finding needs an owner decision.
6. **Tests first.** Write step definitions (S1: Python in `tests/steps/`; S2 and later receiver
   slices: Java in `receiver/gate/src/testFixtures/java`, OD-133), and confirm they fail for the
   right reason before writing any code.
7. **Implement** until the scenarios pass. Then run a mutation check on the new behaviour and
   the full offline suite.

A change in scope found during implementation goes back to step 1 or 2. Changed scenarios go
back through the coverage review before more code is written.

## Running the suite

```bash
uv venv --python 3.12 .venv && uv pip install --python .venv/Scripts/python.exe -r requirements-dev.txt
.venv/Scripts/python -m pytest                 # @offline only (default)
.venv/Scripts/python -m pytest --run-host      # adds @host: this PC's MSYS2 and repo, no iPhone
.venv/Scripts/python -m pytest --run-pc        # adds @pc: this PC plus the owner's iPhone
./gradlew test                                 # S2 @offline scenarios, Cucumber-JVM (JAVA_HOME = a JDK 17)
```

Neither tier needs an elevated terminal. The S1 tools never change the firewall: Windows asks you
to allow `uxplay.exe` the first time it listens, and each run reminds you how to remove that
allowance afterwards (OD-31, OD-36).

S1 uses pytest-bdd in the project's own `.venv` (`requirements-dev.txt`); `tests/feature_sets.py`
keeps pytest-bdd off the S2 feature files. The S2 parking gate is plain Java compiled for Java 7
(`--release 7`, so it can run on Android 4.4: no lambdas or streams in `receiver/gate/src/main`), and
its scenarios run with Cucumber-JVM through the Gradle wrapper (OD-131 to OD-135). The tag-rule and
traceability checks in pytest still cover every feature file.

## Git (OD-90)

- **Commit often**, one coherent change per commit, with the suite in a known state.
- **Publish the branch after each milestone** (owner, OD-110): push the slice branch right after a slice's
  requirements are folded in, its scenarios reviewed, its step definitions written, or its suite turns green.
- **Merge at the end of a slice**: `main` holds finished slices; each slice works on its own
  branch (for example `s2-parking-gate`) and is merged into `main` when it's done.
- The repository is public. Never commit OEM binaries, UxPlay builds, `runs/`, `.astra/`, or
  anything identifying: emails, IP or MAC addresses, VINs, iPhone names, Wi-Fi details.
  Commits use the owner's GitHub noreply address.

## Documentation (OD-90)

Keep the docs current with every change, so other people and agents can contribute: `README.md`,
`CONTRIBUTING.md`, this file, `docs/spec/` and `docs/evidence/`. A change that alters behaviour,
process or layout updates the doc that describes it in the same commit.

## Target facts (from the owner's unit, 260206 CA, NEMESIS 1.16.7)

- Package metadata limits: `short-desc` at most 64 characters, `detailed-desc` at most 256.
  Declare `os` and `models`. Build with the NEMESIS 1.16 package builder, which enforces the limits.
- Toolbox `mv` takes no options, so use plain `mv`. Test hooks with a fake `mv` that rejects
  options.
- Hardware H.264 decoding uses `OMX.TCC.avcdec`, opened by name. There's no hardware HEVC:
  `OMX.TCC.hevcdec` is never registered.
- `mdnsd` is running, and the CarPlay receiver already advertises `_airplay._tcp` through it.
- During wireless CarPlay the iPhone is on the head unit's hotspot (`wlan0 192.168.0.1/24`).
