# Slice S1: PC feasibility of H.264 mirroring

> Author: `gpt-6-astra` via Codex CLI (`codex exec` read-only sandbox, role prompt `tools/astra/roles/astra-requirements-author.md` as developer instructions), reasoning effort medium.
> Staged: docs, specs, tests, tools, package, AGENTS.md + evidence: airplay-receiver-feasibility.md.
> Session `01a1dd04-939d-7fc2-8f0d-6f553167efb2`, 1 turn(s), 167 s; tokens in 211546 (cached 186624), out 4748 (reasoning 273).
> Prompt: `tools/astra/prompts/S1_REQUIREMENTS.md`. Date 2026-11-14.

**Drafted specification, not an owner decision.**

---

Drafted specification, not an owner decision.

## Requirements

The current S1 request specifies the test configuration and verdicts below. It does not resolve the corresponding product-wide ambiguities. Operational failure handling is drafted for review.

| ID | Requirement | Decision or proposal |
|---|---|---|
| AIR-001 | Before building, the build procedure MUST check the MSYS2 UCRT64 environment, required build tools and packages, GStreamer, and an available H.264 decoder. Missing prerequisites MUST be named and MUST prevent the build from starting. | OD-05; S1 request. Exact dependency set requires validation against the selected revision. |
| AIR-002 | The procedure MUST build UxPlay 1.74 from a pinned upstream commit into a tree outside version control. It MUST record the commit, dependency versions, build commands/configuration, executable path and executable hash sufficiently to repeat the build procedure. A failed build MUST be reported and MUST NOT make a partial or previous executable appear to be its successful output. | OD-05; S1 request. Commit selection remains open; bit-for-bit reproducibility is not established. |
| AIR-003 | Before creating a run’s firewall rule, the runner MUST detect and remove leftover rules attributable to this runner. It MUST preserve unrelated rules. If stale-rule removal fails, it MUST identify the remaining rule and stop before launching UxPlay. | S1 request; ownership identification and fail-before-launch behavior are draft operational details. |
| AIR-004 | For a test run, the runner MUST create a Windows Firewall inbound allow rule restricted to the absolute path of that run’s built `uxplay.exe`, effective on this PC’s active network profile. Failure to establish the rule MUST prevent receiver launch and enter cleanup. | OD-05; S1 request. PC evidence records a Public profile. |
| AIR-005 | The runner MUST remove its temporary firewall rule after normal completion, timeout, startup/run failure or catchable interruption. It MUST verify removal. Failed cleanup MUST report the residual rule identifier and executable path without claiming cleanup succeeded. Rules surviving an uncatchable termination MUST be handled by AIR-003 on the next invocation. | OD-05; S1 request. Immediate cleanup cannot be guaranteed after forced termination or power loss. |
| AIR-006 | The runner MUST launch the verified build with HEVC disabled, advertising `1920×720` at `60 fps` under one fixed test name. It MUST record the effective launch configuration and capture UxPlay’s diagnostic output to the run’s log. Failure to start UxPlay or establish log capture MUST be reported and enter cleanup. | Current S1 request; OD-04/05. Tests AMB-02; does not settle product-wide AMB-04. Fixed name remains open. |
| AIR-007 | Once launched, the runner MUST tell the owner how to select its name through iPhone Screen Mirroring and wait for the test. It MUST stop the launched UxPlay process when the owner ends the test or the configured timeout expires, then perform firewall cleanup. It MUST record the termination reason and report any process it cannot stop. | S1 request; OD-06 network arrangement. Timeout duration and timing semantics remain open. |
| AIR-008 | The analyser MUST report separately: evidence of an iPhone connection; mirroring video starting; observed video codec; observed stream resolution; audio format; and client model and iOS version when logged. It MUST distinguish a HEVC request from an observed HEVC stream and the advertised display size from the observed stream size. | S1 request; AMB-02/03/06. Measuring audio format does not establish audio quality. |
| AIR-009 | The analyser MUST associate observations with their source log lines and session where identifiable. Missing or unrecognised fields MUST be reported as unknown, without substituting advertised capabilities or expected values. Unreadable or incomplete input MUST be explicitly flagged as an evidence error or limitation. | S1 request; draft evidence-integrity detail. Verdict handling for incomplete evidence remains open. |
| AIR-010 | For a completed observation with sufficient, unambiguous evidence, the evaluator MUST report **GO** when an iPhone mirroring video stream starts using H.264; **NO-GO** when an iPhone connected but no H.264 mirroring stream started, or it requested HEVC; and **INCONCLUSIVE** when no iPhone connected. Connection alone, audio-only playback or an advertised H.264 capability MUST NOT establish GO. | Current S1 request; AMB-02 remains the empirical question. Conflicting or multiple-session results require a decision. |
| AIR-011 | Every verdict MUST include its reason and references to supporting log lines. When the reason depends on an event’s absence, it MUST identify the examined log extent and observation endpoint; it MUST NOT invent a supporting event line. | S1 request; draft provenance detail. Endpoint timing depends on the timeout decision. |
| AIR-012 | The run MUST retain its log, analyser report, verdict and iOS version under uncommitted `runs/`. If iOS is absent from the log, the report MUST identify that required evidence as missing. Only the verdict and a redacted summary MAY be copied into `docs/evidence/`; raw client names and addresses MUST remain outside committed evidence. | S1 request; `specs/README.md` evidence rules. AMB-03 remains open; fallback iOS collection method needs a decision. |
| AIR-013 | S1 MUST NOT record video frames, screenshots, screen-content recordings or other artifacts showing the phone’s screen contents. | S1 request; `specs/README.md` evidence rules. |
| AIR-014 | Results MUST identify their evidence tier. Synthetic fixture results MUST NOT be presented as proof of iPhone compatibility. A PC GO MUST establish only the tested PC/iPhone configuration, without asserting head-unit decoding, networking or playback success. | OD-04; tier rules; AMB-02. No head-unit behavior is tested in S1. |

## Acceptance tests

Each row may require several scenarios. Each scenario carries exactly one tier tag. `@offline` cases use synthetic fixtures only; `@pc` cases run within the actual PC/iPhone test. No S1 acceptance test is `@unit`.

| ID | Requirements | Criteria |
|---|---|---|
| AT-01 | AIR-001 | **`@offline`:** Synthetic prerequisite inventories cover complete prerequisites, each missing prerequisite and missing H.264 decoder; failures identify the missing item and prevent build invocation. **`@pc`:** Save the actual UCRT64 prerequisite inventory and decoder inspection result before the build; missing prerequisites block it. |
| AT-02 | AIR-002 | **`@offline`:** Synthetic build success/failure records prove failed or stale outputs cannot be selected as successful results. **`@pc`:** Repeat the recorded procedure from a clean build directory at the pinned revision; retain successful build output and provenance, including dependency versions and executable hashes. Both build trees are outside version control. Hash equality is not an acceptance criterion. |
| AT-03 | AIR-003 | **`@offline`:** Synthetic firewall inventories include runner-owned stale rules, unrelated rules and removal failure; only owned rules are targeted and failure blocks launch. **`@pc`:** A deliberately retained runner-owned test rule is detected and removed before the next run, with before/after rule evidence. |
| AT-04 | AIR-004 | **`@offline`:** Synthetic rule-creation failure prevents launch and triggers cleanup. **`@pc`:** Firewall inspection proves the rule is inbound, allows only the selected executable path and applies to the active Public profile; retain its identifier and properties. |
| AT-05 | AIR-005 | **`@offline`:** Simulated normal exit, timeout, startup failure, runtime failure and catchable interruption all invoke and verify removal; simulated removal failure reports the residual rule. **`@pc`:** Before/after firewall inspection confirms removal after normal completion, cancellation and an induced run failure. A surviving rule is reported as cleanup failure. |
| AT-06 | AIR-006 | **`@offline`:** Synthetic launch tests assert the selected executable, fixed name, H.264-only configuration, `1920×720@60`, log capture and cleanup on launch/capture failure. **`@pc`:** Save the effective command/configuration and startup log, checked against the pinned version’s option semantics. |
| AT-07 | AIR-007 | **`@offline`:** Synthetic process events and a controlled clock prove owner completion and timeout stop the launched process and trigger cleanup; stop failure reports the residual process. **`@pc`:** On the wired-PC/home-Wi-Fi arrangement, verify the owner prompt and process termination for owner completion and the agreed timeout. Retain termination reasons and process checks. |
| AT-08 | AIR-008 | **`@offline`:** Fixtures cover H.264 mirroring, HEVC request, HEVC stream, connection without video, audio-only activity, advertised size differing from stream size, and present/absent client metadata. Expected structured fields match each fixture. **`@pc`:** Compare parsed observations with the actual saved log; do not require unlogged metadata to be inferred. |
| AT-09 | AIR-009 | **`@offline`:** Fixtures cover missing fields, unknown lines, separate sessions, unreadable input and detectable truncation. Reports retain correct line/session references, leave absent values unknown and flag evidence limitations. |
| AT-10 | AIR-010 | **`@offline`:** Unambiguous fixtures yield GO for started H.264 mirroring, NO-GO for connected-without-H.264 and HEVC requests, and INCONCLUSIVE for no connection. Capability-only and audio-only fixtures never yield GO. **`@pc`:** Classify the actual completed run using its log. A valid NO-GO or INCONCLUSIVE can pass evaluator acceptance; only a valid GO passes the H.264 feasibility gate. |
| AT-11 | AIR-011 | **`@offline`:** Each verdict fixture yields the expected reason and valid source-line references. Absence-based verdicts identify the observation endpoint and examined extent without fabricated lines. **`@pc`:** Every reference in the saved verdict resolves to the retained run evidence. |
| AT-12 | AIR-012 | **`@offline`:** Synthetic artifact and redaction cases prove required outputs are retained, missing iOS is flagged, and client names/addresses are excluded from published summaries. **`@pc`:** Inspect the run bundle and version-control exclusions; verify recorded iOS and its provenance. If iOS remains missing, evidence acceptance is incomplete. |
| AT-13 | AIR-013 | **`@offline`:** Synthetic recording requests/configurations cannot enable screen-content capture. **`@pc`:** Inspect the effective capture configuration and produced artifacts; no frame dump, screenshot or screen-content recording is produced. |
| AT-14 | AIR-014 | **`@offline`:** Synthetic reports retain their fixture tier and cannot claim actual iPhone or unit verification. **`@pc`:** The final summary identifies the tested build, PC/iPhone configuration and iOS version, and states that head-unit behavior remains unverified. |

## Rationale

**AIR-001.** The documented build prerequisites are a starting point; decoder availability is explicitly unverified. `docs/evidence/S1_PC_FACTS.md:23`, `docs/evidence/S1_PC_FACTS.md:30`.

**AIR-002.** OD-05 specifies UxPlay 1.74 from source, superseding the earlier study’s v1.73.7 suggestion. A source pin and recorded environment make the procedure repeatable without claiming identical binaries. `docs/spec/DECISIONS.md:13`, `evidence/airplay-receiver-feasibility.md:44`.

**AIR-003.** Stale-rule recovery comes from the current S1 request. Restricting removal to runner-owned rules preserves the executable-specific scope of OD-05. `docs/spec/DECISIONS.md:13`.

**AIR-004.** The PC’s Public network profile matters to whether an inbound rule actually enables this test. `docs/evidence/S1_PC_FACTS.md:7`.

**AIR-005.** Removal after the test is an explicit owner decision; failed removal must remain visible as residual state. `docs/spec/DECISIONS.md:13`.

**AIR-006.** The staged notes describe HEVC opt-in and display/name options, but those semantics still need confirmation against the selected source revision. `docs/evidence/S1_PC_FACTS.md:32`.

**AIR-007.** The owner selected a wired PC and an iPhone on the same home network; the earlier study identifies Control Center Screen Mirroring as the intended entry point. `docs/spec/DECISIONS.md:14`, `evidence/airplay-receiver-feasibility.md:9`.

**AIR-008.** S1 measures actual H.264 compatibility and audio format; the expected AAC-ELD format has not been measured on this iPhone. `docs/evidence/S1_PC_FACTS.md:40`, `docs/evidence/S1_PC_FACTS.md:45`.

**AIR-009.** The iOS version is unknown, and offline evidence proves fixture logic only. Unknown observations must remain distinguishable from measured facts. `docs/evidence/S1_PC_FACTS.md:15`, `specs/README.md:20`.

**AIR-010.** AMB-02 identifies H.264 mirroring as the feasibility gate. The current request supplies the three verdict definitions. `docs/spec/AMBIGUITY_REGISTER.md:12`.

**AIR-011.** Saved logs and parsed results are the required PC evidence; line references make the conclusion auditable. `specs/README.md:22`.

**AIR-012.** Raw logs may contain identifying information and belong in uncommitted `runs/`; published evidence is limited to a verdict and redacted summary. `specs/README.md:22`.

**AIR-013.** The evidence rules expressly prohibit captures of phone contents. `specs/README.md:24`.

**AIR-014.** Tier boundaries prevent synthetic or PC results from becoming unsupported hardware claims. `specs/README.md:14`, `specs/README.md:20`.

## Reversibility and safety

- **AIR-001–002:** Prerequisite installation changes MSYS2; builds create source/build artifacts outside version control. Record added packages and generated directories so they can be removed without removing pre-existing dependencies. Preserve failed-build diagnostics and identify partial outputs.
- **AIR-003–005:** Firewall changes are temporary and runner-owned. Verify deletion after cleanup. If deletion fails, retain the rule identity and report that the PC remains changed.
- **AIR-006–007:** The runner starts a receiver process and network listeners. Stopping that process ends them; inability to stop it remains an explicit cleanup failure.
- **AIR-008–014:** Analysis and evidence handling create local reports and logs. Those artifacts can be deleted after review. Redaction occurs before any summary enters version control.
- S1 installs or changes nothing on the head unit. This drafting pass changed no files.

## Open questions

1. **Timeout:** How long should the runner wait, and does the timer start at receiver launch, first connection or video start?
2. **AMB-05:** Must S1 test YouTube, or is ordinary Screen Mirroring sufficient?
3. **AMB-06:** Must S1 assess audio playback quality beyond recording the logged audio format?
4. **AMB-03:** If UxPlay omits iOS, may the owner manually supply its version for the private run record?
5. **Run aggregation:** What verdict applies if one run contains both H.264 success and a HEVC request, or different outcomes across sessions?
6. **Evidence failure:** What verdict policy applies when logs are unreadable, incomplete or insufficient to identify the negotiated codec?
7. **Configuration:** Which upstream commit identifies the intended UxPlay 1.74 build, and what fixed receiver name should S1 use?

The S1 configuration does not settle product-wide display requirements under AMB-04 or supported iOS versions under AMB-03. The AMB-02 proposal to stop the project remains distinct from reporting a NO-GO.

## Not verified

- `SCOPE.md` is absent from the staged root.
- The exact source pin, complete dependency set, decoder operation and pinned-version log signatures have not been verified.
- No build, firewall mutation, receiver run, iPhone connection or head-unit test was performed.
- A 1920×720@60 advertisement does not establish that the actual stream has that resolution or frame rate.
- Forced termination or power loss can prevent immediate cleanup; next-run recovery does not remove that interval of residual exposure.
- The draft received a critic review; it is not a scenario coverage review or owner approval.