Draft the requirements and acceptance tests for slice S1 of this project. The stage root is
STAGE_ROOT. Read `AGENTS.md`, `docs/spec/DECISIONS.md`, `docs/spec/AMBIGUITY_REGISTER.md`,
`specs/README.md`, `docs/spec/REQUIREMENTS.md` (empty so far), `docs/evidence/S1_PC_FACTS.md`, and
`evidence/airplay-receiver-feasibility.md` (the earlier feasibility study).

## Slice S1: PC feasibility test of H.264 mirroring (OD-04, OD-05, OD-06, AMB-02)

S1 answers one question before any head-unit code exists: will the owner's iPhone mirror to a
receiver that offers H.264 only (no HEVC) and advertises 1920×720? It runs on this Windows PC.
The receiver is UxPlay, built from source under MSYS2.

S1 must deliver, as testable behaviour:

1. A reproducible UxPlay build on this PC under MSYS2 UCRT64, from a pinned upstream commit,
   into a build tree outside version control, plus a check that the needed MSYS2 packages
   (including a GStreamer H.264 decoder) are present before building.
2. A Windows Firewall inbound rule that admits only the built `uxplay.exe`. It's created for a
   test run and removed afterwards, including when the run fails or is interrupted. A leftover
   rule from an earlier run is detected and removed.
3. A test runner that starts UxPlay with HEVC off, advertising 1920×720 at 60 fps under a fixed
   test name, captures its log to an evidence file, waits for the owner to start Screen
   Mirroring on the iPhone, and stops UxPlay when the owner ends the test or after a timeout.
4. A log analyser that reads a UxPlay log and reports: whether an iPhone connected, whether a
   mirroring video stream started, the video codec (H.264 or HEVC), the stream resolution, the
   audio format, and the client's model and iOS version if UxPlay logs them. It must be testable
   offline against synthetic log fixtures.
5. A verdict with distinct outcomes: GO (a mirroring session with H.264 video), NO-GO (the
   iPhone connected but no H.264 mirroring stream started, or it asked for HEVC), and
   INCONCLUSIVE (no iPhone ever connected, such as a discovery or firewall failure). Each
   outcome comes with the reason and the log lines behind it.
6. Evidence rules: save the log, the analyser's report, the verdict and the iOS version. Never
   save video frames, screenshots or anything showing the phone's screen content.

The owner hasn't decided whether S1 should also test YouTube or audio playback quality
(AMB-05, AMB-06), or how long the runner should wait. Put those under "Open questions"; don't
decide them.

Use requirement IDs `AIR-001` upward and acceptance tests `AT-01` upward. For each acceptance
test, name its tier (`@offline`, `@pc` or `@unit`, per `specs/README.md`). Behaviour that needs
the iPhone is `@pc`. The analyser and verdict logic must also have `@offline` acceptance tests
on fixtures.
