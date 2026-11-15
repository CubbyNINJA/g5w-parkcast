# S2 parking-gate requirements, revision 2 (OD-43 to OD-49)

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0b9-ece7-7992-83c9-892e6e3c1d20`, 1 turn(s), 184 s; tokens in 372505 (cached 332672), out 6132 (reasoning 71).
> Prompt: `tools/astra/prompts/S2_PARKING_REQUIREMENTS_REV2.md`. Date 2026-11-15.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

These rows replace AIR-016–027 and add AIR-035–040. Existing S2 row structure is retained. “Permitted” means permitted by the receiver’s parking gate; it does not override independent stock restrictions.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-016 | The package MUST default to requiring Park for mirrored video and MUST provide a package flag that disables this requirement. | OD-24, OD-43. |
| AIR-017 | With the parking requirement enabled, the gate MUST initially block mirrored video and MUST permit it only when a current, available gear reading positively establishes Park on a supported firmware build. Stationary status or an applied parking brake alone MUST NOT establish Park. | OD-23, OD-24, OD-46, OD-47. Build-property and gear-access interfaces remain open; see Open questions. |
| AIR-018 | With the parking requirement enabled, (a) a gear change out of Park reaching the receiver MUST cause the displayed mirrored image, including a frozen last frame, to disappear within 250 ms; (b) further frames, including queued frames, MUST NOT appear while the gate is blocked. | OD-23, OD-46. The deadline starts at receiver receipt, not the physical gear movement. |
| AIR-019 | With the parking requirement enabled, (a) unavailable, erroring or unknown gear information MUST block video at startup and after a previously valid Park reading; (b) a reading older than 2 s MUST count as unavailable; (c) an earlier Park reading MUST NOT override a known signal failure. (d) The receiver MUST re-read gear on every resume from sleep. Recovery MUST follow AIR-037. | OD-23, OD-46, OD-48. Freshness timestamp origin and handling while a resume read is pending remain open. |
| AIR-020 | (a) The gate MUST recognize the `CVEV.*` firmware family in any region as supported and MUST use the 260206 CA gear-probe Park/non-Park mapping across that family. (b) With the parking requirement enabled, a build outside that family, or one that cannot be identified as belonging to it, MUST leave video blocked even if a raw value resembles Park. | OD-47. Cross-family mapping portability is an owner-authorized assumption, not verified hardware evidence. The identifying Android property remains open. |
| AIR-021 | When the flag disables the parking requirement, the gate MUST NOT block mirrored video because of gear state, unavailable/erroring/unknown/stale gear information, or unsupported/unidentified firmware. It MUST NOT disable or alter stock restrictions to achieve this. | OD-24, OD-43, OD-47; existing stock-system boundary retained. |
| AIR-022 | The parking gate MUST NOT mute, stop or disconnect mirrored audio because of any gate state or transition, with either flag value. An existing audio stream MUST remain unaffected by this gate when video becomes blocked or returns. | OD-23, OD-24, OD-48. Audio routing remains AMB-06; unrelated playback failures are outside this guarantee. |
| AIR-023 | (a) The flag MUST be key `video.require_park` in package-relative `config/receiver.conf` (source `package/config/receiver.conf`), installed at `/data/nemesis/apps/<id>/config/receiver.conf`, with values `true` or `false` and default `true`. (b) A missing file or key, unreadable file or unrecognised value MUST resolve to `true`, including after a previously disabled run. (c) The receiver MUST read the installed flag at every app start. A change made by editing the installed copy or rebuilding/reinstalling MUST take effect at the next app start, not during the running instance. | **DECIDED: OD-43; AMB-24 resolved.** OD-44 adds AIR-035. Parsing edge cases and a rebuilt package containing `false` remain open. |
| AIR-024 | Vehicle-signal access MUST be read-only: the gate MAY query status and subscribe to notifications, but MUST NOT command vehicle behavior, write directly to CAN, change vehicle settings or driver-assistance toggles, modify stock regulation behavior, or spoof regulation events. | Existing S2 scope boundary; supplied SCOPE exclusions and OD-22. Read-only queries may use IPC. |
| AIR-025 | At every receiver start, the gate MUST log its effective flag value, whether explicitly read or obtained by fallback, its initial video-gate state and the reason. A failed configuration read MUST be identified without logging unrelated configuration contents. Logging failure MUST follow AIR-040. | OD-43, OD-45, OD-49; existing AIR-025 record fields retained. |
| AIR-026 | Each gate-state transition and each effective flag-value transition MUST be logged with previous and resulting states/values and the reason. A flag applied at restart MUST be recorded as the startup value; an unavailable previous value MUST be identified as unknown, not invented. Logging failure MUST follow AIR-040. | Existing AIR-026 transition-record contract retained; OD-43, OD-49. No new cross-start persistence mechanism selected. |
| AIR-027 | The receiver MUST NOT display mirrored video on the HUD, regardless of the parking flag. Any later HUD feature MUST be text only through channels the stock software already uses and MUST NOT touch driver-assistance display toggles. | OD-22; unchanged. |
| AIR-035 | An update or reinstall MUST restore the shipped `receiver.conf` and MUST NOT preserve a locally edited flag, so the parking check MUST be on again at the next app start until the owner edits it again. | OD-44, with OD-43 activation timing. Interaction with a deliberately rebuilt package shipping `false` remains open. |
| AIR-036 | When the parking check is off, the receiver MUST NOT show an on-screen message or indicator announcing that setting. The setting MUST be recorded at every start under AIR-025. | OD-45. This does not suppress mirrored content or AIR-040’s logging-failure notice. |
| AIR-037 | With the parking requirement enabled, after Park is confirmed again under AIR-017–020, mirrored video MUST return automatically on the next frame. The parking gate MUST retain the iPhone connection and MUST NOT require reconnection or interrupt audio during blocking or recovery. | OD-48; OD-23. Independent playback restrictions remain independent. |
| AIR-038 | The receiver MUST log the stock regulation state (`com.hkmc.regulation`) for evidence. That state MUST neither block nor allow video through the receiver’s parking gate; the gate MUST use its own gear check. Logging failure MUST follow AIR-040. | OD-47, OD-49. Observation cadence and unavailable-state recording remain open. |
| AIR-039 | Receiver logs MUST go to `/data/nemesis/logs/airplay-receiver/`, capped at 5 MB with the oldest dropped. | OD-49. Byte definition and retention granularity remain open; no rotation scheme selected. |
| AIR-040 | A logging failure MUST neither block nor allow video by itself and MUST be reported once on screen, with either parking-flag value. | OD-49. The scope over which “once” resets remains open. |

## Acceptance tests

Each future scenario uses `@S2`, exactly one evidence tier, requirement/test IDs and one kind tag. Separate scenarios implement the different tiers listed within a row. AIR-023/AT-23 no longer carry a conditional `@AMB-24` proposal.

`@offline` uses synthetic fixtures only. `@unit` requires the head unit and retained stick logs, supplemented by observations where logs cannot prove visible or audible behavior. Gear-change observations retain the earlier draft’s stationary-test constraint. No `@pc` result substitutes for head-unit evidence.

| ID | AIR IDs | Criteria |
|---|---|---|
| AT-16 | AIR-016 | **`@offline`:** Synthetic default configuration enables the check and an explicit disabled configuration represents the override. **`@unit`:** An unmodified package starts enabled; retain installed configuration and startup evidence. |
| AT-17 | AIR-017 | **`@offline`:** Startup blocks before a qualifying Park reading; fresh Park on a supported build permits video; stationary status or parking brake alone does not. **`@unit`:** Retain build identification, measured gear mapping, startup/gate logs and corresponding display observations. |
| AT-18 | AIR-018 | **`@offline`:** A synthetic receiver event, clock and display trace prove removal of a displayed/frozen frame within 250 ms and rejection of queued/subsequent frames while blocked; an over-bound trace fails. **`@unit`:** Correlate receiver receipt time with actual image disappearance to establish elapsed time ≤250 ms, then observe continued suppression. Untimed observations or a logged hide request alone do not pass. |
| AT-19 | AIR-019 | **`@offline`:** Missing service, exceptions, service loss and unknown values block at startup and after Park. Test reading ages just below, exactly at and above 2 s: exactly 2 s is not stale solely by age; above 2 s is unavailable. Every synthetic sleep/resume invokes a fresh read; returned errors cannot preserve permission from an earlier Park reading. **`@unit`:** Retain timestamped readings, resume/read evidence and display observations for exercised failure and freshness cases. Unexercised faults remain unverified. |
| AT-20 | AIR-020 | **`@offline`:** Synthetic `CVEV.*` builds from different regions use one synthetic 260206 mapping; outside-family and unidentified builds remain blocked with the check on despite Park-like values. **`@unit`:** Retain the actual identifying property/value and 260206 CA probe mapping. Report which builds were physically tested; family-wide support remains based on OD-47’s assumption. |
| AT-21 | AIR-021 | **`@offline`:** With `false`, Park/non-Park, stale/missing/error/unknown gear and outside/unidentified builds impose no parking-gate block; independent restrictions remain independent. **`@unit`:** Retain startup configuration, gate logs and display observations for exercised cases; review integration evidence for absence of stock-restriction changes. |
| AT-22 | AIR-022 | **`@offline`:** Synthetic audio and connection traces show no gate-originated mute, stop or disconnect through blocking/recovery and either flag value. **`@unit`:** Already-playing audio remains uninterrupted by the gate through stationary Park/non-Park transitions and video recovery; retain audible observations alongside logs. |
| AT-23 | AIR-023 | **`@offline`:** Synthetic configurations cover `true`, `false`, missing file/key, unreadable file and unrecognised values, including fallback after a disabled run. A running instance retains its effective setting; the next start reads the changed file. **`@unit`:** Verify the installed path and edit/restart behavior, plus installation of the shipped configuration followed by startup. The modified-package edge case awaits the answer below. |
| AT-24 | AIR-024 | **`@offline`:** Instrumented synthetic interfaces record only status queries/subscriptions; actuator commands, CAN writes, setting changes and regulation spoofing fail acceptance. **`@unit`:** Retain integration review and available call traces identifying the interfaces used; ordinary log silence alone is insufficient. |
| AT-25 | AIR-025 | **`@offline`:** Explicit enabled/disabled and every fallback start produce effective value, provenance, initial state and reason; failed reads are identified without unrelated configuration contents. **`@unit`:** Corresponding startup records appear in retained stick evidence. Missing records are not a successful logging result. |
| AT-26 | AIR-026 | **`@offline`:** Synthetic allowed/blocked transitions and flag changes across starts produce ordered previous/resulting states and reasons; unavailable previous values are explicit. **`@unit`:** Correlate recorded transitions with configuration changes and observed gear/display behavior. |
| AT-27 | AIR-027 | **`@offline`:** Synthetic routing never sends mirrored frames to the HUD with either flag and issues no driver-assistance setting changes. **`@unit`:** Retain routing review and observations of no mirrored HUD video. Later text features need separate stock-channel evidence. |
| AT-35 | AIR-035 | **`@offline`:** Synthetic update and reinstall each replace a locally disabled configuration with the shipped enabled file; the next start enables the check. **`@unit`:** Exercise update and reinstall separately from an edited `false` configuration; retain before/after configuration and startup records. A partial installation is not evidence of successful reset. |
| AT-36 | AIR-036 | **`@offline`:** With `false`, synthetic UI output contains no disabled-setting notice or indicator and every start logs the setting. **`@unit`:** Observe the screen and retain startup records showing no setting indicator and a logged disabled value. Test logging-failure reporting separately under AT-40. |
| AT-37 | AIR-037 | **`@offline`:** On a supported build, transition from blocked to fresh confirmed Park; the next frame is presented automatically, with no reconnection or audio interruption. **`@unit`:** Correlate Park-confirmation and frame/display timing, retain connection evidence and audible observations, and establish that the next frame returns without owner action. |
| AT-38 | AIR-038 | **`@offline`:** Hold configuration, build and gear constant while varying synthetic regulation states: a blocked gate stays blocked and an allowed gate stays allowed; observed regulation states are logged. **`@unit`:** Retain regulation observations and gate/display evidence showing independence for exercised states. Do not alter or spoof stock regulation to obtain evidence. |
| AT-39 | AIR-039 | **`@offline`:** A synthetic log sink uses the specified path and, when the agreed 5 MB boundary is exceeded, drops oldest data while respecting the cap. Exact byte-boundary and retention assertions await the definitions below. **`@unit`:** Retain log-directory sizes and ordering evidence demonstrating retention within the agreed cap. |
| AT-40 | AIR-040 | **`@offline`:** Inject log-create/write/retention failures with video blocked and allowed, and with either flag value. The gate’s video decision remains unchanged and a logging-failure notice appears once within the agreed reporting scope. **`@unit`:** Independently observe the notice and unchanged gate behavior during an exercised logging fault; retain evidence outside the failed receiver log. Repeated-failure/reset assertions await the definition of “once.” |

## Rationale

**AIR-016.** Configurability and enabled default remain explicit owner policy (`docs/spec/DECISIONS.md:32`, `:51`).

**AIR-017.** Park remains the required condition; freshness and supported-family rules now constrain confirmation (`docs/spec/DECISIONS.md:31`, `:54`, `:55`).

**AIR-018.** OD-46 supplies the measurable receiver-arrival deadline. Removing the displayed image preserves the existing frozen-frame requirement (`docs/spec/DECISIONS.md:54`; `docs/spec/REQUIREMENTS.md:28`).

**AIR-019.** The existing failure behavior gains the strict “older than 2 s” threshold and resume reread. Optional recovery is replaced by mandatory AIR-037 recovery (`docs/spec/REQUIREMENTS.md:29`; `docs/spec/DECISIONS.md:54`, `:56`).

**AIR-020.** OD-47 selects family-wide support, superseding AMB-27’s exact-build allowlist proposal (`docs/spec/DECISIONS.md:55`; `docs/spec/AMBIGUITY_REGISTER.md:37`). The probe records candidate build properties but does not establish the receiver’s identifying property (`tools/unit-probes/gear-probe/gear_probe.sh:37`).

**AIR-021.** The override removes this gate’s parking condition, retaining the existing prohibition on changing stock restrictions (`docs/spec/DECISIONS.md:32`; `docs/spec/REQUIREMENTS.md:31`).

**AIR-022.** Audio remains allowed outside Park, and OD-48 expressly preserves it during recovery (`docs/spec/DECISIONS.md:31`, `:56`).

**AIR-023.** OD-43 decides the previously conditional file, key, values, fallback and activation behavior (`docs/spec/DECISIONS.md:51`; `docs/spec/AMBIGUITY_REGISTER.md:34`).

**AIR-024.** Existing read-only boundaries are retained without selecting a gear interface (`docs/spec/REQUIREMENTS.md:34`; `docs/spec/DECISIONS.md:30`).

**AIR-025.** OD-45 requires startup setting logging; the existing record fields remain, with OD-49 governing failure (`docs/spec/DECISIONS.md:53`, `:57`; `docs/spec/REQUIREMENTS.md:35`).

**AIR-026.** Existing transition logging remains intact; next-start activation and logging-failure handling now have decisions (`docs/spec/REQUIREMENTS.md:36`; `docs/spec/DECISIONS.md:51`, `:57`).

**AIR-027.** OD-22’s HUD restriction is independent of the parking flag (`docs/spec/DECISIONS.md:30`).

**AIR-035.** OD-44 expressly rejects preservation of a local edit across update or reinstall (`docs/spec/DECISIONS.md:52`).

**AIR-036.** OD-45 rejects AMB-25’s proposed persistent indicator; “nothing shown” concerns the disabled-setting indication (`docs/spec/DECISIONS.md:53`; `docs/spec/AMBIGUITY_REGISTER.md:35`).

**AIR-037.** OD-48 requires automatic next-frame return with connection and audio maintained (`docs/spec/DECISIONS.md:56`).

**AIR-038.** OD-47 makes stock regulation an evidence input, not a video-permission predicate (`docs/spec/DECISIONS.md:55`).

**AIR-039.** OD-49 selects the destination, cap and oldest-first retention without selecting a storage scheme (`docs/spec/DECISIONS.md:57`).

**AIR-040.** OD-49 separates logging failure from video permission and requires one on-screen report (`docs/spec/DECISIONS.md:57`).

The reconciliation gaps are:

| # | File | Problem | Exact Fix | Hook Required? |
|---|---|---|---|---|
| 1 | `docs/spec/REQUIREMENTS.md:26` | Current S2 rows retain superseded conditions and missing limits. | Replace AIR-016–027 and AT-16–27 with these rows; append AIR-035–040 and AT-35–40. | No |
| 2 | `docs/spec/REQUIREMENTS.md:94` | Provenance still describes AIR-023/AT-23 as conditional and AMB-26–29 as open. | Add a revision note identifying OD-43–49, resolved conditional status, replaced rows and added IDs. | No |
| 3 | `docs/spec/AMBIGUITY_REGISTER.md:34` | Decided parent questions do not separately track remaining interface and edge-case questions. | Add new open entries for the questions below without reopening the decided policy. | No |

## Reversibility and safety

No files were edited and nothing was installed or executed on the unit.

- **AIR-016–022, AIR-036–037:** Affect receiver runtime presentation, audio and connection behavior. Stopping the receiver ends that runtime behavior.
- **AIR-023, AIR-035:** Affect the receiver’s own configuration. Edit the installed flag and restart to change it again. Update/reinstall deliberately discards the local edit. Failed reads enable the check; interrupted-install recovery remains unspecified.
- **AIR-024, AIR-027:** Introduce no vehicle-setting changes or HUD video output requiring restoration.
- **AIR-025–026, AIR-038–040:** Create receiver logs and a fault notice. Old log data dropped under the cap cannot be recovered from that log store. No uninstall cleanup policy is inferred.
- No requirement calls for PC installation, firewall changes, CAN writes or modification of stock regulation.

## Open questions

1. **Build identity:** Which Android property supplies the `CVEV...` string? The probe records filtered build properties; its `ro.build.version.incremental` value and exact-build guard do not answer this (`tools/unit-probes/gear-probe/gear_probe.sh:37`, `:41`, `:47`).
2. **Gear interface:** Should the receiver call a stock manager method or a Binder transaction number? The probe’s transaction 14 is guarded for its specific build; it does not establish transaction portability across `CVEV.*` (`tools/unit-probes/gear-probe/gear_probe.sh:69`).
3. **Rebuilt disabled package:** OD-43 permits rebuilding/reinstalling to change the flag, while OD-44 says reinstall restores the shipped file and enables the check. What happens when the deliberately rebuilt package contains `false`?
4. **Configuration parsing:** How are duplicate/conflicting keys, whitespace and case variants handled?
5. **Freshness and resume:** Does reading age start at source sampling or receiver receipt? While the resume reread is pending, may an otherwise fresh pre-sleep Park reading permit video?
6. **Retention boundary:** Does 5 MB mean 5,000,000 or 5,242,880 bytes? Does the cap cover all receiver logs together, and does “oldest dropped” mean records, files or another unit?
7. **One-time fault notice:** Is “once” per app start, per failure episode or another scope? When does it reset?
8. **Regulation evidence:** When is regulation state sampled/logged, and how is unavailable state represented?
9. **Interrupted installation:** What configuration and recovery behavior is required if update/reinstall fails before completing the shipped-file replacement?

## Not verified

- No scenarios, tests, production code or package changes were made.
- `SCOPE.md` and the earlier draft’s cited `evidence/airplay-receiver-feasibility.md` were absent from this stage. Scope exclusions were supplied in the instructions.
- No measured gear-probe results were inspected. Raw gear mapping, identifying property, interface permissions and family portability remain unverified.
- The probe’s one-second polling does not establish 250 ms image removal or next-frame recovery.
- Actual timing, stale-reading handling, sleep/resume behavior, audio continuity, connection retention and logging faults require unit evidence.
- Offline fixtures prove specification logic only; they cannot establish iPhone or head-unit behavior (`specs/README.md:14`, `:34`).
- A separate read-only critic reviewed decision fidelity; its findings on reinstall ambiguity, timing boundaries, recovery and fault independence are incorporated above.