Revise slice S3's first-milestone requirements for owner decisions OD-148 to OD-158. The stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-138 to OD-158; OD-147 recorded the first draft's fail-safe choices),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-90 to AMB-107; AMB-95 to AMB-98 stay open for the receiver-env-probe),
`docs/spec/REQUIREMENTS.md` (AIR-042 to AIR-082 and AT-42 to AT-82, from
`docs/spec/reviews/CLAUDE_REQUIREMENTS_s3_onunit_receiver_2026-11-16.md`) and `specs/README.md`.

Encode exactly, without adding owner policy: the advertised name (OD-148); the install, start and log-copy route
(OD-149); what the screen shows while blocked and on start failures, and that the app stays open (OD-150); CarPlay
active at start is only logged (OD-151); `INTERNET` as the only permission (OD-152); audio going nowhere is accepted
(OD-153); the owner's picture note on the stick as a GO condition and its format (OD-154); port/advertise failures
INCONCLUSIVE, the model-only rule and the 10-minute timeout (OD-155); the log directory creation and the provisional
package id (OD-156); OpenSSL 3.0 LTS and libplist 2.x (OD-157); accepted proposals stop being marked as proposals
(OD-158). Replace each affected row whole, keeping its ID, with its AT row.

Under OD-86 resolve any parsing, logging or timing edge case with the fail-safe choice and say so; list under Open
questions only what touches safety, scope, or what the owner sees or hears. Keep the report concise.
