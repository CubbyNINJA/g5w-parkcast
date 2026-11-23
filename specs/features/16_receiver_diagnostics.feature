@S3
Feature: The diagnostic raw log: build switch, native raw file, counters and the parkcast-diag stick tool
  The first on-unit run connected but no video reached the app, and every UxPlay line was withheld
  (docs/evidence/S3_FIRST_RUN_FINDINGS.md). OD-198 adds a build-time setting that writes UxPlay's own
  log, redacted for keys, to a raw file in the app's data folder, with counters, and has the
  parkcast-diag stick tool copy it (AIR-084 to AIR-089; OD-198 to OD-203). The receiver's normal log
  and its filter are unchanged.

  The raw file's logic is native C. `@host` scenarios compile the raw-file module, the native log
  path and the ring for this PC with MSYS2's gcc from the same sources the APK uses, and drive them
  through a test harness with a fake clock and a fake file layer that records each open's mode and
  can fail on request. `@offline` scenarios use synthetic fixtures and fake toolbox commands only.
  No fixture holds a real iPhone name, IP address, MAC address or key.

  A raw record line is `<uptime ms> L<level> <text>` (AIR-085(b)). A counter line is
  `<uptime ms> counters gen=<g> au_in=<n> csd_sent=<n> au_ring_drops=<n> log_ring_drops=<n>
  control_drops=<n> raw_written=<n> raw_dropped=<n>` (AIR-086(b)).

  # --- AIR-084: the build switch and the keep check

  @host @failure @AIR-084 @AT-84 @OD-199
  Scenario: A property value other than true or false fails the build and names the value
    When the app's Gradle build is configured with "-Pparkcast.rawDiagnostics=maybe"
    Then the build fails
    And its error output names the value "maybe"

  @host @normal @AIR-084 @AT-84 @OD-199
  Scenario: Only a build with the property true is marked, and switching it rebuilds the native library
    Given the release APK is built with the property absent, then with "true", then with "false", with no clean in between
    Then the APK built with "true" has a version name ending in "-rawdiag"
    And its "libparkcast.so" holds the string "parkcast raw diagnostics on"
    And the APKs built with the property absent and with "false" have neither the suffix nor the string

  @offline @failure @AIR-084 @AT-84 @OD-199
  Scenario Outline: The keep check refuses a raw-diagnostics build and names why
    Given a fixture APK whose version name is "<version>" and whose library <library>
    When the keep check runs on it
    Then the keep check fails
    And its report names "<reason>"

    Examples:
      | version        | library                                         | reason                       |
      | 0.3.0-rawdiag  | holds the string "parkcast raw diagnostics on"  | version name                 |
      | 0.3.0          | holds the string "parkcast raw diagnostics on"  | native library               |
      | 0.3.0-rawdiag  | does not hold the string                        | version name and library disagree |

  @offline @normal @AIR-084 @AT-84 @OD-199
  Scenario: The keep check passes a build without raw diagnostics
    Given a fixture APK whose version name is "0.3.0" and whose library does not hold the string
    When the keep check runs on it
    Then the keep check passes

  # --- AIR-085: the raw file

  @host @normal @AIR-085 @AT-85 @OD-203
  Scenario: A record is one line, with control bytes escaped
    Given the raw-file module is opened at uptime 1000 ms
    When a record at level 7 arrives at 1234 ms with the text "SETUP 1" followed by CR, LF, a backslash and the byte 0x01
    Then the raw file holds the line "1234 L7 SETUP 1\r\n\\\x01"
    And that record ends with one LF and adds exactly one line

  @host @failure @AIR-085 @AT-85 @OD-200
  Scenario Outline: Key material in a record is redacted in place
    Given the raw-file module is opened with the fake key file path "/data/data/parkcast.receiver/files/fake.key"
    When a record arrives with the text "before <material> after"
    Then the raw file's line for it ends with "before [redacted] after"
    And no part of "<material>" is in the raw file

    Examples:
      | material                                                        |
      | 0123456789abcdef0123456789abcdef                                |
      | QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZn                    |
      | 0a 1b 2c 3d 4e 5f 6a 7b                                         |
      | /data/data/parkcast.receiver/files/fake.key                     |
      | fake.key                                                        |

  @host @failure @AIR-085 @AT-85 @OD-200
  Scenario: A byte dump split over two lines is redacted whole
    Given the raw-file module is opened
    When a record arrives with the text "72 byte ekey:" then LF, then "0a 1b 2c 3d" then LF, then "4e 5f 6a 7b " then LF
    Then the raw file's line for it is "<uptime> L7 72 byte ekey:\n[redacted]" with nothing after the redaction but escaped line breaks
    And none of the eight hex bytes is in the raw file

  @host @failure @AIR-085 @AT-85 @OD-200
  Scenario: The quoted PIN value is redacted
    Given the raw-file module is opened
    When a record arrives with the text "*** CLIENT MUST NOW ENTER PIN = \"1234\" AS AIRPLAY PASSWORD"
    Then the raw file's line for it ends with "PIN = [redacted] AS AIRPLAY PASSWORD"
    And "1234" is not in the raw file

  @host @boundary @AIR-085 @AT-85 @OD-200
  Scenario Outline: Material just under each threshold is written unchanged
    Given the raw-file module is opened
    When a record arrives with the text "before <material> after"
    Then the raw file's line for it ends with "before <material> after"

    Examples:
      | material                                    |
      | 0123456789abcdef0123456789abcde             |
      | QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZ |
      | 0a 1b 2c 3d 4e 5f 6a                        |

  @host @normal @AIR-085 @AT-85 @OD-203
  Scenario: The raw file is created owner-only next to the key file and truncated at each app start
    Given the raw-file module is opened with the fake key file path "/fake/files/fake.key"
    Then the fake file layer recorded one open of "/fake/files/uxplay-raw.log" with mode 0600 and truncation
    When a record arrives with the text "first start"
    And the raw-file module is closed and opened again
    Then the raw file does not hold "first start"

  @host @failure @AIR-085 @AT-85 @OD-199
  Scenario: With the setting off, the raw file is never created or touched
    Given the native log path is compiled with raw diagnostics off
    And a file "uxplay-raw.log" already exists in the fake key file's folder with known bytes
    When the KEYCHECK stage's raw-file step runs and a record arrives
    Then the fake file layer recorded no open of "uxplay-raw.log"
    And the existing file's bytes are unchanged

  @host @normal @AIR-072 @AIR-085 @AT-72 @AT-85 @OD-198
  Scenario: A full ring can't hide a record from the raw file
    Given the native log path and the ring are compiled with raw diagnostics on
    And the ring's general slots are all taken and its delivery is stopped
    When UxPlay's log callback is called with the text "Mirroring not initialized at SETUP, playing will fail!"
    Then the raw file holds a line ending with "Mirroring not initialized at SETUP, playing will fail!"
    And the ring's log drop count has risen by one

  @host @normal @AIR-055 @AIR-073 @AT-55 @AT-73 @OD-198
  Scenario: The raw write doesn't change what the ring passes to the filter
    Given the native log path and the ring are compiled with raw diagnostics on
    When UxPlay's log callback is called with each of these texts
      | text                                          |
      | Mirroring initialized successfully            |
      | CSeq: 3                                       |
      | 16 byte aesiv (needed for AES-CBC audio decryption iv): |
      | 0a 1b 2c 3d 4e 5f 6a 7b                        |
    Then the ring holds one log event per text, each with the text exactly as given, unredacted
    And the events are the same as the native log path pushes with raw diagnostics off

  # --- AIR-086: counters

  @host @normal @AIR-086 @AT-86 @OD-201 @OD-203
  Scenario: Counter lines come at open, about once a second, at session end and at stop
    Given the raw-file module is opened at uptime 1000 ms
    Then the raw file's first line is a counter line at 1000 ms
    When records arrive at 1400, 1800, 2200 and 2600 ms
    Then a counter line at 2200 ms comes before the record at 2200 ms
    And no other counter line was written between 1000 and 2600 ms
    When the session ends at 2700 ms
    And stop's last phase runs at 2800 ms
    Then counter lines at 2700 ms and 2800 ms are written

  @host @normal @AIR-086 @AT-86 @OD-201
  Scenario: The counts match the calls, before any check, and count only configurations the ring took
    Given the native log path and the ring are compiled with raw diagnostics on
    When "video_process" is called 3 times with a non-empty access unit and no codec data has been seen
    And the ring's general slots are all taken
    And a codec configuration is pushed
    And the ring is emptied
    And a codec configuration is pushed
    Then the last counter line has "au_in=3" and "csd_sent=1"

  @host @boundary @AIR-086 @AT-86 @OD-201
  Scenario: Counts carry over a session end and start again only at app start
    Given the native log path is compiled with raw diagnostics on
    And "video_process" was called 2 times with a non-empty access unit in session 1
    When session 1 ends and session 2 starts
    And "video_process" is called once with a non-empty access unit
    Then the last counter line has "au_in=3"
    When the raw-file module is closed and opened again
    Then the first counter line has "au_in=0"

  # --- AIR-087: the cap

  @host @boundary @AIR-087 @AT-87 @OD-201
  Scenario: At the cap the file keeps its start and ends with one "raw log full" line
    Given the raw-file module is opened
    And its first record is "first record"
    When records are written until the file is exactly 16,776,704 bytes
    And one more record arrives
    Then the raw file's last line starts with the uptime and "raw log full" and carries the counter fields
    And the raw file is no larger than 16,777,216 bytes
    And its first record line still ends with "first record"
    When another record arrives
    Then the raw file is unchanged

  @host @boundary @AIR-087 @AT-87 @OD-203
  Scenario: A record of 65,536 escaped bytes is written and one byte more is dropped
    Given the raw-file module is opened
    When a record arrives whose escaped text is 65,536 bytes long
    And a record arrives whose escaped text is 65,537 bytes long
    Then the raw file holds the first record and no part of the second
    And the next counter line has "raw_dropped=1"

  # --- AIR-088: failures

  @host @failure @AIR-088 @AT-88 @OD-201
  Scenario Outline: A raw-file failure stops raw writes for the rest of the app start
    Given the fake file layer fails with <failure>
    And the raw-file module is opened
    When three records arrive
    Then the fake file layer recorded no write after the failure
    And no other file was opened

    Examples:
      | failure                        |
      | an open failure                |
      | a failed first write           |
      | a short first write            |

  @host @normal @AIR-088 @AT-88 @OD-203
  Scenario: While raw writes fail, the native log path still pushes every record and reports nothing
    Given the native log path and the ring are compiled with raw diagnostics on
    And the fake file layer fails every write
    When UxPlay's log callback is called with 3 records
    Then each call returns without blocking
    And the ring holds the same 3 log events it would hold with raw writes working
    And no other event was pushed

  # --- AIR-089: the parkcast-diag stick tool

  @offline @normal @AIR-089 @AT-89 @OD-202 @OD-203
  Scenario: The tool copies the raw file and both logs into a new timestamped folder and changes nothing
    Given a fake unit tree with ParkCast running, "uxplay-raw.log" and a key file in its app data, and "receiver.log" and "receiver.log.1" in its log folder
    When the parkcast-diag script runs from the fake stick
    Then a new folder "NEMESIS-diag/parkcast-diag/<timestamp>/" holds copies of "uxplay-raw.log", "receiver.log" and "receiver.log.1"
    And every source file's hash and mode are unchanged
    And no file the script wrote lists app-data file names or holds the key file's name
    And no file on the fake stick ever held unfiltered logcat
    And the script exits 0

  @offline @normal @AIR-089 @AT-89 @OD-202
  Scenario Outline: The tool starts ParkCast only when it isn't running
    Given a fake unit tree with ParkCast <state>
    When the parkcast-diag script runs from the fake stick
    Then the fake "am" recorded <starts>

    Examples:
      | state       | starts                                                   |
      | not running | exactly one "start -n parkcast.receiver/.ReceiverActivity" |
      | running     | no "start" call                                          |

  @offline @failure @AIR-089 @AT-89
  Scenario: A missing raw file is reported by name
    Given a fake unit tree with ParkCast running and no "uxplay-raw.log" in its app data
    When the parkcast-diag script runs from the fake stick
    Then its log says "no uxplay-raw.log"
    And "receiver.log" is still copied

  @offline @failure @AIR-089 @AT-89
  Scenario: A failing copy is reported, the other copies still run, and the script exits non-zero
    Given a fake unit tree with ParkCast running and all three source files
    And the fake "cp" fails for "uxplay-raw.log"
    When the parkcast-diag script runs from the fake stick
    Then its log reports the failed copy of "uxplay-raw.log"
    And "receiver.log" and "receiver.log.1" are copied
    And the script exits non-zero

  @offline @failure @AIR-089 @AT-89
  Scenario: A symlink at the raw file's path is not followed
    Given a fake unit tree where "uxplay-raw.log" in ParkCast's app data is a symlink to the key file
    When the parkcast-diag script runs from the fake stick
    Then the stick holds no copy of the key file's bytes
    And its log reports that the raw file's path is a symlink
    And the script exits non-zero

  @offline @failure @AIR-089 @AT-89
  Scenario: Another firmware build is refused with nothing read
    Given a fake unit tree whose build is not "eng.vmadmin.20260206.060005"
    When the parkcast-diag script runs from the fake stick
    Then its log says the guard failed
    And no file was copied and no "am" call was made
    And the script exits non-zero

  @offline @normal @AIR-083 @AIR-089 @AT-83 @AT-89
  Scenario: The tool never copies or names the key file
    Given a fake unit tree with ParkCast running and a key file "fake.key" in its app data
    When the parkcast-diag script runs from the fake stick
    Then no file on the fake stick has the key file's bytes
    And no file on the fake stick holds the text "fake.key"

  # --- On the unit (evidence from the stick; never run from the PC suite)

  @unit @normal @AIR-085 @AIR-086 @AT-85 @AT-86 @OD-198
  Scenario: The copied raw file holds UxPlay's records and counter lines from the session
    Given the stick copy from a parkcast-diag run with a raw-diagnostics build, during which the iPhone cast
    Then "uxplay-raw.log" holds records from UxPlay during the session and counter lines
    And the owner finds no unredacted run of 32 or more hex digits, of 44 or more base64 characters, or of 8 spaced hex bytes in it

  @unit @normal @AIR-062 @AIR-089 @AT-62 @AT-89
  Scenario: The raw file stays in app data and the tool's copy lands in a timestamped folder
    Given the stick after a parkcast-diag run
    Then its copy is in "NEMESIS-diag/parkcast-diag/<timestamp>/"
    And the root "NEMESIS.data" is gone
    And the copied receiver log folder holds no "uxplay-raw.log"

  @unit @normal @AIR-072 @AT-72
  Scenario: UxPlay's setup lines count only when they are in the receiver's log
    Given the stick copy from a parkcast-diag run with a raw-diagnostics build
    When "Mirroring initialized successfully" is in "uxplay-raw.log" but not in "receiver.log"
    Then AT-72's unit check is not met
