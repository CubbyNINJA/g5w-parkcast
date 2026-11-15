# AGENTS.md: AirPlay receiver for the head unit

This project builds an AirPlay screen-mirroring receiver for the Gen5W head unit, shipped as a
NEMESIS package from `package/`. It lives inside hmg_nemesis but has its own process, set out
below. The repository's `AGENTS.md` §10 points here (OD-03).

## Development process: SDD and BDD only

All work is spec-driven and behaviour-driven. Don't write production code or step definitions
for behaviour that has no reviewed scenario. The loop follows SilphCo Lambda 2.0's
(`D:\Documents\SilphCo Lambda 2.0\AGENTS.md`), as the owner asked (OD-02):

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
4. **Coverage review by Astra (mandatory).** Run `--kind review` with the role prompt
   `astra-scenario-reviewer.md` on the requirements and scenarios. Save the review verbatim as
   `docs/spec/reviews/ASTRA_REVIEW_<topic>_<YYYY-MM-DD>.md`, with the line "External critique,
   not an owner decision."
5. **Apply the review before any test is written.** Fold its findings into the requirements and
   features, regenerate traceability, and note in the review file what was applied. Ask the
   owner only when a finding needs an owner decision.
6. **Tests first.** Write step definitions in `tests/steps/`, and confirm they fail for the
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
```

Neither tier needs an elevated terminal. The S1 tools never change the firewall: Windows asks you
to allow `uxplay.exe` the first time it listens, and each run reminds you how to remove that
allowance afterwards (OD-31, OD-36).

The project's tests use pytest-bdd in their own `.venv`. hmg_nemesis's main suite
(`Sources/tests/unit`) stays stdlib plus pytest and never imports anything from here.

## Git

The work is on branch `chore/airplay-receiver` in `nemesis-rebase`, with no upstream. Commit at
milestones. **Never push it anywhere** unless the owner says so (OD-03).

## Target facts (from the owner's unit, 260206 CA, NEMESIS 1.16.7)

- Package metadata limits: `short-desc` at most 64 characters, `detailed-desc` at most 256.
  Declare `os` and `models`. Build with this tree's builder, which enforces the limits.
- Toolbox `mv` takes no options, so use plain `mv`. Test hooks with a fake `mv` that rejects
  options.
- Hardware H.264 decoding uses `OMX.TCC.avcdec`, opened by name. There's no hardware HEVC:
  `OMX.TCC.hevcdec` is never registered.
- `mdnsd` is running, and the CarPlay receiver already advertises `_airplay._tcp` through it.
- During wireless CarPlay the iPhone is on the head unit's hotspot (`wlan0 192.168.0.1/24`).
