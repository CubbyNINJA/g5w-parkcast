# S2 parking-gate requirements, revision 4 (OD-61)

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0d8-c1db-7611-b9a1-bb57b9f3c3b4`, 1 turn(s), 127 s; tokens in 310023 (cached 267776), out 3816 (reasoning 66).
> Prompt: `tools/astra/prompts/S2_PARKING_REQUIREMENTS_REV4.md`. Date 2026-11-15.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

Replace these five rows and their matching acceptance-test rows. Retain existing IDs; no new IDs are needed. All other rows remain unchanged.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-017 | (a) With the parking requirement enabled, the gate MUST initially block mirrored video and MUST permit it only when a current, available reading positively establishes Park on a supported firmware build. (b) The receiver MUST read Park by polling `VehicleInfoManager.getGearPStatus()` every 200 ms, using system service `com.hkmc.micom.system` (getter transaction 36), with no Park listener. The startup read MUST use the same getter. (c) Returned value `1` MUST mean Park and `0` MUST mean non-Park; any other value, missing value or failed call MUST mean unavailable. (d) Stationary status, an applied parking brake or a successful status-request call returning no gear value MUST NOT establish Park. | OD-23, OD-24, OD-46, OD-47, OD-61. AMB-33 is decided: getter polling only; its listener proposal and alternative notification mappings are not selected. Ordinary-app access remains unverified. |
| AIR-018 | With the parking requirement enabled, (a) a gear change out of Park MUST count as reaching the receiver when a poll returns it, and the displayed mirrored image, including a frozen last frame, MUST disappear within 250 ms of that return; (b) further frames, including queued frames, MUST NOT appear while the gate is blocked. | OD-23, OD-46, OD-61. OD-61’s stated consequence is removal within **about 450 ms of the actual shift**: up to 200 ms until the next poll, then up to 250 ms to remove the image. This is distinct from the strict 250 ms deadline measured from poll return. |
| AIR-019 | With the parking requirement enabled, (a) unavailable, erroring or unknown gear information MUST block video at startup and after a previously valid Park reading; (b) every returned poll MUST count as a fresh observation, including an unchanged cached value, with age measured from receiver receipt of that return; a reading older than 2 s MUST count as unavailable; (c) a failed call or unavailable result MUST NOT preserve permission from an earlier Park reading; (d) on every resume from sleep, the receiver MUST re-read Park through `VehicleInfoManager.getGearPStatus()`, and video MUST remain blocked until that fresh re-read returns `1` and the other AIR-017–020 conditions permit video. A pre-sleep Park reading MUST NOT allow video after resume. Recovery MUST follow AIR-037. | OD-23, OD-46, OD-48, OD-56, OD-61. Freshness is receiver receipt of a poll result, not evidence that the service refreshed its underlying cached state. |
| AIR-020 | (a) The gate MUST identify the supported `CVEV.*` firmware family from `ro.build.product`, in any region, and MUST use the getter mapping `1` = Park and `0` = non-Park across that family. (b) With the parking requirement enabled, a build outside that family, or one that cannot be identified as belonging to it, MUST leave video blocked even if the getter returns `1`. | OD-47, OD-51, OD-61. Mapping portability within the family is an owner-authorized assumption, not verified evidence for other builds. |
| AIR-024 | (a) Vehicle-signal access MUST remain read-only. Park reads MUST use the AIR-017 getter, which returns the service’s cached state without requesting data from the MICOM; the gate MUST NOT register a Park listener. (b) The receiver MAY query status and receive stock regulation notifications under AIR-038, but MUST NOT command vehicle behavior, write directly to CAN, change vehicle settings or driver-assistance toggles, modify stock regulation behavior, or spoof regulation events. | Existing S2 scope boundary and OD-22; OD-61 selects cached getter polling for Park. AIR-038’s regulation notifications remain unchanged. |

## Acceptance tests

Use separate scenarios for each tier, following `specs/README.md`. `@offline` uses synthetic fixtures only; `@unit` requires head-unit evidence retained on the stick. An offline pass does not establish unit acceptance.

| ID | AIR IDs | Criteria |
|---|---|---|
| AT-17 | AIR-017 | **`@offline`:** An instrumented synthetic `VehicleInfoManager` and controlled clock show the same getter used at startup and every 200 ms, with no Park-listener registration. Startup remains blocked before qualifying Park. On a supported build, `1` permits video; `0` blocks; other values, missing values and failed calls are unavailable and block. Stationary status, parking brake and successful no-value request replies cannot establish Park. **`@unit`:** Retain actual receiver call/cadence evidence, build identity, getter results, startup/gate records and corresponding display observations. Exercise the wrapper from the receiver’s ordinary-app context; root-probe success alone does not pass this integration test. |
| AT-18 | AIR-018 | **`@offline`:** A controlled clock, synthetic getter and display trace record shift time `t_shift`, first non-Park poll return `t_return`, and actual image removal `t_hide`. For the idealized 450 ms calculation, the fixture publishes the changed cached state immediately at `t_shift` and returns getter calls without delay. Exercise shifts immediately before, at and immediately after a poll, explicitly ordering coincident events. Assert independently: `t_return − t_shift ≤ 200 ms`, `t_hide − t_return ≤ 250 ms`, and `t_hide − t_shift ≤ 450 ms`. Verify removal of both moving and frozen images and suppression of queued/subsequent frames. Reject a 251 ms removal after return even when the total is below 450 ms; reject traces exceeding the polling or idealized total budget. **`@unit`:** Correlate poll return with actual image disappearance to establish the strict ≤250 ms deadline and continued suppression. Record actual-shift-to-removal timing separately to assess OD-61’s approximately 450 ms consequence; do not present the idealized offline bound as measured unit behavior. Untimed observations or a logged hide request alone do not pass. |
| AT-19 | AIR-019 | **`@offline`:** Repeated returned `1` values refresh receipt time even when the synthetic service cache is unchanged. Distinguish source/cache time from return time. Withhold further results and test just below, exactly at and above 2 s: exactly 2 s is not stale solely by age; above 2 s blocks. Missing service, exceptions, service loss, missing values and unknown values block at startup and after Park without waiting for the prior reading to become stale. Every resume calls the same getter; video stays blocked while pending even when pre-sleep Park is younger than 2 s. Returned `0`, unavailable results and failures leave it blocked; returned `1` restores video on the next frame under AIR-037 when the build is supported. With the flag off, these conditions impose no parking-gate block under AIR-021. **`@unit`:** Retain getter-return timestamps, resume/re-read evidence, exercised faults and display observations. Identify unexercised cases. |
| AT-20 | AIR-020 | **`@offline`:** Synthetic `ro.build.product` values from different `CVEV.*` regions use getter `1` as Park and `0` as non-Park. Outside-family, missing and unreadable product values block with the check on despite returned `1`. Conflicting values in other build properties do not replace `ro.build.product`. **`@unit`:** Retain actual product identity and getter mapping alongside receiver behavior. Identify physically tested builds; do not report the family-wide assumption as measured portability. |
| AT-24 | AIR-024 | **`@offline`:** Instrumented synthetic interfaces show Park access only through the selected getter, with no Park-listener registration or BCM status-request calls. Reject actuator commands, direct CAN writes, setting changes, regulation modification and spoofing. AIR-038 regulation notifications remain permitted. **`@unit`:** Retain integration review and available call traces identifying the ordinary-app getter path and its cached-state behavior. Root-probe results and ordinary log silence alone do not establish receiver integration or absence of prohibited calls. |

## Rationale

**AIR-017.** OD-61 selects the wrapper, service, cadence and value mapping, replacing revision 3’s unresolved interface wording. The park-probe supports the getter’s mapping; its one-second sampling does not establish receiver polling at 200 ms (`docs/spec/DECISIONS.md:69`; `docs/evidence/GEAR_PROBE_FINDINGS.md:65`; revision 3 draft:20).

**AIR-018.** OD-46’s deadline remains 250 ms; OD-61 identifies the returned poll as its starting point and states the approximately 450 ms consequence. The synthetic test makes the assumptions behind the sum explicit and checks each bound independently (`docs/spec/DECISIONS.md:54`, `:69`; `docs/spec/REQUIREMENTS.md:28`).

**AIR-019.** OD-56 establishes receipt-based age and blocks pre-sleep permission after resume. OD-61 supplies the fresh-read mechanism, including unchanged cached values. Failed or unavailable polls cannot refresh an earlier Park permission (`docs/spec/DECISIONS.md:64`, `:69`; `docs/evidence/GEAR_PROBE_FINDINGS.md:54`).

**AIR-020.** The property and supported family remain unchanged. Naming the selected getter mapping removes ambiguity with the earlier `onGearStatus` mapping, where zero meant Park (`docs/spec/DECISIONS.md:55`, `:59`, `:69`; `docs/evidence/GEAR_PROBE_FINDINGS.md:35`, `:71`).

**AIR-024.** The selected getter returns cached state without a MICOM request. This differs from the earlier BCM request call. The Park-listener prohibition does not remove the separately decided regulation notifications (`docs/evidence/GEAR_PROBE_FINDINGS.md:16`, `:54`; `docs/spec/DECISIONS.md:67`, `:69`).

| # | File | Problem | Exact Fix | Hook Required? |
|---|---|---|---|---|
| 1 | `docs/spec/REQUIREMENTS.md` | Five requirement/test pairs retain unresolved interface wording or omit OD-61’s mechanism and timing. | Replace AIR-017, AIR-018, AIR-019, AIR-020, AIR-024 and their matching AT rows with the rows above. | No |
| 2 | `docs/spec/REQUIREMENTS.md` | Draft provenance ends at revision 3. | Add a revision-4 note naming OD-61, these five pairs and any wording changed during adoption. | No |

## Reversibility and safety

No files were edited and nothing was installed.

AIR-017–020 change receiver polling and video-gate behavior only. Stopping the receiver ends that runtime behavior; no vehicle settings need restoration. AIR-024 preserves read-only access and adds no persistent unit changes. These revisions require no PC installation, firewall changes, privilege escalation or SELinux changes.

## Open questions

- **Ordinary-app access:** Do SELinux or service-manager restrictions prevent the receiver from calling the getter? The probes ran as root. This is an unresolved verification question; OD-61 authorizes no permission or policy workaround.
- **Calls that do not return promptly:** OD-61 specifies 200 ms polling but no call timeout or handling of a call that spans polling intervals. Any required timeout or overrun policy needs an owner decision. Existing unavailable/stale blocking remains specified; no new timeout is selected here.

AMB-33’s interface and unchanged-gear freshness choices are decided and are not reopened.

## Not verified

- No scenarios, tests or implementation were written or run. A fresh-context critic checked decision fidelity and the distinction between the two timing bounds.
- The findings document was read; raw probe artifacts, OEM binaries and iPhone contents were not inspected.
- Ordinary-app access, actual 200 ms polling, cache-update latency, getter-call duration, display-removal timing and resume behavior remain unverified.
- Only 260206 CA has supplied getter evidence; other `CVEV.*` builds remain an owner-authorized portability assumption.
- `SCOPE.md` was absent from the stage; the supplied scope exclusions were followed.