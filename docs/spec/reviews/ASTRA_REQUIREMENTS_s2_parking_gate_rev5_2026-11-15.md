# S2 parking-gate requirements, revision 5 (OD-62)

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0dc-4894-73e2-a06d-5b7525664475`, 1 turn(s), 98 s; tokens in 262782 (cached 224896), out 2662 (reasoning 196).
> Prompt: `tools/astra/prompts/S2_PARKING_REQUIREMENTS_REV5.md`. Date 2026-11-15.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

Replace only these rows; retain their IDs.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-017 | (a) With the parking requirement enabled, the gate MUST initially block mirrored video and MUST permit it only when a current, available reading positively establishes Park on a supported firmware build. (b) The receiver MUST read Park by polling `VehicleInfoManager.getGearPStatus()` every 200 ms, subject to clause (e), using system service `com.hkmc.micom.system` (getter transaction 36), with no Park listener. The startup read MUST use the same getter. (c) Returned value `1` MUST mean Park and `0` MUST mean non-Park; any other value, missing value or failed call MUST mean unavailable. (d) Stationary status, an applied parking brake or a successful status-request call returning no gear value MUST NOT establish Park. (e) Only one getter call MUST run at a time. The next poll MUST wait while a call remains pending, including after that pending call has exceeded its 200 ms timeout. | OD-23, OD-24, OD-46, OD-47, OD-61, OD-62. AMB-33 and AMB-41 are decided. Exact scheduling after a pending call returns remains open; see Open questions. Ordinary-app access remains unverified. |
| AIR-019 | With the parking requirement enabled, (a) unavailable, erroring or unknown gear information MUST block video at startup and after a previously valid Park reading; (b) every returned poll MUST count as a fresh observation, including an unchanged cached value, with age measured from receiver receipt of that return; a reading older than 2 s MUST count as unavailable; (c) a failed call or unavailable result MUST NOT preserve permission from an earlier Park reading; (d) on every resume from sleep, the receiver MUST re-read Park through `VehicleInfoManager.getGearPStatus()`, subject to AIR-017(e), and video MUST remain blocked until that fresh re-read returns `1` and the other AIR-017–020 conditions permit video. A pre-sleep Park reading MUST NOT allow video after resume. Recovery MUST follow AIR-037. (e) A getter call still pending 200 ms after invocation MUST count as unavailable and MUST block video, even when an earlier Park reading is less than 2 s old. While that call remains pending, the earlier Park reading MUST NOT restore permission. A subsequent return remains subject to clause (b), the AIR-017 value mapping and AIR-037 recovery. | OD-23, OD-46, OD-48, OD-56, OD-61, OD-62. Freshness is receiver receipt, not evidence that the service refreshed its cache. OD-62 adds timeout blocking; it does not replace OD-61’s returned-poll freshness rule. |

## Acceptance tests

Each tier uses separate scenarios under `specs/README.md`.

| ID | AIR IDs | Criteria |
|---|---|---|
| AT-17 | AIR-017 | **`@offline`:** An instrumented synthetic getter and controlled clock record invocation, return and active-call count. With immediate replies, verify the same getter at startup and every 200 ms, with no Park listener. Preserve checks that qualifying `1` permits video on a supported build; `0`, unknown/missing values and failures block; stationary status, parking brake and successful no-value requests cannot establish Park. Start a call at `t=0`, hold its reply until `t=650 ms`, and advance through `200`, `400` and `600 ms`: assert no second invocation and a maximum active-call count of one, including after timeout. Release the reply at `650 ms`; any subsequent invocation must follow completion of that call. Do not assert an undecided post-overrun scheduling phase. Repeat with a call held pending beyond 2 s and multiple poll deadlines; no overlapping call is permitted. **`@unit`:** Retain ordinary-app receiver invocation/return timestamps, pending-call counts, build identity, getter results and corresponding gate/display observations in stick evidence. Identify unexercised delay/hang cases; root-probe success alone does not pass receiver integration. |
| AT-19 | AIR-019 | **`@offline`:** Use only synthetic getter replies, a controlled clock and gate/frame traces. Test startup and a previously confirmed Park state. For a call invoked at `t=0` and deliberately still pending at `t=200 ms`, verify it has not timed out at `199 ms`, then is unavailable and blocks video at `200 ms`, without waiting for the earlier Park reading to exceed 2 s. Keep it pending through later poll deadlines and beyond 2 s: video remains blocked. In a separate delayed-reply case, release `1` at `650 ms`: verify blocking from timeout until receipt, freshness measured from `650 ms`, and next-frame recovery under AIR-037 on a supported build. Late `0`, unknown/missing values or failure leave video blocked. Also exercise a reply before the timeout. Preserve checks for unchanged returned values refreshing receipt time, missing service, exceptions and service loss. Check the age classification just below, exactly at and above 2 s independently of timeout blocking: exactly 2 s is not stale solely by age. Every resume requires the fresh re-read, without overlapping a pending call; pre-sleep Park cannot permit video. With the flag off, these faults impose no parking-gate block under AIR-021. **`@unit`:** Retain invocation/return timestamps, timeout/gate transitions, resume evidence and display observations in stick evidence; identify unexercised cases. Synthetic results do not establish hardware timing or behavior. |

## Rationale

**AIR-017.** OD-62 qualifies OD-61’s cadence: timeout does not free a still-running call’s slot. The replacement preserves revision 4’s getter and mapping while making serialization explicit (`docs/spec/DECISIONS.md:69`, `:70`; `docs/spec/AMBIGUITY_REGISTER.md:51`; `docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_rev4_2026-11-15.md:20`).

**AIR-019.** Timeout now blocks before the separate 2 s stale-reading limit. A late return still follows the existing “every returned poll” freshness rule and automatic recovery; discarding it would add policy. The age test is separated from timeout so an already-blocked gate cannot falsely prove the stale boundary (`docs/spec/REQUIREMENTS.md:29`, `:47`, `:74`; `docs/spec/DECISIONS.md:64`, `:69`, `:70`).

| # | File | Problem | Exact Fix | Hook Required? |
|---|---|---|---|---|
| 1 | `docs/spec/REQUIREMENTS.md` | Existing rows omit timeout and serialization; withheld-result testing now encounters timeout before staleness. | Replace AIR-017, AIR-019, AT-17 and AT-19 with the rows above. | No |

## Reversibility and safety

No files were edited or software installed. These requirements change runtime polling and video gating only; they introduce no persistent PC or unit changes. Getter access remains read-only. A hung call leaves video blocked with the check on and prevents additional calls; timeout does not imply cancellation.

## Open questions

- After an overdue call returns, should the deferred poll start immediately, at the next original 200 ms boundary, or 200 ms after the return?
- If reply receipt and timeout occur at exactly the same instant, which takes precedence? The proposed timeout fixture keeps the reply pending through the deadline observation, avoiding an invented tie rule.

AMB-41’s timeout and single-call choices are decided and are not reopened.

## Not verified

- No scenarios, tests or implementation were written or run. A fresh-context critic reviewed decision fidelity and test gaps.
- Ordinary-app getter access, real call duration, display timing and resume behavior remain unverified.
- `SCOPE.md` was absent from the stage; supplied scope boundaries were followed. No credentials, OEM binaries or iPhone contents were inspected.