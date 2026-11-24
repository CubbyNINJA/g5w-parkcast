@S3
Feature: The diagnostic raw log: build switch, native raw file, counters and the parkcast-diag stick tool
  The first on-unit run connected but no video reached the app, and every UxPlay line was withheld
  (docs/evidence/S3_FIRST_RUN_FINDINGS.md). OD-198 adds a build-time setting that writes UxPlay's own
  log, redacted for keys, to a raw file in the app's data folder, with counters, and has the
  parkcast-diag stick tool copy it (AIR-084 to AIR-089; OD-198 to OD-204). The receiver's normal log
  and its filter are unchanged.

  The raw file's logic is native C. `@host` scenarios compile the raw-file module, the native log
  path and the ring for this PC with MSYS2's gcc from the same sources the APK uses. The harness
  supplies a fake clock and a fake file layer that records every open (path, mode, flags), write
  and close with the calling thread, and can fail an open or a write, or write short, on request.
  The ring's push is wrapped at link time so every push is recorded with its thread, and
  `pthread_mutex_lock` and `pthread_mutex_unlock` are wrapped too, so the harness knows which mutexes
  each thread holds at every raw-file operation. `@offline` scenarios use synthetic fixtures and fake
  toolbox commands that journal every command, read, copy and change. No fixture holds a real
  iPhone name, IP address, MAC address or key.

  A raw record line is `<uptime ms> L<level> <text>` (AIR-085(b)). A counter line is
  `<uptime ms> counters gen=<g> au_in=<n> csd_sent=<n> au_ring_drops=<n> log_ring_drops=<n>
  control_drops=<n> raw_written=<n> raw_dropped=<n>` (AIR-086(b)). In expected lines below, `\n`,
  `\r` and `\\` are the two-character escapes the raw file holds, not control bytes.

  # --- AIR-084: the build switch and the keep check

  @host @failure @AIR-084 @AT-84 @OD-199
  Scenario: A property value other than true or false fails the build and names the value
    When "./gradlew :receiver:app:help -Pparkcast.rawDiagnostics=maybe" runs
    Then it fails
    And its error output names the value "maybe"

  @host @normal @AIR-084 @AT-84 @OD-199
  Scenario: Only a build with the property true is marked, and switching it rebuilds the native library
    Given the release APK is built with the property absent, then with "true", then with "false", with no clean in between
    Then the APK built with "true" has a version name ending in "-rawdiag"
    And its "libparkcast.so" holds the string "parkcast raw diagnostics on"
    And the APKs built with the property absent and with "false" have neither the suffix nor the string

  @host @normal @AIR-084 @AT-84 @OD-199
  Scenario: There is no runtime switch
    Given the receiver's native and app sources in the repository
    Then the native sources export only the JNI contract's entry points
    And the app's sources read no setting, property or file that turns raw diagnostics on or off

  @host @failure @AIR-084 @AT-84 @OD-199
  Scenario Outline: The no-runtime-switch check flags a planted switch and names its file
    Given a copy of the receiver's native and app sources with <plant>
    When the no-runtime-switch check runs on the copy
    Then it fails and names "<file>"

    Examples:
      | plant                                                                  | file           |
      | an extra JNIEXPORT function in "parkcast_jni.c"                        | parkcast_jni.c |
      | a RegisterNatives call in "parkcast_jni.c"                             | parkcast_jni.c |
      | a getenv call in "rawdiag.c"                                           | rawdiag.c      |
      | a read of the system property "parkcast.rawDiagnostics" in "ReceiverActivity.java" | ReceiverActivity.java |

  @offline @normal @AIR-084 @AT-84 @OD-199 @OD-204
  Scenario Outline: The keep check reports every reason that applies
    Given a fixture APK whose version name is "<version>" and whose library <library> the string "parkcast raw diagnostics on"
    When "tools/s3.py keep-check" runs on it
    Then it <result>
    And its report names exactly the reasons "<reasons>"

    Examples:
      | version       | library      | result | reasons                       |
      | 0.3.0         | doesn't hold | passes |                               |
      | 0.3.0-rawdiag | holds        | fails  | version name, native library  |
      | 0.3.0-rawdiag | doesn't hold | fails  | version name, disagreement    |
      | 0.3.0         | holds        | fails  | native library, disagreement  |

  # --- AIR-085: the raw file

  @host @normal @AIR-055 @AIR-072 @AIR-085 @AT-55 @AT-72 @AT-85 @OD-198
  Scenario: The callback's own thread writes the raw line before the ring push
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    When UxPlay's log callback is called on a harness thread with level 7 and the text "CSeq: 3"
    Then that thread wrote the raw line "<uptime> L7 CSeq: 3" before it pushed to the ring
    And the write finished before the callback returned
    And the ring received exactly one log event with level 7 and the text "CSeq: 3"

  @host @failure @AIR-072 @AIR-085 @AIR-088 @AT-72 @AT-85 @AT-88 @OD-198
  Scenario: Native code writes the raw record without calling Java, whatever the filter does
    Given the native log path and the ring are compiled with raw diagnostics on
    And the harness's JNI environment records every call made through it
    And the Java-side filter is failing, so nothing is written to the receiver's log
    When UxPlay's log callback is called with level 7 and the text "CSeq: 3"
    Then the raw file holds the line "<uptime> L7 CSeq: 3"
    And no JNI call was made while a raw-file call was running

  @host @normal @AIR-085 @AT-85 @OD-203
  Scenario: A record is one line, with control bytes escaped
    Given the raw-file module is opened at uptime 1000 ms
    When a record at level 7 arrives at 1234 ms with the text "SETUP 1" followed by CR, LF, a backslash and the byte 0x01
    Then the raw file holds the line "1234 L7 SETUP 1\r\n\\\x01"
    And that record adds exactly one line, ending in one LF

  @host @boundary @AIR-085 @AT-85 @OD-203
  Scenario Outline: Bytes outside 0x20-0x7E are escaped as two lowercase hex digits
    Given the raw-file module is opened at uptime 1000 ms
    When a record at level 7 arrives at 1234 ms with the text "a" followed by the byte <byte> and "b"
    Then the raw file holds the line "1234 L7 a<escape>b"

    Examples:
      | byte | escape |
      | 0x1b | \x1b   |
      | 0x7f | \x7f   |
      | 0xfe | \xfe   |
      | 0x7e | ~      |

  @host @failure @AIR-085 @AT-85 @OD-200
  Scenario Outline: Key material in a record is redacted in place
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "before <material> after"
    Then the raw file's line for it is "<uptime> L7 before <written> after"

    Examples:
      | material                                     | written                     |
      | 0123456789abcdef0123456789abcdef             | [redacted]                  |
      | QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZn | [redacted]                  |
      | <data>MDEyMzQ1Njc4OWFiY2RlZg==</data>        | <data>[redacted]</data>     |
      | PIN = "1234"                                 | PIN = [redacted]            |
      | <data>AAAA</data> and <data>BBBB</data>      | <data>[redacted]</data> and <data>[redacted]</data> |
      | 0123456789abcdef0123456789abcdefQUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZn | [redacted] |

  @host @failure @AIR-083 @AIR-085 @AT-83 @AT-85 @OD-200
  Scenario Outline: The identity key file's path and name are redacted
    Given the raw-file module is opened with the fake key file path "/fake/files/fake.key"
    When a record at level 7 arrives with the text "before <material> after"
    Then the raw file's line for it is "<uptime> L7 before [redacted] after"

    Examples:
      | material             |
      | /fake/files/fake.key |
      | fake.key             |

  @host @failure @AIR-085 @AT-85 @OD-200 @OD-204
  Scenario: A byte dump in UxPlay's layout is redacted, keeping the separators after it
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "72 byte ekey:" LF "0a 1b 2c 3d " LF "4e 5f 6a 7b " LF
    Then the raw file's line for it is "<uptime> L7 72 byte ekey:\n[redacted] \n"

  @host @boundary @AIR-085 @AT-85 @OD-200 @OD-204
  Scenario: Two different kinds of material that touch give one marker
    Given the raw-file module is opened with the fake key file path "/fake/files/key.fixture"
    When a record at level 7 arrives with the text "before 0123456789abcdef0123456789abcdefkey.fixture after"
    Then the raw file's line for it is "<uptime> L7 before [redacted] after"

  @host @failure @AIR-085 @AT-85 @OD-200 @OD-204
  Scenario: A <data> value that spans lines is redacted between the tags
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "<key>ekey</key>" LF "<data>" LF "QUJD" LF "REVG" LF "</data>"
    Then the raw file's line for it is "<uptime> L7 <key>ekey</key>\n<data>[redacted]</data>"

  @host @failure @AIR-085 @AT-85 @OD-207
  Scenario Outline: Unterminated key material is redacted through the end of the record
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "<input>"
    Then the raw file's line for it is "<uptime> L7 <output>"

    Examples:
      | input                                         | output                  |
      | before PIN = "1234 and no closing quote after | before PIN = [redacted] |
      | before <data>QUJD with no closing tag after   | before <data>[redacted] |

  @host @failure @AIR-085 @AT-85 @OD-200 @OD-204
  Scenario: A byte dump at the very start of a record is redacted
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "0a 1b 2c 3d 4e 5f 6a 7b after"
    Then the raw file's line for it is "<uptime> L7 [redacted] after"

  @host @boundary @AIR-085 @AT-85 @OD-200
  Scenario Outline: Material just under each threshold is written unchanged
    Given the raw-file module is opened
    When a record at level 7 arrives with the text "before <material> after"
    Then the raw file's line for it is "<uptime> L7 before <material> after"

    Examples:
      | material                                    |
      | 0123456789abcdef0123456789abcde             |
      | QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZ |
      | 0a 1b 2c 3d 4e 5f 6a                        |

  @host @normal @AIR-085 @AT-85 @OD-203
  Scenario: The raw file is opened owner-only next to the key file, truncated, and the key file is never opened
    Given the raw-file module is opened with the fake key file path "/fake/files/fake.key"
    Then the fake file layer recorded exactly one open, of "/fake/files/uxplay-raw.log", with mode 0600 and truncation
    And it recorded no open of "/fake/files/fake.key"

  @host @normal @AIR-085 @AT-85 @OD-203
  Scenario: KEYCHECK opens the raw file after the stdio redirect and before raop_init
    Given the native source "receiver/native/src/host.c"
    When its KEYCHECK stage is read
    Then the raw-file open comes after the stdio redirect's "dup2" calls and before "raop_init"

  @host @failure @AIR-085 @AT-85 @OD-199
  Scenario: With the setting off, the raw file is never opened
    Given the native log path is compiled with raw diagnostics off
    When the raw-file module is opened with the fake key file path "/fake/files/fake.key"
    And UxPlay's log callback is called with the text "SETUP 1"
    Then the fake file layer recorded no open
    And the ring received one log event with the text "SETUP 1"

  @host @normal @AIR-072 @AIR-085 @AT-72 @AT-85 @OD-198
  Scenario: A full ring can't hide a record from the raw file
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    And the ring's 64 general slots are full
    When UxPlay's log callback is called with the text "Mirroring not initialized at SETUP, playing will fail!"
    Then the raw file holds a line ending with "Mirroring not initialized at SETUP, playing will fail!"
    And the ring's log drop count has risen by one

  @host @normal @AIR-055 @AIR-073 @AT-55 @AT-73 @OD-198
  Scenario: The raw write doesn't change what the ring passes to the filter
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    When UxPlay's log callback is called with each of these texts
      | text                                                     |
      | Mirroring initialized successfully                       |
      | connection from fake-iphone-name                         |
      | deviceid 02:00:00:00:00:aa                               |
      | client at 192.0.2.10 and 2001:db8::10                    |
      | something unknown                                        |
      | *** CLIENT MUST NOW ENTER PIN = "1234" AS AIRPLAY PASSWORD |
      | User-Agent: AirPlay/000.0                                |
      | CSeq: 3                                                  |
      | X-Apple-ProtocolVersion: 1                               |
      | <?xml version="1.0"?><plist><dict></dict></plist>        |
      | 16 byte aesiv (needed for AES-CBC audio decryption iv):  |
      | ekey 0a1b2c3d                                            |
      | aeskey 0a1b2c3d                                          |
      | ecdh 0a1b2c3d                                            |
      | fake.key                                                 |
      | line one<LF>line two                                     |
      | byte <0x01> here                                         |
      | 0123456789abcdef0123456789abcdef                         |
      | 0123456789abcdef0123456789abcde                          |
      | QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZn             |
      | QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZ              |
    Then the ring holds one log event per text, in order, each with exactly the text's bytes, where "<LF>" stands for the byte 0x0A and "<0x01>" for the byte 0x01
    And those events are the same as the native log path pushes when compiled with raw diagnostics off
    And the raw file holds one record line per text

  # --- AIR-086: counters

  @host @normal @AIR-086 @AT-86 @OD-201 @OD-203
  Scenario: Counter lines come at open, about once a second, at session end and at stop
    Given the raw-file module is opened at uptime 1000 ms
    Then the raw file's first line is a counter line at 1000 ms
    When records arrive at 1400, 1800, 2200 and 2600 ms
    Then between 1000 and 2600 ms the only other counter line is at 2200 ms
    When the session ends at 2700 ms
    And stop's last phase runs at 2800 ms
    Then counter lines at 2700 ms and 2800 ms are written

  @host @boundary @AIR-086 @AT-86 @OD-204
  Scenario Outline: Each kind of event without a record writes the counter line at exactly 1,000 ms
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    And the raw-file module is opened at uptime 1000 ms
    When <event> at 1999 ms
    Then no counter line is written at 1999 ms
    When <event> at 2000 ms
    Then exactly one counter line is written at 2000 ms

    Examples:
      | event                                              |
      | "video_process" is called with an access unit      |
      | a codec configuration is pushed                    |
      | the session-start callback runs                    |

  # In the counter scenarios below, "fixture events" are pushed straight into the ring, where no count
  # measures them, and are emptied out again before the session-end callback runs, so the session-end
  # event is never itself dropped. "video_process", the codec-configuration push, the log callback and
  # the session-end callback are the native path's own entry points, which host.c's UxPlay callbacks
  # call. Expected counter lines are compared whole, uptime included.

  @host @normal @AIR-086 @AT-86 @OD-201
  Scenario Outline: Each count moves exactly as the calls made
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    And the ring is in generation 3
    And the raw-file module is opened at uptime 1000 ms
    When at 1100 ms <actions>
    And the ring is emptied of fixture events
    And the session-end callback runs at 1200 ms
    Then the raw file's last line is "1200 counters gen=3 au_in=<au_in> csd_sent=<csd> au_ring_drops=<aud> log_ring_drops=<logd> control_drops=0 raw_written=<rw> raw_dropped=<rd>"

    Examples:
      | actions                                                                                         | au_in | csd | aud | logd | rw | rd |
      | "video_process" is called 3 times with a non-empty access unit before any codec data            | 3     | 0   | 0   | 0    | 0  | 0  |
      | "video_process" is called twice with an empty access unit and once with a non-empty one         | 1     | 0   | 0   | 0    | 0  | 0  |
      | the 64 general slots are filled with fixture events and a codec configuration is pushed         | 0     | 1   | 0   | 0    | 0  | 0  |
      | the 64 general slots are filled with fixture events, "video_process" pushes one access unit and one record arrives | 1 | 0 | 1 | 1 | 1 | 0 |
      | two records arrive and one record of 65,537 escaped bytes arrives                               | 0     | 0   | 0   | 0    | 2  | 1  |

  @host @failure @AIR-086 @AT-86 @OD-201
  Scenario: A refused codec configuration is counted as a breach of OD-190, not as sent
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    And the ring is in generation 3
    And the raw-file module is opened at uptime 1000 ms
    And all 72 ring slots are full of fixture events
    When a codec configuration is pushed at 1100 ms
    And the ring is emptied of fixture events
    And the session-end callback runs at 1200 ms
    Then the raw file's last line is "1200 counters gen=3 au_in=0 csd_sent=0 au_ring_drops=0 log_ring_drops=0 control_drops=1 raw_written=0 raw_dropped=0"

  @host @boundary @AIR-086 @AT-86 @OD-201
  Scenario: Every count carries over a session end and starts from zero at the next app start
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    And the ring is in generation 2
    And the raw-file module is opened at uptime 1000 ms
    And at 1100 ms, in this order: "video_process" with one access unit; one record; one record of 65,537 escaped bytes; the general slots filled up with fixture events; "video_process" with one access unit; one record; one codec configuration; the reserved slots filled up with fixture events; one codec configuration
    And the ring was emptied of fixture events, and the session-end callback ran at 1200 ms
    When the session-end callback runs again at 1300 ms with no event between
    Then the raw file's last line is "1300 counters gen=2 au_in=2 csd_sent=1 au_ring_drops=1 log_ring_drops=1 control_drops=1 raw_written=2 raw_dropped=1"
    When stop's last phase runs, and a new app start opens the raw-file module at 5000 ms with the ring in generation 1
    Then the raw file's first line is "5000 counters gen=1 au_in=0 csd_sent=0 au_ring_drops=0 log_ring_drops=0 control_drops=0 raw_written=0 raw_dropped=0"

  # --- AIR-087: the cap

  @host @boundary @AIR-087 @AT-87 @OD-201 @OD-204
  Scenario: At the cap the file keeps its start, ends with one "raw log full" line, and then never changes
    Given the raw-file module is opened
    And its first record is "first record"
    When records are written until the file is exactly 16,776,704 bytes
    And one more record arrives
    Then the raw file's last line is the uptime, "raw log full" and the counter fields
    And the raw file is no larger than 16,777,216 bytes
    And every line before the final line is byte for byte as it was before the final line was written
    When a record arrives, a counter event comes due, the session ends and stop's last phase runs
    Then the raw file is byte for byte unchanged

  @host @boundary @AIR-087 @AT-87 @OD-204
  Scenario: A counter line that would cross the reserve gives the final line instead
    Given the raw-file module is opened
    And records are written until fewer bytes remain below 16,776,704 than a counter line needs
    When a counter line comes due
    Then the raw file's last line is the uptime, "raw log full" and the counter fields
    And the raw file is no larger than 16,777,216 bytes

  @host @boundary @AIR-087 @AT-87 @OD-204
  Scenario: A counter line that ends exactly at 16,776,704 bytes is written as an ordinary counter line
    Given the raw-file module is opened
    And records are written until the next session-end counter line would end the file at exactly 16,776,704 bytes
    When the session-end callback runs
    Then the raw file is exactly 16,776,704 bytes long
    And its last line is a counter line, and no line is "raw log full"

  @host @boundary @AIR-087 @AT-87 @OD-204
  Scenario: A record whose escaped text is 65,536 bytes is written and one byte more is dropped
    Given the raw-file module is opened
    When a record arrives whose escaped text is 65,536 bytes long
    And a record arrives whose escaped text is 65,537 bytes long
    And the session-end callback runs
    Then the raw file holds the first record and no part of the second
    And the session-end counter line has "raw_written=1" and "raw_dropped=1"

  # --- AIR-088: failures

  @host @failure @AIR-088 @AT-88 @OD-201
  Scenario Outline: A raw-file failure stops raw writes until the next app start
    Given the fake file layer will fail with <failure> at <when>
    And the raw-file module is opened
    When five records arrive, the session ends and stop's last phase runs
    Then the fake file layer recorded no write, reopen or other open after the failure
    When a new app start opens the raw-file module with the fake file layer working
    And a record arrives
    Then that record is written

    Examples:
      | failure          | when                            |
      | an open failure  | the app start's open            |
      | a failed write   | the open's counter line         |
      | a short write    | the open's counter line         |
      | a failed write   | the first record's line         |
      | a short write    | the first record's line         |
      | a failed write   | the third record's line         |
      | a short write    | the third record's line         |

  @host @normal @AIR-088 @AT-88 @OD-203
  Scenario: While raw writes fail, the native log path still pushes every record and reports nothing
    Given the native log path and the ring are compiled with raw diagnostics on, and delivery is stopped
    And the fake file layer fails every write
    And a watchdog of 5 seconds guards the test
    When UxPlay's log callback is called with 3 records
    Then each call returned
    And the ring holds the same 3 log events it would hold with raw writes working, and no other event
    And at no raw-file operation did the calling thread hold any mutex but the raw-file module's own

  # --- AIR-089: the parkcast-diag stick tool

  @offline @normal @AIR-083 @AIR-089 @AT-83 @AT-89 @OD-202 @OD-203
  Scenario: The tool copies the raw file and both logs into a new folder, and reads nothing else in app data
    Given a fake unit tree with ParkCast running
    And its app data holds "uxplay-raw.log", a key file "fake.key" and another file "other.bin"
    And its log folder holds "receiver.log" and "receiver.log.1"
    And ParkCast's descriptor list points at each of those app-data files
    When the parkcast-diag script runs from the fake stick
    Then a new folder "NEMESIS-diag/parkcast-diag/<timestamp>/" holds copies of "uxplay-raw.log", "receiver.log" and "receiver.log.1" and a listing of the log folder
    And the journal holds no read of "fake.key" or "other.bin", no listing of app data, and no change on the unit
    And no file on the fake stick holds the text "fake.key", the key file's path or bytes, or "other.bin"
    And the script's log ends with "parkcast diag end", written after every copy
    And the script exits 0

  @offline @normal @AIR-089 @AT-89 @OD-202
  Scenario: No unfiltered logcat ever lands on the stick, even for a moment
    Given a fake unit tree with ParkCast running
    And the fake "logcat" puts the sentinel line "UNFILTERED-SENTINEL" in every unfiltered output
    And the fake "rm" records deletions and keeps the file
    When the parkcast-diag script runs from the fake stick
    Then no file on the fake stick holds "UNFILTERED-SENTINEL"
    And the fake "logcat" was never asked to write to a file

  @offline @normal @AIR-089 @AT-89 @OD-202
  Scenario: Every logcat call in the script pipes straight into grep
    Given the parkcast-diag script in the repository
    When the logcat check reads it
    Then the check passes: every "logcat" call has no "-f" option and its output goes through a pipe straight into "grep", with no redirect before the pipe

  @offline @failure @AIR-089 @AT-89 @OD-202
  Scenario Outline: The logcat check fails a script that breaks one of its rules
    Given a fixture script whose one logcat call <breaks>
    When the logcat check reads it
    Then the check fails and names that call

    Examples:
      | breaks                                                                          |
      | has a "-f" option, but is piped straight into grep                              |
      | is redirected to a file before its pipe into grep                               |
      | is piped into tee before grep                                                   |
      | sits inside an if, after "then", and is redirected to a file before its pipe into grep |

  @offline @failure @AIR-089 @AT-89 @OD-202
  Scenario: The logcat check catches a script that writes unfiltered logcat, overwrites it and deletes it
    Given a fixture script that redirects "logcat -d" to a file on the stick, overwrites that file with filtered lines, and deletes it
    When the logcat check reads it
    Then the check fails and names the redirected "logcat" call

  @offline @normal @AIR-089 @AT-89 @OD-202
  Scenario Outline: Starting ParkCast is the only change, and only when it isn't running
    Given a fake unit tree with ParkCast <state>
    When the parkcast-diag script runs from the fake stick
    Then the journal's changes on the unit are <changes>

    Examples:
      | state       | changes                                                       |
      | not running | exactly one "am start -n parkcast.receiver/.ReceiverActivity" |
      | running     | none                                                          |

  @offline @failure @AIR-089 @AT-89 @OD-204
  Scenario Outline: A source that is missing, fails to copy or is a symlink fails alone
    Given a fake unit tree with ParkCast running and all three sources
    And "<source>" <problem>
    When the parkcast-diag script runs from the fake stick
    Then its log has a failure line naming "<source>"
    And the other two sources are copied
    And the journal holds no read through a symlink
    And the script exits non-zero

    Examples:
      | source         | problem                                 |
      | receiver.log   | is missing                              |
      | receiver.log.1 | is missing                              |
      | uxplay-raw.log | fails to copy                           |
      | receiver.log   | fails to copy                           |
      | receiver.log.1 | fails to copy                           |
      | uxplay-raw.log | is a symlink to a sentinel file         |
      | receiver.log   | is a symlink to a sentinel file         |
      | receiver.log.1 | is a symlink to a sentinel file         |

  @offline @failure @AIR-089 @AT-89
  Scenario: A missing raw file is reported with the required words
    Given a fake unit tree with ParkCast running, both receiver logs, and no "uxplay-raw.log"
    When the parkcast-diag script runs from the fake stick
    Then its log has the line "no uxplay-raw.log"
    And both receiver logs are copied
    And the script exits non-zero

  @offline @failure @AIR-089 @AT-89 @OD-206
  Scenario: A copy that doesn't match its source is not left on the stick
    Given a fake unit tree with ParkCast running and all three sources
    And the fake "cp" copies only the first half of "receiver.log" and exits 0
    When the parkcast-diag script runs from the fake stick
    Then its log has a failure line naming "receiver.log"
    And the run folder holds no "receiver.log"
    And "uxplay-raw.log" and "receiver.log.1" are copied whole
    And the script exits non-zero

  @offline @failure @AIR-089 @AT-89 @OD-206
  Scenario: The tool stops before reading anything while Android is still starting
    Given a fake unit tree with ParkCast not running and all three sources
    And the fake "getprop" gives "0" for "sys.boot_completed"
    And the stick's root "NEMESIS.data" is the tool's own
    When the parkcast-diag script runs from the fake stick
    Then the root "NEMESIS.data" is gone
    And the journal holds no source read, no copy and no "am" call
    And the script exits non-zero

  @offline @failure @AIR-089 @AT-89 @OD-204
  Scenario: An output folder that already exists is left alone
    Given a fake unit tree with ParkCast running and all three sources
    And the stick already holds the folder the script would make, with a file in it
    When the parkcast-diag script runs from the fake stick
    Then that folder's contents are unchanged
    And its log reports that the folder already exists
    And no source was copied
    And the script exits non-zero

  @offline @failure @AIR-089 @AT-89 @OD-204
  Scenario: Another firmware build is refused before any source is read
    Given a fake unit tree whose build is not "eng.vmadmin.20260206.060005"
    And the stick's root "NEMESIS.data" is the tool's own
    When the parkcast-diag script runs from the fake stick
    Then the root "NEMESIS.data" is gone, and the journal shows it removed before the build was queried
    And its log says the guard failed
    And the journal holds no source read, no copy and no "am" call
    And the script exits non-zero

  @host @normal @AIR-089 @AT-89
  Scenario: The PC keeps the tool's output under runs/ and out of git
    Given the repository
    Then a file under "runs/" is ignored by git
    And git tracks no file under "runs/" and no file named "uxplay-raw.log"

  @offline @normal @AIR-089 @AIR-075 @AT-89 @AT-75
  Scenario: Writing a result keeps the raw file under runs/ and out of the evidence summary
    Given a synthetic run folder holding "receiver.log" and a "uxplay-raw.log" with the text "RAW-SENTINEL"
    When the evaluator writes the result for it into a fake repository root
    Then the fake root's "runs/" folder holds the copied "uxplay-raw.log"
    And the only file written under the fake root's "docs/evidence/" is the summary
    And the summary does not hold "RAW-SENTINEL"

  @offline @normal @AIR-075 @AIR-089 @AT-75 @AT-89
  Scenario: The evaluator reads "receiver.log.1" before "receiver.log", as one log
    Given a complete synthetic GO run with the owner note "picture: yes"
    And its log split in the middle of its session into an older "receiver.log.1" and a newer "receiver.log"
    When the evaluator judges the split folder and the same log as one "receiver.log"
    Then both are GO with the same summary and cited lines

  @offline @boundary @AIR-089 @AIR-075 @AIR-076 @AT-89 @AT-75 @AT-76
  Scenario: The raw file can't supply evidence the receiver's log lacks
    Given a complete synthetic receiver run with the owner note "picture: yes"
    And its one session has every GO record except "session 1 first frame rendered"
    And the evaluator's INCONCLUSIVE verdict, summary and cited lines for that folder are saved
    And a "uxplay-raw.log" is added holding, in receiver-log form, a complete start marker, "session 1 connection accepted model=iPhone15,2", "Mirroring initialized successfully", "session 1 codec h264 1920x1080", "decoder OMX.TCC.avcdec configured", "session 1 first frame rendered", a session end record and a complete end marker, all within the observation window
    When the evaluator judges the folder again
    Then its verdict, summary and cited lines equal the saved ones
    And judging the folder with the evaluator as it was before this revision, which reads every "*.log", gives GO, so the check can fail

  # --- On the unit (evidence from the stick; never run from the PC suite)

  @unit @normal @AIR-085 @AIR-086 @AT-85 @AT-86 @OD-198
  Scenario: The copied raw file holds UxPlay's records and counter lines from the session
    Given the stick copy from a parkcast-diag run with a raw-diagnostics build, during which the iPhone cast
    Then "uxplay-raw.log" holds records from UxPlay during the session and counter lines
    And the owner finds in it no unredacted run of 32 or more hex digits, of 44 or more base64 characters, `<data>` value, byte dump, PIN value, or the key file's path or name

  @unit @normal @AIR-062 @AIR-089 @AT-62 @AT-89
  Scenario: The raw file stays in app data and the tool's copy lands in a timestamped folder
    Given the stick after a parkcast-diag run
    Then its copy is in "NEMESIS-diag/parkcast-diag/<timestamp>/"
    And the root "NEMESIS.data" is gone
    And the copied listing of the receiver's log folder shows no "uxplay-raw.log"

  @unit @normal @AIR-072 @AT-72
  Scenario: UxPlay's setup lines count only when they are in the receiver's log
    Given the stick copy from a parkcast-diag run with a raw-diagnostics build
    When "Mirroring initialized successfully" is in "uxplay-raw.log" but not in "receiver.log"
    Then AT-72's unit check is not met
