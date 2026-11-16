@S2
Feature: Regulation evidence, the log cap and logging failures
  The stock regulation state is logged but never decides video. Receiver logs live in
  /data/nemesis/logs/airplay-receiver/, two rotated files capped at 5,242,880 bytes together. A
  logging failure never changes the gate and shows at most one notice per app start. No notice is
  on screen while another window covers the receiver (AIR-038 to AIR-041; OD-47, OD-49, OD-57 to
  OD-59, OD-73, OD-82, OD-85, OD-88, OD-101 to OD-103, OD-105 to OD-107, OD-111 to OD-125, OD-128,
  OD-129).

  Clock times, "runs for", "within 10 ms", the default Park getter behaviour, the ACC registration
  steps, the two on-screen notices and their texts, the UI double, "shown at S", "an attempt fails"
  and the default focus order at 0 ms are as defined in 09_parking_gate.feature.

  The focus double delivers onWindowFocusChanged(true) ("the receiver's window gains focus") and
  onWindowFocusChanged(false) ("the receiver's window loses focus") to the synthetic receiver window
  at the stated clock times. The broadcast double delivers com.hkmc.intent.action.REVERSE_ON and
  com.hkmc.intent.action.REVERSE_OFF intents to the receiver's runtime receiver at the stated clock
  times, and can make that receiver's registration throw or return an intent. The lifecycle double
  drives the receiver's activity glue through onCreate, onStart and onResume and the focus
  callbacks, and records the order in which those calls and the registration calls return. Unless a
  step says otherwise the REVERSE_ON registration returns null. "Covered" is as AIR-041 defines it:
  from process start until the first focus gain, from a focus loss until the next focus gain, from a
  REVERSE_ON until the next focus gain, from a registration that returns an intent until the next
  focus gain, and for the whole run if that registration throws (OD-120, OD-124, OD-128, OD-129). A
  notice attempt that falls due while the receiver is covered is "held": nothing is created, and the
  attempt is made at the time the receiver stops counting as covered (AIR-041(d)) (OD-122).

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

  @offline @failure @AIR-040 @AT-40 @OD-112 @OD-114 @OD-116
  Scenario Outline: The logging-failure notice is one toast window added when logging first fails
    Given the parking check is <check> and mirrored video is <video>
    When <fault> at 1000 ms
    And the receiver runs for 60000 ms
    Then exactly one logging-failure notice add call was made, at exactly 1000 ms
    And that add call has window type TYPE_TOAST and a view from the toast-view factory
    And its flags include FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE and not FLAG_KEEP_SCREEN_ON
    And its view's text is exactly "Receiver logging stopped working. Mirroring is unaffected."
    And no Toast.show() call, other window type, dialog, notification or activity was recorded
    And the gate's decision didn't change

    Examples:
      | check | video     | fault                                   |
      | on    | permitted | the log file fails to create            |
      | on    | blocked   | a log write fails                       |
      | off   | permitted | rotation fails to delete the older file |
      | off   | permitted | a log write fails                       |

  @offline @normal @AIR-040 @AT-40 @OD-116
  Scenario: The built package asks for no permission to show a notice
    When the built package's manifest is scanned
    Then it declares no SYSTEM_ALERT_WINDOW permission
    And it declares no permission that only the notice code uses

  @offline @boundary @AIR-040 @AT-40 @OD-115 @OD-116
  Scenario: The logging-failure notice stays on screen for exactly 5000 ms
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    When the receiver runs for 12000 ms
    Then the logging-failure notice was shown at 1000 ms
    And its window is on screen at 5999 ms
    And exactly one removal call for it was made, at exactly 6000 ms

  @offline @failure @AIR-040 @AT-40 @OD-58 @OD-115
  Scenario Outline: Nothing that happens while the logging-failure notice is up moves its removal
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    When <events>
    And the receiver runs for 12000 ms
    Then exactly one removal call for the logging-failure window was made, at exactly 6000 ms
    And no other logging-failure notice add call was made

    Examples:
      | events                                                 |
      | ACC status changes to 6 at 2000 ms and to 0 at 4000 ms |
      | ACC status changes to 2 at 2000 ms                     |
      | logging recovers at 3000 ms                            |
      | a log write fails again at 3000 ms                     |

  @offline @failure @AIR-040 @AT-40 @OD-107 @OD-111 @OD-113
  Scenario Outline: A logging-failure notice that fails to show is tried once more 5000 ms later
    Given the parking check is <check> and mirrored video is permitted
    And the UI double throws on <call> for the first logging-failure notice attempt
    When a log write fails at 1000 ms
    And the receiver runs for 60000 ms
    Then the first logging-failure notice attempt was made at 1000 ms
    And no logging-failure notice window exists from 1000 ms until 6000 ms
    And no logging-failure notice attempt was made at 5999 ms
    And exactly one more attempt, with the same text and form, was made at exactly 6000 ms
    And that window was removed by one removal call at exactly 11000 ms
    And no further logging-failure notice attempt was made
    And the gate's decision didn't change

    Examples:
      | check | call   |
      | on    | create |
      | on    | add    |
      | off   | create |
      | off   | add    |

  @offline @failure @AIR-040 @AT-40 @OD-58 @OD-107 @OD-111
  Scenario Outline: Further logging failures and recovery neither move nor cancel the notice's retry
    Given the parking check is on, on a supported build
    And the UI double throws on add for the first logging-failure notice attempt
    When a log write fails at 1000 ms
    And <events>
    And the receiver runs for 60000 ms
    Then logging-failure notice attempts were made only at 1000 ms and 6000 ms

    Examples:
      | events                                                             |
      | a log write fails again at 2000 ms                                 |
      | logging recovers at 3000 ms                                        |
      | logging recovers at 3000 ms and a log write fails again at 4000 ms |
      | a log write fails again at 8000 ms                                 |

  @offline @failure @AIR-040 @AT-40 @OD-107 @OD-111
  Scenario Outline: A logging-failure notice whose retry also fails is given up
    Given the parking check is on, on a supported build
    And the UI double throws on <call> for both logging-failure notice attempts
    When a log write fails at 1000 ms
    And a log write fails again at 20000 ms
    And the receiver runs for 61000 ms
    Then logging-failure notice attempts were made only at 1000 ms and 6000 ms
    And no logging-failure notice window exists at any time

    Examples:
      | call   |
      | create |
      | add    |

  @offline @failure @AIR-040 @AT-40 @OD-100 @OD-107 @OD-111 @OD-113
  Scenario Outline: ACC broadcasts do not move the logging-failure notice's retry, even into sleep
    Given the parking check is on, on a supported build
    And the UI double throws on add for the first logging-failure notice attempt
    When a log write fails at 1000 ms
    And <events>
    And the receiver runs for 12000 ms
    Then logging-failure notice attempts were made only at 1000 ms and 6000 ms
    And that window was removed by one removal call at exactly 11000 ms

    Examples:
      | events                                                 |
      | ACC status changes to 6 at 2000 ms and to 0 at 4000 ms |
      | ACC status changes to 2 at 2000 ms                     |
      | ACC status changes to 6 at 2000 ms and never resumes   |

  @offline @failure @AIR-040 @AT-40 @OD-107 @OD-111
  Scenario Outline: Exit drops the logging-failure notice's pending retry
    Given the parking check is on, on a supported build
    And the UI double throws on add for the first logging-failure notice attempt
    When a log write fails at 1000 ms
    And the receiver starts exit at <exit> ms
    Then no logging-failure notice attempt was made after 1000 ms
    And exit completes without waiting for the retry
    And no logging-failure notice window exists after exit

    Examples:
      | exit |
      | 4000 |
      | 6000 |

  @offline @failure @AIR-040 @AT-40 @OD-107 @OD-111
  Scenario: After exit dropped a logging notice's retry, the next start's first failure makes a new first attempt
    Given during the previous app start the first logging-failure notice attempt failed and exit came at 4000 ms
    When the receiver starts again and a log write fails at 500 ms
    And the receiver runs for 12000 ms
    Then the first logging-failure notice attempt since the restart was made at 500 ms
    And no removal call was made for any window of the previous app start

  @offline @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-107 @OD-111
  Scenario Outline: Two notices that both fail to show are each retried at the same time
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    And the UI double throws on <call> for the first attempt of each notice
    When the receiver runs for 12000 ms
    Then registration-failure notice attempts were made only at 0 ms and 5000 ms
    And logging-failure notice attempts were made only at 0 ms and 5000 ms

    Examples:
      | call   |
      | create |
      | add    |

  @offline @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-107 @OD-111 @OD-118
  Scenario Outline: When only one of two notices fails to show, only that one is retried
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    And the UI double throws on add for the first <failing> notice attempt
    When the receiver runs for 12000 ms
    Then <failing> notice attempts were made only at 0 ms and 5000 ms
    And exactly one <other> notice add call was made, at 0 ms
    And the <other> window was removed by one removal call at exactly 5000 ms

    Examples:
      | failing              | other                |
      | registration-failure | logging-failure      |
      | logging-failure      | registration-failure |

  @offline @normal @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-118
  Scenario Outline: Two notices added together keep their positions in either order
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    And the <first> notice is added first
    When the receiver runs for 12000 ms
    Then both notice windows exist together from 0 ms until 5000 ms
    And both windows have gravity Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL and x=0
    And the registration-failure window has y=Y and the logging-failure window has y=Y+H
    And no updateViewLayout call was made

    Examples:
      | first                |
      | registration-failure |
      | logging-failure      |

  @offline @normal @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-118 @OD-123
  Scenario Outline: A notice on screen alone keeps its own slot
    Given <setup>
    When the receiver runs for 12000 ms
    Then the <notice> window has y=<y>
    And no updateViewLayout call was made

    Examples:
      | setup                                                                    | notice               | y   |
      | the parking check is on and a log write fails at 1000 ms                 | logging-failure      | Y+H |
      | the parking check is off and a log write fails at 1000 ms                | logging-failure      | Y+H |
      | the parking check is on and ACC registration throws an exception at 0 ms | registration-failure | Y   |

  @offline @boundary @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-121
  Scenario: A toast gravity at the top of the screen falls back to the bottom centre
    Given the parking check is on, on a supported build
    And the UI double supplies gravity Gravity.TOP and x=40
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    When the receiver runs for 12000 ms
    Then both windows have gravity Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL and x=0
    And the registration-failure window has y=Y and the logging-failure window has y=Y+H

  @offline @failure @AIR-040 @AT-40 @OD-111 @OD-121
  Scenario Outline: A notice whose view cannot be measured counts as a failed attempt
    Given the parking check is on, on a supported build
    And the UI double's measurement of the first logging-failure view <measure>
    When a log write fails at 1000 ms
    And the receiver runs for 12000 ms
    Then no logging-failure window was added at 1000 ms
    And exactly one more attempt was made at exactly 6000 ms

    Examples:
      | measure          |
      | throws           |
      | returns height 0 |

  @offline @normal @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-118 @OD-123
  Scenario: A logging notice added while the registration notice is up keeps its raised slot after it goes
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    When a log write fails at 2000 ms
    And the receiver runs for 12000 ms
    Then the registration-failure window was removed at exactly 5000 ms
    And the logging-failure window was removed at exactly 7000 ms
    And the logging-failure window has y=Y+H from 5000 ms to 7000 ms
    And no updateViewLayout call was made

  @offline @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-118
  Scenario Outline: One notice's failed call does not change the other notice
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    And the UI double throws on <call> for the <failing> notice
    When the receiver runs for 12000 ms
    Then exactly one <other> notice add call was made, at 0 ms
    And the <other> window was removed by one removal call at exactly 5000 ms

    Examples:
      | call   | failing              | other                |
      | add    | registration-failure | logging-failure      |
      | add    | logging-failure      | registration-failure |
      | remove | registration-failure | logging-failure      |
      | remove | logging-failure      | registration-failure |

  @offline @failure @AIR-040 @AT-40 @OD-119
  Scenario: A removal that throws is tried once more 5000 ms later
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on the logging-failure window's removal at 6000 ms
    When the receiver runs for 20000 ms
    Then no removal call was made at 10999 ms
    And exactly one more removal call was made, at exactly 11000 ms
    And no other logging-failure notice add call was made
    And no registration-failure notice was shown

  @offline @failure @AIR-040 @AT-40 @OD-119
  Scenario: A window whose second removal also throws is left until exit
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on both removal calls for the logging-failure window
    When the receiver starts exit at 40000 ms
    Then removal calls for that window were made only at 6000 ms, 11000 ms and 40000 ms
    And exit completes

  @offline @failure @AIR-040 @AT-40 @OD-116
  Scenario Outline: Exit removes a logging-failure notice still on screen once
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And <setup>
    When the receiver starts exit at <exit> ms
    Then exactly one removal call for the logging-failure window was made at <exit> ms
    And no removal call was made after <exit> ms
    And exit completes

    Examples:
      | setup                                                                   | exit  |
      | nothing else happens                                                    | 3000  |
      | the UI double throws on removal at 3000 ms                              | 3000  |
      | nothing else happens                                                    | 6000  |
      | the UI double throws on the logging-failure window's removal at 6000 ms | 11000 |

  @offline @normal @AIR-040 @AT-40 @OD-116
  Scenario: The next app start leaves the previous run's notice windows alone
    Given during the previous app start a logging-failure notice was shown and exit came at 3000 ms
    When the receiver starts again
    And the receiver runs for 12000 ms
    Then no removal call was made for any window of the previous app start
    And no window of the previous app start is on screen

  @offline @normal @AIR-040 @AT-40 @OD-58 @OD-119
  Scenario Outline: Notice faults leave the gate and every other trace unchanged
    Given the parking check is <check> and mirrored video is permitted
    And a log write fails at 1000 ms
    And <fault>
    When the receiver runs for 20000 ms
    Then the gate decision, effective flag value, Park getter schedule, registration calls, and audio and connection traces match a run without <fault>

    Examples:
      | check | fault                                                                     |
      | on    | the UI double throws on create for both logging-failure notice attempts   |
      | on    | the UI double throws on both removal calls for the logging-failure window |
      | off   | the UI double throws on add for the first logging-failure notice attempt  |
      | off   | the UI double throws on both removal calls for the logging-failure window |

  @offline @failure @AIR-041 @AT-41 @OD-121
  Scenario: A receiver that never gains focus never shows a notice
    Given the parking check is on, on a supported build
    And the receiver's window never gains focus
    And ACC registration throws an exception at 0 ms
    When the receiver starts exit at 60000 ms
    Then no notice add call was made
    And no removal call was made
    And exit completes

  @offline @normal @AIR-041 @AT-41 @OD-117 @OD-120
  Scenario Outline: Losing focus removes a notice on screen at once
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    When the receiver's window loses focus at <loss> ms
    And the receiver runs for 20000 ms
    Then exactly one removal call for the logging-failure window was made, at <loss> ms, inside the focus callback
    And no other removal call for it was made

    Examples:
      | loss |
      | 2000 |
      | 6000 |

  @offline @normal @AIR-041 @AT-41 @OD-117 @OD-120
  Scenario: Losing focus removes both notices at once
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    When the receiver's window loses focus at 1000 ms
    And the receiver runs for 12000 ms
    Then exactly one removal call per notice window was made, each at 1000 ms
    And no other removal call was made

  @offline @failure @AIR-041 @AT-41 @OD-125
  Scenario: A removal that throws while covered is tried every 1000 ms
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on every removal call for the logging-failure window
    When the receiver's window loses focus at 2000 ms
    And the receiver runs for 9500 ms
    Then removal calls for that window were made only at 2000, 3000, 4000, 5000, 6000, 7000, 8000 and 9000 ms

  @offline @failure @AIR-041 @AT-41 @OD-125
  Scenario: The 1000 ms removal tries stop once one succeeds
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on the removal calls at 2000 ms and 3000 ms and lets the one at 4000 ms return
    When the receiver's window loses focus at 2000 ms
    And the receiver runs for 20000 ms
    Then removal calls for that window were made only at 2000, 3000 and 4000 ms

  @offline @failure @AIR-041 @AT-41 @OD-119 @OD-125 @OD-128
  Scenario Outline: A window still not removed when focus returns is tried once more 5000 ms after
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on every removal call for the logging-failure window
    When the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at <gain> ms
    And the receiver runs for 20000 ms
    Then the last removal call while covered was made at <last> ms
    And no removal call was made after <gain> ms and before <retry> ms
    And exactly one removal call was made at <retry> ms
    And no further removal call was made

    Examples:
      | gain | last | retry |
      | 5500 | 5000 | 10500 |
      | 5000 | 5000 | 10000 |

  @offline @failure @AIR-041 @AT-41 @OD-119 @OD-125
  Scenario: A window that still cannot be removed after focus returns gets one more call at exit
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on every removal call for the logging-failure window
    When the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 5500 ms
    And the receiver starts exit at 30000 ms
    Then removal calls for that window after 5500 ms were made only at 10500 ms and 30000 ms
    And exit completes

  @offline @failure @AIR-041 @AT-41 @OD-125
  Scenario: Losing focus again restarts the removal tries for a window that could not be removed
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on every removal call for the logging-failure window
    When the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 5500 ms
    And the receiver's window loses focus at 15000 ms
    And the receiver runs for 17500 ms
    Then removal calls for that window after 5500 ms were made only at 10500, 15000, 16000 and 17000 ms

  @offline @failure @AIR-041 @AT-41 @OD-119 @OD-125
  Scenario: A focus loss after a failed timed removal makes one call at once and none at the timed retry
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on the logging-failure window's removal at 6000 ms
    When the receiver's window loses focus at 8000 ms
    And the receiver runs for 20000 ms
    Then removal calls for that window were made only at 6000 ms and 8000 ms

  @offline @failure @AIR-041 @AT-41 @OD-119 @OD-125
  Scenario: A focus loss after a failed timed removal falls back to 1000 ms tries if that call throws too
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    And the UI double throws on every removal call for the logging-failure window
    When the receiver's window loses focus at 8000 ms
    And the receiver runs for 11500 ms
    Then removal calls for that window were made only at 6000, 8000, 9000, 10000 and 11000 ms

  @offline @normal @AIR-041 @AT-41 @OD-122
  Scenario: A notice removed by covering is not shown again
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    When the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 3000 ms
    And a log write fails again at 4000 ms
    And the receiver runs for 20000 ms
    Then exactly one logging-failure notice add call was made, at 1000 ms
    And no logging-failure notice attempt was made after 1000 ms

  @offline @normal @AIR-019 @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario Outline: REVERSE_ON removes a notice on screen at once
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    When REVERSE_ON is delivered at 1000 ms <extras>
    And the receiver runs for 12000 ms
    Then exactly one removal call for the registration-failure window was made, at 1000 ms, inside the broadcast callback
    And no other removal call for it was made

    Examples:
      | extras         |
      | with no extras |
      | with extras    |

  @offline @normal @AIR-019 @AIR-041 @AT-41 @OD-122 @OD-124 @OD-128
  Scenario: A logging failure after REVERSE_ON is held until focus is regained
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    When REVERSE_ON is delivered at 1000 ms
    And a log write fails at 2000 ms
    And the receiver's window loses focus at 2500 ms
    And the receiver's window gains focus at 9000 ms
    And the receiver runs for 20000 ms
    Then no view was created, measured or added from 1000 ms until 9000 ms
    And exactly one logging-failure notice add call was made, at exactly 9000 ms
    And it was removed at exactly 14000 ms
    And no registration-failure notice add call was made after 0 ms

  @offline @failure @AIR-019 @AIR-041 @AT-41 @OD-125
  Scenario: A removal at REVERSE_ON that throws is tried every 1000 ms
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And the UI double throws on every removal call for the registration-failure window
    When REVERSE_ON is delivered at 1000 ms
    And the receiver runs for 4500 ms
    Then removal calls for that window were made only at 1000, 2000, 3000 and 4000 ms

  @offline @normal @AIR-041 @AT-41 @OD-122 @OD-124
  Scenario: REVERSE_ON while focus is already lost leaves held attempts for the next focus gain
    Given the parking check is on, on a supported build
    And the receiver's window loses focus at 500 ms
    When a log write fails at 1000 ms
    And REVERSE_ON is delivered at 2000 ms
    And the receiver's window gains focus at 4000 ms
    And the receiver runs for 12000 ms
    Then exactly one logging-failure notice add call was made, at exactly 4000 ms

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: REVERSE_ON with no later focus change keeps notices off screen until exit
    Given the parking check is on, on a supported build
    When REVERSE_ON is delivered at 1000 ms
    And a log write fails at 2000 ms
    And the receiver starts exit at 60000 ms
    Then no notice add call was made
    And exit makes no held attempt
    And exit completes

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: A second REVERSE_ON while covered changes nothing
    Given the parking check is on, on a supported build
    And a log write fails at 1000 ms
    When REVERSE_ON is delivered at 2000 ms
    And REVERSE_ON is delivered again at 3000 ms
    And the receiver runs for 12000 ms
    Then the only removal call was made at 2000 ms
    And exactly one record containing "notice cover" was written after 1000 ms

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario Outline: REVERSE_OFF changes nothing about covering
    Given the parking check is on, on a supported build
    And <setup>
    When REVERSE_OFF is delivered at 5000 ms <extras>
    And the receiver runs for 20000 ms
    Then the covered state, removal calls and held attempts match the same run without REVERSE_OFF

    Examples:
      | setup                                                               | extras         |
      | REVERSE_ON is delivered at 2000 ms and a log write fails at 3000 ms | with no extras |
      | REVERSE_ON is delivered at 2000 ms and a log write fails at 3000 ms | with extras    |
      | a log write fails at 3000 ms                                        | with no extras |
      | a log write fails at 3000 ms                                        | with extras    |

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: A REVERSE_ON registration that returns an intent counts as covered until focus is gained
    Given the parking check is on, on a supported build
    And the REVERSE_ON registration returns an intent at 0 ms
    And no focus gain is delivered before that registration call returns
    When a log write fails at 1000 ms
    And the receiver's window gains focus at 3000 ms
    And the receiver runs for 12000 ms
    Then no view was created, measured or added before 3000 ms
    And exactly one logging-failure notice add call was made, at exactly 3000 ms
    And it was removed at exactly 8000 ms
    And exactly one record containing "reverse_on received" was written, at 0 ms
    And no record containing "notice cover" was written before 3000 ms

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario Outline: The REVERSE_ON registration returns before the window can first gain focus
    Given the parking check is <flag>, on a supported build
    And the lifecycle double runs onCreate, onStart and onResume at 0 ms and delivers the first focus gain as soon as onResume returns
    When the receiver starts
    Then the recorder shows the REVERSE_ON registration call returned before onResume returned
    And the recorder shows it returned before the first onWindowFocusChanged(true) was delivered
    And exactly one REVERSE_ON registration call was made

    Examples:
      | flag |
      | on   |
      | off  |

  @offline @boundary @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: A REVERSE_ON with no focus change after it keeps notices off until exit
    Given the parking check is on, on a supported build
    And REVERSE_ON is delivered at 500 ms
    When a log write fails at 1000 ms
    And the receiver runs for 60000 ms and then exits
    Then no view was created, measured or added at any time
    And no removal call was made, before or at exit
    And exactly one record containing "notice cover", "covered=yes" and "cause=reverse" was written, at 500 ms
    And no record containing "covered=no" was written

  @offline @failure @AIR-019 @AIR-041 @AT-41 @OD-129
  Scenario: A REVERSE_ON registration that throws keeps every notice off screen for the run
    Given the parking check is on, on a supported build
    And the REVERSE_ON registration throws at 0 ms
    When ACC registration throws an exception at 0 ms
    And a log write fails at 1000 ms
    And the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 3000 ms
    And the receiver starts exit at 60000 ms
    Then no notice add call was made
    And exit completes

  @offline @failure @AIR-041 @AT-41 @OD-129
  Scenario: A REVERSE_ON registration that throws is recorded once and never retried
    Given the parking check is on, on a supported build
    And the REVERSE_ON registration throws at 0 ms
    When the receiver's window gains focus at 3000 ms
    And the receiver starts exit at 60000 ms
    Then exactly one record containing "reverse registration failed" was written
    And the REVERSE_ON registration was attempted only once
    And no unregisterReceiver call was made for it at exit
    And exit completes

  @offline @normal @AIR-041 @AT-41 @OD-128
  Scenario Outline: The REVERSE_ON receiver is unregistered at exit
    Given the parking check is on, on a supported build
    And unregistering the REVERSE_ON receiver <unregister>
    When the receiver starts exit at 4000 ms
    Then exactly one unregisterReceiver call for the REVERSE_ON receiver was made, at 4000 ms
    And exit completes

    Examples:
      | unregister |
      | returns    |
      | throws     |

  @offline @normal @AIR-041 @AT-41 @OD-128
  Scenario: The next app start registers the REVERSE_ON receiver again
    Given during the previous app start the REVERSE_ON receiver was registered and exit came at 4000 ms
    When the receiver starts again
    Then the REVERSE_ON registration was attempted once since the restart

  @offline @normal @AIR-041 @AT-41 @OD-122
  Scenario: A logging failure while covered is held and shown when focus returns, for the full 5000 ms
    Given the parking check is on, on a supported build
    And the receiver's window loses focus at 500 ms
    When a log write fails at 1000 ms
    And a log write fails again at 2000 ms
    And the receiver's window gains focus at 3000 ms
    And the receiver runs for 12000 ms
    Then no view was created, measured or added before 3000 ms
    And exactly one logging-failure notice add call was made, at exactly 3000 ms
    And it was removed at exactly 8000 ms

  @offline @failure @AIR-041 @AT-41 @OD-107 @OD-122
  Scenario: A retry that falls due while covered is made when focus returns
    Given the parking check is on, on a supported build
    And the UI double throws on add for both logging-failure notice attempts
    When a log write fails at 1000 ms
    And the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 9000 ms
    And the receiver runs for 61000 ms
    Then logging-failure notice attempts were made only at 1000 ms and 9000 ms

  @offline @failure @AIR-041 @AT-41 @OD-107 @OD-122 @OD-128
  Scenario: A held first attempt that fails when focus returns is retried 5000 ms after the focus gain
    Given the parking check is on, on a supported build
    And the UI double throws on add for the first logging-failure notice attempt
    And the receiver's window loses focus at 500 ms
    When a log write fails at 1000 ms
    And the receiver's window gains focus at 3000 ms
    And the receiver runs for 12000 ms
    Then logging-failure notice attempts were made only at 3000 ms and 8000 ms

  @offline @failure @AIR-041 @AT-41 @OD-107 @OD-122
  Scenario: A retry is held again if focus is lost before it falls due
    Given the parking check is on, on a supported build
    And the UI double throws on add for the first logging-failure notice attempt
    And the receiver's window loses focus at 500 ms
    When a log write fails at 1000 ms
    And the receiver's window gains focus at 3000 ms
    And the receiver's window loses focus at 5000 ms
    And the receiver's window gains focus at 11000 ms
    And the receiver runs for 20000 ms
    Then logging-failure notice attempts were made only at 3000 ms and 11000 ms

  @offline @normal @AIR-019 @AIR-040 @AIR-041 @AT-41 @OD-118 @OD-122
  Scenario: Two held notices are both added when focus returns, each in its own slot
    Given the parking check is on, on a supported build
    And the receiver's window loses focus at 0 ms
    When ACC registration throws an exception at 100 ms
    And a log write fails at 1000 ms
    And the receiver's window gains focus at 3000 ms
    And the receiver runs for 12000 ms
    Then both notices were added at exactly 3000 ms, in either order
    And the registration-failure window has y=Y and the logging-failure window has y=Y+H

  @offline @failure @AIR-019 @AIR-041 @AT-41 @OD-113 @OD-122
  Scenario: A retry held in a registration-failure run is not made while focus stays lost
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And the UI double throws on add for the first logging-failure notice attempt
    When a log write fails at 1000 ms
    And the receiver's window loses focus at 2000 ms
    And the receiver runs for 60000 ms
    Then logging-failure notice attempts were made only at 1000 ms

  @offline @normal @AIR-041 @AT-41 @OD-122
  Scenario: Exit while an attempt is held makes no attempt
    Given the parking check is on, on a supported build
    And the receiver's window loses focus at 500 ms
    And a log write fails at 1000 ms
    When the receiver starts exit at 3000 ms
    Then no notice add call was made
    And exit completes without waiting
    And the next app start's first logging failure makes its own first attempt

  @offline @normal @AIR-041 @AT-41 @OD-120 @OD-128
  Scenario Outline: Each focus change that changes the covered state is recorded once
    Given the parking check is on, on a supported build
    When the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 4000 ms
    And the receiver runs for 6000 ms
    Then exactly one record containing "notice cover", "cause=focus" and "<covered>" was written, at <t> ms

    Examples:
      | covered     | t    |
      | covered=yes | 2000 |
      | covered=no  | 4000 |

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: A REVERSE_ON is recorded on receipt and as a cover start
    Given the parking check is on, on a supported build
    When REVERSE_ON is delivered at 2000 ms
    And REVERSE_ON is delivered again at 3000 ms
    And the receiver runs for 6000 ms
    Then a record containing "reverse_on received" was written at 2000 ms and at 3000 ms
    And exactly one record containing "notice cover", "cause=reverse" and "covered=yes" was written, at 2000 ms

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: A focus loss while already covered by REVERSE_ON writes no cover record
    Given the parking check is on, on a supported build
    When REVERSE_ON is delivered at 2000 ms
    And the receiver's window loses focus at 3000 ms
    And the receiver runs for 6000 ms
    Then no record containing "notice cover" was written at 3000 ms

  @offline @normal @AIR-041 @AT-41 @OD-128
  Scenario: Process start writes no cover record
    Given the parking check is on, on a supported build
    When the receiver runs for 6000 ms
    Then no record containing "notice cover" was written

  @offline @failure @AIR-040 @AIR-041 @AT-41 @OD-122 @OD-128
  Scenario: A cover record that cannot be written gives a logging notice shown at the next focus gain
    Given the parking check is on, on a supported build
    And writing the "notice cover" record at 2000 ms fails
    When the receiver's window loses focus at 2000 ms
    And the receiver's window gains focus at 5000 ms
    And the receiver runs for 12000 ms
    Then no logging-failure notice view was created before 5000 ms
    And exactly one logging-failure notice add call was made, at exactly 5000 ms

  @offline @normal @AIR-041 @AT-41 @OD-117
  Scenario Outline: Covering leaves the gate and every other trace unchanged
    Given the installed receiver.conf holds "video.require_park=<flag>"
    And the Park getter replies Park within 10 ms
    And a log write fails at 1000 ms
    When <cover>
    And the receiver runs for 20000 ms
    Then the gate decision, effective flag value, Park getter schedule, registration calls, and audio and connection traces match a run without covering
    And mirrored video stays permitted throughout

    Examples:
      | flag  | cover                                                                |
      | true  | the receiver's window loses focus at 2000 ms and gains it at 9000 ms |
      | true  | REVERSE_ON is delivered at 2000 ms and REVERSE_OFF at 4000 ms        |
      | false | the receiver's window loses focus at 2000 ms and gains it at 9000 ms |
      | false | REVERSE_ON is delivered at 2000 ms and REVERSE_OFF at 4000 ms        |

  @offline @normal @AIR-041 @AT-41 @OD-124 @OD-128
  Scenario: Covering is wired to REVERSE_ON through a runtime receiver only
    When the receiver's sources and source AndroidManifest.xml are scanned
    Then the sources register a runtime receiver for com.hkmc.intent.action.REVERSE_ON and unregister it at exit
    And the manifest declares no receiver for REVERSE_ON
    And the sources contain no REVERSE_OFF or isRearViewOn reference
    And the sources read none of REVERSE_ON's extras
