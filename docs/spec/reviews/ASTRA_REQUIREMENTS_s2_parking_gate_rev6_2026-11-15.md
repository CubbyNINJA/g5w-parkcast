# S2 parking-gate requirements, revision 6 (OD-63, OD-64)

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md.
> Session `01a1e0df-a152-7502-8a0d-43e98a3862ff`, 1 turn(s), 97 s; tokens in 311924 (cached 274304), out 2967 (reasoning 51).
> Prompt: `tools/astra/prompts/S2_PARKING_REQUIREMENTS_REV6.md`. Date 2026-11-15.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

Replace only these rows; retain their IDs.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-017 | (a) With the parking requirement enabled, the gate MUST initially block mirrored video and MUST permit it only when a current, available reading positively establishes Park on a supported firmware build. (b) The receiver MUST read Park by polling `VehicleInfoManager.getGearPStatus()` every 200 ms, subject to clauses (e) and (f), using system service `com.hkmc.micom.system` (getter transaction 36), with no Park listener. The startup read MUST use the same getter. (c) Returned value `1` MUST mean Park and `0` MUST mean non-Park; any other value, missing value or failed call MUST mean unavailable. (d) Stationary status, an applied parking brake or a successful status-request call returning no gear value MUST NOT establish Park. (e) Only one getter call MUST run at a time. The next poll MUST wait while a call remains pending, including after its 200 ms timeout. (f) After a late call returns, the next poll MUST start immediately after that call completes. Subsequent polls MUST be scheduled every 200 ms from that new invocation, subject to clause (e). Each further late return MUST restart this schedule in the same way. A return at exactly 200 ms MUST count as late under AIR-019(e). | OD-23, OD-24, OD-46, OD-47, OD-61, OD-62, OD-63, OD-64. AMB-33, AMB-41, AMB-42 and AMB-43 are decided. Ordinary-app access remains unverified. |
| AIR-019 | With the parking requirement enabled, (a) unavailable, erroring or unknown gear information MUST block video at startup and after a previously valid Park reading; (b) every returned poll MUST count as a fresh observation, including an unchanged cached value, with age measured from receiver receipt of that return; a reading older than 2 s MUST count as unavailable. Receipt-age freshness MUST NOT permit a late return to unblock video contrary to clause (e); (c) a failed call or unavailable result MUST NOT preserve permission from an earlier Park reading; (d) on every resume from sleep, the receiver MUST re-read Park through `VehicleInfoManager.getGearPStatus()`, subject to AIR-017(e) and (f), and video MUST remain blocked until a fresh, on-time re-read returns `1` and the other AIR-017–020 conditions permit video. A pre-sleep Park reading MUST NOT allow video after resume. Recovery MUST follow AIR-037; (e) a getter call without a reply received strictly before 200 ms after invocation MUST count as unavailable and MUST block video at 200 ms, even when an earlier Park reading is less than 2 s old. A reply received at exactly 200 ms or later MUST count as late: the timeout MUST win. Neither the earlier Park reading nor the late reply MUST restore permission. Video MUST remain blocked until a subsequent on-time reading permits it under the AIR-017 value mapping and the other AIR-017–020 conditions, with recovery under AIR-037. | OD-23, OD-46, OD-48, OD-56, OD-61, OD-62, OD-63, OD-64. Freshness is receiver receipt, not evidence that the service refreshed its cache. OD-64 excludes late replies from restoring permission, including replies at exactly the timeout. |

## Acceptance tests

Each tier uses separate scenarios under `specs/README.md`.

| ID | AIR IDs | Criteria |
|---|---|---|
| AT-17 | AIR-017 | **`@offline`:** An instrumented synthetic getter and controlled clock record invocation, return and active-call count. With immediate replies, verify the same getter at startup and every 200 ms, with no Park listener. Preserve checks that qualifying `1` permits video on a supported build; `0`, unknown/missing values and failures block; stationary status, parking brake and successful no-value requests cannot establish Park. Invoke at `t=0`, hold the reply until `650 ms`, and advance through `200`, `400` and `600 ms`: assert no second invocation and maximum active-call count one. At `650 ms`, assert completion followed immediately by the next invocation at the same clock time. Return that new call at `660 ms`; with subsequent on-time replies, assert further invocations at `850` and `1050 ms`, with none between scheduled invocations, including none at `800` or `860 ms`. Separately, return the initial call at exactly `200 ms`: assert it is late, the next invocation immediately follows completion at `200 ms`, and subsequent invocations occur at `400` and `600 ms` when replies are on time. Exercise consecutive late calls to verify each return restarts the cadence. Repeat with a call pending beyond 2 s and multiple poll deadlines: no overlap, then an immediate next invocation on return. **`@unit`:** Retain ordinary-app receiver invocation/return timestamps, pending-call counts, build identity, getter results and corresponding gate/display observations in stick evidence. For exercised late returns, demonstrate immediate serialized restart and the subsequent 200 ms cadence. Identify unexercised delay/hang and exact-boundary cases; root-probe success alone does not pass receiver integration. |
| AT-19 | AIR-019 | **`@offline`:** Use only synthetic getter replies, a controlled clock and gate/frame traces. Test startup and a previously confirmed Park state. For a call invoked at `t=0` and held pending, verify no timeout at `199 ms`, then unavailability and video blocking at `200 ms`, without waiting for the earlier Park reading to exceed 2 s. Keep it pending through later poll deadlines and beyond 2 s: video remains blocked. Compare Park replies received at `199`, exactly `200`, `201` and `650 ms`: `199 ms` is on time; every other reply is late and cannot restore permission or present a frame. At exactly `200 ms`, exercise both reply-first and timeout-first callback processing orders: timeout wins in both, with no transient permission from the late reply. After each late return, let the immediately started next call remain pending for 10 ms: video stays blocked during that interval. An on-time `1` from that call permits next-frame recovery under AIR-037 on a supported build; an on-time `0`, unknown/missing value or failure leaves video blocked. Verify receipt-age freshness, including unchanged and late returned values, separately from permission to unblock. Preserve checks for missing service, exceptions and service loss. Check age classification just below, exactly at and above 2 s independently of timeout blocking: exactly 2 s is not stale solely by age. Every resume requires a fresh, on-time Park re-read without overlapping a pending call; pre-sleep Park and a late resume reply cannot permit video. With the flag off, these faults impose no parking-gate block under AIR-021. **`@unit`:** Retain invocation/receipt timestamps, timeout/gate transitions, subsequent on-time recovery, resume evidence and display observations in stick evidence. Identify unexercised cases, including exact-timeout races. Synthetic results do not establish hardware timing or behavior. |

## Rationale

**AIR-017.** OD-63 fixes the formerly open scheduling phase; OD-64 includes an exact-timeout return in the late category. The revised acceptance criteria distinguish immediate restart from waiting for an old deadline or another 200 ms, and distinguish invocation-based cadence from return-based cadence (`docs/spec/DECISIONS.md:71`, `docs/spec/DECISIONS.md:72`, `docs/spec/AMBIGUITY_REGISTER.md:52`, `docs/spec/REQUIREMENTS.md:72`).

**AIR-019.** OD-64 replaces revision 5’s permission to recover from a late Park reply. Receipt-age freshness remains, but recovery requires a subsequent on-time Park reading. The boundary test asserts timeout precedence instead of avoiding simultaneous receipt and timeout (`docs/spec/DECISIONS.md:72`, `docs/spec/AMBIGUITY_REGISTER.md:53`, `docs/spec/reviews/ASTRA_REQUIREMENTS_s2_parking_gate_rev5_2026-11-15.md:30`).

| # | File | Problem | Exact Fix | Hook Required? |
|---|---|---|---|---|
| 1 | `docs/spec/REQUIREMENTS.md` | Revision 5 leaves restart scheduling open and allows late-reply recovery. | Replace AIR-017, AIR-019, AT-17 and AT-19 with the rows above. | No |

## Reversibility and safety

No files were edited or software installed. AIR-017 changes runtime polling schedules; AIR-019 changes runtime video-gate decisions. Neither introduces persistent PC or unit changes. Getter access remains read-only. A hung call leaves video blocked with the check enabled and prevents overlapping calls; timeout does not cancel the call.

## Open questions

None remain for this revision: AMB-42 and AMB-43 are decided.

Outside this revision, the register retains AMB-02 and AMB-04–10 as open, and AMB-03 as partly decided.

## Not verified

- No scenarios, tests or implementation were written or run. A fresh-context critic reviewed the proposed changes.
- Ordinary-app getter access, hardware timing, exact-boundary behavior and resume behavior remain unverified. Offline evidence cannot substitute for unit evidence (`specs/README.md:14`, `specs/README.md:34`).
- `SCOPE.md` was absent from the stage. No credentials, OEM binaries or iPhone contents were inspected.