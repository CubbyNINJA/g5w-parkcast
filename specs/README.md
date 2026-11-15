# Executable specifications (Gherkin)

`specs/features/*.feature` hold this project's BDD scenarios. They are specifications first:
only scenarios in an assigned slice get step definitions. Every scenario follows these tag rules
after feature-level tags merge.

## Tag conventions

| Tag | Rule |
|---|---|
| Requirement IDs | At least one `@AIR-nnn`, copied exactly from `docs/spec/REQUIREMENTS.md`. Tag only IDs whose behaviour the steps actually assert. |
| Test IDs | At least one `@AT-nn` from the acceptance-test table in `docs/spec/REQUIREMENTS.md`. |
| Slice | Exactly one `@S1`, `@S2`, and so on. |
| Tier | Exactly one of: **`@offline`** (the default suite: synthetic fixtures only, no network, no iPhone, no head unit); **`@host`** (needs this PC's real MSYS2, Windows Firewall or repository, but no iPhone; run with `--run-host`); **`@pc`** (needs this PC's UxPlay build and the owner's iPhone on the same network, run with `--run-pc`); **`@unit`** (needs the head unit, never run from the PC suite; its evidence is the stick logs). |
| Kind | Exactly one of `@normal`, `@failure` or `@boundary`. |
| Optional | `@AMB-nn` when the scenario asserts an open proposal, and `@OD-nn` when it asserts an owner decision. |

## Log excerpts

- A step that says "a UxPlay log excerpt with these lines" wraps the excerpt into a complete log
  (AIR-028): the runner's start marker and UxPlay's version banner go before it, and the
  runner's end marker and a final newline go after it. Line numbers in the scenario count the
  excerpt's own lines; the step definitions add the wrapper's offset.
- A step that names a damaged log ("a log without the runner's end marker") builds that log
  deliberately and doesn't wrap it.
- Excerpts carry no logger prefix. The analyser matches a message's text wherever it appears in
  a line, so a prefix that the real logger adds doesn't change a match.
- Message texts come from `docs/evidence/UXPLAY_LOG_SIGNATURES.md`. The values in them (names,
  sizes, formats) are synthetic. The codec names `h264` and `h265` are string literals in
  UxPlay's source, so they're exact.

## Evidence rules

- A passing `@offline` scenario is specification evidence only. It shows the logic is right
  against fixtures, not that an iPhone or the head unit behaves that way.
- An `@host` scenario's evidence is what it saves from this PC (prerequisite inventory, build
  provenance, firewall rule lists, interpreter details) under `runs/`. It proves nothing about
  the iPhone.
- An `@pc` scenario's evidence is the run's saved log and its parsed result, kept under
  `runs/` (never committed: it can hold the iPhone's name and addresses) with the iPhone's iOS
  version. Only the verdict and a redacted summary go into `docs/evidence/`. Never save screen
  captures of the phone's contents.
- A scenario tagged `@AMB-nn` asserts a proposal. If the owner decides otherwise, the scenario
  changes. The requirement doesn't bend to fit the scenario.
