@S3
Feature: The tools around the AirPlay receiver: build checks, stick scripts and the S3 evaluator
  These scenarios cover the PC-side tools of the first S3 milestone (video only): the receiver's
  build and its checks, the two stick scripts (install and start, log copy), and the S3 evaluator
  that reads the copied logs on the PC. They run under pytest-bdd like S1 (OD-177). The receiver's
  own runtime behaviour is in 13_receiver_runtime.feature, and on-unit evidence is in
  15_receiver_unit.feature (AIR-042 to AIR-047, AIR-062, AIR-071, AIR-074 to AIR-079, AIR-083;
  OD-139, OD-143, OD-145, OD-154 to OD-156, OD-158, OD-160, OD-162, OD-163, OD-166, OD-168,
  OD-169, OD-171 to OD-174, OD-177 to OD-184).

  `@host` scenarios read this PC's real build outputs: the APK from `./gradlew`, its native
  libraries, the build and SDK install logs, and the repository's files. `@offline` scenarios use
  synthetic fixtures only. No fixture holds a real iPhone name, IP address or MAC address.

  Marker texts (AIR-071, OD-178), exact under AIR-071(f). Each is one record, written by the
  receiver's logger after AIR-073's filter; the logger's own prefix (timestamp and level) is not
  part of the text. `<n>` is the session number, counted from 1 at each app start (AIR-071(e)).
    Start marker:      parkcast start app=<version> commit=<git commit> uxplay=<UxPlay commit> product=<ro.build.product> require_park=<true|false>
    Connection:        session <n> connection accepted model=<iPhone model>
    UxPlay setup:      Mirroring initialized successfully | Mirroring not initialized at SETUP  (UxPlay's own lines, unchanged)
    S3 codec line:     session <n> codec <h264|h265> <width>x<height>
    Decoder:           decoder OMX.TCC.avcdec configured  (AIR-071(b)'s text, unchanged)
    First frame:       session <n> first frame rendered
    Gate withheld:     session <n> first frame withheld by gate
    Session end:       session <n> end reason=<reason> withheld=<count>
    End marker:        parkcast stop reason=<reason>
  The `withheld=` count is AIR-073(c)'s count of dropped native records. `model=` is allowed by
  AIR-073(f) and carries the iPhone model only, never its name. Other records the evaluator reads
  keep the texts their own requirements give them: `hevc refused`, `decoder unavailable`,
  `decoder error`, `port busy <proto> <port>`, `advertise failed`, and `carplay listener present`,
  `carplay listener absent` or `carplay listener unknown`.

  Log fixtures follow specs/README.md's rules, with S3's markers in place of S1's. A step that
  says "a receiver log excerpt with these lines" wraps the excerpt into a complete log (AIR-075(b)
  under AIR-028(b)): the start marker goes before it, and the end marker and a final newline go
  after it. The start marker's `uxplay=` field stands in for UxPlay's version banner, which
  `uxplay.cpp` prints and S3 does not build (AIR-042(b)). A step that names a damaged log builds
  that log deliberately and doesn't wrap it. "At <t> s" gives a record a timestamp <t> seconds
  after the run's first start marker; the step definitions write it in the logger's prefix.
  "A session that would be GO" is one session with `connection accepted`, a `codec h264` line,
  `decoder OMX.TCC.avcdec configured` and `first frame rendered`, with `picture: yes` in the note.

  The owner's note is `owner-note.txt` in the run's `NEMESIS-diag/airplay-receiver/<timestamp>/`
  folder on a fake stick (AIR-076(e)). Stick scripts run under the host's POSIX `sh` against a
  fake tree: a fake unit root and a fake stick root. The fake `nemesis`, `pm`, `am`, `chown`,
  `chmod` and `cp` record every call with its arguments in order, and reject any option they do
  not know, as toolbox does on the unit.

  The owner's other observations are in `run-checklist.txt` in the same folder (AIR-079(e)), one
  `<key>: yes` or `<key>: no` line for each of the keys `parkcast listed`, `letterboxed`,
  `sound on unit`, `stayed behind other app`, `nothing on cluster or hud` and
  `carplay after exit`, in that order, read under AIR-079(f). A fixture has no `run-checklist.txt`
  unless a step gives one. "The blank checklist" is the file the log-copy script places under
  AIR-074(d): exactly the bytes
  `parkcast listed: \nletterboxed: \nsound on unit: \nstayed behind other app: \nnothing on cluster or hud: \ncarplay after exit: \n`.
  A step in which placing a file fails makes the fake stick refuse to create that one file.

  @offline @normal @AIR-042 @AT-42 @OD-174
  Scenario: Every UxPlay patch the build applies comes from the repository
    Given a clean checkout with the receiver's patch files in the repository
    When the native build runs against a fixture copy of the UxPlay source tree
    Then every patch it applied is a file tracked in the repository
    And no patch is applied from outside the repository

  @host @normal @AIR-042 @AT-42 @OD-169
  Scenario: A clean build checks out UxPlay 05b0275 and applies the dns_sd and bind patches
    Given a clean build of the receiver on this PC
    When the build log is read
    Then it shows UxPlay commit 05b0275cb1b726592700e47a6e0307544b3d3a52 checked out
    And it shows patches applied only from files in the repository
    And the applied patches include the libmdnssd.so patch and the bind patch

  @host @normal @AIR-042 @AT-42
  Scenario: The CMake source list builds only UxPlay's protocol library
    Given a clean build of the receiver on this PC
    When the CMake source list is read
    Then no source file is under "renderers/"
    And "uxplay.cpp" is not in the list

  @host @normal @AIR-042 @AT-42
  Scenario: No native library in the APK links GStreamer
    Given the signed APK from a clean build on this PC
    When "readelf -d" is run on each ".so" in the APK
    Then no NEEDED entry names a "gst" library

  @host @normal @AIR-042 @AT-42 @OD-164
  Scenario: The built protocol library names libmdnssd.so, never libdns_sd.so
    Given the signed APK from a clean build on this PC
    When the strings of the built protocol library are read
    Then they include "libmdnssd.so"
    And they do not include "libdns_sd.so"

  @host @normal @AIR-042 @AT-42 @OD-169
  Scenario: The patched sources never bind to a wildcard address
    Given the UxPlay sources as patched by a clean build on this PC
    When every "bind" call in them is read
    Then no call passes "INADDR_ANY"
    And no call passes "in6addr_any"

  @offline @failure @AIR-042 @AT-42 @OD-174
  Scenario: A patch that does not apply cleanly fails the build
    Given a patch fixture that does not apply cleanly to the UxPlay source tree
    When the native build runs
    Then the build fails naming the patch
    And no protocol library is produced

  @host @normal @AIR-043 @AT-43 @OD-143
  Scenario: The APK carries native libraries for armeabi-v7a only
    Given the signed APK from a clean build on this PC
    When "unzip -l" lists the APK
    Then every native library entry is under "lib/armeabi-v7a/"
    And there is no other directory under "lib/"

  @host @normal @AIR-043 @AT-43 @OD-143
  Scenario: The native libraries are built for ARMv7
    Given the signed APK from a clean build on this PC
    When "readelf -A" is run on each ".so" in the APK
    Then each reports the ARMv7 architecture

  @host @failure @AIR-043 @AT-43 @OD-143
  Scenario: An NDK other than r25c fails the build and names the version found
    Given an NDK other than r25c configured for the receiver build on this PC
    When "./gradlew" builds the receiver
    Then the build fails naming the NDK version it found
    And no APK is produced
    And no other NDK on the PC is used instead

  @host @normal @AIR-044 @AT-44
  Scenario: Every native dependency is pinned with a version and a SHA-256
    Given the repository's native dependency pins
    When each third-party dependency of the protocol library is checked
    Then each has a pinned version in the repository
    And each has the SHA-256 of its source archive in the repository

  @host @normal @AIR-044 @AT-44 @OD-168
  Scenario: The pins name an OpenSSL 3.5 release and a libplist 2.x release
    Given the repository's native dependency pins
    When the OpenSSL and libplist pins are read
    Then the OpenSSL pin is a 3.5.x release
    And the libplist pin is a 2.x release

  @host @normal @AIR-044 @AT-44 @OD-164
  Scenario: The native libraries need only API 19 platform libraries
    Given the signed APK from a clean build on this PC
    When the NEEDED entries of each ".so" in the APK are read
    Then every entry is a platform library that NDK API 19 exposes
    And no entry is "libmdnssd.so"

  @offline @failure @AIR-044 @AT-44
  Scenario: A dependency archive with the wrong SHA-256 fails before extraction
    Given a fixture dependency archive whose SHA-256 differs from its pin
    When the native build fetches that dependency
    Then the build fails naming the SHA-256 mismatch
    And the archive is never extracted
    And no output of that dependency is left

  @offline @failure @AIR-044 @AT-44 @OD-168
  Scenario Outline: A pin outside the allowed release line fails the build
    Given a pin fixture naming <dependency> <version>
    When the native build reads the pins
    Then the build fails naming <dependency> and its version <version>

    Examples:
      | dependency | version |
      | OpenSSL    | 3.0.22  |
      | OpenSSL    | 3.6.0   |
      | libplist   | 1.12    |
      | libplist   | 3.0.0   |

  @host @normal @AIR-045 @AT-45 @OD-142
  Scenario: The SDK install log shows a checksum check for each package
    Given the Android SDK install log from this PC
    When the log is read
    Then it shows a checksum check for each package installed under "C:\Android"

  @offline @failure @AIR-045 @AT-45 @OD-142
  Scenario: A corrupted SDK archive stops the install and changes nothing installed
    Given a fake SDK root with packages already installed
    And a fixture SDK archive whose checksum does not match
    When the SDK install script runs
    Then the install stops naming the checksum mismatch
    And the corrupted archive is not extracted
    And the hashes of the already-installed package directories are unchanged

  @host @normal @AIR-046 @AT-46 @OD-144
  Scenario: One gradlew invocation on a clean clone produces the signed APK
    Given a clean clone of the repository on this PC, with the SDK, the NDK and the local signing file present
    When "./gradlew" is run once
    Then the signed APK is produced

  @host @boundary @AIR-046 @AT-46 @OD-144
  Scenario: The gate module builds and passes its tests without the Android SDK
    Given "ANDROID_HOME" is unset
    When "receiver/gate" is built and its tests are run
    Then the build succeeds
    And every test passes

  @offline @normal @AIR-047 @AT-47 @OD-145
  Scenario: The signing key and the local signing file are untracked and ignored
    Given the repository and the paths of the signing key and the local signing file
    When "git ls-files" and "git check-ignore" are run on both paths
    Then neither path is tracked
    And both paths are ignored

  @host @failure @AIR-047 @AT-47 @OD-145
  Scenario Outline: A missing signing input fails the build and names which one
    Given a build setup on this PC in which the <input> is absent
    When "./gradlew" builds the receiver
    Then the build fails naming the <input> as missing
    And no APK is produced

    Examples:
      | input              |
      | local signing file |
      | signing key        |

  @host @normal @AIR-047 @AT-47 @OD-145
  Scenario: The build output never prints the signing passwords
    Given a throwaway test key and test passwords, never the owner's, in the local signing file
    When "./gradlew" builds the receiver
    Then the build output does not contain either test password

  @offline @normal @AIR-074 @AT-74 @OD-149
  Scenario: The log-copy script copies the receiver's logs and leaves the originals unchanged
    Given a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"
    When the log-copy stick script runs
    Then the stick holds both files under "NEMESIS-diag/airplay-receiver/<timestamp>/"
    And each copied file has the same bytes as its original
    And the originals' hashes are unchanged
    And the script exits 0

  @offline @normal @AIR-074 @AIR-076 @AIR-079 @AT-74 @OD-181 @OD-182 @OD-184
  Scenario: The log-copy script places a zero-byte note and the blank checklist, which read as nothing recorded
    Given a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"
    When the log-copy stick script runs
    Then the new "<timestamp>/" folder holds an "owner-note.txt" of zero bytes
    And it holds a "run-checklist.txt" whose bytes are exactly the blank checklist
    When the evaluator reads that folder
    Then it reports every checklist key as "not recorded"
    And it reads the owner's note as missing

  @offline @failure @AIR-074 @AT-74 @OD-184
  Scenario: An interrupted copy is reported incomplete and fails the script, and the blank files are still placed
    Given a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"
    And the fake "cp" fails after copying the first file
    When the log-copy stick script runs
    Then the script reports the copy as incomplete
    And the copy is never reported as complete
    And the script exits non-zero
    And the originals' hashes are unchanged
    And the run's folder holds an "owner-note.txt" of zero bytes and the blank checklist as "run-checklist.txt"

  @offline @boundary @AIR-074 @AT-74 @OD-184
  Scenario Outline: An owner file already in the target folder is left byte for byte
    Given a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"
    And the target folder already holds <existing file>
    When the log-copy stick script runs
    Then that file's hash is unchanged
    And the script reports that it left "<file name>"

    Examples:
      | existing file                                                    | file name         |
      | an "owner-note.txt" holding "picture: yes"                       | owner-note.txt    |
      | a "run-checklist.txt" with all six keys "yes"                    | run-checklist.txt |

  @offline @failure @AIR-074 @AT-74 @OD-184
  Scenario Outline: A file that can't be placed is named, fails the script and leaves the copied logs alone
    Given a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"
    And placing "<file name>" on the fake stick fails
    When the log-copy stick script runs
    Then the script names "<file name>"
    And the script exits non-zero
    And the copied logs' hashes are unchanged
    And the copy is not reported as incomplete

    Examples:
      | file name         |
      | owner-note.txt    |
      | run-checklist.txt |

  @offline @failure @AIR-074 @AT-74 @OD-184
  Scenario: A run folder that already exists on the stick is refused
    Given a fake unit tree with two receiver log files in "/data/nemesis/logs/airplay-receiver/"
    And the fake stick already has the run's "NEMESIS-diag/airplay-receiver/<timestamp>/" folder with files in it
    When the log-copy stick script runs
    Then nothing is copied into that folder
    And every file in that folder keeps its hash, and no file is added to it
    And the script reports that the folder exists
    And the script exits non-zero

  @offline @normal @AIR-083 @AIR-073 @AIR-074 @AT-83 @OD-170
  Scenario: The log copy never includes the identity key file
    Given a fake unit tree with receiver log files and an identity key file in the receiver's app-data directory
    When the log-copy stick script runs
    Then no file is copied from the receiver's app-data directory
    And no file on the stick has the key file's name
    And no file on the stick contains the key file's bytes

  @offline @normal @AIR-078 @AIR-062 @AT-78
  Scenario: The install zip contains only NEMESIS.data and the APK
    Given the install zip built for the stick
    When its members are listed
    Then they are exactly "NEMESIS.data" and the receiver APK

  @offline @normal @AIR-078 @AIR-062 @AT-78 @OD-156
  Scenario: The install zip declares the receiver as its one APK launchable
    Given the install zip built for the stick
    When its "NEMESIS.data" is read
    Then it has "id=airplay.receiver"
    And it has exactly one launchable section, "[app.0]"
    And "[app.0]" has "kind=apk", a "path" naming the APK in the zip, and a "package" naming the receiver's Android package

  @offline @normal @AIR-078 @AIR-062 @AIR-063 @AT-78
  Scenario Outline: The install zip declares nothing beyond the APK
    Given the install zip built for the stick
    When its "NEMESIS.data" and members are read
    Then it has no <item>

    Examples:
      | item                                |
      | "[service.N]" section               |
      | "preinstall" key                    |
      | "postinstall" key                   |
      | "preuninstall" key                  |
      | "postuninstall" key                 |
      | "depends" key                       |
      | "config/receiver.conf" member       |

  @offline @normal @AIR-078 @AT-78 @OD-172
  Scenario: The install zip names the owner's build and the EV6 models, each once
    Given the install zip built for the stick
    When its "NEMESIS.data" is read
    Then "os=CVEV.CAN.0000.V015.011.260206" appears exactly once
    And "models=cvev*" appears exactly once

  @offline @normal @AIR-078 @AT-78
  Scenario: Every metadata field of the install zip is within NEMESIS 1.16's limits
    Given the install zip built for the stick
    When its metadata is checked against NEMESIS 1.16's limits
    Then "short-desc" is at most 64 characters and "detailed-desc" at most 256
    And "package-version" is dotted numbers under 32 bytes
    And "id", "name" and "type" are under 96, 128 and 80 bytes
    And "os" and "models" are under 256 bytes each

  @offline @boundary @AIR-078 @AT-78
  Scenario Outline: The description limits are checked at their edges
    Given an install manifest fixture whose <field> is <length> characters long
    When the install zip check runs on it
    Then the check <result>

    Examples:
      | field         | length | result                         |
      | short-desc    | 64     | accepts it                     |
      | short-desc    | 65     | refuses it naming short-desc    |
      | detailed-desc | 256    | accepts it                     |
      | detailed-desc | 257    | refuses it naming detailed-desc |

  @offline @boundary @AIR-078 @AT-78 @OD-172 @OD-171
  Scenario Outline: An os or models line that is not exactly its value is refused
    Given an install manifest fixture whose <key> is <fault>
    When the install zip check runs on it
    Then the check refuses it naming <key>
    And no install zip is produced

    Examples:
      | key    | fault                                                     |
      | os     | missing                                                   |
      | os     | present twice                                             |
      | os     | "os=any"                                                  |
      | os     | "os=CVEV.CAN.0000.V015.011.260207"                        |
      | os     | "os=CVEV.CAN.0000.V015.011.260206 " with a trailing space |
      | models | missing                                                   |
      | models | present twice                                             |
      | models | "models=any"                                              |
      | models | "models=cvev"                                             |
      | models | "models=cvev_us"                                          |
      | models | "models=cvev* " with a trailing space                     |

  @offline @boundary @AIR-078 @AT-78
  Scenario Outline: The os and models lengths are checked against the 256-byte limit
    Given an install manifest fixture whose <key> value is 256 bytes long
    When the install zip check runs on it
    Then the check refuses it
    And the check's findings include <key> at or over 256 bytes

    Examples:
      | key    |
      | os     |
      | models |

  @offline @normal @AIR-078 @AT-78 @OD-160 @OD-163 @OD-156
  Scenario: The install script installs through NEMESIS, checks, prepares the log directory, then starts
    Given a fake unit tree on which every fake command succeeds and the receiver's package has uid 10123
    And the install zip staged on the fake stick
    When the install stick script runs
    Then the calls are, in this order:
      | step               | call                                                     |
      | install            | nemesis install --update <zip on the stick>              |
      | install check      | nemesis pkg-list                                         |
      | install check      | pm list packages                                         |
      | directory step     | creates /data/nemesis/logs/airplay-receiver/ if missing  |
      | directory step     | chown to uid 10123                                       |
      | directory step     | chmod 0700                                               |
      | start              | am start                                                 |
    And "pm install" is never called
    And the script exits 0

  @offline @failure @AIR-078 @AT-78 @OD-163
  Scenario Outline: A failed install stops the script before the directory step and the start
    Given a fake unit tree on which <failure>
    And the install zip staged on the fake stick
    When the install stick script runs
    Then the script exits non-zero
    And the directory step is never run
    And "am start" is never called
    And no further install or uninstall step is run
    And the output of every command it ran is kept on the stick

    Examples:
      | failure                                                                                       |
      | "nemesis install" exits 1                                                                     |
      | "nemesis install" exits 0 but prints "invalid package metadata: short-desc"                    |
      | "nemesis install" exits 0 with "Failure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]"      |
      | "nemesis pkg-list" has no line whose first field is "airplay.receiver"                        |
      | "pm list packages" has no line "package:" followed by the receiver's package                  |

  @offline @failure @AIR-078 @AT-78
  Scenario Outline: A failed directory step stops the script before the start
    Given a fake unit tree on which the install succeeds and <failure>
    And the install zip staged on the fake stick
    When the install stick script runs
    Then the script reports the directory step's failure
    And the script exits non-zero
    And "am start" is never called
    And the output of every command it ran is kept on the stick

    Examples:
      | failure                                   |
      | the receiver's uid cannot be found        |
      | the fake "chown" exits non-zero           |

  @offline @failure @AIR-078 @AT-78 @OD-163
  Scenario: An am start that prints an error fails the script
    Given a fake unit tree on which the install and the directory step succeed
    And the fake "am start" prints "Error: Activity class does not exist." and exits 0
    When the install stick script runs
    Then the script exits non-zero
    And the output of every command it ran is kept on the stick

  @offline @normal @AIR-071 @AIR-075 @AT-71 @OD-178
  Scenario: The evaluator reads every S3 marker of a complete log, in order
    Given a stick copy whose log is:
      """
      parkcast start app=0.1.0 commit=0123456789abcdef0123456789abcdef01234567 uxplay=05b0275cb1b726592700e47a6e0307544b3d3a52 product=CVEV.CAN.0000.V015.011.260206 require_park=true
      carplay listener absent
      session 1 connection accepted model=iPhone15,2
      Mirroring initialized successfully
      session 1 codec h264 1920x720
      decoder OMX.TCC.avcdec configured
      session 1 first frame withheld by gate
      session 1 first frame rendered
      session 1 end reason=iphone disconnected withheld=0
      parkcast stop reason=owner exit
      """
    When the evaluator reads the log
    Then the log is complete
    And the start marker gives app "0.1.0", commit "0123456789abcdef0123456789abcdef01234567", UxPlay commit "05b0275cb1b726592700e47a6e0307544b3d3a52", product "CVEV.CAN.0000.V015.011.260206" and parking flag "true"
    And session 1's markers are read in this order: connection accepted, mirroring initialized, codec h264 1920x720, decoder configured, first frame withheld by gate, first frame rendered, session end with reason "iphone disconnected"
    And the end marker gives the stop reason "owner exit"

  @offline @normal @AIR-075 @AT-75
  Scenario: The evaluator keeps raw logs only under the ignored runs folder
    Given a synthetic stick copy of one run
    When the evaluator judges it
    Then every raw log it writes is under "runs/"
    And "git check-ignore" reports "runs/" as ignored
    And nothing it writes outside "runs/" contains a raw log line

  @offline @normal @AIR-075 @AIR-077 @AT-75 @AT-77 @OD-183
  Scenario: The S3 summary has the S1 summary fields, one Checklist: line and nothing else
    Given a synthetic stick copy of one run judged as a unit run
    When the evaluator writes the summary to "docs/evidence/"
    Then the summary has these S1 summary fields, in order:
      | field                  |
      | # AirPlay receiver ... |
      | Tier:                  |
      | Verdict:               |
      | Sessions:              |
      | Device model:          |
      | iOS:                   |
      | UxPlay build commit:   |
      | Head unit:             |
      | Firewall:              |
    And it has exactly one line starting "Checklist: "
    And the summary has no other line

  @offline @boundary @AIR-075 @AIR-079 @AT-75 @OD-183 @OD-184
  Scenario Outline: Each checklist gives exactly one Checklist: line in key order, with no checklist text
    Given a synthetic stick copy of one run judged as a unit run
    And its "run-checklist.txt" is <checklist fixture>
    When the evaluator writes the summary
    Then the summary has exactly one line starting "Checklist: "
    And that line is "<checklist line>"
    And neither "secret text" nor "free text" appears anywhere in the summary

    Examples:
      | checklist fixture                                                                                                                        | checklist line |
      | all six keys "yes"                                                                                                                       | Checklist: parkcast listed=yes; letterboxed=yes; sound on unit=yes; stayed behind other app=yes; nothing on cluster or hud=yes; carplay after exit=yes |
      | "parkcast listed: yes", "letterboxed: no", "sound on unit: ", "stayed behind other app: yes", "nothing on cluster or hud: no", "carplay after exit: " | Checklist: parkcast listed=yes; letterboxed=no; sound on unit=not recorded; stayed behind other app=yes; nothing on cluster or hud=no; carplay after exit=not recorded |
      | absent                                                                                                                                   | Checklist: parkcast listed=not recorded; letterboxed=not recorded; sound on unit=not recorded; stayed behind other app=not recorded; nothing on cluster or hud=not recorded; carplay after exit=not recorded |
      | all six keys "yes" plus the lines "secret text: yes" and "free text"                                                                     | Checklist: parkcast listed=yes; letterboxed=yes; sound on unit=yes; stayed behind other app=yes; nothing on cluster or hud=yes; carplay after exit=yes |

  @offline @failure @AIR-075 @AIR-028 @AT-75
  Scenario Outline: A log without an S3 marker makes every session an evidence error
    Given a damaged stick log with two sessions that would be GO and <damage>
    When the evaluator judges the run
    Then every session's verdict is INCONCLUSIVE with the reason "evidence error"
    And the limitation "<limitation>" is named
    And the run's verdict is INCONCLUSIVE

    Examples:
      | damage                 | limitation      |
      | no start marker        | no start marker |
      | no end marker          | truncated       |

  @offline @normal @AIR-076 @AT-76 @OD-139
  Scenario: A session with an H.264 picture the owner saw is GO
    Given a receiver log excerpt with these lines:
      """
      session 1 connection accepted model=iPhone15,2
      Mirroring initialized successfully
      session 1 codec h264 1920x720
      decoder OMX.TCC.avcdec configured
      session 1 first frame rendered
      session 1 end reason=iphone disconnected withheld=0
      """
    And the owner's note says "picture: yes"
    When the evaluator judges the run
    Then the session's verdict is GO
    And the run's verdict is GO
    And the verdict cites the log lines it rests on and "owner-note.txt"

  @offline @normal @AIR-076 @AT-76 @OD-139
  Scenario Outline: A session that connected but could not show an H.264 picture is NO-GO
    Given a receiver log excerpt with these lines:
      """
      session 1 connection accepted model=iPhone15,2
      <what happened>
      session 1 end reason=<end reason> withheld=0
      """
    And the owner's note says "<note>"
    When the evaluator judges the run
    Then the session's verdict is NO-GO
    And the verdict cites the line "<cited line>"
    And the run's verdict is NO-GO

    Examples:
      | what happened                                                                                      | end reason          | note        | cited line                                 |
      | Mirroring initialized successfully\nsession 1 codec h264 1920x720\ndecoder unavailable IllegalStateException | decoder unavailable | picture: no | decoder unavailable IllegalStateException  |
      | Mirroring initialized successfully\nsession 1 codec h264 1920x720\ndecoder OMX.TCC.avcdec configured\ndecoder error  | decoder error       | picture: no | decoder error                              |
      | Mirroring initialized successfully\nsession 1 codec h265 1920x720\nhevc refused                    | hevc refused        | picture: no | session 1 codec h265 1920x720              |
      | Mirroring not initialized at SETUP                                                                 | iphone disconnected | picture: no | Mirroring not initialized at SETUP         |
      | Mirroring initialized successfully\nsession 1 codec h264 1920x720\ndecoder OMX.TCC.avcdec configured\nsession 1 first frame rendered | iphone disconnected | picture: no | picture: no |

  @offline @normal @AIR-076 @AT-76 @OD-26
  Scenario: A run in which no iPhone connected is INCONCLUSIVE
    Given a receiver log excerpt with these lines:
      """
      carplay listener absent
      """
    And the owner's note says "picture: no"
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "no iPhone connected"
    And no session verdict is listed

  @offline @normal @AIR-076 @AT-76
  Scenario: A session in which the gate never permitted video is INCONCLUSIVE
    Given a receiver log excerpt with these lines:
      """
      session 1 connection accepted model=iPhone15,2
      Mirroring initialized successfully
      session 1 codec h264 1920x720
      decoder OMX.TCC.avcdec configured
      session 1 first frame withheld by gate
      session 1 end reason=iphone disconnected withheld=0
      """
    And the owner's note says "picture: no"
    When the evaluator judges the run
    Then the session's verdict is INCONCLUSIVE with the reason "gate never permitted video"
    And the run's verdict is INCONCLUSIVE

  @offline @boundary @AIR-076 @AT-76 @OD-154 @OD-162
  Scenario: A session that would be GO with no owner's note is INCONCLUSIVE
    Given a session that would be GO
    And the run's "<timestamp>/" folder has no "owner-note.txt"
    When the evaluator judges the run
    Then the session's verdict is INCONCLUSIVE with the reason "owner note missing"
    And the run's verdict is INCONCLUSIVE

  @offline @boundary @AIR-076 @AT-76 @OD-154 @OD-162
  Scenario Outline: Only an exact picture line in the run's folder is read as the owner's note
    Given a session that would be GO, apart from its note
    And the owner's note fixture is <note fixture>
    When the evaluator judges the run
    Then the session's verdict is <verdict>

    Examples:
      | note fixture                                                                   | verdict                                         |
      | "picture: yes" with one trailing LF                                            | GO                                              |
      | " Picture: YES " with one trailing CRLF                                        | GO                                              |
      | "picture: no" with one trailing LF                                             | NO-GO                                           |
      | an empty file                                                                  | INCONCLUSIVE with the reason "owner note missing" |
      | the two lines "picture: yes" and "picture: no"                                 | INCONCLUSIVE with the reason "owner note missing" |
      | "picture: maybe"                                                               | INCONCLUSIVE with the reason "owner note missing" |
      | an unreadable file                                                             | INCONCLUSIVE with the reason "owner note missing" |
      | "picture: yes" in "NEMESIS-diag/airplay-receiver/", outside the "<timestamp>/" folder | INCONCLUSIVE with the reason "owner note missing" |

  @offline @boundary @AIR-076 @AT-76 @OD-179 @OD-180
  Scenario: A picture line in the checklist never counts as the owner's note
    Given a session that would be GO, apart from its note
    And the run's "<timestamp>/" folder has no "owner-note.txt"
    And its "run-checklist.txt" holds the line "picture: yes"
    When the evaluator judges the run
    Then the session's verdict is INCONCLUSIVE with the reason "owner note missing"
    And the run's verdict is INCONCLUSIVE

  @offline @boundary @AIR-076 @AT-76 @OD-179
  Scenario Outline: The checklist changes no verdict of a GO run
    Given a session that would be GO
    And the run's "run-checklist.txt" is <checklist fixture>
    When the evaluator judges the run
    Then the session's verdict is GO
    And the run's verdict is GO

    Examples:
      | checklist fixture   |
      | all six keys "yes"  |
      | all six keys "no"   |
      | absent              |

  @offline @boundary @AIR-054 @AIR-076 @AIR-079 @AT-54 @OD-179
  Scenario Outline: The checklist's sound answer changes no verdict
    Given a session that would be GO
    And the run's "run-checklist.txt" is <checklist fixture>
    When the evaluator judges the run
    Then the session's verdict is GO
    And the run's verdict is GO

    Examples:
      | checklist fixture                                                      |
      | all six keys "yes" except "sound on unit: no"                          |
      | all six keys "yes"                                                     |
      | absent                                                                 |

  @offline @boundary @AIR-079 @AT-79 @OD-180 @OD-182
  Scenario Outline: A checklist key counts only from exactly one yes or no line
    Given a synthetic stick copy of one run
    And its "run-checklist.txt" in the run's "<timestamp>/" folder holds all six keys "yes" except that <letterboxed lines>
    When the evaluator reads the checklist
    Then it reports "letterboxed" as "<value>"
    And it reports every other key as "yes"

    Examples:
      | letterboxed lines                                                  | value        |
      | "letterboxed: yes" is unchanged                                    | yes          |
      | the line is " Letterboxed : NO " and every line ends in CRLF       | no           |
      | the line is "letterboxed:"                                         | not recorded |
      | the line is "letterboxed: maybe"                                   | not recorded |
      | "letterboxed: yes" appears on two lines                            | not recorded |
      | there is no "letterboxed" line                                     | not recorded |

  @offline @boundary @AIR-079 @AT-79 @OD-180
  Scenario: Unknown-key and colon-less lines are named malformed and change no key
    Given a synthetic stick copy of one run
    And its "run-checklist.txt" holds all six keys "yes" plus the lines "secret text: yes" and "free text"
    When the evaluator reads the checklist
    Then it names both added lines as malformed
    And it reports every one of the six keys as "yes"

  @offline @boundary @AIR-079 @AT-79 @OD-180
  Scenario Outline: An unreadable or misplaced checklist records nothing
    Given a synthetic stick copy of one run
    And the run's checklist is <checklist fixture>
    When the evaluator reads the checklist
    Then it reports every one of the six keys as "not recorded"

    Examples:
      | checklist fixture                                                                                         |
      | an unreadable "run-checklist.txt" in the run's "<timestamp>/" folder                                     |
      | a "run-checklist.txt" with all six keys "yes" in "NEMESIS-diag/airplay-receiver/", outside the "<timestamp>/" folder |

  @offline @boundary @AIR-076 @AT-76 @OD-154
  Scenario: A yes note with two sessions that rendered frames is ambiguous
    Given a receiver log excerpt with two sessions, each with "connection accepted", a "codec h264" line, "decoder OMX.TCC.avcdec configured" and "first frame rendered"
    And the owner's note says "picture: yes"
    When the evaluator judges the run
    Then each session's verdict is INCONCLUSIVE with the reason "owner note ambiguous"
    And neither session is GO

  @offline @failure @AIR-076 @AT-76 @OD-155
  Scenario Outline: A start failure in the logs keeps the run from GO
    Given a session that would be GO
    And the run's logs also contain "<record>"
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "<reason>"
    And the run's verdict is not GO

    Examples:
      | record             | reason           |
      | port busy tcp 35000 | port busy        |
      | advertise failed   | advertise failed |

  @offline @failure @AIR-076 @AIR-079 @AT-76 @OD-162
  Scenario: A CarPlay listener present at start keeps the run from GO
    Given a session that would be GO
    And the run's logs also contain "carplay listener present"
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "carplay present"

  @offline @boundary @AIR-076 @AIR-079 @AT-76 @OD-166
  Scenario Outline: An unknown or missing CarPlay record counts as CarPlay absent
    Given a session that would be GO
    And the run's logs <carplay record>
    When the evaluator judges the run
    Then the run's verdict is GO
    And no CarPlay reason is recorded

    Examples:
      | carplay record                                           |
      | also contain "carplay listener unknown FileNotFoundException" |
      | contain no "carplay listener" record                     |

  @offline @boundary @AIR-076 @AIR-079 @AT-76 @OD-171
  Scenario: A present CarPlay listener wins over an unknown one in the same run
    Given a session that would be GO
    And the run's logs contain "carplay listener unknown FileNotFoundException" and "carplay listener present"
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "carplay present"

  @offline @boundary @AIR-076 @AT-76 @OD-171
  Scenario: A run with more than one run-level reason names each one
    Given a session that would be GO
    And the run's logs contain "port busy tcp 35000" and "carplay listener present"
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE
    And the record names both "port busy" and "carplay present"

  @offline @boundary @AIR-076 @AT-76 @OD-162
  Scenario: The observation deadline is launch plus 600 s, and the record names its timing
    Given a session that would be GO, with its first frame rendered at 120 s
    And the end marker at 700 s
    When the evaluator judges the run
    Then the record names the launch time at 0 s and the deadline at 600 s
    And the record names the actual endpoint at 600 s and the termination reason "timeout"

  @offline @boundary @AIR-076 @AT-76 @OD-162
  Scenario Outline: Only a first frame before the deadline counts
    Given a session that would be GO, with its first frame rendered at <frame time> s
    And the end marker at 700 s
    When the evaluator judges the run
    Then the session's verdict is <verdict>

    Examples:
      | frame time | verdict                                |
      | 599.999    | GO                                     |
      | 600.000    | INCONCLUSIVE with the reason "timeout" |

  @offline @boundary @AIR-076 @AT-76 @OD-162
  Scenario: A session still open at the deadline is a timeout
    Given a receiver log excerpt in which session 1 is accepted at 10 s with a "codec h264" line and "decoder OMX.TCC.avcdec configured", and no first frame or session end before 600 s
    And the end marker at 650 s
    And the owner's note says "picture: no"
    When the evaluator judges the run
    Then the session's verdict is INCONCLUSIVE with the reason "timeout"
    And the record names the launch, the deadline at 600 s, the endpoint and the reason "timeout"

  @offline @boundary @AIR-076 @AT-76 @OD-162
  Scenario: A run that ends before the deadline is observed up to its end marker
    Given a session that would be GO, with its first frame rendered at 30 s
    And the end marker at 200 s
    When the evaluator judges the run
    Then the session's verdict is GO
    And the record names the deadline at 600 s and the actual endpoint at 200 s, the end marker

  @offline @boundary @AIR-076 @AT-76 @OD-162
  Scenario: The deadline is taken from the run's first start marker
    Given a stick copy with two app starts, the second start marker at 300 s
    And a session in the second app start that would be GO, with its first frame rendered at 610 s
    When the evaluator judges the run
    Then the deadline is at 600 s, counted from the first start marker
    And the session's verdict is INCONCLUSIVE with the reason "timeout"

  @offline @normal @AIR-077 @AT-77 @OD-139
  Scenario: A unit result states its tier, build, model, iOS version and UxPlay commit
    Given a synthetic stick copy of one run judged as a unit run
    And its start marker names product "CVEV.CAN.0000.V015.011.260206" and UxPlay commit "05b0275cb1b726592700e47a6e0307544b3d3a52"
    And its session names the model "iPhone15,2"
    And the owner entered the iOS version "18.0 (22A0000)"
    When the evaluator writes the summary
    Then the summary states "Tier: unit"
    And it states the firmware build "CVEV.CAN.0000.V015.011.260206"
    And it states the model "iPhone15,2"
    And it states the iOS version "18.0 (22A0000)" exactly as entered
    And it states the UxPlay commit "05b0275cb1b726592700e47a6e0307544b3d3a52"

  @offline @normal @AIR-077 @AT-77
  Scenario: A result from offline fixtures is labelled offline and never unit
    Given a synthetic log fixture judged by the offline suite
    When the evaluator writes the summary
    Then the summary states "Tier: offline"
    And it never states "Tier: unit"
    And it says the result is a synthetic fixture result and not evidence about any iPhone

  @host @normal @AIR-079 @AT-79 @OD-173 @OD-179
  Scenario: The first on-unit run's procedure ends with the exit-and-CarPlay step
    Given the procedure for the first on-unit S3 run in the repository
    When its steps are read
    Then its last step tells the owner to exit ParkCast and confirm wireless CarPlay connects as it did before the run
    And that step records "carplay after exit: yes" or "carplay after exit: no" in "run-checklist.txt"
    And that step says not to record it in "owner-note.txt"
