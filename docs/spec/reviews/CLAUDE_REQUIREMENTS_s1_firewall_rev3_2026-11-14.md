# Slice S1 requirements, revision 3: no scripted firewall changes (OD-31)

> Author: a fresh-context Claude subagent standing in for `gpt-6-astra`, which was at its Codex usage limit (OD-32). Same role prompt (`tools/astra/roles/astra-requirements-author.md`) and prompt (`tools/astra/prompts/S1_REQUIREMENTS_REV3_FIREWALL.md`), with STAGE_ROOT as the project root. Read-only.
> Date 2026-11-14.

**Drafted specification, not an owner decision. Substitute for Astra under OD-32.**

---

## Requirements

### Changed and retired

Retired rows stay in the table under their IDs. New behaviour goes into the next free IDs, AIR-032 and AIR-033, so no retired ID gets a new meaning.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-003 | **Retired by OD-31.** Was: before creating the run's rule, detect and remove leftover runner-owned rules and stop before launch if removal fails. The runner now creates and removes no rule, so it has no leftovers to remove. | OD-31 supersedes the scripted rule in OD-05 and OD-27. |
| AIR-004 | **Retired by OD-31.** Was: create an inbound allow rule for the built `uxplay.exe` on the active profile, and refuse launch if that fails. The allowance now comes from Windows' own prompt, answered by the owner. | OD-31 supersedes OD-05's scripted rule. The Public profile fact (OD-06) now applies to the owner's allowance. |
| AIR-005 | **Retired by OD-31.** Was: remove and verify the run's rule after every ending, and report a residual rule. The runner removes nothing. The owner removes the allowance (AIR-033 reminds them how). | OD-31 supersedes OD-05's "removed after the test" and OD-29's AIR-005 cleanup clause. |
| AIR-006 | The runner MUST launch the verified build with HEVC disabled, advertising `1920×720` at `60 fps` with receiver name `HU-Test`. It MUST record the effective launch configuration and capture UxPlay's diagnostic output to the run's log. If UxPlay fails to start or log capture can't be set up, the runner MUST report it, stop any UxPlay process the run started, report any process it cannot stop, and give the end-of-run reminder (AIR-033). | OD-04/05, OD-14, OD-31; S1 configuration. Tests AMB-02 without settling product-wide AMB-04. "Enter cleanup" is replaced: there is no firewall cleanup. |
| AIR-007 | (a) Once launched, the runner MUST tell the owner how to select `HU-Test` through iPhone Screen Mirroring and wait for the test. (b) The timeout MUST be 10 minutes measured from receiver launch. Connection and video-start events MUST NOT restart that timer. The owner MUST be able to end the test earlier. (c) At owner termination or timeout, the runner MUST stop the launched UxPlay process, then give the end-of-run reminder (AIR-033). It MUST record the termination reason, report any process it cannot stop, and give the reminder even when the stop fails. | OD-06, OD-08, OD-14, OD-31. Clause (c)'s "then perform firewall cleanup" is replaced. |
| AIR-030 | **Retired by OD-31.** Was: the runner owns a rule only if it is named `NEMESIS-AirPlay-S1` with a `uxplay.exe` under the recorded build root, and a same-named rule for another program stops the run. Once the runner touches no rule, ownership has no use. | OD-31 supersedes OD-27 in full. |
| AIR-031 | (a) The S1 tools MUST be one command, `python tools/s1.py <command>`, with the commands `check`, `build`, `run`, `analyse`, `evaluate` and `summary`. (b) `--help`, alone or after any command, MUST print usage and exit 0 with no other effect. (c) An unknown command, unknown option or missing required argument MUST exit 2 with usage on stderr. It MUST NOT start a build, change a firewall rule, launch UxPlay, or create or change any file. (d) If the build provenance is missing or unreadable, `run` MUST exit 1 naming the file, before UxPlay is launched. (e) If an output location can't be written, the command MUST exit 1 naming it, before UxPlay is launched, and leave no partial output file. (f) Any other failure MUST exit 1 with its reason on stderr. A failure after UxPlay is launched MUST stop the launched process and report any process it cannot stop. A completed command exits 0, whatever its verdict. | OD-28; OD-29's preflight (provenance and output locations checked first; later failures exit 1). Its firewall clauses are superseded by OD-31. Clauses (d) to (f) read OD-28's "before the firewall is touched" as "before receiver launch", which is open question 8. |

### New

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-032 | (a) No S1 command MUST create, change, enable, disable or remove any Windows Firewall rule or firewall setting. This holds on every path: success, NO-GO, failure, timeout, refusal and catchable interruption. (b) The runner MUST leave Windows' own firewall prompt to the owner: it MUST NOT answer, dismiss or suppress it. | OD-31: "The runner makes no firewall changes." Whether the runner may *read* firewall state is open question 2. |
| AIR-033 | (a) At the end of every `python tools/s1.py run` invocation, the runner MUST remind the owner how to remove the firewall allowance. Help and usage errors (AIR-031(b), (c)) are excluded. The reminder covers every ending: `owner ended`, `timeout`, `uxplay exited`, `failed to start`, `capture failed`, `interrupted`, `refused`, and the exit-1 preflight failures in AIR-031(d) and (e). (b) The reminder MUST name the program `uxplay.exe`, MUST name the location `Windows Defender Firewall > Allowed apps`, and MUST tell the owner to remove that entry. It MAY also give the executable path recorded in the provenance. (c) When UxPlay was launched, the reminder MUST come after UxPlay has stopped, or has been reported as impossible to stop, and after the log's end marker. It MUST NOT be written into the run's log, so AIR-028's last-line rule still holds. (d) Whenever the run writes its run record (`run.json`), the record MUST say that the firewall was handled by the owner. The record MUST NOT say that an allowance was granted, declined, present or removed, because the runner doesn't observe any of these. (e) If the run record can't be written (AIR-031(e)), the reminder MUST still be given, and the command MUST NOT claim a record was made. (f) Giving the reminder MUST NOT change the command's exit code. | OD-31: "At the end of every run the runner reminds the owner how to remove that allowance ... and records that the firewall was handled by the owner." Proposed record form: `run.json` key `firewall` with the value `owner-handled`. The marker text is a draft detail. Including pre-launch failures reads "every run" literally (open question 4). The owner's own steps (allowing the prompt on Public, removing the allowance) are not runner behaviour and get no requirement row. |

### Unchanged

- Requirements: **AIR-001, AIR-002, AIR-008 to AIR-029**. AIR-014 is not affected: its tier and claim limits don't depend on how the firewall was opened. AIR-001 and AIR-002 still cite OD-05, whose MSYS2, GStreamer and UxPlay parts are not superseded.
- Acceptance tests: **AT-01, AT-02, AT-08 to AT-29**.

## Acceptance tests

`@offline` uses synthetic fixtures only. `@host` needs this PC but no iPhone. `@pc` needs this PC and the owner's iPhone. No row here is `@unit`.

| ID | Requirements | Criteria |
|---|---|---|
| AT-03 | AIR-003 | **Retired by OD-31.** There is no stale-rule removal left to test. |
| AT-04 | AIR-004 | **Retired by OD-31.** There is no rule creation left to test. |
| AT-05 | AIR-005 | **Retired by OD-31.** There is no rule removal or residual-rule reporting left to test. |
| AT-06 | AIR-006 | **`@offline`:** Synthetic launch cases check the verified executable, the exact name `HU-Test`, HEVC disabled, `1920×720@60` and log capture. On a launch or capture failure they check that the failure is reported, that no process the run launched is still running (or the one that can't be stopped is named with its PID), and that the AIR-033 reminder is given. **`@pc`:** Retain the effective command, configuration and startup log, checked against the pinned revision's option semantics, including the receiver name. |
| AT-07 | AIR-007 | **`@offline`:** Controlled-clock fixtures prove the timeout falls at launch plus 600 seconds, including a delayed connection and a reconnection, with no timer reset. An earlier owner termination stops the process, and the AIR-033 reminder follows. A stop failure names the residual process, and the reminder still follows. **`@pc`:** On the wired-PC/home-Wi-Fi arrangement, verify the Screen Mirroring prompt and process termination for an early owner end and for the timeout. Retain launch and end times, termination reasons and process checks. |
| AT-30 | AIR-030 | **Retired by OD-31.** There is no rule ownership left to test. |
| AT-31 | AIR-031 | **`@offline`:** Help, unknown command, unknown option, missing argument, missing or unreadable provenance, and unwritable output each give the specified exit code and message. Instrumented fakes record no build, launch or file change for the usage errors, and no launch for the other failures. The no-firewall-change check is carried by AT-32. |
| AT-32 | AIR-032 | **`@offline`:** A recording process launcher captures every process the S1 tools start across help, every usage error, every preflight failure, `refused`, `failed to start`, `capture failed`, `uxplay exited`, `owner ended`, `timeout` and a keyboard interrupt. None of those processes is `netsh` or a PowerShell firewall cmdlet. A scan of `tools/` finds no firewall-changing command: `New-NetFirewallRule`, `Set-NetFirewallRule`, `Remove-NetFirewallRule`, `Enable-NetFirewallRule`, `Disable-NetFirewallRule`, `Set-NetFirewallProfile`, `netsh advfirewall`, `netsh firewall`. It also finds no code that sends input to or closes another window. **`@host` (proposal, open question 7):** Firewall rule lists saved before and after a run show every rule that existed before the run unchanged after it. Rules added during the run are listed with their program and not counted against the runner, because Windows' prompt adds them. This check cannot prove the runner created nothing. The `@offline` half carries that. |
| AT-33 | AIR-033 | **`@offline`:** For each ending in AIR-033(a), the owner double receives the reminder. The reminder contains `uxplay.exe`, `Windows Defender Firewall`, `Allowed apps` and an instruction to remove the entry. When UxPlay was launched, the reminder comes after the process stop and after the end marker, and the log's last line is still the end marker. `run.json` carries the owner-handled record and no claim that an allowance was granted, declined, present or removed. The exit code is the one AIR-031 gives for that ending. With an unwritable runs folder, the reminder is still given and no `run.json` exists. Help and usage errors give no reminder. **`@pc`:** In the live run, a recording wrapper around the console owner (`Deps.real`) shows the reminder after UxPlay stopped, and the live `run.json` holds the owner-handled record. No tier gives evidence that the owner actually allowed or removed anything. |

**`@host` firewall checks that remain:** none of the old ones. Both `@host` scenarios at `specs/features/02_firewall.feature:97-115` go with AT-03, AT-04, AT-05 and AT-30. The only candidate left is the before/after "pre-existing rules unchanged" check in AT-32, which depends on open question 7.

## Rationale

- **AIR-003, AIR-004, AIR-005, AIR-030 (retired):** OD-31 (`docs/spec/DECISIONS.md:39`) says the runner "makes no firewall changes" and supersedes the scripted rule in OD-05 (`:13`), OD-27 (`:35`) and OD-29's firewall clauses (`:37`). Each of these rows exists only to create, own or remove that rule (`docs/spec/REQUIREMENTS.md:13-15,40`), so no wording could keep them while obeying OD-31. They are retired rather than reused, so existing `@AIR-003/004/005/030` tags can't silently change meaning.
- **AIR-006:** "enter cleanup" (`REQUIREMENTS.md:16`) meant removing the rule (`docs/spec/S1_INTERFACES.md:68-70`). Stopping the started process is kept, because that is the only state a failed launch can now leave behind. The reminder is added because OD-31 says "every run".
- **AIR-007(c):** "stop UxPlay, then firewall cleanup" (`REQUIREMENTS.md:17`, `S1_INTERFACES.md:73-74`) becomes "stop, then remind". The order is kept so the reminder can't come before the end marker (AIR-028, `REQUIREMENTS.md:38`).
- **AIR-031(d)-(f):** OD-29's preflight checks stand: provenance, hash and writable outputs (`S1_INTERFACES.md:62-63`). With no firewall change left as the line to stop before, receiver launch is the next state-changing step. The residual-rule half of (f) (`REQUIREMENTS.md:41`) goes. (c) is unchanged: "change a firewall rule" is still true there and is backed by AIR-032.
- **AIR-032:** Encodes OD-31's first sentence. Clause (b) encodes "Windows' own firewall prompt asks the owner". The proposed test is a source and process scan rather than a firewall fake, because after OD-31 the runner should take no firewall backend at all (`S1_INTERFACES.md:45-51`, `tools/airplay_s1/firewall.py:80-90`).
- **AIR-033:** Encodes OD-31's reminder and record sentence. The record says "handled by the owner" and nothing more, because the runner can't see the prompt or the owner's answer, and claiming more would break the evidence-honesty rule (`specs/README.md:32-44`). The location the reminder names is quoted from OD-31.
- **Fallout outside the tables (for the parent):** The following all assume the scripted rule:
  - `specs/features/02_firewall.feature` (all scenarios)
  - `specs/features/03_runner.feature:5,39,54,62,64-85,101,113,123,127-134`
  - `specs/features/06_evidence.feature:77,87`
  - `specs/features/08_cli.feature:5,13,30,41-45,58`
  - `S1_INTERFACES.md:45-51` (the `firewall` module)
  - `S1_INTERFACES.md:62-75` (preflight wording, rule steps, `firewall-*.json`)
  - `S1_INTERFACES.md:77-79` (`RunRecord.rule`, `cleanup_ran`, `cleanup_clean`, `residual_rule`)
  - `S1_INTERFACES.md:152` (`Deps.firewall`)
  - `docs/spec/TRACEABILITY.md:9-11,36`
  - `AGENTS.md:55-59` (elevation, because "the runner creates and removes a Windows Firewall rule")
  - `tools/airplay_s1/firewall.py`
  - `tests/steps/firewall_snapshot.ps1`

  OD-29's register entry is not edited. It is superseded in part by OD-31's own wording.

## Reversibility and safety

- **AIR-032:** This removes the tools' only firewall-changing code path. Undoing it means reverting the commit.
- **The owner's allowance (outside the runner):**
  - When the owner clicks Allow, Windows creates its own inbound rules for that exact `uxplay.exe` path. They persist across runs and reboots until the owner removes them under Allowed apps.
  - While they exist, any later launch of that `uxplay.exe` can be reached from any network Windows classes as Public, not only the home network.
  - A clean rebuild into a new directory (AT-02 `@host`, `S1_INTERFACES.md:42-43`) is a new path, so it gets a new prompt and a new entry to remove.
  - If the owner declines, Windows may create block entries for the program (not verified). Those also persist and are removed in the same place.
- **Forced kill or power loss:** No reminder is given and `run.json` may lack the record. The runner leaves no firewall state of its own. Any allowance the owner granted remains until the owner removes it.
- **AIR-033:** Prints console text and writes one field into `run.json` under the uncommitted `runs/`. Deleting the run folder undoes it.
- **Current PC state:** A read-only check on 2026-11-14 found no rule named `NEMESIS-AirPlay-S1` and none with "uxplay" in its display name. No leftover from the old design needs removing.
- **Elevation:** The runner no longer needs admin rights for the firewall. The test-side snapshot still does while it reads port filters (`AGENTS.md:55-59`).

## Open questions

1. **Declined prompt.** If the owner declines Windows' prompt, the iPhone can't connect. Under the existing rules the run ends INCONCLUSIVE with "no iPhone connected" (AIR-010(a), (f); OD-26). Should a declined prompt get a distinct reason or message? Should the reminder also cover removing a block entry Windows may have created?
2. **No prompt appears.** An allowance (or block) may already exist from an earlier run or build, so Windows doesn't ask. Should the runner say anything about this at launch? May it *read* firewall state to tell the owner, given that reading is not a change but a full listing may need elevation? Or should it do nothing?
3. **Notice before launch.** OD-31 specifies only the end-of-run reminder. Should the runner also tell the owner at launch that Windows may ask, and to allow on Public only?
4. **Which invocations get the reminder.** The draft gives it on every `run` invocation except help and usage errors, including preflight failures and `refused`, where this run never launched UxPlay. Confirm, or narrow it to runs that launched UxPlay.
5. **Where the record lives.** The draft proposes the `run.json` field `firewall: owner-handled`. Should the redacted summary in `docs/evidence/` carry it too? Should a preflight failure write a run record at all, so the "records" half of OD-31 holds for those runs?
6. **Reminder content.** Should the reminder give the exact executable path, or every `uxplay.exe` path built so far, since each rebuild path is a separate allowance?
7. **The `@host` firewall snapshot.** Keep the before/after "pre-existing rules unchanged" check in AT-32, or drop it?
   - Keeping it with port filters needs elevation for `--run-host` and `--run-pc`.
   - `AGENTS.md:58-59` records that a listing without port filters read all 944 rules unelevated, so a reduced snapshot could drop the elevation requirement.
   - Dropping the check leaves AIR-032 with `@offline` evidence only.
8. **OD-28's wording.** OD-28 says failures exit 1 "before the firewall is touched", and OD-31 does not name OD-28. Confirm it now reads "before receiver launch", as AIR-031(d)-(f) are drafted.

## Not verified

- Windows 11 10.0.26200's prompt behaviour for `uxplay.exe`:
  - whether it appears for a non-elevated process that starts listening;
  - which network boxes are pre-checked;
  - whether allowing on Public needs admin approval;
  - whether Cancel creates block rules;
  - whether entries are per path;
  - what name the entry shows under Allowed apps.

  No run was performed.
- Whether UxPlay 1.74's own mDNS traffic and its AirPlay ports are all covered by one program allowance, or whether the installed Bonjour Service (`docs/evidence/S1_PC_FACTS.md:10`) changes that.
- `tools/airplay_s1/` has no `runner.py` or `cli.py` yet, so there is no runner code to check against these rows. `firewall.py` still holds `create` and `remove` (`:80-90`).
- The read-only rule check ran unelevated. It returned no error and no match, but full visibility without elevation was not confirmed.
- `SCOPE.md` was not read for this revision.
