@S2
Feature: Mirrored video only in Park, read from the stock Park getter
  With the parking check on, the receiver shows mirrored video only while an on-time reading from
  VehicleInfoManager.getGearPStatus() says Park on a supported build. It polls that getter every
  200 ms, one call at a time; a call not answered within 200 ms blocks video. Audio and the iPhone
  connection are never touched by the gate (AIR-016 to AIR-022, AIR-024, AIR-027, AIR-037,
  AIR-040, AIR-041; OD-23, OD-24, OD-46, OD-47, OD-51, OD-56, OD-61 to OD-64, OD-72, OD-83, OD-84,
  OD-87, OD-88, OD-91, OD-100 to OD-107, OD-111 to OD-116, OD-118, OD-119, OD-121, OD-122).

  Times are in milliseconds on a controlled clock. "Within 10 ms" means a reply received 10 ms
  after the call starts. A supported build is one whose ro.build.product starts with "CVEV.".

  "Runs for N ms" covers clock times 0 to N inclusive. "Was called only at" lists every call started
  in that span and no others.

  Unless a step says otherwise, the car is in Park, the build is supported, and every Park getter
  call replies with the car's gear state (1 in Park, 0 out of it) within 10 ms. "Video is permitted
  after an on-time Park reading at 0 ms" means the call started at 0 ms replied 1 at 10 ms.

  Sleep and resume come only from a synthetic sticky com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED
  broadcast. "ACC status" is its EXTRA_ACC_STATUS extra. "ACC registration returns sticky status S at
  T ms" means the runtime registration for that broadcast returns at T ms with a sticky broadcast
  whose extra is S. "ACC status changes to S at T ms" means a broadcast whose extra is S arrives at
  T ms. Unless a step says otherwise, registration returns sticky status 0 at 0 ms, so process start
  is a resume and the call started at 0 ms is the startup re-read. "The re-read" is the first Park
  getter call started after a resume.

  "Registration-failure notice" and "logging-failure notice" are the two on-screen notices of
  AIR-019(i) and AIR-040, as seen in the synthetic UI output. The registration-failure notice's text
  is exactly "Mirroring is off: the car's power state couldn't be read. Restart the receiver from NEMESIS Manager."
  (OD-106). The logging-failure notice's text is exactly "Receiver logging stopped working. Mirroring is unaffected."
  (OD-112). The UI double stands in for the window manager and the toast-view factory. Unless a step
  says otherwise it supplies gravity Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, x=0, y=Y and a
  measured view height H, and it records every add, removal and updateViewLayout call. "Shown at S"
  means that notice's add call returned at S ms. "An attempt fails" means the UI double threw on its
  create, measure or add call, so no window of it exists. Unless a step says otherwise the receiver's
  activity window has focus throughout, so it is not covered (AIR-041). With the check off, a
  registration failure shows no registration-failure notice, and with no logging fault the synthetic
  notice output stays empty (AIR-021(b), OD-103).

  Log records are matched on literal tokens (OD-104, OD-105). A record that contains "discarded"
  also contains "during sleep" or "after resume", and not the other. A record that contains
  "ignored" also contains "status=" followed by the status as a decimal integer, "status=missing"
  or "status=non-integer". receiver.conf contents are written as lines separated by " ; ".

  @offline @normal @AIR-016 @AT-16 @OD-24
  Scenario Outline: The package requires Park by default, and its flag can turn that off
    Given the installed receiver.conf is <config>
    When the receiver starts
    Then the parking check is <state>

    Examples:
      | config                                    | state |
      | the package's shipped file                | on    |
      | a file holding "video.require_park=false" | off   |

  @offline @normal @AIR-017 @AT-17 @OD-61
  Scenario Outline: Only an on-time 1 from the Park getter permits video
    Given the parking check is on, on a supported build
    And the Park getter replies <reply> within 10 ms
    When the receiver starts
    Then mirrored video is <result>

    Examples:
      | reply              | result    |
      | 1                  | permitted |
      | 0                  | blocked   |
      | 2                  | blocked   |
      | with no value      | blocked   |
      | with a failed call | blocked   |

  @offline @failure @AIR-017 @AT-17
  Scenario Outline: Signals other than the Park getter never establish Park
    Given the parking check is on, on a supported build
    And the Park getter replies <getter>
    And <signal>
    When the receiver starts
    Then mirrored video is blocked

    Examples:
      | getter                          | signal                                |
      | 0 within 10 ms                  | the car reports that it is stationary |
      | with no value within 10 ms      | the car reports that it is stationary |
      | with a failed call within 10 ms | the car reports that it is stationary |
      | 0 within 10 ms                  | the parking brake reads applied       |
      | with no value within 10 ms      | the parking brake reads applied       |
      | with a failed call within 10 ms | the parking brake reads applied       |

  @offline @normal @AIR-017 @AIR-024 @AT-17 @AT-24 @OD-61
  Scenario: Park is read with the same getter at start and every 200 ms, one call at a time
    Given the parking check is on, on a supported build
    And the Park getter replies 1 within 10 ms
    When the receiver runs for 1000 ms
    Then the Park getter was called only at 0, 200, 400, 600, 800 and 1000 ms
    And at most one Park getter call was active at any time
    And no Park listener was registered and no BCM status request was made

  @offline @boundary @AIR-017 @AT-17 @OD-62 @OD-63
  Scenario: A late reply is followed at once by the next call, which restarts the 200 ms cadence
    Given the parking check is on, on a supported build
    And the first Park getter call replies 1 at 650 ms and every later call within 10 ms
    When the receiver runs for 1100 ms
    Then the Park getter was called only at 0, 650, 850 and 1050 ms
    And at most one Park getter call was active at any time

  @offline @boundary @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62 @OD-63 @OD-64
  Scenario: A first call answered at exactly 200 ms is late, and the cadence restarts from 200 ms
    Given the parking check is on, on a supported build
    And the first Park getter call replies 1 at exactly 200 ms and every later call within 10 ms
    When the receiver runs for 700 ms
    Then the Park getter was called only at 0, 200, 400 and 600 ms
    And the reply at 200 ms never permits video
    And mirrored video is blocked until 210 ms and shown from the next frame after 210 ms
    And at most one Park getter call was active at any time

  @offline @boundary @AIR-017 @AT-17 @OD-62 @OD-63
  Scenario: Each late reply restarts the cadence again
    Given the parking check is on, on a supported build
    And the first two Park getter calls each reply 1 after 300 ms and every later call within 10 ms
    When the receiver runs for 1000 ms
    Then the Park getter was called only at 0, 300, 600, 800 and 1000 ms
    And at most one Park getter call was active at any time

  @offline @boundary @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62 @OD-63 @OD-64
  Scenario Outline: A reply at exactly 200 ms is late, whichever is processed first
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at exactly 400 ms, and the call starting at 400 ms replies 1 at 410 ms
    And at 400 ms the <first> is processed first
    When the receiver runs for 1000 ms
    Then mirrored video is blocked from 400 ms until 410 ms
    And the late reply never permits video
    And the Park getter was called only at 0, 200, 400, 600, 800 and 1000 ms

    Examples:
      | first   |
      | reply   |
      | timeout |

  @offline @failure @AIR-019 @AT-19 @OD-62
  Scenario: A call still pending at 200 ms blocks video, even with a recent Park reading
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms never replies
    When the receiver runs for 2700 ms
    Then mirrored video is permitted at 399 ms
    And mirrored video is blocked from 400 ms to the end
    And no other Park getter call started after 200 ms
    And at most one Park getter call was active at any time

  @offline @boundary @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62 @OD-63
  Scenario: A call pending past 2 s is never overlapped, and its return starts the next call at once
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 2500 ms and every later call within 10 ms
    When the receiver runs for 2800 ms
    Then the Park getter was called only at 0, 200, 2500 and 2700 ms
    And at most one Park getter call was active at any time
    And mirrored video is blocked from 400 ms until 2510 ms and shown from the next frame after 2510 ms

  @offline @failure @AIR-017 @AIR-019 @AT-17 @AT-19 @OD-62
  Scenario: A first call that never replies keeps video blocked and is never overlapped
    Given the parking check is on, on a supported build
    And the first Park getter call never replies
    When the receiver runs for 2700 ms
    Then mirrored video is blocked throughout
    And the Park getter was called only at 0 ms

  @offline @boundary @AIR-019 @AT-19 @OD-62 @OD-64
  Scenario: A reply at 199 ms is on time
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 399 ms
    When the receiver runs for 1000 ms
    Then mirrored video was never blocked

  @offline @failure @AIR-019 @AIR-037 @AT-19 @AT-37 @OD-62 @OD-63
  Scenario Outline: A late Park reply never restores video; the next on-time reply decides
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at <late> ms
    And the call after it replies <next> after 10 ms, and the call after that replies 1 within 10 ms
    When the receiver runs for 1500 ms
    Then mirrored video is blocked from 400 ms until that next reply
    And after that next reply mirrored video is <after>

    Examples:
      | late | next               | after                                                     |
      | 401  | 1                  | shown again on the next frame                             |
      | 850  | 1                  | shown again on the next frame                             |
      | 850  | 0                  | still blocked until 1060 ms, then shown on the next frame |
      | 850  | 2                  | still blocked until 1060 ms, then shown on the next frame |
      | 850  | with no value      | still blocked until 1060 ms, then shown on the next frame |
      | 850  | with a failed call | still blocked until 1060 ms, then shown on the next frame |
      | 401  | 0                  | still blocked until 611 ms, then shown on the next frame  |

  @offline @boundary @AIR-019 @AT-19 @OD-46
  Scenario Outline: A Park reading counts as unavailable only once it is older than 2 s
    Given a Park reading of 1 from a call started at 1000 ms and received at 1150 ms
    When its age is judged at <t> ms
    Then the reading is <state> by age

    Examples:
      | t    | state       |
      | 3149 | fresh       |
      | 3150 | fresh       |
      | 3151 | unavailable |

  @offline @boundary @AIR-019 @AT-19 @OD-64
  Scenario: A late reply counts as a fresh reading by age but still cannot permit video
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 850 ms
    And the call starting at 850 ms never replies
    When the receiver runs for 1000 ms
    Then the reading received at 850 ms is fresh by age at 1000 ms
    And mirrored video is blocked from 400 ms to the end

  @offline @normal @AIR-019 @AT-19 @OD-61
  Scenario: Every on-time reply refreshes the reading, even when the value hasn't changed
    Given the parking check is on, on a supported build
    And the Park getter replies 1 within 10 ms
    When the receiver runs for 3000 ms
    Then consecutive Park replies were received at most 200 ms apart
    And mirrored video was never blocked after the first reply

  @offline @failure @AIR-019 @AT-19
  Scenario Outline: Missing or failing Park information at start blocks video
    Given the parking check is on, on a supported build
    And <fault>
    When the receiver runs for 1000 ms
    Then mirrored video is blocked <until>

    Examples:
      | fault                                                   | until                                      |
      | the Park getter service is missing                      | throughout                                 |
      | the first Park getter call throws an exception at 10 ms | until 210 ms, then shown on the next frame |

  @offline @failure @AIR-019 @AT-19
  Scenario Outline: Missing or failing Park information after Park blocks video from the moment it arrives
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    When <fault> at 210 ms
    And the receiver runs for 1000 ms
    Then mirrored video is blocked from 210 ms <until>

    Examples:
      | fault                                                       | until                                      |
      | the Park getter service is lost                             | to the end                                 |
      | the Park getter call starting at 200 ms throws an exception | until 410 ms, then shown on the next frame |
      | the Park getter call starting at 200 ms replies 7           | until 410 ms, then shown on the next frame |

  @offline @failure @AIR-019 @AIR-037 @AT-19 @AT-37 @OD-56 @OD-84 @OD-87
  Scenario Outline: After a resume from sleep, only a fresh on-time re-read permits video
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And ACC status changes to 6 at 100 ms and to 0 at 1100 ms
    And the re-read after the resume <reply>
    When the receiver runs for 2500 ms
    Then mirrored video is blocked from 100 ms <until>
    And at most one Park getter call was active at any time

    Examples:
      | reply                    | until                                       |
      | replies 1 after 10 ms    | until 1110 ms, then shown on the next frame |
      | replies 1 after 250 ms   | until 1360 ms, then shown on the next frame |
      | replies 0 after 10 ms    | until 1310 ms, then shown on the next frame |
      | stays pending to the end | to the end                                  |

  @offline @boundary @AIR-017 @AIR-019 @AT-19 @OD-84 @OD-88
  Scenario: With no call pending, the re-read starts at the resume itself, not on the 200 ms schedule
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And ACC status changes to 6 at 100 ms and to 0 at 1150 ms
    When the receiver runs for 1600 ms
    Then the Park getter was called only at 0, 1150, 1350 and 1550 ms

  @offline @normal @AIR-017 @AIR-019 @AT-19 @OD-87
  Scenario Outline: An awake ACC status at registration makes process start a resume
    Given the parking check is on, on a supported build
    And ACC registration returns sticky status <status> at 50 ms
    When the receiver runs for 500 ms
    Then the Park getter was called only at 50, 250 and 450 ms
    And mirrored video is blocked until 60 ms and shown from the next frame after 60 ms

    Examples:
      | status |
      | 0      |
      | 2      |
      | 5      |

  @offline @failure @AIR-019 @AT-19 @OD-87 @OD-88 @OD-100
  Scenario Outline: Any other ACC status at registration starts the receiver asleep until an awake status arrives
    Given the parking check is on, on a supported build
    And ACC registration returns a sticky broadcast <extra> at 0 ms
    And ACC status changes to 0 at 1000 ms
    When the receiver runs for 1500 ms
    Then mirrored video is blocked until 1010 ms and shown from the next frame after 1010 ms
    And the Park getter was called only at 1000, 1200 and 1400 ms
    And no record contains "ACC registration failed"

    Examples:
      | extra                                |
      | with status 6                        |
      | with status 7                        |
      | with status 255                      |
      | with status 1                        |
      | with status 3                        |
      | with the status extra missing        |
      | with a non-integer status extra "on" |

  @offline @failure @AIR-019 @AT-19 @OD-87 @OD-88
  Scenario Outline: A change from an awake to an asleep ACC status blocks video and stops polling
    Given the parking check is on, on a supported build
    And ACC registration returns sticky status <awake> at 0 ms
    And video is permitted after an on-time Park reading at 0 ms
    When an ACC broadcast <asleep> arrives at 500 ms
    And the receiver runs for 3000 ms
    Then mirrored video is blocked from 500 ms to the end
    And the Park getter was called only at 0, 200 and 400 ms

    Examples:
      | awake | asleep                               |
      | 0     | with status 6                        |
      | 0     | with status 7                        |
      | 0     | with status 255                      |
      | 0     | with status 1                        |
      | 0     | with status 3                        |
      | 0     | with the status extra missing        |
      | 0     | with a non-integer status extra "on" |
      | 2     | with status 6                        |
      | 2     | with status 7                        |
      | 2     | with status 255                      |
      | 2     | with status 1                        |
      | 2     | with status 3                        |
      | 2     | with the status extra missing        |
      | 2     | with a non-integer status extra "on" |
      | 5     | with status 6                        |
      | 5     | with status 7                        |
      | 5     | with status 255                      |
      | 5     | with status 1                        |
      | 5     | with status 3                        |
      | 5     | with the status extra missing        |
      | 5     | with a non-integer status extra "on" |

  @offline @normal @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-83 @OD-87 @OD-88
  Scenario Outline: A change between awake ACC statuses (ADM) keeps polling on schedule with no re-read
    Given the parking check is <check>, on a supported build
    And ACC registration returns sticky status <start> at 0 ms
    And ACC status changes <changes>
    When the receiver runs for 6000 ms with mirroring active
    Then the Park getter was called every 200 ms from 0 to 6000 ms and at no other time
    And the parking gate <gate>
    And no record contains "discarded"

    Examples:
      | check | start | changes                             | gate                            |
      | on    | 0     | to 2 at 1000 ms and to 0 at 5000 ms | never blocked video after 10 ms |
      | on    | 5     | to 0 at 1000 ms                     | never blocked video after 10 ms |
      | on    | 0     | to 5 at 1000 ms                     | never blocked video after 10 ms |
      | on    | 2     | to 5 at 1000 ms                     | never blocked video after 10 ms |
      | off   | 0     | to 2 at 1000 ms and to 0 at 5000 ms | never blocked video             |
      | off   | 5     | to 0 at 1000 ms                     | never blocked video             |

  @offline @failure @AIR-019 @AT-19 @OD-87
  Scenario: Changes between asleep ACC statuses change nothing
    Given the parking check is on, on a supported build
    And ACC registration returns sticky status 6 at 0 ms
    And ACC status changes to 7 at 500 ms and to 255 at 1000 ms
    When the receiver runs for 2000 ms
    Then mirrored video is blocked throughout
    And the Park getter was never called

  @offline @normal @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-87 @OD-104
  Scenario: Sleep never comes from the micom power service
    Given the receiver's sources and its compiled classes
    When a static scan searches them
    Then it finds no reference to registerSystemOffResponder
    And it finds no micom power-service transaction 14

  @offline @normal @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-87
  Scenario Outline: The ACC broadcast receiver is unregistered at exit
    Given the parking check is <check>
    When the receiver starts, runs for 1000 ms and exits
    Then the ACC broadcast receiver was unregistered at exit

    Examples:
      | check |
      | on    |
      | off   |

  @offline @failure @AIR-019 @AT-19 @OD-84
  Scenario: A reply that arrives during sleep never permits video, during sleep or after resume
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the Park getter call starting at 200 ms replies 1 at 350 ms
    And ACC status changes to 6 at 300 ms and to 0 at 1000 ms
    And the re-read after the resume replies 0 after 10 ms, and the call after it replies 1 within 10 ms
    When the receiver runs for 1500 ms
    Then mirrored video is blocked from 300 ms until 1210 ms and shown from the next frame after 1210 ms
    And no Park getter call started after 200 ms and before 1000 ms

  @offline @boundary @AIR-019 @AIR-037 @AT-19 @AT-37 @OD-84 @OD-88
  Scenario Outline: A call pending at resume is never the re-read; the re-read starts when it returns
    Given the parking check is on, on a supported build
    And the Park getter call starting at 0 ms replies 1 at <reply> ms
    And ACC status changes to 6 at 50 ms and to 0 at 100 ms
    When the receiver runs for 1000 ms
    Then no Park getter call started after 0 ms and before <reply> ms
    And the re-read was called at exactly <reply> ms
    And the reply at <reply> ms never permits video
    And mirrored video is blocked until <shown> ms and shown from the next frame after <shown> ms

    Examples:
      | reply | shown |
      | 150   | 160   |
      | 650   | 660   |

  @offline @failure @AIR-019 @AT-19 @OD-84
  Scenario: A call started before sleep that never returns leaves video blocked with no re-read
    Given the parking check is on, on a supported build
    And the Park getter call starting at 0 ms never replies
    And ACC status changes to 6 at 50 ms and to 0 at 100 ms
    When the receiver runs for 3000 ms
    Then mirrored video is blocked throughout
    And the Park getter was called only at 0 ms

  @offline @failure @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-84 @OD-88 @OD-104
  Scenario Outline: Each discarded reply writes one record that says why it was discarded
    Given the parking check is <check>
    And the Park getter call starting at 0 ms replies 1 at <reply> ms
    And ACC status changes to 6 at 50 ms and to 0 at <resume> ms
    When the receiver runs for 1500 ms
    Then exactly one record contains "discarded"
    And that record contains "<token>" and does not contain "<other>"

    Examples:
      | check | reply | resume | token        | other        |
      | on    | 150   | 1000   | during sleep | after resume |
      | on    | 150   | 100    | after resume | during sleep |
      | on    | 650   | 100    | after resume | during sleep |
      | off   | 150   | 1000   | during sleep | after resume |
      | off   | 150   | 100    | after resume | during sleep |
      | off   | 650   | 100    | after resume | during sleep |

  @offline @failure @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-72 @OD-84
  Scenario Outline: A discarded reply does not refresh the reading's age
    Given the parking check is <check>
    And the Park getter call starting at 200 ms replies 1 at 350 ms
    And ACC status changes to 6 at 300 ms and to 0 at <resume> ms
    And the re-read after the resume never replies
    When the receiver runs for 2500 ms
    Then the latest counted Park reading is the one received at 10 ms
    And that reading is fresh by age at 2010 ms and unavailable by age at 2011 ms

    Examples:
      | check | resume |
      | on    | 1000   |
      | on    | 320    |
      | off   | 1000   |
      | off   | 320    |

  @offline @boundary @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-100
  Scenario Outline: Until ACC registration returns, the receiver counts as asleep
    Given the parking check is <check>, on a supported build
    And ACC registration returns sticky status 0 at 300 ms
    When the receiver runs for 700 ms with mirroring active
    Then the Park getter was called only at 300 and 500 ms
    And <gate>

    Examples:
      | check | gate                                                                              |
      | on    | mirrored video is blocked until 310 ms and shown from the next frame after 310 ms |
      | off   | the parking gate never blocked video                                              |

  @offline @failure @AIR-019 @AT-19 @OD-91 @OD-100
  Scenario Outline: A failed ACC registration keeps the receiver asleep for the rest of the run
    Given the parking check is on, on a supported build
    And ACC registration <failure> at 0 ms
    When the receiver runs for 5000 ms with mirroring active
    Then the Park getter was never called
    And mirrored video is blocked throughout
    And exactly one record contains "ACC registration failed", and it names "<cause>"
    And ACC registration was attempted only once

    Examples:
      | failure                 | cause           |
      | throws an exception     | exception       |
      | returns no sticky state | no sticky state |

  @offline @failure @AIR-021 @AT-21 @OD-91 @OD-103 @OD-105
  Scenario Outline: With the check off, a failed ACC registration is logged as unavailable, not as a failure
    Given the parking check is off, on a supported build
    And ACC registration <failure> at 0 ms
    When the receiver runs for 5000 ms with mirroring active
    Then the Park getter was never called
    And the parking gate never blocked video
    And exactly one record contains "ACC registration unavailable", and it names "<cause>"
    And no record contains "ACC registration failed"
    And ACC registration was attempted only once

    Examples:
      | failure                 | cause           |
      | throws an exception     | exception       |
      | returns no sticky state | no sticky state |

  @offline @failure @AIR-021 @AT-21 @OD-103 @OD-105
  Scenario Outline: With the check off, the "ACC registration unavailable" record has no failure wording
    Given the parking check is off, on a supported build
    And ACC registration <failure> at 0 ms
    When the receiver runs for 1000 ms
    Then the record that contains "ACC registration unavailable" contains neither "fail" nor "error" in any letter case
    And no record contains "SyntheticFailureError" or "synthetic failure error"
    And no record contains a stack trace

    Examples:
      | failure                                                                   |
      | throws a SyntheticFailureError with the message "synthetic failure error" |
      | returns no sticky state                                                   |

  @offline @failure @AIR-021 @AT-21 @OD-103
  Scenario Outline: With the check off, a failed ACC registration shows no notice
    Given the parking check is off, on a supported build
    And ACC registration <failure> at 0 ms
    When the receiver runs for 5000 ms with mirroring active
    Then no registration-failure notice was shown at any time
    And the synthetic notice output is empty

    Examples:
      | failure                 |
      | throws an exception     |
      | returns no sticky state |

  @offline @failure @AIR-019 @AT-19 @OD-100 @OD-104 @OD-105
  Scenario Outline: After a registration with no sticky state, later ACC broadcasts are logged as ignored
    Given the parking check is on, on a supported build
    And ACC registration returns no sticky state at 0 ms
    When an ACC broadcast <extra> arrives at 1000 ms
    And the receiver runs for 2000 ms with mirroring active
    Then the Park getter was never called
    And mirrored video is blocked throughout
    And exactly one record contains "ignored", and it contains "<token>"

    Examples:
      | extra                                | token              |
      | with status 0                        | status=0           |
      | with status 5                        | status=5           |
      | with status 255                      | status=255         |
      | with the status extra missing        | status=missing     |
      | with a non-integer status extra "on" | status=non-integer |

  @offline @failure @AIR-021 @AT-21 @OD-103 @OD-105
  Scenario: With the check off, ACC broadcasts after a registration with no sticky state write no record
    Given the parking check is off, on a supported build
    And ACC registration returns no sticky state at 0 ms
    When ACC status changes to 0 at 1000 ms
    And ACC status changes to 5 at 2000 ms
    And an ACC broadcast with the status extra missing arrives at 3000 ms
    And an ACC broadcast with a non-integer status extra "on" arrives at 4000 ms
    And the receiver runs for 5000 ms with mirroring active
    Then no record was written from just before to just after each of those broadcasts
    And no record contains "ignored"
    And none of those broadcasts was a resume or started a re-read
    And the Park getter was never called
    And the parking gate never blocked video

  @offline @boundary @AIR-021 @AT-21 @OD-103 @OD-105
  Scenario Outline: With the check off, a sticky state with an unusable extra is not a registration failure
    Given the parking check is off, on a supported build
    And ACC registration returns <sticky> at 0 ms
    And ACC status changes to 0 at 1000 ms
    When the receiver runs for 1500 ms
    Then no record contains "ACC registration unavailable"
    And the Park getter was called only at 1000, 1200 and 1400 ms

    Examples:
      | sticky                                       |
      | a sticky state with the status extra missing |
      | a sticky state with a non-integer extra "on" |

  @offline @failure @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-105
  Scenario: A check that is on by fallback logs a failed registration and ignored broadcasts as with the check on
    Given the installed receiver.conf is missing
    And ACC registration returns no sticky state at 0 ms
    When ACC status changes to 0 at 1000 ms
    And the receiver runs for 2000 ms
    Then the parking check is on
    And exactly one record contains "ACC registration failed", and it names "no sticky state"
    And exactly one record contains "ignored", and it contains "status=0"
    And no record contains "ACC registration unavailable"
    And no record contains "parking check off"

  @offline @failure @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-100
  Scenario Outline: After a failed ACC registration, exit still completes
    Given the parking check is <check>
    And ACC registration <failure> at 0 ms
    When the receiver runs for 1000 ms and exits
    Then the exit completes
    And the ACC broadcast receiver was <unregister>

    Examples:
      | check | failure                 | unregister         |
      | on    | throws an exception     | never unregistered |
      | on    | returns no sticky state | unregistered once  |
      | off   | throws an exception     | never unregistered |
      | off   | returns no sticky state | unregistered once  |

  @offline @normal @AIR-019 @AIR-037 @AT-19 @AT-37 @OD-91 @OD-100
  Scenario Outline: A new process start whose ACC registration works ends a registration failure
    Given the parking check is on, on a supported build
    And the previous run's ACC registration <failure>
    When the receiver starts again and ACC registration returns sticky status 0 at 50 ms
    And the receiver runs for 500 ms
    Then the Park getter was first called at exactly 50 ms
    And mirrored video is blocked until 60 ms and shown from the next frame after 60 ms

    Examples:
      | failure                  |
      | threw an exception       |
      | returned no sticky state |

  @offline @failure @AIR-019 @AIR-040 @AT-19 @OD-49 @OD-100
  Scenario: A registration failure whose record cannot be written still keeps video blocked
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And writing the "ACC registration failed" record fails
    When the receiver runs for 2000 ms
    Then mirrored video is blocked throughout
    And the Park getter was never called

  @offline @failure @AIR-019 @AT-19 @OD-101 @OD-102
  Scenario Outline: With the check on, a failed ACC registration shows one registration-failure notice
    Given the parking check is on, on a supported build
    And ACC registration <failure> at 0 ms
    When the receiver runs for 5000 ms with mirroring active
    Then exactly one registration-failure notice was shown
    And mirrored video is blocked throughout

    Examples:
      | failure                 |
      | throws an exception     |
      | returns no sticky state |

  @offline @failure @AIR-019 @AT-19 @OD-100 @OD-102 @OD-104
  Scenario: ACC broadcasts ignored after a registration with no sticky state add no notice
    Given the parking check is on, on a supported build
    And ACC registration returns no sticky state at 0 ms
    When ACC status changes to 0 at 1000 ms
    And ACC status changes to 5 at 2000 ms
    And an ACC broadcast with the status extra missing arrives at 3000 ms
    And an ACC broadcast with a non-integer status extra "on" arrives at 4000 ms
    And the receiver runs for 5000 ms
    Then exactly one registration-failure notice was shown in the whole run
    And exactly four records contain "ignored"
    And in order they contain "status=0", "status=5", "status=missing" and "status=non-integer"
    And the Park getter was never called

  @offline @boundary @AIR-019 @AT-19 @OD-102
  Scenario Outline: A registration that returns a sticky state never shows a registration-failure notice
    Given the parking check is on, on a supported build
    And ACC registration returns <sticky> at <returns> ms
    And <later>
    When the receiver runs for 2000 ms
    Then no registration-failure notice was shown at any time

    Examples:
      | sticky                                       | returns | later                              |
      | sticky status 0                              | 300     | nothing else happens               |
      | sticky status 0                              | 0       | nothing else happens               |
      | sticky status 6                              | 0       | nothing else happens               |
      | a sticky state with the status extra missing | 0       | ACC status changes to 0 at 1000 ms |
      | a sticky state with a non-integer extra "on" | 0       | ACC status changes to 0 at 1000 ms |

  @offline @failure @AIR-019 @AT-19 @OD-102
  Scenario Outline: A check that is on by fallback still shows the registration-failure notice
    Given the installed receiver.conf <config>
    And ACC registration throws an exception at 0 ms
    When the receiver runs for 2000 ms
    Then the parking check is on
    And exactly one registration-failure notice was shown

    Examples:
      | config                                                     |
      | is missing                                                 |
      | is unreadable                                              |
      | holds "video.require_park=maybe"                           |
      | holds "other.key=false"                                    |
      | holds "video.require_park=false ; video.require_park=true" |

  @offline @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-101 @OD-102 @OD-106 @OD-112 @OD-118
  Scenario Outline: A registration failure whose record cannot be written shows both notices
    Given the parking check is on, on a supported build
    And ACC registration <failure> at 0 ms
    And writing the "ACC registration failed" record fails
    When the receiver runs for 2000 ms
    Then exactly one registration-failure notice was shown, at 0 ms, with text exactly "Mirroring is off: the car's power state couldn't be read. Restart the receiver from NEMESIS Manager."
    And exactly one logging-failure notice was shown, at 0 ms, with text exactly "Receiver logging stopped working. Mirroring is unaffected."
    And each notice was added as its own window
    And the registration-failure window has y=Y and the logging-failure window has y=Y+H
    And each window was removed by its own removal call at exactly 5000 ms

    Examples:
      | failure                 |
      | throws an exception     |
      | returns no sticky state |

  @offline @failure @AIR-019 @AT-19 @OD-102 @OD-107 @OD-119
  Scenario Outline: Showing the registration-failure notice, or failing to show it, changes nothing
    Given the parking check is on, on a supported build
    And mirroring is active with audio playing
    And ACC registration throws an exception at 0 ms
    And the registration-failure notice <outcome>
    When the receiver runs for 12000 ms
    Then mirrored video is blocked throughout
    And the Park getter was never called
    And ACC registration was attempted only once
    And audio was never muted, stopped or disconnected
    And the iPhone connection was never stopped or disconnected
    And no logging-failure notice was shown at any time

    Examples:
      | outcome                                   |
      | is shown                                  |
      | fails to show                             |
      | fails to show and its retry is shown      |
      | fails to show and its retry fails to show |
      | is shown and its removal call throws      |
      | is shown and both its removal calls throw |

  @offline @failure @AIR-019 @AT-19 @OD-101
  Scenario Outline: Each app start whose ACC registration fails shows its own registration-failure notice
    Given the parking check is on, on a supported build
    And one registration-failure notice was shown during the previous app start
    When the receiver starts again and ACC registration <failure> at 0 ms
    And the receiver runs for 2000 ms
    Then exactly one registration-failure notice was shown since the restart

    Examples:
      | failure                 |
      | throws an exception     |
      | returns no sticky state |

  @offline @failure @AIR-019 @AT-19 @OD-106 @OD-114 @OD-115 @OD-116
  Scenario Outline: The registration-failure notice is one toast window added when registration fails
    Given the parking check is on, on a supported build
    And ACC registration <failure> at <t> ms
    When the receiver runs for 60000 ms
    Then exactly one registration-failure notice add call was made, at <t> ms
    And that add call has window type TYPE_TOAST and a view from the toast-view factory
    And its flags include FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE and not FLAG_KEEP_SCREEN_ON
    And its view's text is exactly "Mirroring is off: the car's power state couldn't be read. Restart the receiver from NEMESIS Manager."
    And no Toast.show() call, other window type, dialog, notification or activity was recorded
    And mirrored video is blocked throughout

    Examples:
      | failure                 | t   |
      | throws an exception     | 0   |
      | returns no sticky state | 0   |
      | returns no sticky state | 700 |

  @offline @boundary @AIR-019 @AT-19 @OD-115 @OD-116
  Scenario Outline: The registration-failure notice stays on screen for exactly 5000 ms
    Given the parking check is on, on a supported build
    And ACC registration <failure> at <t> ms
    When the receiver runs for 12000 ms
    Then the registration-failure notice was shown at <t> ms
    And its window is on screen at <on> ms
    And exactly one removal call for it was made, at exactly <off> ms

    Examples:
      | failure                 | t   | on   | off  |
      | throws an exception     | 0   | 4999 | 5000 |
      | returns no sticky state | 700 | 5699 | 5700 |

  @offline @failure @AIR-019 @AT-19 @OD-100 @OD-116
  Scenario: Ignored ACC broadcasts do not move the registration-failure notice's removal
    Given the parking check is on, on a supported build
    And ACC registration returns no sticky state at 0 ms
    When ACC status changes to 0 at 1000 ms
    And ACC status changes to 5 at 2000 ms
    And an ACC broadcast with the status extra missing arrives at 3000 ms
    And an ACC broadcast with a non-integer status extra "on" arrives at 4000 ms
    And the receiver runs for 12000 ms
    Then the registration-failure notice was shown at 0 ms
    And exactly one removal call for it was made, at exactly 5000 ms
    And no other registration-failure notice add call was made

  @offline @failure @AIR-019 @AIR-041 @AT-19 @AT-41 @OD-121 @OD-122
  Scenario: A registration failure while the window has no focus is shown when focus is first gained
    Given the parking check is on, on a supported build
    And the receiver's window first gains focus at 3000 ms
    And ACC registration throws an exception at 0 ms
    When the receiver runs for 12000 ms
    Then no view was created, measured or added before 3000 ms
    And exactly one registration-failure notice add call was made, at exactly 3000 ms
    And exactly one removal call for it was made, at exactly 8000 ms
    And mirrored video is blocked throughout

  @offline @failure @AIR-019 @AT-19 @OD-107 @OD-111 @OD-113
  Scenario Outline: A registration-failure notice that fails to show is tried once more 5000 ms later, while asleep
    Given the parking check is on, on a supported build
    And ACC registration <failure> at 0 ms
    And the UI double throws on <call> for the first registration-failure notice attempt
    When the receiver runs for 60000 ms
    Then the first registration-failure notice attempt was made at 0 ms
    And no registration-failure notice window exists from 0 ms until 5000 ms
    And no registration-failure notice attempt was made from 1 ms to 4999 ms
    And exactly one more attempt, with the same text and form, was made at exactly 5000 ms
    And that window was removed by one removal call at exactly 10000 ms
    And no further registration-failure notice attempt was made
    And the Park getter was never called

    Examples:
      | failure                 | call   |
      | throws an exception     | create |
      | throws an exception     | add    |
      | returns no sticky state | create |
      | returns no sticky state | add    |

  @offline @failure @AIR-019 @AT-19 @OD-107 @OD-111
  Scenario Outline: A registration-failure notice whose retry also fails is given up
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And the UI double throws on <call> for both registration-failure notice attempts
    When the receiver runs for 60000 ms
    Then registration-failure notice attempts were made only at 0 ms and 5000 ms
    And no registration-failure notice window exists at any time
    And no logging-failure notice was shown at any time

    Examples:
      | call   |
      | create |
      | add    |

  @offline @failure @AIR-019 @AT-19 @OD-100 @OD-107 @OD-111
  Scenario: Broadcasts after a registration with no sticky state do not move the notice's retry
    Given the parking check is on, on a supported build
    And ACC registration returns no sticky state at 0 ms
    And the UI double throws on add for the first registration-failure notice attempt
    When ACC status changes to 0 at 1000 ms
    And ACC status changes to 6 at 3000 ms
    And the receiver runs for 12000 ms
    Then registration-failure notice attempts were made only at 0 ms and 5000 ms
    And that window was removed by one removal call at exactly 10000 ms

  @offline @failure @AIR-019 @AT-19 @OD-107 @OD-111
  Scenario Outline: Exit drops the registration-failure notice's pending retry
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    And the UI double throws on add for the first registration-failure notice attempt
    When the receiver starts exit at <exit> ms
    Then no registration-failure notice attempt was made after 0 ms
    And exit completes without waiting for the retry
    And no registration-failure notice window exists after exit

    Examples:
      | exit |
      | 3000 |
      | 5000 |

  @offline @failure @AIR-019 @AT-19 @OD-107 @OD-111
  Scenario: After exit dropped a retry, the next failing start makes its own first attempt
    Given the parking check is on, on a supported build
    And during the previous app start the first registration-failure notice attempt failed and exit came at 3000 ms
    When the receiver starts again and ACC registration throws an exception at 400 ms
    And the receiver runs for 12000 ms
    Then the first registration-failure notice attempt since the restart was made at 400 ms
    And no removal call was made for any window of the previous app start

  @offline @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-116
  Scenario: Exit removes a registration-failure notice that is on screen
    Given the parking check is on, on a supported build
    And ACC registration throws an exception at 0 ms
    When the receiver starts exit at 2000 ms
    Then exactly one removal call for the registration-failure window was made, at 2000 ms
    And no removal call was made at 5000 ms
    And exit completes

  @offline @boundary @AIR-018 @AT-18 @OD-46 @OD-61
  Scenario Outline: Leaving Park removes the image within 250 ms of the poll that sees it
    Given the parking check is on and video is showing a <image> image after an on-time Park reading at 0 ms
    And frames are queued for display
    And the Park getter replies at once with the state at the moment it is called
    When the car shifts out of Park at <shift> ms
    Then the first reply of 0 is received at most 200 ms after the shift
    And the image is gone at most 250 ms after that reply is received
    And the image is gone at most 450 ms after the shift
    And no frame, queued or new, is shown from the moment that reply is received

    Examples:
      | image  | shift |
      | moving | 399   |
      | moving | 401   |
      | frozen | 399   |
      | frozen | 401   |

  @offline @boundary @AIR-018 @AT-18 @OD-46 @OD-61
  Scenario Outline: A shift at the same moment as a poll stays within all three bounds, in either order
    Given the parking check is on and video is showing a <image> image after an on-time Park reading at 0 ms
    And frames are queued for display
    And the Park getter replies at once with the state at the moment it is called
    And at 400 ms the <first> is processed first
    When the car shifts out of Park at 400 ms
    Then the first reply of 0 is received at <return> ms
    And the image is gone at most 250 ms after that reply is received
    And the image is gone at most 450 ms after the shift
    And no frame, queued or new, is shown from the moment that reply is received

    Examples:
      | image  | first | return |
      | moving | shift | 400    |
      | moving | poll  | 600    |
      | frozen | shift | 400    |
      | frozen | poll  | 600    |

  @offline @boundary @AIR-018 @AT-18 @OD-61
  Scenario Outline: The AT-18 timing check rejects a trace that breaks any one bound
    Given a display trace with the shift at <shift> ms, the first reply of 0 at <return> ms and the image gone at <hide> ms
    When the AT-18 timing check judges it
    Then it is <verdict>
    And the bounds it names as broken are <broken>

    Examples:
      | shift | return | hide | verdict  | broken                                  |
      | 399   | 400    | 650  | accepted | none                                    |
      | 399   | 400    | 651  | rejected | the 250 ms removal                      |
      | 401   | 602    | 652  | rejected | the 200 ms polling                      |
      | 400   | 600    | 851  | rejected | the 250 ms removal and the 450 ms total |

  @offline @failure @AIR-020 @AT-20 @OD-47 @OD-51
  Scenario Outline: Only a CVEV.* ro.build.product is a supported build
    Given the parking check is on
    And ro.build.product is <product>
    And the Park getter replies 1 within 10 ms
    When the receiver starts
    Then mirrored video is <result>

    Examples:
      | product                         | result    |
      | "CVEV.CAN.0000.V015.011.260206" | permitted |
      | "CVEV.USA.0000.V016.002.270415" | permitted |
      | "CVEV.KOR.0000.V014.000.251020" | permitted |
      | "NE1.CAN.0000.V015.011.260206"  | blocked   |
      | "NE1.CAN.CVEV.260206"           | blocked   |
      | missing                         | blocked   |
      | unreadable                      | blocked   |

  @offline @failure @AIR-020 @AT-20 @OD-51
  Scenario: Other build properties don't stand in for ro.build.product
    Given the parking check is on
    And ro.build.product is "NE1.CAN.0000.V015.011.260206" and ro.build.product2 is "CVEV.CAN.S5W_M.V015.011.260206"
    And the Park getter replies 1 within 10 ms
    When the receiver starts
    Then mirrored video is blocked

  @offline @normal @AIR-020 @AT-20 @OD-51
  Scenario: A CVEV.* ro.build.product is supported whatever ro.build.product2 holds
    Given the parking check is on
    And ro.build.product is "CVEV.CAN.0000.V015.011.260206" and ro.build.product2 is "NE1.CAN.S5W_M.V015.011.260206"
    And the Park getter replies 1 within 10 ms
    When the receiver starts
    Then mirrored video is permitted

  @offline @normal @AIR-021 @AT-21 @OD-24
  Scenario Outline: With the check off, no gear or build condition blocks video
    Given the parking check is off
    And <condition>
    When the receiver runs for 3000 ms with mirroring active
    Then the parking gate never blocked video
    And no stock restriction or setting was changed

    Examples:
      | condition                                                                                                              |
      | the Park getter replies 1 within 10 ms                                                                                 |
      | the Park getter replies 0 within 10 ms                                                                                 |
      | the Park getter replies 2 within 10 ms                                                                                 |
      | the Park getter replies with no value within 10 ms                                                                     |
      | the Park getter call starting at 0 ms never replies                                                                    |
      | the Park getter call throws an exception                                                                               |
      | the Park getter service is missing                                                                                     |
      | the Park getter replies 0 at 10 ms and the call starting at 200 ms never replies, so the reading is stale from 2010 ms |
      | ro.build.product is "NE1.CAN.0000.V015.011.260206"                                                                     |
      | ro.build.product is missing                                                                                            |
      | ro.build.product is unreadable                                                                                         |

  @offline @normal @AIR-021 @AT-21 @OD-83 @OD-87
  Scenario Outline: With the check off, the ACC status starts and stops polling just as with it on
    Given the parking check is off
    And ACC registration returns a sticky broadcast <extra> at 50 ms
    And <then>
    When the receiver runs for 1500 ms with mirroring active
    Then the Park getter was called only at <calls>
    And the parking gate never blocked video

    Examples:
      | extra                                | then                               | calls                                          |
      | with status 0                        | nothing else changes               | 50, 250, 450, 650, 850, 1050, 1250 and 1450 ms |
      | with status 2                        | nothing else changes               | 50, 250, 450, 650, 850, 1050, 1250 and 1450 ms |
      | with status 5                        | nothing else changes               | 50, 250, 450, 650, 850, 1050, 1250 and 1450 ms |
      | with status 6                        | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |
      | with status 7                        | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |
      | with status 255                      | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |
      | with status 1                        | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |
      | with status 3                        | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |
      | with the status extra missing        | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |
      | with a non-integer status extra "on" | ACC status changes to 0 at 1000 ms | 1000, 1200 and 1400 ms                         |

  @offline @failure @AIR-021 @AT-21 @OD-83 @OD-84
  Scenario Outline: With the check off, sleep stops polling but the gate blocks nothing, before or after resume
    Given the parking check is off
    And the Park getter call starting at 200 ms replies <value> at <reply> ms
    And ACC status changes to 6 at 300 ms and to 0 at <resume> ms
    And the re-read replies 1 after 100 ms
    When the receiver runs for 2000 ms with mirroring active
    Then no Park getter call started after 200 ms and before <reread> ms
    And the re-read was called at exactly <reread> ms
    And the parking gate never blocked video

    Examples:
      | value | reply | resume | reread |
      | 1     | 350   | 1000   | 1000   |
      | 1     | 350   | 320    | 350    |
      | 1     | 850   | 320    | 850    |
      | 0     | 350   | 320    | 350    |

  @offline @failure @AIR-021 @AT-21 @OD-83 @OD-84 @OD-87
  Scenario Outline: With the check off, every asleep ACC status stops polling without blocking video
    Given the parking check is off
    And the Park getter call starting at 200 ms replies 1 at 350 ms
    And an ACC broadcast <asleep> arrives at 300 ms
    And ACC status changes to 0 at 1000 ms
    And the re-read replies 1 after 100 ms
    When the receiver runs for 2000 ms with mirroring active
    Then no Park getter call started after 200 ms and before 1000 ms
    And the re-read was called at exactly 1000 ms
    And the parking gate never blocked video

    Examples:
      | asleep                               |
      | with status 7                        |
      | with status 255                      |
      | with status 1                        |
      | with status 3                        |
      | with the status extra missing        |
      | with a non-integer status extra "on" |

  @offline @normal @AIR-022 @AIR-037 @AT-22 @AT-37 @OD-23 @OD-48
  Scenario Outline: The gate never mutes, stops or disconnects audio
    Given the parking check is <check> and mirrored audio is playing
    When the car leaves Park at 500 ms and returns to Park at 1500 ms
    And the receiver runs for 2500 ms
    Then the gate never muted, stopped or disconnected the audio
    And the iPhone stayed connected throughout

    Examples:
      | check |
      | on    |
      | off   |

  @offline @normal @AIR-037 @AT-37 @OD-48
  Scenario: Back in Park, video returns by itself on the next frame
    Given the parking check is on, on a supported build, and video is blocked because the car left Park
    And mirrored audio is playing
    When an on-time Park reply of 1 is received
    Then the next frame is shown without the iPhone reconnecting
    And the audio was not interrupted

  @offline @failure @AIR-024 @AT-24
  Scenario: The receiver only reads vehicle state
    Given every vehicle interface the receiver can reach is recorded
    When the receiver starts, blocks, recovers and stops
    Then Park was read only through VehicleInfoManager.getGearPStatus()
    And no vehicle command, CAN write, setting change, regulation change or spoofed regulation event was sent
    And its registration for stock regulation change notifications was allowed

  @offline @normal @AIR-027 @AT-27 @OD-22
  Scenario Outline: Mirrored video never goes to the HUD
    Given the parking check is <check> and mirrored video is permitted
    When the receiver shows frames for 1000 ms
    Then no mirrored frame was routed to the HUD
    And no driver-assistance display setting was changed

    Examples:
      | check |
      | on    |
      | off   |
