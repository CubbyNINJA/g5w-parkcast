# S2 parking-gate requirements, revision 3 (OD-51, OD-54 to OD-60)

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0ca-2178-7583-a7a3-13a33f87d5e4`, 1 turn(s), 139 s; tokens in 417273 (cached 368256), out 4711 (reasoning 160).
> Prompt: `tools/astra/prompts/S2_PARKING_REQUIREMENTS_REV3.md`. Date 2026-11-15.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

Replace only the rows below, retaining revision 2’s table format and IDs. No new IDs are needed. Permission to display video means permission from this receiver’s parking gate; independent stock restrictions remain unchanged.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-017 | (a) With the parking requirement enabled, the gate MUST initially block mirrored video and MUST permit it only when a current, available gear reading positively establishes Park on a supported firmware build. (b) Stationary status or an applied parking brake alone MUST NOT establish Park. (c) A successful status-request call returning no gear value MUST NOT establish Park. | OD-23, OD-24, OD-46, OD-47. Probe evidence identifies stock notification mappings: `onGearPStatus` 1 = Park, 0 = non-Park; `onGearStatus` 0 = Park, 255 = non-Park. The probed BCM calls return no value. **AMB-33 remains open for the receiver’s interface and permission; these notification names do not select an interface.** |
| AIR-019 | With the parking requirement enabled, (a) unavailable, erroring or unknown gear information MUST block video at startup and after a previously valid Park reading; (b) a reading’s age MUST count from receiver receipt, and a reading older than 2 s MUST count as unavailable; (c) an earlier Park reading MUST NOT override a known signal failure; (d) the receiver MUST re-read gear on every resume from sleep, and video MUST remain blocked until that fresh re-read confirms Park; a pre-sleep Park reading MUST NOT allow video after resume. Recovery MUST follow AIR-037. | OD-23, OD-46, OD-48, OD-56. **AMB-33:** the fresh-read mechanism remains unresolved. The probed BCM calls return no gear value; their completion is not a fresh reading. |
| AIR-020 | (a) The gate MUST identify the supported `CVEV.*` firmware family from `ro.build.product`, in any region, and MUST use the 260206 CA gear-probe Park/non-Park mapping across that family. (b) With the parking requirement enabled, a build outside that family, or one that cannot be identified as belonging to it, MUST leave video blocked even if a raw value resembles Park. | OD-47, OD-51. Mapping portability within the family is an owner-authorized assumption, not verified evidence for other builds. AMB-33 remains open for gear access. |
| AIR-023 | (a) The flag MUST be key `video.require_park` in package-relative `config/receiver.conf` (source `package/config/receiver.conf`), installed at `/data/nemesis/apps/<id>/config/receiver.conf`, with default `true`. (b) Parsing MUST trim surrounding whitespace and accept only lowercase `true` and `false`. A key duplicated with conflicting values MUST count as unrecognised. (c) A missing file or key, unreadable file or unrecognised value MUST resolve to `true`, including after a previously disabled run. (d) The receiver MUST read the installed flag at every app start. A change made by editing the installed copy or rebuilding/reinstalling MUST take effect at the next app start, not during the running instance. | OD-43, OD-55. OD-54’s shipped-file behavior is specified in AIR-035. |
| AIR-035 | (a) A successful update or reinstall MUST replace the installed `receiver.conf` with the file shipped by that package and MUST NOT preserve a locally edited flag. (b) A normal update or reinstall shipping `video.require_park=true` MUST turn the check on at the next app start. A package deliberately rebuilt with `video.require_park=false` MUST start with the check off. (c) If update or reinstall fails before the shipped file is in place, a missing or unreadable installed file MUST resolve to check-on at the next app start under AIR-023; no additional failed-install behavior is required by this parking-gate requirement. | OD-43, OD-44 as clarified by OD-54; OD-60. No additional rollback or forced configuration rewrite is specified. |
| AIR-038 | (a) The receiver MUST log the stock regulation state (`com.hkmc.regulation`) at app start and on every change notification. An unavailable state MUST be logged as `unavailable` with the reason. (b) That state MUST neither block nor allow video through the receiver’s parking gate; the gate MUST use its own gear check. Logging failure MUST follow AIR-040. | OD-47, OD-49, OD-59. |
| AIR-039 | (a) Receiver logs MUST go to `/data/nemesis/logs/airplay-receiver/`. (b) All receiver logs together MUST be capped at 5,242,880 bytes (5 MiB), kept as two rotated files. When that cap is reached, the oldest whole file MUST be deleted. Logging failure MUST follow AIR-040. | OD-49, OD-57. |
| AIR-040 | (a) A logging failure MUST neither block nor allow video by itself, with either parking-flag value. (b) A logging failure MUST be reported on screen, but the logging-failure notice MUST be shown at most once per app start. Further logging failures during that same app start MUST NOT produce another notice. | OD-49, OD-58. Recovery from a logging fault does not start a new notice allowance; a new app start does. |

## Acceptance tests

Each future scenario uses `@S2`, its requirement and test IDs, exactly one tier, and one kind tag. The tiers below require separate scenarios. `@offline` uses synthetic fixtures only. `@unit` requires head-unit evidence retained on the stick, supplemented by observations where logs cannot prove display behavior. Neither offline nor PC results establish head-unit acceptance.

| ID | AIR IDs | Criteria |
|---|---|---|
| AT-17 | AIR-017 | **`@offline`:** Synthetic startup remains blocked before qualifying Park evidence. Fixtures exercise both documented notification mappings, preserving which notification supplied each value. Fresh Park on a supported build permits video; non-Park, stationary status alone, parking brake alone and successful no-value request replies do not. **`@unit`:** Retain build identity, actual signal-source and mapping evidence, receiver startup/gate logs and corresponding display observations. The existing stock-service probe does not prove that the receiver can register for notifications; that integration evidence remains dependent on AMB-33. |
| AT-19 | AIR-019 | **`@offline`:** Missing service, exceptions, service loss and unknown values block at startup and after Park. Controlled clocks distinguish source time from receiver receipt; age is measured from receipt. Test just below, exactly at and above 2 s: exactly 2 s is not stale solely by age; above 2 s is unavailable. Every resume requests fresh gear confirmation. Even a pre-sleep Park reading younger than 2 s cannot permit video while confirmation is pending. No-value replies, errors and non-Park leave video blocked; fresh Park restores video under AIR-037. With the flag off, these conditions impose no parking-gate block under AIR-021. **`@unit`:** Retain receipt timestamps, resume and fresh-confirmation evidence, exercised faults and display observations. Record unexercised cases as unverified. |
| AT-20 | AIR-020 | **`@offline`:** Synthetic `ro.build.product` values from different `CVEV.*` regions use the same synthetic representation of the documented mapping. Outside-family, missing and unreadable product values block with the check on despite Park-like readings. Conflicting values in other build properties do not replace `ro.build.product` as the identifier. **`@unit`:** Retain the actual `ro.build.product` value and probe mapping alongside receiver behavior. Identify physically tested builds; do not report the family-wide assumption as measured portability. |
| AT-23 | AIR-023 | **`@offline`:** Synthetic files exercise the specified location/key, lowercase `true`/`false`, surrounding whitespace, case variants, unknown values, missing file/key and unreadable file. Conflicting duplicates in either order enable the check rather than choosing the first or last value. Fallback also works after a disabled run. Editing the file leaves the running setting unchanged; the next start reads it. **`@unit`:** Retain relevant configuration and startup evidence proving the installed path, parsing cases and edit/restart behavior. Rebuilt-package replacement is tested under AT-35. |
| AT-35 | AIR-035 | **`@offline`:** Synthetic update and reinstall separately replace local edits with shipped configuration. Shipping `true` restores check-on at next start; deliberately shipping `false` produces check-off. Failed replacement leaving a missing or unreadable file enables the check at next start. A partial installation is not reported as successful replacement. **`@unit`:** Retain before/after configuration, installation outcome and startup records for update, reinstall and a deliberately rebuilt `false` package. Retain evidence for any exercised missing/unreadable-file failure case. No additional rollback behavior is an acceptance condition. |
| AT-38 | AIR-038 | **`@offline`:** Synthetic regulation state is logged at startup and on every change notification; unavailable observations contain literal `unavailable` and a reason. Hold flag, build and gear constant while varying regulation state, including unavailability: an allowed gate stays allowed and a blocked gate stays blocked. **`@unit`:** Retain startup and change-notification records correlated with regulation and gate/display observations. Exercise unavailability where possible and identify untested cases. Do not modify or spoof stock regulation to obtain evidence. |
| AT-39 | AIR-039 | **`@offline`:** A synthetic filesystem verifies the destination, two-file rotation and aggregate 5,242,880-byte cap. Exercise totals just below, exactly at and crossing the cap; reaching the cap triggers deletion of the oldest whole file. The cap is not applied separately to each file, and record trimming does not substitute for whole-file deletion. Retention failures follow AT-40. **`@unit`:** Retain timestamped file inventories, byte totals and rotation/deletion evidence demonstrating the aggregate cap and oldest-file removal. |
| AT-40 | AIR-040 | **`@offline`:** Inject log-create, write and retention failures with video allowed and blocked and with either flag. The first failure produces one on-screen notice without changing the gate decision. Repeated failures, including failure after logging recovers, produce no further notice during that app start. After a new app start, its first logging failure produces one new notice. **`@unit`:** Independently observe the notice count and unchanged gate behavior across repeated faults and restart. Retain evidence outside the failed receiver log. |

## Rationale

**AIR-017.** The probe distinguishes notification evidence from empty request replies. It establishes Park/non-Park mappings without establishing the receiver’s accessible interface (`docs/evidence/GEAR_PROBE_FINDINGS.md:11`, `:35`, `:45`; `docs/spec/AMBIGUITY_REGISTER.md:43`).

**AIR-019.** OD-56 fixes receipt-based age and invalidates pre-sleep Park permission after resume. The notification findings leave the mechanism for obtaining fresh confirmation unresolved (`docs/spec/DECISIONS.md:64`; `docs/evidence/GEAR_PROBE_FINDINGS.md:19`, `:49`).

**AIR-020.** OD-51 selects `ro.build.product`; the probe records its actual value. OD-47 authorizes applying the mapping across the family, without supplying measurements from other builds (`docs/spec/DECISIONS.md:55`, `:59`; `docs/evidence/GEAR_PROBE_FINDINGS.md:58`, `:80`).

**AIR-023.** OD-55 supplies strict parsing while OD-43 retains next-start activation and fallback behavior (`docs/spec/DECISIONS.md:51`, `:63`). No additional configuration grammar is selected.

**AIR-035.** OD-54 clarifies that replacement uses the actual shipped file, including deliberate `false` builds. OD-60 adds no recovery policy beyond existing fallback; it does not promise that a failed replacement resets a surviving readable `false` file (`docs/spec/DECISIONS.md:62`, `:68`).

**AIR-038.** OD-59 supplies logging cadence and unavailable-state representation. Regulation remains evidence only (`docs/spec/DECISIONS.md:55`, `:67`).

**AIR-039.** OD-57 supplies the exact aggregate limit, two-file rotation and deletion unit. “Reached” replaces the previous acceptance row’s “exceeded” trigger (`docs/spec/DECISIONS.md:65`; `docs/spec/REQUIREMENTS.md:94`).

**AIR-040.** OD-58 limits notices per app start while preserving OD-49’s obligation to report logging failure and its independence from video permission (`docs/spec/DECISIONS.md:57`, `:66`).

The reconciliation gaps are:

| # | File | Problem | Exact Fix | Hook Required? |
|---|---|---|---|---|
| 1 | `docs/spec/REQUIREMENTS.md` | Eight requirement/test pairs retain unresolved wording now answered by decisions or probe evidence. | Replace AIR-017, AIR-019, AIR-020, AIR-023, AIR-035, AIR-038–040 and their matching AT rows with the rows above. | No |
| 2 | `docs/spec/REQUIREMENTS.md` | Draft provenance needs this revision recorded. | Add a revision-3 note listing OD-51 and OD-54–60, the changed IDs and any wording changed during adoption. | No |

## Reversibility and safety

No files were edited and nothing was installed.

- **AIR-017, AIR-019, AIR-020:** Affect receiver runtime gating and read-only signal access. Stopping the receiver ends that runtime behavior; no vehicle settings require restoration.
- **AIR-023, AIR-035:** Affect receiver configuration. Editing the installed flag and restarting changes it again. Successful update/reinstall discards local edits. Failed installation receives no additional rollback guarantee.
- **AIR-038, AIR-039:** Create and rotate receiver logs. Deleted oldest files cannot be recovered from this log store. No uninstall cleanup policy is added.
- **AIR-040:** Adds a per-start fault notice without changing video permission.

These rows require no PC installation or firewall changes. Existing prohibitions on vehicle actuation, direct CAN writes and stock-regulation changes remain in force.

## Open questions

**AMB-33 remains partly open:** Which stock interface provides the required Park notifications, and what permission allows this receiver to register? Within that integration work, establish how startup and resume obtain fresh Park confirmation and how fresh readings arrive while gear remains unchanged. The probed BCM replies cannot provide that value; the candidate getter remains unverified.

This does not reopen the decided two-second freshness rule or blocked-until-fresh-Park resume behavior. No interface, polling policy or permission is selected here.

## Not verified

- Read-only review established no runtime acceptance. No scenarios, tests or implementation were written or run.
- The supplied probe findings were read; raw stick artifacts and OEM binaries were not inspected.
- Receiver subscription access, permissions and fresh-confirmation delivery remain unverified.
- Only 260206 CA has the supplied mapping evidence. Other-family-member behavior remains an owner-authorized assumption.
- Actual display timing, resume handling, audio continuity, installation failures, log rotation and fault notices require unit evidence.
- `SCOPE.md` was absent from the stage; the supplied scope exclusions were followed.
- A fresh-context critic reviewed decision fidelity; its checks on the positive fault-notice obligation and exact-cap deletion trigger are incorporated.