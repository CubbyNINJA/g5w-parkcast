@S2
Feature: Mirrored video only in Park, read from the stock Park getter
  With the parking check on, the receiver shows mirrored video only while an on-time reading from
  VehicleInfoManager.getGearPStatus() says Park on a supported build. It polls that getter every
  200 ms, one call at a time; a call not answered within 200 ms blocks video. Audio and the iPhone
  connection are never touched by the gate (AIR-016 to AIR-022, AIR-024, AIR-027, AIR-037;
  OD-23, OD-24, OD-46, OD-47, OD-51, OD-56, OD-61 to OD-64).

  Times are in milliseconds on a controlled clock. "Within 10 ms" means a reply received 10 ms
  after the call starts. A supported build is one whose ro.build.product starts with "CVEV.".

  "Runs for N ms" covers clock times 0 to N inclusive. "Was called only at" lists every call started
  in that span and no others.

  Unless a step says otherwise, the car is in Park, the build is supported, and every Park getter
  call replies with the car's gear state (1 in Park, 0 out of it) within 10 ms. "Video is permitted
  after an on-time Park reading at 0 ms" means the call started at 0 ms replied 1 at 10 ms.

  @offline @normal @AIR-016 @AT-16 @OD-24
  Scenario Outline: The package requires Park by default, and its flag can turn that off
    Given the installed receiver.conf is <config>
    When the receiver starts
    Then the parking check is <state>

    Examples:
      | config                                        | state |
      | the package's shipped file                    | on    |
      | a file holding "video.require_park=false"     | off   |

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
      | getter                              | signal                                                   |
      | 0 within 10 ms                      | the car reports that it is stationary                    |
      | with no value within 10 ms          | the car reports that it is stationary                    |
      | with a failed call within 10 ms     | the car reports that it is stationary                    |
      | 0 within 10 ms                      | the parking brake reads applied                          |
      | with no value within 10 ms          | the parking brake reads applied                          |
      | with a failed call within 10 ms     | the parking brake reads applied                          |
      | 0 within 10 ms                      | a BCM gear status request succeeds with no value         |

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
      | fault                                                     | until                                       |
      | the Park getter service is missing                        | throughout                                  |
      | the first Park getter call throws an exception at 10 ms   | until 210 ms, then shown on the next frame  |

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

  @offline @failure @AIR-019 @AIR-037 @AT-19 @AT-37 @OD-56
  Scenario Outline: After a resume from sleep, only a fresh on-time re-read permits video
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    And the head unit sleeps at 100 ms and resumes at 1100 ms
    And the re-read after the resume <reply>
    When the receiver runs for 2500 ms
    Then mirrored video is blocked from the resume <until>
    And at most one Park getter call was active at any time

    Examples:
      | reply                     | until                                           |
      | replies 1 after 10 ms     | until 1110 ms, then shown on the next frame     |
      | replies 1 after 250 ms    | until 1360 ms, then shown on the next frame     |
      | replies 0 after 10 ms     | until 1310 ms, then shown on the next frame     |
      | stays pending to the end  | to the end                                      |

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
      | shift | return | hide | verdict  | broken                                       |
      | 399   | 400    | 650  | accepted | none                                         |
      | 399   | 400    | 651  | rejected | the 250 ms removal                           |
      | 401   | 602    | 652  | rejected | the 200 ms polling                           |
      | 400   | 600    | 851  | rejected | the 250 ms removal and the 450 ms total      |

  @offline @failure @AIR-020 @AT-20 @OD-47 @OD-51
  Scenario Outline: Only a CVEV.* ro.build.product is a supported build
    Given the parking check is on
    And ro.build.product is <product>
    And the Park getter replies 1 within 10 ms
    When the receiver starts
    Then mirrored video is <result>

    Examples:
      | product                                     | result    |
      | "CVEV.CAN.0000.V015.011.260206"             | permitted |
      | "CVEV.USA.0000.V016.002.270415"             | permitted |
      | "CVEV.KOR.0000.V014.000.251020"             | permitted |
      | "NE1.CAN.0000.V015.011.260206"              | blocked   |
      | "NE1.CAN.CVEV.260206"                       | blocked   |
      | missing                                     | blocked   |
      | unreadable                                  | blocked   |

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
