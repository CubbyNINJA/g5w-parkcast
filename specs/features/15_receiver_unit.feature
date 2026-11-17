@S3 @unit
Feature: The AirPlay receiver on the owner's head unit (video only)
  These scenarios need the receiver package installed on the owner's unit (260206 CA). They never
  run from the PC suite: their evidence is the receiver's stick log, read-only stick probes, the
  owner's note and the first-run checklist, all saved to the stick. Offline, host and PC results
  never stand in for them (AT-48 to AT-83). Runs are made parked, with CarPlay disconnected and
  the iPhone joined to the unit's hotspot.

  A scenario whose Given the unit never reached is reported as unexercised, not as passed. Stick
  evidence follows specs/README.md: no raw runs are committed, no phone screen content is
  recorded and no iPhone name is recorded. Only the verdict and a redacted summary go to
  docs/evidence/.

  @normal @AIR-048 @AT-48 @OD-169
  Scenario: The receiver listens on its AirPlay ports at the hotspot address
    Given the receiver package is installed unmodified on the owner's unit
    When the receiver starts
    Then the stick log records listening on TCP 35000-35001 and UDP 35000-35002 at 192.168.0.1

  @normal @AIR-049 @AT-49 @OD-169
  Scenario: During a session the receiver is bound only to the hotspot address
    Given the receiver is running a session on the owner's unit
    When the owner runs the read-only listener probe from the stick
    Then the probe output shows the receiver's uid bound only to 192.168.0.1 on TCP 35000-35001 and UDP 35000-35002
    And the probe output shows no entry for the receiver in /proc/net/tcp6 or /proc/net/udp6

  @normal @AIR-050 @AIR-069 @AT-50 @OD-148 @OD-164
  Scenario: The receiver advertises itself as ParkCast, or logs why it could not
    Given the receiver package is installed unmodified on the owner's unit
    When the receiver starts with the iPhone on the hotspot
    Then the stick log shows either "mdnsd up" and "advertise ok", or "advertise failed" with its reason
    And a read-only probe output records the owner, group and mode of /dev/socket/mdnsd
    And the owner's note says the iPhone lists "ParkCast"

  @normal @AIR-051 @AT-51
  Scenario: A portrait picture is letterboxed, not stretched or cropped
    Given the receiver is mirroring a portrait iPhone screen parked
    Then the stick log records the picture geometry
    And the owner's note says the portrait picture was letterboxed, not stretched or cropped

  @normal @AIR-054 @AT-54 @OD-139 @OD-153
  Scenario: The first milestone plays no sound, and the owner's sound note changes no verdict
    Given the receiver has run a session parked
    Then the stick log has no audio-output record
    And the owner's note on sound is recorded
    And the note on sound does not change any verdict

  @normal @AIR-056 @AT-56
  Scenario: The unit's hardware H.264 decoder is configured
    Given the receiver has started a mirroring session
    Then the stick log shows "decoder OMX.TCC.avcdec configured"

  @normal @AIR-057 @AT-57 @AT-41 @OD-130 @OD-140
  Scenario: The movement check is AT-41's on-unit block, run in the first on-unit run
    Given the first on-unit S3 run is made parked with the parking check on from day one
    Then the movement part of AT-57 is evidenced by AT-41's @unit block from that run
    And S3 adds no driving test of its own

  @normal @AIR-058 @AT-58
  Scenario: Parked, the start record shows the Park getter reading 1
    Given the car is parked
    When the receiver starts
    Then the stick log's start record shows a getter reading of 1

  @normal @AIR-059 @AT-59
  Scenario: The ACC state is logged at start
    When the receiver starts on the owner's unit
    Then the stick log records the ACC state at start

  @normal @AIR-062 @AT-62 @OD-156
  Scenario: The log directory belongs to the receiver alone and no log file grows past its cap
    Given the receiver package has been installed
    When the owner runs a read-only "ls -ld" probe of /data/nemesis/logs/airplay-receiver/
    Then the probe output shows the directory owned by the app's uid with mode drwx------
    And after a run, no log file in that directory is larger than 2,621,440 bytes

  @normal @AIR-063 @AT-63
  Scenario: The start record names the missing file that turned the parking check on
    Given the receiver starts with its configuration file missing
    Then the stick log's start record shows the parking check as true with reason=the missing file

  @normal @AIR-064 @AT-64
  Scenario: The start record names the build product
    When the receiver starts on the owner's unit
    Then the stick log's start record shows the unit's ro.build.product

  @normal @AIR-066 @AT-66
  Scenario: The start record shows the regulation state or why it is unavailable
    When the receiver starts on the owner's unit
    Then the stick log's start record shows the regulation state, or "unavailable" with its reason

  @normal @AIR-068 @AT-68
  Scenario: A session changes nothing under /data/nemesis outside the log directory
    Given the owner ran a read-only tree probe of /data/nemesis from the stick before a session
    When the session ends and the owner runs the same probe again
    Then the two probe outputs list the same names and sizes outside /data/nemesis/logs/airplay-receiver/

  @failure @AIR-069 @AT-69
  Scenario: After a crash the receiver stays down until the owner relaunches it
    Given the receiver crashed during a run
    When the owner runs a read-only "ps" probe from the stick before relaunching it
    Then the probe output shows no receiver process
    And a crash that did not happen in the run is listed as unexercised

  @normal @AIR-070 @AT-70
  Scenario: A finished run ends with the end marker and releases its ports
    Given the receiver has run a session
    When the receiver stops
    Then the stick log's last line is the end marker
    And a post-run probe output shows nothing bound on TCP 35000-35001 or UDP 35000-35002

  @normal @AIR-071 @AT-71
  Scenario: The copied log holds the start marker, the session markers and the end marker
    Given the owner has copied the receiver's log to the stick
    Then the copied log contains the start marker with the app version, git commit, UxPlay commit, ro.build.product and the effective parking flag
    And it contains the session markers that occurred
    And it contains the end marker with its stop reason

  @normal @AIR-072 @AT-72
  Scenario: The copied log keeps UxPlay's mirroring line
    Given the owner has copied the receiver's log to the stick after a mirroring attempt
    Then the copied log contains "Mirroring initialized successfully" or "Mirroring not initialized at SETUP"

  @normal @AIR-073 @AT-73 @OD-155
  Scenario: The copied log carries no addresses, names or session secrets
    Given the owner has copied the receiver's log to the PC
    When the owner scans it with the iPhone's name entered locally
    Then the scan finds no IPv4, IPv6 or MAC address patterns
    And it finds no iPhone name
    And it finds none of "User-Agent", "aesiv", "ekey", "aeskey", "ecdh" or "plist"
    And the iPhone's name is never written to the repository

  @normal @AIR-074 @AT-74
  Scenario: The collect script copies the logs to the stick and leaves the originals on the unit
    Given the receiver has run on the owner's unit
    When the owner runs the second stick script and taps Yes
    Then the stick holds the copy under NEMESIS-diag/airplay-receiver/<timestamp>/
    And the originals remain on the unit

  @normal @AIR-076 @AT-76 @OD-154 @OD-162
  Scenario: The first real run gets a verdict backed by its log lines and the owner's note
    Given the first real on-unit run has been collected to the stick
    Then the run has a verdict
    And the verdict references its supporting log lines
    And it references owner-note.txt in the run's <timestamp>/ folder

  @normal @AIR-078 @AIR-062 @AT-78 @OD-156 @OD-160
  Scenario: The stick script installs the receiver, and uninstall removes all but the log directory
    Given the owner runs the install stick script and taps Yes
    Then "nemesis pkg-list" shows airplay.receiver
    And "pm list packages" shows the build
    And the receiver starts
    When the owner runs "nemesis uninstall airplay.receiver"
    Then neither list shows the receiver
    And /data/nemesis/apps/airplay.receiver/ and the app-data directory are gone
    And only the log directory remains

  @failure @AIR-078 @AT-78
  Scenario: A failed install leaves its output on the stick and starts nothing
    Given the owner runs the install stick script and taps Yes
    When the install fails
    Then the install route's output is on the stick
    And the run stops with no receiver started
    And this case is listed as unexercised if the install did not fail

  @normal @AIR-079 @AT-79 @OD-151 @OD-166 @OD-173
  Scenario: CarPlay's absence is logged, and CarPlay after exit is checked in the first run
    Given the receiver runs with CarPlay disconnected
    Then the stick log shows "carplay listener absent"
    And after the first on-unit run the checklist records "carplay after exit: yes" or "carplay after exit: no"

  @failure @AIR-079 @AT-79 @OD-173
  Scenario: A CarPlay-after-exit failure or missing record holds further on-unit runs
    Given the first on-unit run has finished
    When the checklist records "carplay after exit: no", or has no such record
    Then the finding is logged
    And no further on-unit S3 run starts before the finding is closed

  @normal @AIR-080 @AT-80
  Scenario: The receiver stays behind another app opened during a session
    Given the receiver is running a session
    When the owner opens another app
    Then the owner's note says the receiver stayed behind that app

  @normal @AIR-081 @AT-81
  Scenario: Nothing from the receiver appears on the cluster or the HUD
    Given the receiver is mirroring parked
    Then the owner's note says nothing appeared on the cluster or the HUD

  @normal @AIR-082 @AT-82
  Scenario: The receiver starts only from the stick execute script
    Given the receiver package is installed on the owner's unit
    Then the receiver starts only by the install route's stick execute script

  @normal @AIR-083 @AIR-073 @AIR-074 @AT-83 @OD-170
  Scenario: The pairing key stays in app data and never reaches the log or the stick
    Given the receiver has run and its logs have been copied to the stick
    Then neither the copied log nor the stick has the key file's name
    And the stick has no file with the key file's bytes
    And after uninstall the app-data directory is gone

  @failure @AIR-069 @AIR-078 @AT-69 @AT-78
  Scenario: Cases the unit never gave are listed as unexercised
    Given the on-unit S3 runs are finished
    Then a crash that did not happen is listed as unexercised
    And an install that did not fail is listed as unexercised
    And any other Given the unit did not reach is listed as unexercised, not as passed

  @normal @AIR-071 @AIR-073 @AT-71 @AT-73
  Scenario: Receiver evidence is kept on the stick without raw runs or phone content
    Given an on-unit S3 run has been collected to the stick
    Then its evidence is kept on the stick and not committed as raw runs
    And it contains no phone screen content and no iPhone name
    And only its verdict and a redacted summary go to docs/evidence/
