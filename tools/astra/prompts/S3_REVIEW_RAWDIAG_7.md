Narrow coverage review of scenarios added after the native mutation check, slice S3 revision 10. The stage root is
STAGE_ROOT.

The native raw-file module (`receiver/native/src/rawdiag.c`, `logpath.c`, staged under `evidence/`) had 8 surviving
mutants: upper-case `\xHH`; DEL (0x7f) written raw; a byte dump at the very start of a record not redacted; an
unterminated `<data>` kept; no counter trigger on a codec-configuration push; none on a session start; `au_in`
counting empty access units; and a `fits` check using `<` instead of `<=` at the cap. These were added to
`specs/features/16_receiver_diagnostics.feature` (find them by title or Examples): "Bytes outside 0x20-0x7E are escaped
as two lowercase hex digits"; two unterminated rows in "Key material in a record is redacted in place"; "A byte dump at
the very start of a record is redacted"; "Each kind of event without a record writes the counter line at exactly
1,000 ms"; an empty-access-unit row in "Each count moves exactly as the calls made". AIR-085(c) in
`docs/spec/REQUIREMENTS.md` gained the unterminated rule (OD-207). The cap mutant is a step oracle, not a scenario gap:
"At the cap the file keeps its start..." already says a record bringing the file to exactly 16,776,704 bytes is written.

Check only: does each new scenario or row catch its mutant (would the mutant make it fail); is each expected output
right under AIR-085 and AIR-086 as worded; does any assert more than they say? Report blockers and majors only; if
none, say they are ready for step definitions. Keep it short.
