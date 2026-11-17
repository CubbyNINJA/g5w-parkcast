# S2 parking gate: design and test harness

How the parking gate is built and how its Cucumber-JVM scenarios drive it. The requirements
(`docs/spec/REQUIREMENTS.md`, AIR-016 to AIR-041) and the scenarios (`specs/features/09` to `12`) are the
source of truth; this document is the contract between the step definitions and the gate code, so both can
be written and reviewed separately. Decisions: OD-66 (plain-Java gate), OD-131 to OD-135. Reviewed by a
fresh-context critic on 2026-11-15; its findings are folded in.

## Constraints

- **Plain Java 7** (`--release 7`, OD-132): no lambdas, streams or `java.time`. Try-with-resources is fine
  (API 19 has `AutoCloseable`). Build with JDK 17: JDK 20 and later can't target Java 7. No Android imports
  anywhere in `receiver/gate/src/main`. Everything the gate needs from Android or the head unit comes
  through a port (below). The Android app (`receiver/app/`, a later slice) supplies the real ports; the
  tests supply doubles.
- **One thread.** Every gate method runs on one event thread (on the unit, the main looper). Ports that
  block on the unit are called through the port and answer through callbacks on that thread. The gate
  never sleeps, never spawns threads and never reads the system clock.

## Proof by construction: what the gate never touches

Scenarios that say audio, the iPhone connection, the HUD, vehicle commands or stock restrictions are never
touched (AT-21, AT-22, AT-24, AT-38, AT-40, AT-41; 09 around l.1040-1072) are proven two ways, and each such
Then step names which it uses:

1. **Recording doubles that the gate is given.** `Receiver` receives a `MirrorSession` (audio plus the
   iPhone connection) and a `VehicleBus` (any vehicle or HUD interface the receiver can reach). Their doubles
   keep synthetic, uninterrupted audio and connection traces and record every call; the steps assert zero
   calls and unbroken traces.
2. **A static scan** (`@offline`) of the gate's sources and compiled classes for forbidden types and
   methods: `AudioManager` and audio-focus or session APIs, HUD display ids, vehicle setters, CAN access,
   stock regulation setters, `registerSystemOffResponder`, micom power transaction 14, `REVERSE_OFF`,
   `isRearViewOn`.

## Ports (package `parkcast.gate.port`)

| Port | Methods (sketch) | Unit implementation (later) | Test double |
|---|---|---|---|
| `Scheduler` | `long now()`; `Task at(long ms, Runnable r)`; `Task.cancel()` | `Handler` + `SystemClock.uptimeMillis()` | `VirtualScheduler` (below) |
| `ParkGetter` | `void call(ParkReply cb)`; `ParkReply.value(int)`, `.failed(String why)` | worker thread calling `VehicleInfoManager.getGearPStatus()`, posting back | scripted reply value or failure at a time after the call, or never; service missing or lost |
| `AccSource` | `void register(AccListener l)`; `void unregister()`; `AccListener.registered(AccStatus sticky)` (null = no sticky state), `.registrationFailed(String cause)`, `.changed(AccStatus s)` | `registerReceiver` called on a worker thread with a main-looper `Handler`, result posted back, so a pending registration is possible | scripted outcome and return time; broadcasts at times; records register and unregister calls |
| `ReverseSource` | `boolean register(ReverseListener l)` (true = an intent was returned; throws on failure); `void unregister()`; `ReverseListener.reverseOn()` | runtime `registerReceiver` for `REVERSE_ON` only, called in `onCreate` | behaves like an IntentFilter for `REVERSE_ON`: `REVERSE_OFF` and extras are delivered to it and dropped before the gate; records calls and their order against lifecycle calls |
| `NoticeWindows` | `NoticeView create(String text)`; `int measure(NoticeView v, int displayWidth)`; `Placement placementFrom(NoticeView regFailToast)` (may throw); `Win add(NoticeView v, WindowSpec spec)`; `void remove(Win w)` | `Toast.makeText(...).getView()`, `WindowManager.addView/removeView` with `TYPE_TOAST` | records every call with the time it returned and the full spec (`type`, `flags`, `gravity`, `x`, `y`, text, `fromToastFactory`); throws on create, measure, placement, add or remove when told; the port has no `updateViewLayout`, `Toast.show()`, dialog, notification or activity method, so those are proven absent by the static scan |
| `LogFiles` | `long size(String name)`; `void append(String name, byte[] b)`; `void rotate()`; `void deleteOlder()`; each may throw | files in `/data/nemesis/logs/airplay-receiver/` | in-memory files with sizes; failure per operation and time; survives a restart |
| `ConfigFile` | `byte[] read()` throwing `Missing` or `Unreadable` | `config/receiver.conf` under the install folder | the scenario's contents or fault; records reads; survives a restart |
| `BuildInfo` | `String product()`: null when the property is missing, throws `Unreadable` | `ro.build.product` | scenario value or fault |
| `RegulationSource` | `void register(RegulationListener l)`; `void unregister()`; `RegulationListener.state(String s)`, `.unavailable(String reason)` | stock regulation service | scripted states and losses; records calls |
| `Display` | `void show(Frame f)`; `void clear()` | the receiver's video surface | records the times of `show` and `clear` |
| `MirrorSession`, `VehicleBus` | whatever the app exposes; the gate holds them only to pass nothing to them | the AirPlay session; vehicle and HUD interfaces | record every call; must stay at zero |

`AccStatus` carries an `int` value or one of two markers, `MISSING` and `NON_INTEGER`. The mapping to awake
or asleep (AIR-019(f)) lives in the gate.

`ReverseSource.register` is synchronous on purpose: AIR-041(j) needs it to return before the window can
first gain focus, which only holds if it runs and returns inside `onCreate`.

## Frames and the display

A `FrameSource` (harness only) delivers decoded frames to `Receiver.onFrame(Frame)` at a cadence set per
scenario (default one every 16 ms from 0 ms), and can be set to `moving` or `frozen` (a frozen source
delivers no new frames). The gate decides per frame: permitted frames go to `Display.show`; when video
becomes blocked the gate calls `Display.clear()` at that moment, so a frozen image is removed too.
"Shown from the next frame after T" means the first `FrameSource` tick after T reached `Display.show`.
"The image is gone" means the time of `Display.clear()`. "Frames are queued for display" means ticks
delivered while blocked never reach `show`. The AT-18 timing check (09 about l.910) is test-only code,
checked only by its own Examples table.

## Gate components (package `parkcast.gate`)

| Class | Responsibility | Requirements |
|---|---|---|
| `ReceiverConfig` | Strict parse of `receiver.conf`: the effective flag and the fallback cause | AIR-023, AIR-025 |
| `AccState` | ACC mapping, registration pending, failed or ok, sleep entry and resume, ignored broadcasts | AIR-019(f), (h), AIR-021(b) |
| `ParkPoller` | The 200 ms schedule, one call at a time, the timeout (exactly 200 ms is late), the next call at once after a late reply, receipt age, the resume re-read, discarded replies | AIR-017, AIR-018, AIR-019(a) to (e), (g) |
| `ParkingGate` | The decision per frame from the flag, build, Park reading and sleep state; `Display.clear()` on block; gate transitions | AIR-016, AIR-020 to AIR-022, AIR-024, AIR-026, AIR-037 |
| `RegulationLog` | Logs the stock regulation state at start, on each change and when unavailable; no link to `ParkingGate` | AIR-038 |
| `GateLog` | Records with the literal tokens; rotation before a write that would exceed 2,621,440 bytes and right after a write that reaches it exactly; the 5,242,880-byte cap; oversize records; write failures reported to `Notices` | AIR-025, AIR-026, AIR-039, AIR-040(a) to (d) |
| `Notices` | The two notices: allowances, first attempt, 5 s display, one retry, stacking, removal retries, exit | AIR-019(i), AIR-040(b) to (i) |
| `Cover` | Covered state from focus, `REVERSE_ON` and its registration; holds and releases notice attempts | AIR-041 |
| `Receiver` | Wires everything. Entry points: `onCreate` (registers `REVERSE_ON`), `onStart`, `onResume` (registers ACC), `onWindowFocusChanged(boolean)`, `onFrame(Frame)`, `exit()` (synchronous) | all |

Each class takes its ports and collaborators in its constructor. No static state, so every scenario and
every restart builds a fresh `Receiver`.

## Test harness (`receiver/gate/src/testFixtures/java/parkcast/gate`; moved there from `src/test` under OD-189)

- **Doubles** (package `...gate.doubles`): one per port, each recording every call as
  `(time, cause, name, details)` in one shared, ordered `Trace`. `cause` is the innermost callback being
  dispatched (for example `focus(false)@2000` or `reverseOn@500`); doubles push and pop it around every
  delivery, so "removed inside the focus callback" (AT-41) is checkable.
- **`VirtualScheduler`** runs events in time order. Every scripted external event has a tie rank,
  `before` or `after` the gate tasks due at the same time (the AT-19 reply-first and timeout-first cases
  at exactly 200 ms; 09's "the shift is processed first" at 400 ms). Fixed rules for the gate's own ties:
  exit runs before any removal or retry due at the same time; a focus loss or `REVERSE_ON` runs before a
  due attempt or retry; a focus gain runs before the held attempts it releases.
- **`World`** (shared through `cucumber-picocontainer`): doubles, scheduler, frame source, settings and
  the current `Receiver`. Given steps set up doubles and queue scripted events. `When the receiver starts`
  builds the `Receiver` and runs the lifecycle at 0 ms: `onCreate` (REVERSE_ON registration returns), then
  `onStart`, `onResume` (ACC registration starts), and the first focus gain, unless the scenario says
  otherwise; a "no activity window" option skips focus entirely (covered for the whole run). `runs for N
  ms` advances the clock to N inclusive. `the receiver restarts at T` calls `exit()` on the old `Receiver`,
  cancels its tasks, builds a new one, keeps `LogFiles` and `ConfigFile` and resets the other doubles.
  "Exit completes" means `exit()` returned and the trace holds `exit done`.
- **Step classes** follow the feature files: `GateSteps` (09), `ConfigSteps` (10), `LogSteps`,
  `NoticeSteps` and `CoverSteps` (11), `UnitEvidenceSteps` (12, last); shared steps in `CommonSteps`. Each
  step's expression matches the Gherkin text as written. Steps never read gate fields. State words map to
  observables: "the parking check is on/off" to the start record's tokens; "counts as asleep" to the
  getter schedule and blocked video; "counts as covered" to held attempts and `notice cover` records.
- **Static scans** read the source tree and the compiled classes from the Gradle build directories, plus
  the app's source `AndroidManifest.xml`. That manifest is created, minimal, in
  `receiver/app/src/main/AndroidManifest.xml` when work reaches 11 (no permissions, no manifest receiver
  for `REVERSE_ON`), so 11 can go green before the app slice.

## Verification per feature file (OD-135)

1. Step definitions written; `./gradlew test` shows every scenario of that file failing on a missing gate
   class or method or a failed assertion, never on an undefined step or a harness error.
2. Gate code written until that file's scenarios pass, with the earlier files still green.
3. Mutation check on the new gate code: each deliberate fault (for example "exactly 200 ms counts as on
   time", "a pre-sleep reply refreshes age") is caught by at least one scenario.
4. `pytest` and `./gradlew test` both green, traceability regenerated, commit, privacy scan, publish.
