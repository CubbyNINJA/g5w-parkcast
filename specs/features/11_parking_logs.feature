@S2
Feature: Regulation evidence, the log cap and logging failures
  The stock regulation state is logged but never decides video. Receiver logs live in
  /data/nemesis/logs/airplay-receiver/, two rotated files capped at 5,242,880 bytes together. A
  logging failure never changes the gate and shows at most one notice per app start (AIR-038 to
  AIR-040; OD-47, OD-49, OD-57 to OD-59, OD-73, OD-82, OD-85, OD-88, OD-101 to OD-103, OD-105).

  Clock times, "runs for", "within 10 ms", the default Park getter behaviour, the ACC registration
  steps and the two on-screen notices are as defined in 09_parking_gate.feature.

  The "current" log file is the one being written; the "older" file is the one kept from the last
  rotation. A record's size counts every byte written, including its line terminator. Rotation
  makes the current file the older file and starts a new, empty current file.

  @offline @normal @AIR-038 @AT-38 @OD-59
  Scenario: Regulation state is logged at start and on every change, and unavailable is named
    Given the stock regulation state is "restricted" when the receiver starts
    When it changes to "unrestricted" and then becomes unavailable because the service is lost
    Then the log has the regulation state at start and at each change, in order
    And the unavailable state is logged as "unavailable" with the reason

  @offline @normal @AIR-038 @AT-38 @OD-47
  Scenario Outline: Regulation state never blocks or allows video
    Given the parking check is on, on a supported build
    And the Park getter replies <gear> within 10 ms
    When the regulation state changes to <regulation> at 500 ms
    And the receiver runs for 1000 ms
    Then mirrored video stays <video> throughout

    Examples:
      | gear | regulation   | video     |
      | 1    | restricted   | permitted |
      | 1    | unavailable  | permitted |
      | 0    | unrestricted | blocked   |
      | 0    | unavailable  | blocked   |

  @offline @boundary @AIR-039 @AT-39 @OD-57 @OD-73
  Scenario Outline: The current log file rotates on reaching 2,621,440 bytes, not before
    Given the current log file holds <before> bytes and the older log file holds 1000 bytes
    When a record of <size> bytes is logged
    Then the whole record is in the file that held <before> bytes
    And that file <rotation>
    And the older log file <older>

    Examples:
      | before  | size    | rotation                                         | older                       |
      | 2621430 | 9       | holds 2621439 bytes and was not rotated          | is kept                     |
      | 2621430 | 10      | holds exactly 2621440 bytes and was then rotated | was deleted as a whole file |
      | 0       | 2621440 | holds exactly 2621440 bytes and was then rotated | was deleted as a whole file |

  @offline @boundary @AIR-039 @AT-39 @OD-73 @OD-82
  Scenario Outline: A record that would carry the current file past 2,621,440 bytes goes whole into a new file
    Given the current log file holds <before> bytes and the older log file holds 1000 bytes
    When a record of <size> bytes is logged
    Then the current log file was rotated before the record was written
    And the older log file was deleted as a whole file
    And the whole record is in the new current log file
    And the rotated file contains no part of the record

    Examples:
      | before  | size |
      | 2621430 | 11   |
      | 2621439 | 2    |

  @offline @boundary @AIR-039 @AT-39 @OD-57 @OD-73 @OD-82
  Scenario: A record of exactly 2,621,440 bytes into a non-empty current file rotates before and after it is written
    Given the current log file holds 1000 bytes and the older log file holds 1000 bytes
    When a record of 2621440 bytes is logged
    Then the current log file was rotated before the record was written
    And the older 1000-byte log file was deleted as a whole file at that rotation
    And the whole record is in one file, which holds exactly 2621440 bytes and was then rotated
    And the file that was current before the record was deleted as a whole file at that second rotation
    And no rotated file contains part of the record
    And at no point did the log files together hold more than 5242880 bytes

  @offline @boundary @AIR-039 @AT-39 @OD-82 @OD-85 @OD-88
  Scenario: A record larger than 2,621,440 bytes is dropped, and a line says so
    Given the current log file holds 1000 bytes and the older log file holds 1000 bytes
    When a record of 2621441 bytes is logged
    Then no part of that record is in either log file
    And exactly one line was logged in its place
    And that line contains "record too large" and "2621441"

  @offline @boundary @AIR-039 @AT-39 @OD-82 @OD-85 @OD-88
  Scenario: The "record too large" line rotates like any other record
    Given the current log file holds 2621439 bytes and the older log file holds 1000 bytes
    When a record of 2621441 bytes is logged
    Then the current log file was rotated before the "record too large" line was written
    And the older log file was deleted as a whole file
    And the whole "record too large" line is in the new current log file

  @offline @normal @AIR-039 @AT-39 @OD-57 @OD-73
  Scenario Outline: The logs are capped at 5 MiB together, dropping the oldest whole file
    Given the receiver's log folder is empty
    When records of <size> bytes each are logged until the current file has rotated at least 5 times
    Then the logs are in /data/nemesis/logs/airplay-receiver/
    And at no point did the log files together hold more than 5242880 bytes
    And at no point did either log file hold more than 2621440 bytes
    And every record still present is whole and in exactly one file
    And no record was trimmed

    Examples:
      | size    |
      | 100     |
      | 1000000 |
      | 1700000 |
      | 2621440 |

  @offline @failure @AIR-040 @AT-40 @OD-58
  Scenario Outline: A logging failure shows one notice per app start and never changes the gate
    Given the parking check is <check> and mirrored video is <video>
    When logging fails by <fault>, recovers, and then fails again
    Then exactly one on-screen logging notice was shown
    And the gate's decision didn't change

    Examples:
      | check | video     | fault                          |
      | on    | permitted | the log file failing to create |
      | on    | blocked   | a write failing                |
      | off   | permitted | rotation failing to delete     |
      | on    | permitted | a write failing                |

  @offline @normal @AIR-040 @AT-40 @OD-58
  Scenario: After a restart, the first logging failure shows a new notice
    Given one logging notice was shown during the previous app start
    When the receiver restarts and logging fails
    Then exactly one on-screen logging notice was shown since the restart

  @offline @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-58 @OD-101 @OD-102
  Scenario Outline: A logging failure and an ACC registration failure each show their own notice once
    Given the parking check is on, on a supported build
    And <failures>
    When a log write fails again at 4000 ms
    And the receiver runs for 5000 ms
    Then exactly one logging-failure notice was shown
    And exactly one registration-failure notice was shown

    Examples:
      | failures                                                                                             |
      | a log write fails at 0 ms, and ACC registration throws an exception at 50 ms                         |
      | ACC registration throws an exception at 0 ms, and a log write fails at 1000 ms                       |
      | ACC registration throws an exception at 0 ms, and writing the "ACC registration failed" record fails |
      | ACC registration returns no sticky state at 0 ms, and a log write fails at 1000 ms                   |

  @offline @failure @AIR-021 @AIR-025 @AIR-040 @AT-21 @AT-25 @AT-40 @OD-58 @OD-103 @OD-105
  Scenario Outline: With the check off, a failed log write shows one logging-failure notice and nothing else
    Given the installed receiver.conf holds "video.require_park=false"
    And ACC registration <registration> at 0 ms
    And <write> fails
    When a log write fails again at 4000 ms
    And the receiver runs for 5000 ms with mirroring active
    Then exactly one logging-failure notice was shown
    And no registration-failure notice was shown
    And the parking check is off
    And the parking gate never blocked video

    Examples:
      | registration            | write                                             |
      | returns sticky status 0 | writing the "parking check off" record            |
      | throws an exception     | writing the "ACC registration unavailable" record |
      | returns no sticky state | writing the "ACC registration unavailable" record |
      | returns sticky status 0 | the first log write after 1000 ms                 |
