# Slice S2: the parking gate, on by default, with a package flag

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: airplay-receiver-feasibility.md.
> Session `01a1dd16-4417-7153-bf0f-55acd6a098ea`, 1 turn(s), 169 s; tokens in 306607 (cached 276096), out 4594 (reasoning 107).
> Prompt: `tools/astra/prompts/S2_PARKING_REQUIREMENTS.md`. Date 2026-11-14.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

These rows apply to **S2 only**. They introduce no S1 behavior. “Video permitted” means permitted by this parking gate; it does not override other receiver or stock-system restrictions. AMB-24 proposals remain conditional on owner approval.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-016 | The package MUST default to requiring Park for mirrored video. It MUST provide a package flag that can disable this parking requirement. | OD-24; flag representation and activation remain AMB-24. |
| AIR-017 | With the parking requirement enabled, the gate MUST initially block mirrored video and MUST permit it only when the current gear signal positively establishes Park on a recognized firmware build. Stationary status or an applied parking brake alone MUST NOT establish Park. | OD-23, OD-24; request for startup and unknown-firmware handling. Firmware recognition and gear mapping remain open. |
| AIR-018 | With the parking requirement enabled, leaving Park MUST immediately prevent further mirrored frames from appearing and remove any already displayed mirrored image, including a frozen last frame. Queued frames MUST NOT restore the image while blocked. | OD-23; explicit request for stopping video at once. Measurable latency remains open. |
| AIR-019 | With the parking requirement enabled, unavailable, erroring or unknown gear information MUST block video, both at startup and after a previously valid Park reading. A previous Park reading MUST NOT override a known signal failure. A restored valid Park reading MAY make video eligible again, subject to AIR-017. | OD-23; explicit failure-path request. Signal freshness and playback-resumption mechanics remain open. |
| AIR-020 | With the parking requirement enabled, an unknown firmware build MUST leave video blocked, even if a returned value resembles Park on another build. This failure MUST NOT be treated as verified Park. | Explicit unknown-firmware request; feasibility firmware gap. Recognition mechanism and supported-build evidence remain open. |
| AIR-021 | When the package flag explicitly disables the parking requirement, the parking gate MUST NOT block mirrored video because of gear state, unavailable/erroring/unknown gear information, or an unknown firmware build. It MUST NOT disable or alter stock restrictions to achieve this. | OD-24; removes this gate’s parking requirement only. |
| AIR-022 | The parking gate MUST NOT mute, stop or disconnect mirrored audio because of any parking-gate state or transition, with the flag enabled or disabled. An existing audio stream MUST remain unaffected by this gate when video becomes blocked. | OD-23, OD-24. Actual audio routing remains AMB-06; this does not promise immunity from unrelated playback failures. |
| AIR-023 | **Proposed:** (a) The flag MUST be `video.require_park` in `package/config/receiver.conf`, installed as `/data/nemesis/apps/<id>/config/receiver.conf`, accepting `true` and `false`. (b) A missing file/key, unreadable configuration or unrecognised value MUST resolve to enabled, rather than preserve an earlier disabled value. (c) The receiver MUST read the installed flag at every app start; a change MUST take effect at the next app start after editing the installed copy or rebuilding and reinstalling the package. It MUST NOT take effect during an already running instance. | **OPEN AMB-24 proposal**, not an owner decision. Exact parsing details and update preservation remain open. |
| AIR-024 | Vehicle-signal access by the gate MUST be read-only: it MAY query status and subscribe to status notifications, but MUST NOT command vehicle behavior, write directly to CAN, change vehicle settings or driver-assistance toggles, modify stock regulation behavior, or spoof regulation events. | Supplied SCOPE exclusions; OD-22. Status-query/subscription IPC is permitted; “read-only” does not prohibit sending a query to the stock service. |
| AIR-025 | At every receiver start, the gate MUST log its effective flag value, whether that value was explicitly read or obtained by fallback, its initial video-gate state, and the reason for that state. A failed configuration read MUST be identified as such, without logging unrelated configuration contents. | Explicit logging request; fallback semantics depend on AMB-24. Log destination and logging-failure behavior remain open. |
| AIR-026 | Each gate-state transition and each effective flag-value transition MUST be logged with the previous and resulting values/states and the reason. A newly applied flag value at restart MUST be recorded as the startup value; if its previous value is unavailable, the log MUST identify that rather than invent it. | Explicit transition-logging request. Record fields are draft operational detail; cross-start history mechanism remains open. |
| AIR-027 | The receiver MUST NOT display mirrored video on the HUD, regardless of the parking flag. Any later HUD feature MUST be text only through channels the stock software already uses and MUST NOT touch driver-assistance display toggles. | OD-22. No HUD feature is introduced by this slice. |

## Acceptance tests

All future scenarios use `@S2`, exactly one evidence tier, requirement/test IDs, and a kind tag. Scenarios asserting AIR-023’s proposals also carry `@AMB-24`. Offline results establish synthetic logic only.

For `@unit`, retain stick logs plus timestamped operator observations of the visible display and audible playback. Conduct gear changes with the vehicle stationary and secured against movement; do not conduct driving tests. Logs alone do not prove visible image removal or audible continuity.

| ID | Requirements | Criteria |
|---|---|---|
| AT-16 | AIR-016 | **`@offline`:** A synthetic package configuration defaults to enabled and represents an explicit disabled setting. **`@unit`:** An unmodified package starts with the parking requirement enabled, evidenced by its configuration and startup log. |
| AT-17 | AIR-017 | **`@offline`:** Fake startup and gear sequences show no video before a valid Park reading; recognized Park permits video; non-Park remains blocked even with the parking brake applied or stationary status true. **`@unit`:** On the validated build, startup and stationary Park/non-Park observations agree with gate logs. |
| AT-18 | AIR-018 | **`@offline`:** Start with a displayed frame and queued frames; inject a departure from Park. The displayed image is removed and subsequent queued frames cannot appear. **`@unit`:** During stationary shifts out of Park, observe removal of the existing image and absence of later frames; retain event/display timing. Quantitative latency acceptance awaits the owner’s bound. |
| AT-19 | AIR-019 | **`@offline`:** Synthetic missing service, read exception, lost service and unknown-value cases block video at startup and after Park. A retained earlier Park value cannot permit video. Recovery supplies a new valid reading before eligibility returns. **`@unit`:** Any actual signal-failure/recovery claim requires corresponding unit logs and display observations; unexercised faults remain unverified. |
| AT-20 | AIR-020 | **`@offline`:** A synthetic unknown build remains video-blocked despite a Park-like raw value. **`@unit`:** Retain the tested build identifier and validated mapping evidence; success on 260206 CA does not establish support for other builds. |
| AT-21 | AIR-021 | **`@offline`:** With the explicit disabled value, Park, non-Park, signal failures and unknown builds impose no parking-gate video block. Independent restrictions remain represented independently. **`@unit`:** After applying the approved flag procedure, observe the gate disabled during stationary gear changes; record any stock restriction separately without altering it. |
| AT-22 | AIR-022 | **`@offline`:** Synthetic audio-output traces continue through every parking-gate state and transition, with both flag values; no gate-originated mute, stop or disconnect occurs. **`@unit`:** With audio already playing, stationary shifts into and out of Park leave sound uninterrupted by the gate, including when enabled video disappears. Retain operator observations alongside logs. |
| AT-23 | AIR-023 | **Conditional AMB-24 proposal — `@offline`:** Synthetic files cover `true`, `false`, missing file/key, unreadable file and unrecognised values, including invalid configuration after a previously disabled run. Verify enabled fallback, no live application, and reread at the next start. **`@unit`:** After owner approval, verify the installed path and both edit/restart and rebuild/reinstall/restart procedures; retain configuration and startup evidence. |
| AT-24 | AIR-024 | **`@offline`:** Instrumented fake vehicle interfaces record status queries/subscriptions only; any actuator command, CAN write, setting change or regulation spoof fails acceptance. **`@unit`:** Retain integration review and available call traces identifying the stock read/notification interfaces actually used. Silence in ordinary logs alone is insufficient proof of no writes. |
| AT-25 | AIR-025 | **`@offline`:** Synthetic starts with explicit enabled/disabled values and each proposed fallback case produce effective value, provenance, initial state and reason; unreadable configuration is identified without copying unrelated contents. **`@unit`:** Stick logs contain the corresponding startup records for exercised configurations. Logging failures cannot be counted as complete evidence. |
| AT-26 | AIR-026 | **`@offline`:** Fake sequences cover allowed-to-blocked, blocked-to-allowed, and both effective flag changes. Logs preserve ordering, prior/resulting states and reasons; unavailable prior-start values remain explicitly unknown. **`@unit`:** Correlate transition records with stationary gear changes, approved flag changes and operator observations. |
| AT-27 | AIR-027 | **`@offline`:** Synthetic output routing cannot send mirrored frames to the HUD with either flag value and issues no driver-assistance setting changes. **`@unit`:** Retain output-routing review and observations confirming no mirrored HUD video. Later text features require their own acceptance evidence for stock-channel use. |

## Rationale

**AIR-016.** OD-24 explicitly makes the parking requirement configurable and enabled by default (`docs/spec/DECISIONS.md:32`).

**AIR-017.** Park is the required condition, rather than merely being stationary or having the parking brake applied (`docs/spec/DECISIONS.md:31`). The supplied BCM method names identify candidate observations, but do not establish their return-value mapping.

**AIR-018.** The request requires immediate video removal on leaving Park. Checking only new-frame delivery would leave a frozen mirrored image visible. The earlier ParkGuard proposal addresses stopping playback but incorrectly includes audio (`evidence/airplay-receiver-feasibility.md:31`).

**AIR-019.** Unknown or failed readings cannot establish Park. The feasibility note specifically warns about an example that reports parked on error (`evidence/airplay-receiver-feasibility.md:21`).

**AIR-020.** The feasibility gap identifies firmware-dependent transaction numbers (`evidence/airplay-receiver-feasibility.md:32`). Unknown-build blocking is requested, but that does not select a transaction-number implementation or a particular allowlist mechanism.

**AIR-021.** Disabling the requirement must actually remove this gate’s parking condition (`docs/spec/DECISIONS.md:32`). This does not authorize altering `com.hkmc.regulation` or sending its exit-regulation event.

**AIR-022.** Audio outside Park is expressly permitted in both configurations (`docs/spec/DECISIONS.md:31`, `docs/spec/DECISIONS.md:32`). Actual audio integration is still open (`docs/spec/AMBIGUITY_REGISTER.md:16`).

**AIR-023.** The proposed path, key, fallback and next-start behavior come from AMB-24, whose status remains OPEN (`docs/spec/AMBIGUITY_REGISTER.md:34`).

**AIR-024.** Read-only access follows the supplied scope exclusions. OD-22 separately prohibits touching driver-assistance display toggles (`docs/spec/DECISIONS.md:30`). No staged `SCOPE.md` was available.

**AIR-025.** Startup logging is independently requested here. Although flag logging also appears in AMB-25’s proposal, that does not decide its separate on-screen indicator question (`docs/spec/AMBIGUITY_REGISTER.md:35`).

**AIR-026.** Transition logging is explicitly requested. Its evidence must remain distinct from observed on-unit behavior (`specs/README.md:14`, `specs/README.md:20`).

**AIR-027.** The parking override does not alter any part of OD-22’s HUD boundary (`docs/spec/DECISIONS.md:30`).

The source gaps requiring reconciliation are:

| # | File | Problem | Exact Fix | Hook Required? |
|---|---|---|---|---|
| 1 | `evidence/airplay-receiver-feasibility.md:31` | Historical ParkGuard proposal stops audio and mandates an additional regulation check. | Annotate it as superseded for this slice by OD-23/24 and AIR-017–022; do not adopt the regulation predicate without a decision. | No |
| 2 | `evidence/airplay-receiver-feasibility.md:32` | Historical firmware proposal says to refuse to run, broader than blocking video. | Annotate that S2 blocks video when enabled; audio remains unaffected by the gate, and the flag can disable the gate. | No |
| 3 | `docs/spec/AMBIGUITY_REGISTER.md:34` | Flag mechanics remain unapproved. | Keep AMB-24 OPEN and mark AIR-023/AT-23 conditional until the owner decides. | No |

## Reversibility and safety

- **This draft:** installs nothing, edits no files and changes no PC or unit state.
- **AIR-016–022:** future behavior changes only the receiver’s video permission and presentation. Ending the receiver removes that runtime behavior; no vehicle-state restoration is needed.
- **AIR-023:** future configuration changes affect the package’s own file. Restore the previous value and apply the owner-approved activation procedure to undo them. Failed reads use enabled fallback only if that proposal is approved. Partial installation and update-preservation semantics remain unspecified.
- **AIR-024:** future signal queries/subscriptions require no vehicle-setting rollback. Subscriptions must be released through the stock interface when the gate stops.
- **AIR-025–026:** future logging creates receiver-owned evidence; its location, retention and removal procedure remain to be selected. No firewall change is required.
- **AIR-027:** creates no HUD output or vehicle-setting change.

## Open questions

1. **AMB-24:** Approve or replace the proposed path, key, accepted values, enabled fallback and next-start activation. Also define duplicate/conflicting keys, parsing normalization, and whether reinstall/update preserves a locally edited flag.
2. **AMB-25:** Should disabled mode show a persistent “Parking check off” message? No display choice is adopted here.
3. **Immediate response and freshness:** What maximum elapsed time is acceptable from leaving Park to image removal? When does a silent or stale signal become unavailable, including after suspend/resume?
4. **Firmware and signal contract:** What identifies a supported build, and what evidence validates its Park/non-Park mapping? The supplied named stock-manager access is a candidate; raw transaction numbers are not established as portable. Is an additional stock-regulation predicate required?
5. **Return to Park:** Is renewed eligibility sufficient, or must video automatically resume without reconnecting? Automatic resumption is not specified here.
6. **Logging:** Where are records stored, how long are they retained, how is prior flag state tracked across starts, and what happens if logging fails? No video/audio failure policy is invented for a logging fault.

## Not verified

- No code, scenarios, tests, package installation or vehicle operation was performed.
- `SCOPE.md` was absent from the staged root; its exclusions were supplied in the instructions.
- BCM service names, methods, listener callbacks and transaction numbers are owner-supplied facts, not independently inspected binaries.
- Gear-value mappings, permissions, callback reliability, firmware recognition, lifecycle handling, visible-removal latency and audio continuity remain unverified on 260206 CA.
- The staged target facts identify the unit and platform constraints (`AGENTS.md:62`); they do not prove parking-gate operation.
- Offline acceptance cannot establish head-unit behavior. S2 `@unit` evidence remains required.