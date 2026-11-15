# Contributing to g5w-parkcast

This guide is for people and coding agents alike. `AGENTS.md` holds the rules; this file walks
through doing the work. Keep both current: a change that alters behaviour, process or layout
updates the docs that describe it in the same commit (OD-90).

## 1. Set up

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/Scripts/python.exe -r requirements-dev.txt
.venv/Scripts/python -m pytest
```

You also need a JDK 17 for the S2 gate tests. Git must keep files LF: `.gitattributes` does this,
and on Windows run `git config core.autocrlf input` in your clone.

## 2. The loop (spec first, always)

No production code or step definition without a reviewed scenario.

1. **Decisions.** Policy comes from the owner and is recorded in `docs/spec/DECISIONS.md` as `OD-nn`,
   quoting the owner's words. Open questions go to `docs/spec/AMBIGUITY_REGISTER.md` as `AMB-nn`,
   each with a proposal. An agent never decides owner policy, with one exception: parsing, logging
   and timing edge cases take the fail-safe proposal and are recorded citing OD-86.
2. **Requirements.** Drafted by the external author (Astra, run through `tools/astra/run.py`), or a
   fresh-context Claude substitute with the same role file when Astra is unavailable (OD-78). The
   draft is saved verbatim under `docs/spec/reviews/`, then its rows go into `REQUIREMENTS.md`
   unchanged, with a provenance note at the end of that file.
3. **Scenarios.** Gherkin in `specs/features/`, following `specs/README.md` (one slice tag, one tier
   tag, one kind tag, requirement and test IDs). Regenerate `docs/spec/TRACEABILITY.md` with
   `python tools/trace_specs.py`.
4. **Coverage review.** The reviewer role checks the scenarios against the requirements. Save the
   review, apply its findings, and raise anything that needs the owner as an `AMB`.
5. **Tests first.** Write step definitions in `tests/steps/` and watch them fail for the right reason.
   Oracles must be independent of the code under test: fakes record what was actually asked of
   them, and the steps compare against the numbers in the scenario.
6. **Code** until the scenarios pass. Then mutate the new behaviour (break it on purpose and check
   the suite fails) and run the full offline suite.

## 3. Git (OD-90)

- Commit often, each commit one coherent change.
- Push at milestones: requirements folded in, scenarios reviewed, a green suite.
- Each slice works on its own branch (`s2-parking-gate`, …) and is merged into `main` when the slice
  is finished. `main` holds finished slices.
- Agent commits end with their `Co-Authored-By:` trailer.

## 4. Privacy and safety

The repository is public.

- Never commit OEM binaries or framework files, UxPlay builds, `runs/` (PC test logs name the
  iPhone), `.astra/`, Wi-Fi details, or identifying data: emails, IP or MAC addresses, VINs,
  serial numbers, iPhone names. Test fixtures use obviously synthetic values.
- Evidence docs under `docs/evidence/` are redacted summaries.
- Commits use a GitHub noreply address.
- Nothing here may actuate the car, write to CAN, change driver-assistance or regulation behaviour,
  or touch anti-theft or key systems. Vehicle access is read-only.

## 5. Head-unit probes

Facts about the car come from read-only probes in `tools/unit-probes/`, run from a USB stick
through a NEMESIS `type=execute` descriptor.

- Every probe is read-only, guarded to the exact firmware build it was written for, and checks
  the stock service it calls before calling it.
- It removes its own root `NEMESIS.data` when it starts (or after its last run), and writes to
  `NEMESIS-diag/<probe>/` on the stick.
- Scripts are LF, toolbox-only shell (`#!/system/bin/sh`; no busybox, awk or sed on the unit).
- Dry-run a probe on the PC with fake Android commands before it goes on the stick.
- Results go into `docs/evidence/`, redacted, citing the output files by line.

## 6. Where to ask

Open an issue on GitHub. For anything that changes what the car does or shows, the owner decides.
