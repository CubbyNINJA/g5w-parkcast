# S3 receiver: design and test harness

How the AirPlay receiver is built around the S2 gate, and how its scenarios drive it: file 13 in Cucumber-JVM
with doubles, file 14 in pytest for the build, tools and stick, file 15 on the unit. The requirements
(`docs/spec/REQUIREMENTS.md`, AIR-042 to AIR-083, plus AIR-038, AIR-039 and AIR-041, which file 13 reuses)
and the scenarios (`specs/features/13` to `15`) are the source of truth. This document is the contract
between the step definitions, the tools and the receiver code, and follows `S2_GATE.md`'s format.
Decisions: OD-135 (verification), OD-138 to OD-190. Reviewed by a fresh-context critic on 2026-11-17; its
findings are folded in. The owner settled the staged start (OD-187) and signing (OD-188). OD-189 and OD-190
record the rest.

## Constraints

- **Three layers.** `receiver/core` is plain Java 7 (`--release 7`, like the gate) with no Android imports;
  it reaches the network, the native library, the codec, files and windows only through ports. `receiver/app`
  is a thin Android shell: the Activity, the port adapters and the JNI glue. `receiver/native` is C: UxPlay
  `lib/` plus a small host library, `libparkcast.so`.
- **One event thread.** Core and the gate run on the main looper. Native threads never call core; they hand
  events to one delivery thread, which posts to the event thread (Threads, below).
- **Native stays narrow** (AIR-055): no Android, vehicle, audio or display APIs in C (b). Java calls native
  only for start, stop and status (c). Under OD-187, "start" is staged: `start(KEYCHECK)`, `start(BIND)`,
  `start(ADVERTISE)` and `start(ACCEPT)`, and there is no other entry point. Native passes up only the
  AIR-055(a) items.
- **No log text beyond AIR-071, AIR-073 and file 13** (OD-178, OD-185). A counter that only helps diagnosis
  goes into `status()`, not the log.

## Proof by construction: what the receiver never touches

1. **Recording doubles.** `MirrorSession` and `VehicleBus` stay at zero calls (AIR-067(a)). The fake
   NsdManager records `registerService` and `resolveService`, which must stay at zero (AIR-050(a)). The fake
   file system records every write, rename, chmod and delete; all must fall under the log dir or app-data
   (AIR-068(a)). The audio fixture `PARKCAST-AUDIO-FIXTURE-55` (AIR-054) and the stdio marker
   `STDIO-MARKER-91` (AIR-055(f)) appear in no log, stick copy or trace.
2. **A static scan of the whole app** (AIR-067(b)): core and app sources, compiled classes, JNI sources and
   the manifest. It extends S2's list with `AudioTrack` and `AudioManager` (AIR-054); `SYSTEM_ALERT_WINDOW`,
   `TYPE_SYSTEM_ALERT` and `Toast.show()`, since only `Toast.makeText(...).getView()` is allowed (AIR-061);
   property setters, `remount` and Wi-Fi state setters (AIR-068); `Presentation` and other display ids
   (AIR-081); `registerService` and `resolveService` (AIR-050(a)); port 7000 as a bind or connect target,
   `ndc`, `ctl.start`, `su`, `nemesis root`, and `/dev/socket/mdns` as a whole token, so `/dev/socket/mdnsd`
   stays allowed (AIR-049); boot receivers, `AlarmManager` and `Service` (AIR-068).
3. **The manifest check**: exactly one permission, `INTERNET`; no launcher category; no receiver, service or
   provider (AIR-068, AIR-082). The `.so` is checked by file 14's host checks (Tools, below).

## Modules and Gradle

**`receiver/core`**: a plain Java library (main release 7, tests 17) depending on `:receiver:gate`. Packages:
`parkcast.core` (components), `parkcast.core.port` (ports) and `parkcast.core.adapter`, which holds the
plain-Java halves of the Android adapters: `ReverseFilter` (action and extras in, `REVERSE_ON` only, never
sticky, AIR-060), `ParkCall` (200 ms timeout, class-name failure text, AIR-058), `AccAdapter` (worker
registration posted back, AIR-059), `RegulationAdapter` (notifications only, AIR-066) and `LogDirFiles`
(`java.io` rooted at the log dir, AIR-062). The Android classes in `receiver/app` only convert `Intent`,
`Bundle` and service objects into these halves; the existing `AndroidReverseSource` delegates to
`ReverseFilter`. That split lets the meta profile run on a plain JVM; Robolectric was considered and
rejected. Tests: glue `parkcast.core.steps`, features 13 and 15 from `specs/java-features.txt`, default tags
`@S3 and @offline`.

**`receiver/app`**: `com.android.application`, AGP 8.7.x on the existing Gradle 8.10.2 wrapper, JDK 17.
Namespace `parkcast.receiver`; compileSdk 34, minSdk 19, targetSdk 19 (lint's expired-target check off,
because the APK is never published); `ndkVersion "25.2.9519653"` (r25c), `abiFilters 'armeabi-v7a'`, CMake
3.22.1 via `externalNativeBuild` on `receiver/native/CMakeLists.txt`. Lint `NewApi` is an error and
`abortOnError` is on; Java source level 8, written in Java 7 style. `android.builder.sdkDownload=false`, so
AGP never fetches an SDK or NDK package around `sdk_install.py` (AIR-045). Signing (AIR-047):
`signingConfigs.release` reads `parkcast.keystore`, `parkcast.keystore.password`, `parkcast.key.alias` and
`parkcast.key.password` from git-ignored `local.properties`. The keystore lives at
`%USERPROFILE%\.parkcast\parkcast-release.jks` (OD-188). `.gitignore` gains `local.properties`, `*.jks`,
`*.keystore` and `receiver/native/.cache/`. A `checkSigningInputs` task runs before packaging, names every missing item and never
prints a value; there is no debug-key fallback. `./gradlew assembleRelease` is the one command that yields
the signed APK (AIR-046(b)). BuildConfig carries `APP_VERSION`, `GIT_COMMIT` (`git rev-parse HEAD` at
configuration time) and `UXPLAY_COMMIT` (from `deps.lock`) for the start marker (AIR-071(a)). Manifest:
`INTERNET` only, one exported-false activity, no launcher and no receivers (AIR-068, AIR-082).

**`settings.gradle`** declares AGP in `pluginManagement`, always includes `:receiver:core`, and includes
`:receiver:app` only when `sdk.dir` (in `local.properties`) or `ANDROID_HOME` is set and `-Pparkcast.noApp`
is absent. Otherwise it prints one line saying the app was skipped and why, so the gate and core build
without the SDK (AIR-046(c)).

**`receiver/native/`**: `deps.lock` (name, version, URL and SHA-256 per archive, plus the UxPlay commit),
`patches/` (numbered, applied in order), `src/` (`parkcast_jni.c` entry points; `ring.c`, the bounded event
ring; `host.c`, the callback table, stdio redirect, start and stop phases, and the mdnsd connect probe),
`CMakeLists.txt`, and a git-ignored `.cache/` for downloads and extracted trees.

## Ports (package `parkcast.core.port`)

| Port | Methods (sketch) | Unit implementation | Test double |
|---|---|---|---|
| `InterfaceQuery` | `Iface get("wlan0")`: exists, up, IPv4 addresses; may throw | `NetworkInterface.getByName`, read-only (AIR-048(b)) | interface double: missing, down, other address, throws |
| `NativeLibrary` | `StartResult start(Stage s, StartPlan p, NativeEvents e)` with stages `KEYCHECK`, `BIND`, `ADVERTISE`, `ACCEPT` (OD-187); `void stop(Phase p)`; `Status status()` | JNI into `libparkcast.so` | fake native library (below) |
| `NsdWake` | `NsdHandle get()` (null, or throws `SecurityException`); `discover(type, l)`; `stop(l)`; `l.started()`, `l.startFailed(int)` | `getSystemService(NSD_SERVICE)`, `discoverServices`, `stopServiceDiscovery`. Discovery results are ignored and never logged (AIR-050(a)) | fake NsdManager: callback time or never, error codes, delivers found services that must not reach the log, records every call |
| `Mdns` (inside `NativeLibrary`) | not a separate port: the `ADVERTISE` stage does the `/dev/socket/mdnsd` connect probe (one attempt per call; core repeats it every 250 ms) and both registrations, returning `mdnsd up`, an errno token, a reply code or `name-conflict`; a lost registration socket arrives as an `advertise lost` event | UxPlay's dns_sd through `dlopen` of `libmdnssd.so`, patches (iv) and (v) (OD-187) | fake native library: errno per attempt, socket appears at T, reply codes, conflicts, close at T; records full TXT and flags |
| `CodecFactory`, `Codec` | `create(name)`; `configure(fmt, surface)`; queue and dequeue; `release(int index, boolean render)`; `release()` | `MediaCodec.createByCodecName("OMX.TCC.avcdec")` on the activity Surface, synchronous mode (AIR-056) | fake codec factory and codec: throws on create or configure, error at T, outputs per input |
| `CoverView` | `void black(boolean on)` | full-window black View above the video surface | records times |
| `AppFiles` | `exists`, `length`, `read`; may throw | `getFilesDir()` plus `java.io` | fake file system, shared with the gate's `LogFiles` double |
| `ProcNetTcp` | `byte[] read()`; may throw | `/proc/net/tcp`, read-only (AIR-079) | contents, malformed, unreadable |
| `CrashHook` | `void install(CrashSink s)` | default uncaught-exception handler, then the process ends (AIR-069(a), (b)) | throws at T on the event thread |

`ro.build.product` is read through the gate's existing `BuildInfo` port. On the unit that is reflection on
`android.os.SystemProperties.get`, read-only. The static scan allows only the getter.

Gate ports are reused unchanged: `Scheduler` (`Handler` plus `uptimeMillis`, AIR-065), `ParkGetter`
(AIR-058), `AccSource` (AIR-059), `ReverseSource` (AIR-060), `NoticeWindows` (AIR-061), `LogFiles` (AIR-062),
`ConfigFile` (AIR-063), `BuildInfo` (`ro.build.product` only, AIR-064), `RegulationSource` (AIR-066),
`Display` (implemented by `CodecDisplay`), and `MirrorSession` and `VehicleBus` at zero calls (AIR-067).

**The fake native library** stands for `libparkcast` plus UxPlay. It records UxPlay-level calls
(`raop_init`, `raop_set_log_*`, `raop_init2` with its keyfile, binds, registrations). JNI entries are not
calls, so a start that fails at step 0 records none (file 13 l.365-369). It keeps:
- an fd table for stdio;
- a key file in the fake file system, whose writes, renames and deletes it records;
- the **socket double** (file 13 l.28): a port table recording create, bind, listen, accept, connect, send
  and close per port. File 13's "no accept call" assertions read it.

It prints the stdio marker at its first library call. It delivers scripted callbacks through a model of the
ring, the generation check and the delivery thread. Its behaviour is the specification for `host.c`; file 15
checks the real thing on the unit.

## Core components (package `parkcast.core`)

| Class | Responsibility | Requirements |
|---|---|---|
| `ReceiverRuntime` | Wires core and the gate; entry points mirror the Activity (`onCreate`, `onStart`, `onResume`, `onWindowFocusChanged`, `onPause`, `exit()`) | all |
| `CarplayProbe` | Reads `/proc/net/tcp` for a TCP 7000 listener; logs `carplay listener present`, `absent` or `unknown <fully qualified exception class>`; logs no line of the file; never connects | AIR-079(b) to (d) |
| `HotspotCheck` | wlan0 exists, is up and holds 192.168.0.1, else `hotspot address missing` (with the class if it threw). The interface is queried exactly once per start, with no retry | AIR-048(b), (d) |
| `PortPlan` | The five ports on 192.168.0.1, never TCP 35002 or any other port; a bind failure becomes `port busy <tcp\|udp> <port>` | AIR-048(a), (c), AIR-049 |
| `KeyFileStore` | Fixed keyfile path in app-data; never writes, renames or deletes; path and name never logged. The key check itself is native (`KEYCHECK`, patch (vi)); core logs `identity key unavailable <class or library error token>` from the `StartResult`, through `NativeLogFilter` | AIR-083 |
| `Advertiser` | NSD wake and its 5000 ms window (exactly 5000 is late); the 250 ms probe for up to 5000 ms through `start(ADVERTISE)`; both registrations; failure tokens; withdraw on failure; `advertise lost`; stop order. Calls `start(ACCEPT)` only after `advertise ok`. Never logs the service name, device id or TXT values | AIR-050, AIR-073(g) |
| `NativeEvents` | Generation check, then dispatch of session, codec, AU, geometry and log events on the event thread | AIR-055(a), (e) |
| `DecoderControl` | Creates the decoder at codec config, never before; HEVC refused; `decoder unavailable` (start) or `decoder error` (mid-session: no notice, no retry) | AIR-052, AIR-056 |
| `CodecDisplay` | Implements `Display`: maps each `Frame` to its output buffer index, releases with render only if `show` was called; `clear()` raises `CoverView`; keeps draining while blocked | AIR-057 |
| `Letterbox` | Fits the stream into 1920x720, centred, never stretched; geometry for AIR-051(c) | AIR-051 |
| `SessionLog` | Start marker, session records in AIR-071(b) order, numbering from 1, end marker, uptime prefix | AIR-071 |
| `NativeLogFilter` | Allowlist, redaction, whole-record drops (including key path or name, file 13 l.1017-1022), the withheld count. Ready once the allowlist is loaded, before the stdio redirect. A record before ready or one that makes the filter throw is dropped and counted. Drops outside a session go into the next session's `withheld=`, or are discarded if none follows (OD-190) | AIR-072, AIR-073 |
| `StartFailures` | Only AIR-069(c)'s five causes; one notice per start, for the first cause logged; held while covered, 5000 ms, dropped at exit. Other start failures (AIR-069(d), such as `stdio redirect failed`) are logged with no notice | AIR-069(c) to (g) |
| `StopSequence` | The AIR-070 order; a failed step is logged, a step with nothing to undo is skipped silently | AIR-070 |
| `CrashRecorder` | `crash` record with the class and `parkcast.*` frames only, then the process ends | AIR-069(a), (b) |

## Start and stop

**Start** runs on the event thread at `onCreate` and `onResume`, following the gate's own lifecycle:

1. `parkcast start app=... commit=... uxplay=... product=... require_park=...` is the first record
   (AIR-071(a)). The gate gains a start-marker hook so this record comes before the gate writes
   `start check=...` and carries the gate's effective flag (OD-189, additive).
2. `CarplayProbe` (AIR-079).
3. `HotspotCheck`, logged at once (AIR-069(f)). Its result does not stop the key check.
4. `start(KEYCHECK)`, which always runs, even when the hotspot check failed (AIR-069(f)):
   - Step 0 is `dup2` of fds 1 and 2 to `/dev/null`. On failure it logs `stdio redirect failed`, makes no
     library call and takes the AIR-069(d) path: no notice (AIR-055(f), file 13 l.365-372). With no library
     call allowed, the key check can't run, and AIR-055(f) and file 13 settle that overlap with AIR-069(f).
   - `raop_init` with all seven callbacks non-NULL (AIR-055(g)). The log callback and level are set before
     `raop_init2` and never again (AIR-072(a)).
   - `raop_init2` with the keyfile is the key check, always second (AIR-069(f), AIR-083). Patch (vi) is
     required. The library never opens an existing key file for writing. On a read or parse failure it
     returns an error code, which `libparkcast` maps to a library error token. It creates a missing file
     once, mode 0600.
   - The stage returns `(ok | failure, token)`. Core logs the key result after the hotspot result, then hands
     the first cause to `StartFailures`.
5. `start(BIND)`, only if both checks passed. It advertises 1920x720 at 60 fps in the receiver's info
   (AIR-051(a)) and binds the five ports on 192.168.0.1 (patch (ii)), holding the three UDP ports (patch
   (iii)). On failure it releases what was bound and returns `port busy <tcp|udp> <port>` (AIR-048(c)). This
   stage calls listen but never accept.
6. `Advertiser`, only after `BIND` passes (AIR-050). It wakes NSD, calls `start(ADVERTISE)` every 250 ms
   until the probe connects, registers both services, and logs `mdnsd up` then `advertise ok`.
7. `start(ACCEPT)`, only after `advertise ok`, starts the listener thread. If any earlier step failed, this
   stage never runs, so file 13's "no accept call" holds for port busy, hotspot missing, the key, and
   advertise failure (AIR-050(d), AIR-083(c)).
8. The decoder is created at `video_set_codec_and_size` (AIR-056(f)). For H.265, native refuses without
   waiting on Java; Java logs `hevc refused` and ends the session (AIR-052). URL, HLS and DRM are refused
   natively and come up as log events (AIR-053).

Only AIR-069(c)'s five causes go to `StartFailures`: one notice per start for the first cause logged, held
while covered. The notice lives in the gate's `Notices` and `Cover` as an additive API (OD-189). Any other
start failure (AIR-069(d)) is logged with no notice. Either way, video stays blocked and the activity stays
open.

**Stop** (`StopSequence`, AIR-070(a)). Each step runs even if an earlier one failed, and a step with nothing
to undo is skipped without an error:
1. Block video: `Display.clear()`, drop a held start-failure notice, and the gate's own exit. The gate exit
   runs inside this step and records no stop step of its own (OD-189). If it throws, that is logged as this
   step failing (AIR-070(b)).
2. Native `stop(ACCEPT)`.
3. Native `stop(SESSION)`, which moves the native generation on, then joins the delivery thread (OD-190).
4. Withdraw both registrations.
5. `stopServiceDiscovery`, only if `discoverServices` had returned.
6. Release the decoder.
7. Native `stop(PORTS)`.
8. `parkcast stop reason=<owner exit|destroy>`, written last (AIR-071(c)).

These match file 13 l.806-816's step list one for one.

## Threads

- **Event thread (main looper)**: core, the gate, `Advertiser` timing on `Scheduler`, the codec feed and
  drain, and the log filter, which runs before every write. Park and ACC keep the gate's worker-then-post
  model (AIR-058, AIR-059).
- **UxPlay threads** (listener and per-connection). Each callback first checks the native generation and
  drops itself if it is stale. Otherwise it copies into the bounded ring under a mutex and returns at once,
  never waiting on Java (AIR-055(g)). `audio_process` discards before the ring (AIR-054, AIR-055(g)). When
  the ring is full (OD-190):
  - a video AU is dropped and counted in `status()` only;
  - a log record is dropped and added to the withheld count (AIR-072(b), AIR-073(c));
  - control events (connect, codec configuration, disconnect, error, `advertise lost`) use reserved slots and
    are never dropped, so `session <n> end` is never lost (AIR-071(b)).
- **One delivery thread**, attached to the JVM once, pops the ring and calls `NativeEvents.deliver`, which
  posts to the main `Handler` tagged with the current generation. `stop(SESSION)` moves the generation on
  first, so per-connection threads still running drop their callbacks. It then closes the ring and joins the
  thread. No JNI call reaches Java after `stop` returns, and posts already queued are dropped (AIR-055(e),
  file 13 l.357-362).
- **MediaCodec in synchronous mode on the event thread** (API 19 has no async callback): input queued when an
  AU arrives; output drained on that call and on a `Scheduler` tick while a session is live, with zero-timeout
  dequeues. If main-thread load is too high on the unit, the `Codec` adapter moves to a codec `HandlerThread`
  that posts output indexes back; core does not change, because `Codec` is a port.

## Native build

**NDK check in three layers** (AIR-043), with no fallback NDK, ABI or API level: (1) a `checkNdk` Gradle task
reads `Pkg.Revision` from the NDK's `source.properties`, fails unless it is `25.2.9519653`, and names the
version found; (2) `CMakeLists.txt` checks `ANDROID_NDK_REVISION` the same way, covering a direct CMake run;
(3) AGP's `ndkVersion` pin.

**Native prep**, `tools/airplay_s3/native_prep.py`, a Gradle `Exec` task that CMake configure depends on:

1. Parse `deps.lock`; fail unless OpenSSL is 3.5.x and libplist 2.x (AIR-044(d)).
2. Download into `.cache/` and verify SHA-256 before extracting; a mismatch fails and nothing is extracted
   (AIR-044(a), (b)).
3. Fetch UxPlay commit `05b0275c...` with a shallow `git fetch` of that commit and check `HEAD` equals it
   (AIR-042(a)). The object id is the integrity check, since GitHub archive bytes are not guaranteed stable.
4. `git apply --check` every patch, then apply; any failure fails the build (AIR-042(c)).
5. Write the unguarded-callback list to the build log as `unguarded callbacks: a, b, ...` (file 14 l.238)
   and compare it with the committed `receiver/native/unguarded-callbacks.txt`; any difference fails (OD-189).
   File 13 reads the committed file, so it stays offline.

**CMake** builds, in order: OpenSSL as static libcrypto and libssl. Its Configure runs MSYS2's Perl,
`C:\msys64\usr\bin\perl.exe`, which `parkcast.perl` can override. Native prep checks that Perl's version and
the modules it needs; Git Bash's Perl lacks them (OD-189); libplist as a static library from a source list, skipping autotools; UxPlay `lib/` only
(AIR-042(b)); then `libparkcast.so`, linked statically against those, with hidden symbols. `libmdnssd.so` is
opened only by `dlopen`, by name, through patch (i), never as a `NEEDED` entry (AIR-044(c)).

**Patches**: (i) `dns_sd.c:177` opens `libmdnssd.so` (AIR-042(d)(i)); (ii) bind to the passed address, never
the wildcard (`netutils.c` around l.157 and l.183, AIR-042(d)(ii)); (iii) bind and hold the three UDP ports
from the `BIND` stage (OD-189); (iv) `kDNSServiceFlagsNoAutoRename` and a register callback reporting reply
codes (AIR-050(c), (d)); (v) expose the registration socket so its close becomes `advertise lost`
(AIR-050(e)); (vi) required: `raop_init2` never opens an existing key file for writing, returns an error
code on a read or parse failure, and creates a missing file once, mode 0600 (AIR-083(a) to (c)); (vii) split
bind from listen-accept for the `ACCEPT` stage and its stop phase, and add hook points for the AIR-053
refusals.

## Tools on the pytest side

`tools/airplay_s3/` mirrors `tools/airplay_s1/`, with a `tools/s3.py` CLI. Steps go in new
`tests/steps/receiver_*_steps.py` modules; file 14 is registered in `tests/feature_sets.py`; `@host`
scenarios need `--run-host`.

| Module | Job | Requirements |
|---|---|---|
| `native_prep.py` | Above | AIR-042 to AIR-044 |
| `sdk_install.py` | Pinned SDK packages under `C:\Android` from a lock file; each archive is checksum-verified in a temp dir before extraction and rename into place; a mismatch leaves it unextracted and installed packages unchanged | AIR-045 |
| `zipbuild.py`, `zipcheck.py` | The `airplay.receiver` zip: only `NEMESIS.data` and the APK; one `[app.0]` `kind=apk`; no services, hooks, depends or config; `os=` and `models=` exact, once each, never `any`; the 1.16 limits (64 and 256, quotes not counted). Reports every failing rule, one line each | AIR-078(b), (c), (h) |
| `hostchecks.py` | Over the `.so`, APK and repo: `readelf` NEEDED against the allowlist; ARM EABI5 and `armeabi-v7a` the only ABI; no wildcard bind; no responder symbols; `libmdnssd.so` in strings and `libdns_sd.so` absent; the manifest permission; the callback list against the committed file; no keystore, `*.jks`, `*.keystore`, key file or `local.properties` tracked, and each one ignored (`git ls-files`, `git check-ignore`, OD-188) | AIR-044, AIR-046, AIR-047(b), AIR-049, AIR-055 |
| `evaluator.py` | Raw logs under `runs/`; completeness needs both markers; GO, NO-GO or INCONCLUSIVE; reuses `airplay_s1` parsing where the format matches. Also: the `Checklist:` line last, `Checklist: not run (offline)` for an offline tier, and malformed checklist text named but kept out of the summary (AIR-075(c) to (f), AIR-079(f)); the owner-note rules and `owner note ambiguous` (AIR-076(e), (f)); port busy, advertise failed and carplay present make the run INCONCLUSIVE, while an unknown or missing carplay record counts as absent (AIR-076(g)); the 10 min deadline, with an unparseable prefix counting as at or after it (AIR-076(h)) | AIR-075 to AIR-077, AIR-079(f) |
| `stick/install-receiver.sh` | Toolbox only, LF, plain `mv`. `nemesis install --update` with exit and output checks, then `pkg-list` and `pm list packages`. Never `pm install`. On any AIR-078(d) failure it prints the install output to the stick log and stops: no `mkdir`, no `am start`. After success it creates the log dir, owned by the app uid with mode 0700, and checks the uid; on a uid or directory-step failure it reports which and skips `am start` (AIR-078(e)). Non-zero on any failure, including `Error` from `am start` | AIR-062, AIR-078 |
| `stick/copy-logs.sh` | Copies to `NEMESIS-diag/airplay-receiver/<timestamp>/` and never changes the originals; a partial copy is reported incomplete and exits non-zero. Places a zero-byte `owner-note.txt` and a `run-checklist.txt` holding AIR-079(e)'s six bare `<key>: ` lines, LF, even after a partial copy. Never overwrites a file that appears during the run, and reports leaving it. A file it can't place is named and exits non-zero without being called an incomplete copy. A run folder that already exists is refused, left unchanged and reported. Never copies the key file | AIR-074(a) to (g), AIR-079(e), AIR-083(e) |

Stick scripts run against fake toolbox commands (`nemesis`, `pm`, `am`, `mkdir`, `chown`, `chmod`, `mv`,
`cp`, `ls`): `sh` scripts in a temporary `bin/` first on `PATH` that record argv and reject any option the
unit's toolbox rejects, so a PC `mv -f` can't hide a bug.

## Cucumber harness for file 13

Code lives in `receiver/core/src/test/java/parkcast/core/` (`doubles/`, `steps/`). `Trace`
(`(time, cause, name, details)`), `VirtualScheduler` (tie ranks) and the gate doubles come from the gate's
test fixtures (`java-test-fixtures`, never in a release build; OD-189). The doubles are the ones file 13's preamble names: fake native library, fake
NsdManager, fake dns_sd, interface double, fake codec factory and codec, fake file system, UI double, black
cover view, lifecycle and focus doubles.

**World** (one per scenario, picocontainer). `When the receiver runs for N ms` builds `ReceiverRuntime` at
0 ms and runs the S2 lifecycle; "in either app start" and restarts work as in S2, with `LogFiles`,
`ConfigFile` and the key file surviving a restart. "Exactly T" means a record beginning with T in ms and a
space (AIR-071(g)). "The withheld count" is the number of records dropped under AIR-073(c) (file 13 l.80).

**Cross-thread timing.** "The Java side is held busy from 1500 ms to 2500 ms" makes the event thread
unavailable for that span in virtual time; the modelled callback still returns at once, and its delivery
reaches core at 2500 ms or later. The Examples list is compared with the committed
`receiver/native/unguarded-callbacks.txt`, and every listed callback must pass through the generation check
in the fake.

**Step classes** match the Gherkin as written and never read core fields: `StartSteps` (AIR-048, AIR-049,
AIR-069), `KeySteps` (AIR-083), `NativeSteps` (AIR-055, AIR-072), `AdvertiseSteps` (AIR-050), `StreamSteps`
(AIR-051 to AIR-054, AIR-056, AIR-057), `LogSteps` (AIR-038, AIR-039, AIR-071, AIR-073), `StopSteps`
(AIR-070), `AndroidPortSteps` (AIR-058 to AIR-066), `ScopeSteps` (static scans, AIR-067, AIR-068),
`RunConditionSteps` (AIR-079 to AIR-082), `MetaSteps` (the meta profile).

**The meta profile** (file 13, about l.510) runs the gate's own `@S2 and @AIR-041 and @offline` scenarios
against the real `REVERSE_ON` adapter instead of the gate's double. `MetaSteps` calls
`io.cucumber.core.cli.Main.run` in-process with glue `parkcast.gate.steps`, features 09 to 12, the scenario's
tag expression and a JSON report to a temp file. A binding seam in the gate's `World` (OD-189,
mutation-checked like other changed gate code) builds its
`ReverseSource` from `ReverseFilter` plus a registration shim, driven by a fake broadcast source that also
delivers `REVERSE_OFF`, extras and a sticky-looking first intent. The step passes only if the exit status is
0, at least one scenario ran, and every scenario passed with none undefined, pending or skipped. The thin
Android wrapper is covered by the static scan and by l.506 (`registerReceiver` returned inside `onCreate` on
the main thread; the filter has one action).

**File 15** runs on demand: `./gradlew :receiver:core:test -Dcucumber.filter.tags="@S3 and @unit"
-Dparkcast.evidence=<dir>`. Steps read the stick evidence (logs, run checklist, probe output). A Given whose
evidence is missing is reported as unexercised, naming that Given, and never passes (as in file 12).

## Verification order

Each step below uses OD-135's four steps: fail for the right reason; green with earlier work still green;
mutation check on the new code; pytest and `./gradlew test` green, traceability, commit, privacy scan,
publish.

0. **Gate first** (OD-189). Give each line of `specs/java-features.txt` its module (`gate` or `core`), so
   the gate runner no longer picks up files 13 to 15. Then add the test fixtures, the `World` seam and the
   additive gate API: the start-marker hook and the start-failure notice. S2 stays green and is
   mutation-checked.
1. **File 14 `@offline`.** Pure Python, no SDK: zip build and check, stick scripts on the fake toolbox, the
   evaluator, `sdk_install` and `native_prep` on fixture archives, the repo signing check.
2. **File 14 `@host`, native prep only.** One network fetch. It produces the patched tree and checks the
   committed callback list.
3. **File 13, area by area**: start and ports (AIR-048, AIR-069(f)); key (AIR-083); native boundary
   (AIR-055, AIR-072); advertise (AIR-050); stream, decoder and display (AIR-051 to AIR-057); logs and filter
   (AIR-071, AIR-073); stop (AIR-070); Android-side ports (AIR-058 to AIR-066); scope scans (AIR-049,
   AIR-067, AIR-068); run conditions (AIR-079 to AIR-082); the meta profile.
4. **The rest of file 14 `@host`**: full `./gradlew assembleRelease`, then readelf, ABI, strings, manifest
   and signed-APK checks.
5. **File 15 on the unit**: stick install, a run, copy-logs, the evaluator, core's `@unit` run over the
   evidence; the owner records GO, NO-GO or INCONCLUSIVE.

## Open on-unit risks

Each is answered by file 15, or first by a read-only probe under `tools/unit-probes/`.

- **Loader**: API 19's loader with an r25c `.so` (RELR, packed relocations, `DT_RUNPATH`); linker flags may
  be needed.
- **mdnsd**: whether `INTERNET` alone can connect to `/dev/socket/mdnsd` (AIR-050(g), OD-164); whether
  libmdnssd's client stub works when `dlopen`ed; whether mdnsd accepts full TXT records and `NoAutoRename`
  (the TXT question is open in the CROSSBUILD spike).
- **Decoder**: whether `OMX.TCC.avcdec` takes the iPhone's H.264 profile and level at 1920x720, 60 fps.
- **Crypto**: OpenSSL 3.5 and playfair on ARMv7 (`modified_md5.c:74` in the spike).
- **UDP**: when UxPlay binds UDP during SETUP, and whether holding the ports from `BIND` is compatible.
  File 15's run, or a read-only probe of `/proc/net/udp`, confirms the three ports are held before SETUP.
- **Late callbacks**: whether every UxPlay per-connection thread passes through the generation check, so
  nothing reaches the ring after `stop(SESSION)` (AIR-055(e)).
- **Key file**: whether `raop_init2` rewrites an existing file (AIR-083 forbids it), and its mode on creation.
- **Stop phases**: whether UxPlay can stop accepting without closing TCP 35000, so AIR-070 maps onto
  `ACCEPT`, `SESSION` and `PORTS`.
- **Load**: main-thread latency with the codec on the event thread, and ring sizing; a dropped AU corrupts the
  picture until the next IDR.
- **HEVC**: whether a negative return from `video_set_codec_and_size` makes UxPlay end the session cleanly.
- **AIR-053**: where UxPlay's URL, HLS and DRM paths can be refused.
- **AIR-069(b)**: whether KitKat's ActivityManager relaunches anything after the process ends.
- **App uid**: how the toolbox-only stick script learns it (for example, the owner of
  `/data/data/parkcast.receiver`).
- **Park access**: app access to `getGearPStatus` (AMB-98, still open). Until the unit answers it, the gate
  fails safe and video stays blocked.

## Choices made here (cheap to reverse)

- Build: AGP 8.7.x, targetSdk 19, Java 8 source level, `android.builder.sdkDownload=false`, the conditional
  include of `:receiver:app`, MSYS2's Perl, native prep in stdlib Python (OD-189).
- Native: the staged start (OD-187); the ring with reserved control slots (OD-190); one delivery thread with
  a generation check; the step-0 stdio redirect inside `KEYCHECK`; the phased `stop`; UDP held from `BIND`;
  UxPlay fetched by commit rather than archive hash; the committed callback list.
- Runtime: MediaCodec synchronous on the event thread with a codec-thread fallback behind the port; the
  `CodecDisplay` frame-to-index map, so the gate's `Frame` is unchanged; letterboxing in Java.
- Layout: plain-Java adapter halves instead of Robolectric; each runner's scope set by the module column in
  `specs/java-features.txt` (core: 13 and 15, `@S3 and @offline`); shared gate test fixtures with a `World`
  seam; tool and step locations mirroring S1; fake toolbox commands on `PATH`.
- Gate: additive API only (start-marker hook, start-failure notice); gate exit inside stop step 1.

## Decisions settled at review

| Item | Ruling | Record |
|---|---|---|
| Additive gate API (start-marker hook, start-failure notice) and where gate exit sits | Additive only, mutation-checked; gate exit inside stop step 1 | OD-189 |
| Gate runner scope | Module column in `specs/java-features.txt` | OD-189 |
| Shared gate test fixtures and a `World` seam | Adopted; fixtures never in a release build | OD-189 |
| dns_sd placement and AIR-055(c) | Staged start; UxPlay's dns_sd as the `ADVERTISE` stage | OD-187 (owner) |
| UDP check | Patch (iii) holds the ports from `BIND` | OD-189 |
| Host Perl | MSYS2's Perl, already installed | OD-189 |
| Signing setup | Profile keystore, git-ignored `local.properties`, a tracked-file host check | OD-188 (owner) |
| Native prep language | Stdlib-only Python | OD-189 |
| Callback list link | Committed `receiver/native/unguarded-callbacks.txt` | OD-189 |
| Ring overflow and filter readiness | Reserved control slots; log drops counted | OD-190 |
| AMB-98 | Still open; answered by the first on-unit run | AMBIGUITY_REGISTER |
