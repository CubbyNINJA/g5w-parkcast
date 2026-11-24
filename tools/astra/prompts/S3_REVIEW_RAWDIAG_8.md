Narrow coverage review of one scenario added to slice S3 revision 10. The stage root is STAGE_ROOT.

In `specs/features/16_receiver_diagnostics.feature`, "A counter line that ends exactly at 16,776,704 bytes is written as
an ordinary counter line" was added so that a cap check using `<` instead of `<=` for counter lines fails (the native
source is staged under `evidence/rawdiag.c`, function `fits`). Check it against AIR-087(a) in `docs/spec/REQUIREMENTS.md`:
is its expectation right (a counter line ending exactly at 16,776,704 bytes is an ordinary counter line, not the final
line), and does it assert anything AIR-087 doesn't say? Report blockers and majors only; if none, say it is ready.
