# Coverage review: S1 changes for OD-31 to OD-39 (substitute reviewer)

> Reviewer: a fresh-context Claude subagent standing in for `gpt-6-astra`, which was at its Codex usage limit (OD-40). Same role prompt (`tools/astra/roles/astra-scenario-reviewer.md`) and prompt (`tools/astra/prompts/S1_REVIEW_OD31.md`), with STAGE_ROOT as the project root. Read-only; it ran the default offline suite only.
> Date 2026-11-14.

**External critique, not an owner decision. Substitute for Astra under OD-40.**

---

## Verdict

**Not blocked. The runner can be written. Fix findings 1 and 2 before the runner's tests run for the first time, and get owner rulings on findings 5 and 6 before the `run` exit codes and the can't-stop path are final.**

The rewrite follows OD-31 to OD-39. The scenarios, steps, fakes and `S1_INTERFACES.md` no longer create, list or remove rules, and the launch notice, the reminder, the owner-handled record and OD-35's "only runs that launched" are each covered by an offline scenario. The weak spot is AIR-032, the core of OD-31. The static scan misses the usual Python way of calling `netsh`. The process recorder really starts whatever it records. The dynamic check covers only five endings, not every command or early stop. Two Thens are weaker than their words: the live "after UxPlay stopped" check, and the "after the run reported the process" check. AIR-031 doesn't say which exit code `uxplay exited` and `interrupted` get, so AT-33's exit-code criterion can't be tested yet. I ran the default offline suite: 64 failed, 69 passed, 7 skipped. 62 of the failures are `ModuleNotFoundError: airplay_s1.runner` or `airplay_s1.cli` (expected). The other two are the missing `tools/s1.py` and the missing `runner.py`, also expected. This is external critique, not an owner decision.

## Findings

Paths are relative to the staged root.

| # | Severity | File:line | Finding | Concrete fix |
|---|---|---|---|---|
| 1 | major | `tests/steps/prompt_steps.py:19-21`, `:198-201` | The static scan, which OD-36 relies on for "no firewall changes", misses the list form `subprocess.run(["netsh", "advfirewall", ...])`. `netsh(?:\.exe)?\s+(?:advfirewall\|firewall)` needs whitespace after `netsh`, but in a list a `",` comes next. It also misses `Set-NetFirewallSetting`, `Copy-/Rename-NetFirewallRule`, `Set-NetFirewall*Filter` and the COM API (`HNetCfg.FwPolicy2`). Surviving mutation: `build` or `check` adds a rule through list-form netsh. | `FIREWALL_COMMANDS = re.compile(r"NetFirewall\|\bnetsh\b\|advfirewall\|HNetCfg\.Fw\|INetFw\|FwPolicy2", re.I)`. Flagging any `netsh` matches the dynamic check at `:187`. |
| 2 | major | `tests/steps/prompt_steps.py:168-178`, `:185-187` | (a) `Recording` calls the real `Popen.__init__`, so a broken implementation's firewall command would actually run during an `@offline` test. That is the host-state change AIR-032 forbids, and OD-36 no longer requires the terminal to be unelevated. (b) Anything that binds `Popen` before the patch (`from subprocess import Popen`) bypasses it, and so do `os.system`, `os.startfile` and ShellExecute. (c) The guard "no process was recorded at all" is always met by the fake launcher's own `proc.start` event, so it doesn't show the recorder is hooked. | Patch `_winapi.CreateProcess`, which every Windows `Popen` goes through whatever name it was imported under, plus `os.system`, `os.startfile` and `os.spawnv`/`os.spawnve`. Record the command, and raise `OSError` for anything matching finding 1's pattern instead of starting it. Replace the non-empty guard with a canary: in the Given, call `subprocess.run(["s1-canary"])`, assert it was recorded and refused, then clear the list. This also lets the Then work on paths that start no process (finding 3). |
| 3 | major | `specs/features/02_firewall.feature:66-79` | AIR-032(a) covers "No S1 command ... on every path", and AT-32 lists help, every usage error, every preflight failure, `refused` and `capture failed`. The outline runs only five `run` endings. `check`, `build`, `analyse`, `evaluate`, `summary`, usage errors, preflight stops, `refused`, `capture failed` and process-creation failure are never checked dynamically. With finding 1 open, a firewall call in `build` passes both checks. | Add the two outlines under Missing scenarios (1 and 2). |
| 4 | major | `tests/steps/live_steps.py:230-238` | "reminded ... after UxPlay stopped" only checks that the reminder came after `proc.start` and that some stop request happened. A reminder printed right after launch, and never again, passes (`len(told) == 1`). Its oracle doesn't match AT-33's `@pc` clause. | In `RecordedProc.stop`, add `self.launcher.events.add("proc.stop", pid=self.pid)`. After `inner.stop` returns, add `proc.exit` if `inner.poll() is not None`. In `RecordingOwner.tell`, when `"Allowed apps" in message`, save `_os_uxplay()` on the event. Assert reminder index > last `proc.stop` index, and that the saved snapshot has no row whose `ExecutablePath` equals `provenance.executable`. Also assert the block-entry note (`"block" in text`). |
| 5 | major | `docs/spec/REQUIREMENTS.md:41` (AIR-031(f)), `:43` (AIR-033(f)), `:82` (AT-33) | AT-33 says "The exit code is the one AIR-031 gives for that ending", but AIR-031 doesn't say whether `uxplay exited` (mid-test) and `interrupted` are completed commands (0) or failures (1, or 130). No scenario runs `s1.py run` through an ending after launch and checks the exit code. Every reminder scenario calls `runner.run` directly. Surviving mutation: the CLI returns 1 whenever it prints the reminder, or 0 after `capture failed`. | Owner decision (proposed wording below), then add Missing scenario 3. |
| 6 | major | `docs/spec/REQUIREMENTS.md:38` (AIR-028(a)), `:43` (AIR-033(c)); `docs/spec/S1_INTERFACES.md:70-71` | When UxPlay can't be stopped, the requirements don't say whether the end marker is written. AIR-028(a) writes it "after UxPlay has stopped", yet AIR-033(c) needs the reminder "after the log's end marker". `S1_INTERFACES.md` step 7 writes it anyway, which is the parent's reading, not a requirement. The still-running process can write after the marker, so the marker can't be guaranteed to be the last line. `02_firewall.feature:39-46` and `03_runner.feature:123-130` assert neither. | Owner decision (proposed AIR-028(a) wording below). Then add `And the log's last line is the runner's end marker naming "owner ended"` (or its negation) to both scenarios. |
| 7 | minor | `tests/steps/prompt_steps.py:109-113` | "the reminder came after the run reported the process it couldn't stop" checks only that the reminder came after the last `proc.stop` event. `FakeProc.stop` (`fakes.py:308`) writes that event when the stop is requested, not when it's reported. The report exists only in `record.errors`, which is returned at the end, so its timing can't be seen. | Require the residual report to be an owner message naming the PID: `i = first owner.tell containing str(proc.pid)` and `i < reminder index`. Document this in `S1_INTERFACES.md` step 6. |
| 8 | minor | `specs/features/02_firewall.feature:57-64`; `docs/spec/S1_INTERFACES.md:128-129` | AIR-033(d) "Whenever the run writes `run.json`" is checked only on a GO, owner-ended run after `evidence.write`, which also writes `run.json`. Surviving mutation: the runner leaves out `firewall`, the evidence writer adds it, and every failure ending's `run.json` lacks it. | Add the owner-handled and no-claims Thens to the reminder outline (Missing scenario 4). |
| 9 | minor | `tests/steps/prompt_steps.py:26`, `:154-157` | `CLAIMS` scans all of `run.json`. It fails wrongly if an unrelated field or error contains "present" or "removed" (for example a residual-process error once finding 8 applies to that ending), and it misses `"allowed"`, `"added"` and `"rule_created"`. | `fw = {k: v for k, v in rec.items() if re.search(r"firewall\|allow\|rule\|block", k, re.I)}; assert fw == {"firewall": "owner-handled"}`. Keep `CLAIMS` only for values under those keys. |
| 10 | minor | `specs/features/08_cli.feature:40-45`, `:53-59`; `tests/steps/prompt_steps.py:63-67` | OD-35's no-reminder cases "bad provenance, unwritable output" aren't asserted, only "refused build" (`02:15-20`). The existing Then calls `rec(world)`, which fails on the CLI path (`world.executed` is False). | Add a CLI-safe step, `the console owner is given no firewall notice or reminder`, that checks `world.owner.messages` only. Add it to both outlines (Missing scenario 5). |
| 11 | minor | `specs/features/02_firewall.feature:23-37` | No row checks the recorded reason. A keyboard interrupt recorded as `owner ended`, or a mid-test exit recorded as `timeout`, passes. | Add a `reason` column and `the log's last line is the runner's end marker naming "<reason>"` (Missing scenario 4). |
| 12 | minor | `specs/features/03_runner.feature:148-156` | AIR-006 "report any process it cannot stop" on the capture-failure path has no scenario. Every can't-stop scenario uses the owner ending. | Missing scenario 6. |
| 13 | minor | `tools/airplay_s1/__init__.py:3`; `tests/conftest.py:30`; `specs/README.md:14`, `:37`; `tools/airplay_s1/__pycache__/firewall.cpython-312.pyc`; `tests/steps/live_steps.py:16`, `:18`; `tests/steps/prompt_steps.py:6` | Leftovers from the scripted-rule design: the module list names `firewall`; the `--run-host` help says "firewall"; the `@host` tier says "Windows Firewall" and its evidence "firewall rule lists"; a stale `firewall` `.pyc` remains (it can't be imported without its source, but it is dead); `os` and `signal` are imported unused (likely from the old elevation check); `json` is imported unused. | Module list: drop `firewall, `. conftest help: `"this PC's MSYS2 and repository; no iPhone"`. README:14: `needs this PC's real MSYS2 or repository`. README:37: `(prerequisite inventory, build provenance, interpreter details)`. Delete the `.pyc`. Remove the unused imports. |
| 14 | minor | `tools/trace_specs.py:22-24`, `:79`; `docs/spec/TRACEABILITY.md:9-11`, `:36` | Retired rows still count as live IDs: AIR-003, 004, 005 and 030 show as `**none**` (which reads as uncovered), and a stale `@AIR-003` tag would pass `check()`. | In `known_ids()`, also return the IDs whose row contains `**Retired`. `check()` reports a tag that names one; `write()` prints `retired (OD-31)` instead of `**none**`. |
| 15 | minor | `specs/features/03_runner.feature:90`, `:101`, `:112` | These three scenarios assert AIR-033(c) ordering ("the firewall reminder comes only after UxPlay has stopped", `:97`, `:109`, `:119`) but aren't tagged, so traceability under-counts AIR-033. | Add `@AIR-033 @AT-33` to those tag lines. |
| 16 | minor | `docs/spec/REQUIREMENTS.md:82` (AT-33); `tests/steps/prompt_steps.py:116-123` | AT-33 says "an unwritable runs folder after launch", but the step makes only `run.json` unwritable. The scenario also runs `runner.run` only. Through `s1.py run`, the evidence writer adds fields to `run.json` afterwards (`S1_INTERFACES.md:128-129`), so it could create the record or report a save. AIR-033(e) "the command MUST NOT claim a record was made" isn't checked at the command level. | Reword AT-33 (below). Add a CLI variant: `Given the run record can't be written once UxPlay is running`, `When "s1.py run" runs`, `Then no run record exists`, `And the console output doesn't claim a record was made`. |

## Missing scenarios

Steps marked `# new` need a definition. The others exist.

```gherkin
  # 1: AT-32 across every command and early stop. Needs finding 2's canary fix, because these
  # rows start no process.
  @offline @failure @AIR-032 @AT-32 @OD-31
  Scenario Outline: No command, usage error or preflight stop starts a firewall command
    Given every process the S1 tools start is recorded
    And <condition>
    When "s1.py <arguments>" runs
    Then none of the recorded processes is a firewall command

    Examples:
      | arguments                          | condition                                            |
      | --help                             | nothing else is set up                               |
      | frobnicate                         | nothing else is set up                               |
      | run --bogus                        | nothing else is set up                               |
      | check                              | nothing else is set up                               |
      | build                              | nothing else is set up                               |
      | summary                            | nothing else is set up                               |
      | run                                | the build provenance file is missing                 |
      | run                                | the runs folder can't be written                     |
      | analyse fixture.log -o report.json | the report path can't be written                     |
      | evaluate fixture-run               | a fixture run whose evidence makes its verdict NO-GO |
  # "nothing else is set up" is a new no-op Given.

  # 2: AT-32 on the runner's own early stops
  @offline @failure @AIR-032 @AT-32 @OD-31
  Scenario Outline: No refused, uncaptured or uncreated launch starts a firewall command
    Given <build>
    And every process the S1 tools start is recorded
    And <condition>
    When the runner attempts to launch the receiver
    Then none of the recorded processes is a firewall command

    Examples:
      | build                                                                                 | condition                                                    |
      | the build at "C:\uxplay-s1\build\uxplay.exe" whose SHA-256 differs from its provenance | nothing else is set up                                       |
      | the verified build at "C:\uxplay-s1\build\uxplay.exe"                                 | UxPlay starts but its output can't be attached to the run's log |
      | the verified build at "C:\uxplay-s1\build\uxplay.exe"                                 | creating the UxPlay process raises an operating-system error |

  # 3: AIR-033(f). Add rows for "UxPlay exiting mid-test" and "a keyboard interrupt" once the
  # owner rules on finding 5.
  @offline @boundary @AIR-031 @AIR-033 @AT-31 @AT-33
  Scenario Outline: The reminder doesn't change the run command's exit code
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the run will end by <ending>
    When "s1.py run" runs
    Then it exits with code <code>
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"

    Examples:
      | ending                                    | code |
      | the owner ending the test                 | 0    |
      | the 10-minute timeout                     | 0    |
      | UxPlay failing to start                   | 1    |
      | UxPlay's output failing to be captured    | 1    |
  # "the run will end by ..." is a new Given that sets the same fakes as the existing When,
  # without plan_run; it adds the capture-failure ending.

  # 4: replaces 02_firewall.feature:22-37 (findings 8 and 11)
  @offline @normal @AIR-033 @AT-33 @OD-33 @OD-35 @OD-38
  Scenario Outline: Every run that launched UxPlay ends with the reminder
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the run ends by <ending>
    Then the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And the reminder says a declined or closed prompt may have left a block entry to remove too
    And the reminder came after UxPlay had stopped and after the log's end marker
    And the log's last line is the runner's end marker naming "<reason>"
    And the log's last line is still the runner's end marker
    And the run record says the firewall was "owner-handled"
    And the run record doesn't say an allowance was granted, declined, present or removed

    Examples:
      | ending                    | reason          |
      | the owner ending the test | owner ended     |
      | the 10-minute timeout     | timeout         |
      | UxPlay failing to start   | failed to start |
      | UxPlay exiting mid-test   | uxplay exited   |
      | a keyboard interrupt      | interrupted     |

  # 5: add to 08_cli.feature:40-45 and :53-59, after "UxPlay is never launched"
    And the console owner is given no firewall notice or reminder   # new, CLI-safe

  # 6: finding 12
  @offline @failure @AIR-006 @AIR-033 @AT-06 @AT-33
  Scenario: A capture failure whose UxPlay won't stop names the process, with the reminder
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And UxPlay starts but its output can't be attached to the run's log
    And the UxPlay process ignores the stop request
    When the runner attempts to launch the receiver
    Then the run reports the diagnostic capture failure
    And the run reports the UxPlay process that is still running, with its process ID
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And the reminder came after the run reported the process it couldn't stop
```

## Requirement wording

Proposals for the owner to rule on, not decisions:

- **AIR-031(f)**, add (finding 5): "`owner ended`, `timeout` and `uxplay exited` are completed runs and exit 0. `refused`, `failed to start` and `capture failed` exit 1. `interrupted` exits 130." The `uxplay exited` and `interrupted` codes need an owner ruling.
- **AIR-028(a)**, replace (finding 6): "(a) The runner MUST write a start marker as the log's first line before UxPlay's output. After UxPlay has stopped, or after the runner has reported the process it couldn't stop, it MUST write an end marker naming the termination reason, and the process ID still running if any, as its last line at the time of writing." Owner ruling needed: the alternative is no end marker, which makes the log `truncated` under AIR-028(c).
- **AIR-033(a)**, replace the last sentence: "A run that stops before UxPlay's process is created (bad provenance, unwritable output, refused build, or process creation raising an error) gives no reminder." This states the reading that `03_runner.feature:158-166` already tests.
- **AT-32**, after "a keyboard interrupt": "and a normal `check`, `build`, `analyse`, `evaluate` and `summary`".
- **AT-33**, replace "With an unwritable runs folder after launch" with "With a run record that can't be written after launch, through both the runner and `s1.py run`".

## Traceability

- Add `@AIR-033 @AT-33` to `03_runner.feature:90`, `:101`, `:112`.
- Missing scenarios 1 and 2 add AIR-032 and AT-32 (2 outlines, 13 rows). Scenario 3 adds AIR-031, AIR-033, AT-31 and AT-33. Scenario 6 adds AIR-006, AIR-033, AT-06 and AT-33. Scenario 4 replaces an existing outline, so the count doesn't change for it. Total: 71 → 75 scenarios once 1, 2, 3 and 6 are added.
- `trace_specs.py` should show the retired rows (AIR-003, 004, 005, 030) as retired, not `**none**`, and reject tags that name them.
- Regenerate `TRACEABILITY.md` after the changes.

## Not verified

- No mutation was run, because `airplay_s1.runner` and `airplay_s1.cli` don't exist. Every surviving mutation above comes from reading the steps.
- That every Windows `Popen` goes through `_winapi.CreateProcess` comes from reading CPython 3.12's `subprocess.py`. It wasn't checked against this `.venv`'s interpreter.
- Windows' actual prompt on this PC was not observed: whether its checkbox label contains "Public networks", whether declining adds a block entry under Allowed apps (OD-33), and whether clicking Allow needs administrator consent on the owner's account. If it needs admin approval on an unelevated account, the notice's instructions may be incomplete.
- That pytest-bdd fills `<condition>` and `<build>` into the Given text for Missing scenarios 1 and 2 was not checked on the installed version.
- `AMBIGUITY_REGISTER.md` was searched for firewall entries but not read in full. The `.astra/` raw drafts still mention the retired rows; they are history, so not counted as leftovers.
- `@pc` and `@host` were not run, as instructed.
