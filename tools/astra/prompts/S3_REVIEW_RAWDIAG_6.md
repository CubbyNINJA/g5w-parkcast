Narrow coverage review of new scenarios for slice S3 revision 10. The stage root is STAGE_ROOT.

Implementation found mutants that the reviewed scenarios in `specs/features/16_receiver_diagnostics.feature` didn't
catch, so these scenarios were added (search for them by title): "The no-runtime-switch check flags a planted switch and
names its file"; "The logcat check fails a script that breaks one of its rules"; "A copy that doesn't match its source
is not left on the stick"; "The tool stops before reading anything while Android is still starting"; "The evaluator
reads "receiver.log.1" before "receiver.log", as one log". Two clauses were added to AIR-089(d) and (e) in
`docs/spec/REQUIREMENTS.md` for the partial-copy and boot rules (OD-206 in `docs/spec/DECISIONS.md`). The tools under
test are `tools/airplay_s3/hostchecks.py` (`jni_contract_methods`, `runtime_switch_reads`), `tools/airplay_s3/stickcheck.py`,
`tools/airplay_s3/stick/parkcast-diag.sh` and `tools/airplay_s3/evaluator.py`.

Check only these scenarios and clauses: does each Examples row break exactly the one rule it names (so turning off that
rule alone makes the scenario fail); is each oracle independent of the code under test; does any assert more than the
requirements say; is the split-log scenario able to fail if the read order is reversed or `receiver.log.1` is ignored?
Report blockers and majors only; if none, say they are ready for step definitions. Keep it short.
