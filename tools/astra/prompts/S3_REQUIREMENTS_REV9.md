Revise slice S3's first-milestone requirements for OD-185. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-178 to OD-185), `docs/spec/REQUIREMENTS.md` (AIR-042 to AIR-083, AT-42 to AT-83),
and `docs/spec/reviews/CLAUDE_REVIEW_s3_scenarios_2026-11-16.md` ("Requirement wording" section, which OD-185 adopts).

Encode OD-185 exactly in the rows it touches: AIR-050 and AT-50 (the reason tokens and `code=`; 5,000 ms is late; EPERM
and `unreachable`), AIR-048 (`port busy <tcp|udp> <port>`), AIR-053 (`refused url|hls|drm`), AIR-058 and AIR-079 (the
exception classes), AIR-069(f) (check order, both run), AIR-056 (decoder created when the H.264 configuration arrives),
AIR-071 (the session-end and stop reason vocabulary; the uptime-milliseconds record prefix), AIR-073(g) (header lines,
non-printable bytes, hex and base64 runs) and AT-73, AIR-075 (`Checklist:` last after S1's fields; `not run
(offline)`), AIR-076(h) (an unparseable timestamp counts as at or after the deadline; UxPlay lines belong to the
latest open session), AIR-078 (every failed rule reported), AIR-074(d) (keep-existing applies to files that appear
during the run) and AT-74. Replace only those rows, whole. Keep the report short.
