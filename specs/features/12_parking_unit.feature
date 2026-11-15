@S2 @unit
Feature: The parking gate on the owner's head unit
  These scenarios need the receiver installed on the owner's unit (260206 CA). They never run from
  the PC suite: their evidence is the receiver's logs and the owner's observations, saved to the
  stick. Offline results never stand in for them (AT-16 to AT-27, AT-35 to AT-40).

  A scenario whose Given the unit never reached is reported as unexercised, not as passed. The
  registration-failure and logging-failure notices are as defined in 09_parking_gate.feature;
  their wording, timing and duration are not asserted (AMB-73).

  @normal @AIR-016 @AT-16
  Scenario: An unmodified package starts with the parking check on
    Given the receiver package is installed unmodified on the owner's unit
    When the receiver starts
    Then the stick evidence shows the installed receiver.conf and a start record with the parking check on

  @normal @AIR-017 @AIR-024 @AT-17 @AT-24 @OD-61
  Scenario: The receiver, as an ordinary app, polls the Park getter every 200 ms, one call at a time
    Given the receiver package is installed unmodified on the owner's unit
    When the receiver runs parked for 60 seconds with mirroring active
    Then the stick evidence shows the receiver's own getGearPStatus calls, their times and replies, every 200 ms
    And at most one call was pending at any time
    And any late return in the evidence is followed at once by the next call, then calls every 200 ms from it
    And unexercised delay, hang and exact-200 ms cases are listed as unverified
    And the receiver's own call trace and an integration review, not log silence alone, show no Park listener and no BCM status request

  @boundary @AIR-018 @AT-18 @OD-46 @OD-61
  Scenario: Leaving Park removes the image within 250 ms of the poll that sees it
    Given the receiver is mirroring parked, with the parking check on and the owner's foot on the brake
    When the owner shifts out of Park and back while a timing camera records the screen
    Then the stick evidence correlates the first poll returning 0 with the image disappearing within 250 ms
    And the time from the actual shift to the image disappearing is recorded separately
    And no new frame appears from the first poll returning 0 until an on-time reply of 1 after the car is back in Park

  @failure @AIR-019 @AT-19 @OD-56 @OD-83 @OD-84 @OD-87
  Scenario: After a resume from sleep, video waits for a fresh on-time Park reading
    Given the receiver is mirroring parked, with the parking check on
    When the head unit sleeps and resumes
    Then the stick evidence shows video blocked from the resume until a fresh on-time reply of 1
    And it shows the ACC broadcasts that marked sleep entry and resume, each with its receipt time
    And it shows no Park getter call while asleep
    And it names the call that served as the resume re-read, with its invocation time beside the resume broadcast's receipt time
    And it holds every pre-sleep call that replied after resume and every "discarded" record
    And it holds the invocation and receipt times of the getter calls, the timeouts and gate changes, and the on-time recovery after them
    And any untested fault, exact-timeout race, reply during sleep or pre-sleep reply after resume is listed as unverified

  @normal @AIR-019 @AT-19 @OD-83 @OD-87
  Scenario: Switching the car off with the head unit up (ADM) keeps mirroring while Park permits
    Given the receiver is mirroring parked, with the parking check on
    When the owner switches the car off with the head unit staying up, and then back on
    Then the stick evidence shows ACC status 0, then 2, then 0, each with EXTRA_ACC_STATUS, EXTRA_PREVIOUS_ACC_STATUS and its receipt time
    And it shows the sticky ACC state returned at registration
    And it shows Park getter calls continuing every 200 ms through both changes, with no re-read at either
    And the owner's display observations show mirroring continuing throughout

  @normal @AIR-019 @AIR-021 @AT-19 @AT-21 @OD-91 @OD-100 @OD-101 @OD-103 @OD-105
  Scenario: Each process start's ACC registration outcome is kept, with the records and notice its parking check calls for
    Given the receiver package is installed on the owner's unit
    When the receiver starts several times during the test, with the parking check on in some starts and off in others
    Then the stick evidence holds the ACC registration outcome and the parking check state of every process start
    And for every start with the check on whose registration failed, it holds an "ACC registration failed" record and any "ignored" records
    And for every start with the check off whose registration failed, it holds an "ACC registration unavailable" record and no "ACC registration failed" or "ignored" record
    And for every start whose registration failed, an observation independent of the receiver's log shows whether a registration-failure notice appeared
    And that observation shows no registration-failure notice in any start with the check off
    And a registration that threw or returned no sticky state is listed as unexercised, for each check state, if no start reached it
    And the long-off sequence (ACC status 6, 7, 255, shutdown and process start) is listed as unexercised if the unit did not reach it

  @normal @AIR-020 @AT-20 @OD-51
  Scenario: The unit's own build is recognised as supported
    Given the receiver package is installed unmodified on the owner's unit
    When the receiver starts parked
    Then the stick evidence shows the ro.build.product value it read and that it counted it as supported
    And only the builds actually tested are listed as tested

  @normal @AIR-021 @AT-21 @OD-24
  Scenario: With the check off, video plays outside Park without changing stock restrictions
    Given the installed receiver.conf holds "video.require_park=false"
    When the owner shifts out of Park and back with mirroring active, foot on the brake
    Then the stick evidence shows the gate never blocked video
    And a review of the integration evidence shows no stock restriction setting changed

  @normal @AIR-021 @AIR-025 @AT-21 @AT-25 @OD-83 @OD-84 @OD-87 @OD-103 @OD-105
  Scenario: With the check off, ADM and a sleep and resume never block video on the unit
    Given the installed receiver.conf holds "video.require_park=false"
    When the owner switches the car off with the head unit up and back on, and later lets the head unit sleep and resume
    Then the stick evidence holds the startup configuration, the gate logs and the owner's display observations
    And it holds the Park getter invocation times and the ACC broadcast history
    And it holds the "parking check off" record and any "discarded" or "ACC registration unavailable" records
    And it holds no "ACC registration failed" or "ignored" record
    And a failed ACC registration is listed as unexercised if no start with the check off reached it
    And it shows the gate never blocked video across the ADM changes, the sleep or the resume

  @normal @AIR-022 @AIR-037 @AT-22 @AT-37 @OD-48
  Scenario: Audio keeps playing and video returns by itself
    Given the receiver is mirroring parked with sound playing, with the parking check on
    When the owner shifts out of Park and back, foot on the brake
    Then the owner heard the audio continue without a break
    And the video came back by itself on returning to Park, without reconnecting the iPhone
    And the stick evidence correlates the on-time reply of 1 with the time the next frame appeared

  @boundary @AIR-023 @AT-23 @OD-55
  Scenario: Edits to the installed receiver.conf take effect at the next start
    Given the receiver package is installed on the owner's unit
    When the owner edits the installed receiver.conf and restarts the receiver
    Then the stick evidence shows the installed path, the file read and the start record for each tested edit

  @normal @AIR-025 @AIR-026 @AIR-036 @AT-25 @AT-26 @AT-36 @OD-45
  Scenario: Start records and gate changes appear in the receiver's logs, with no on-screen indicator
    Given the receiver package is installed on the owner's unit
    When the receiver starts, blocks and recovers once
    Then the stick evidence holds the start record and one record per gate change
    And each gate-change record matches the gear and display the owner observed at that time
    And the owner saw no on-screen message about the parking check

  @normal @AIR-025 @AT-25 @OD-103 @OD-105
  Scenario: On the unit, only a start with an explicit false begins with "parking check off"
    Given the receiver package is installed on the owner's unit
    When the owner starts the receiver once with the installed receiver.conf holding "video.require_park=false" and once holding "video.require_park=true"
    Then the stick evidence shows that the false start's first retained record contains "parking check off" and is followed by that start's start record
    And it shows no "parking check off" record in the true start

  @normal @AIR-026 @AT-26 @OD-104 @OD-105
  Scenario: On the unit, a gate change caused by a Park reading names its value and receipt time
    Given the receiver is mirroring parked, with the parking check on
    When the owner shifts out of Park and back, foot on the brake
    Then the stick evidence holds the gate-change records for leaving Park and for returning to it
    And each names the Park value that caused it and that reading's receipt time
    And each named reading matches a getter reply in the evidence with that value and receipt time
    And any retained gate change from another cause names that cause and no Park reading

  @normal @AIR-026 @AT-26 @OD-71
  Scenario: After a restart with a changed flag, the start record gives the previous value as unknown
    Given the receiver ran on the owner's unit with the parking check on
    When the owner changes the installed receiver.conf to hold "video.require_park=false" and restarts the receiver
    Then the stick evidence holds the start record giving the parking check as off, as the startup value
    And that record gives the previous value as unknown
    And the recorded gate changes match the configuration change and the gear and display the owner observed

  @normal @AIR-027 @AT-27 @OD-22
  Scenario: Nothing mirrored appears on the HUD
    Given the receiver is mirroring parked
    Then the owner saw no mirrored video on the HUD
    And the stick evidence shows no driver-assistance display setting changed

  @normal @AIR-035 @AT-35 @OD-54
  Scenario: An update restores the shipped receiver.conf
    Given the installed receiver.conf was edited to hold "video.require_park=false"
    When the owner installs an update of the package
    Then the stick evidence shows the shipped receiver.conf in place and a start record with the parking check on

  @normal @AIR-035 @AT-35 @OD-54
  Scenario Outline: A reinstall or a rebuilt package puts its shipped receiver.conf in place
    Given the installed receiver.conf was edited to hold "video.require_park=<edited>"
    When the owner <action>
    Then the stick evidence shows the shipped receiver.conf in place and a start record with the parking check <state>

    Examples:
      | edited | action                                                        | state |
      | false  | reinstalls the package                                        | on    |
      | true   | installs a package rebuilt to ship "video.require_park=false" | off   |

  @normal @AIR-038 @AT-38 @OD-59
  Scenario: Regulation state is logged and doesn't decide video
    Given the receiver is mirroring parked, with the parking check on
    When the stock regulation state changes during the test, without the receiver or the owner modifying or spoofing it
    Then the stick evidence holds each regulation state with the gate state beside it
    And untested regulation states are listed as unverified

  @boundary @AIR-039 @AT-39 @OD-57 @OD-73 @OD-82
  Scenario: The logs stay within 5 MiB on the unit
    Given the receiver has logged enough to rotate its current log file more than once
    Then the stick evidence holds timestamped listings of the log folder with each file's size and the byte total
    And they show each rotation happening at or before 2621440 bytes
    And they show no file ever above 2621440 bytes
    And they show the older file deleted at each rotation
    And they show the two files never above 5242880 bytes together
    And an oversized-record drop is listed as unexercised unless the unit logged a "record too large" line

  @failure @AIR-040 @AT-40 @OD-58
  Scenario: A logging failure on the unit shows one notice and doesn't change the gate
    Given the receiver is mirroring parked, with the parking check on
    When logging fails twice in one app start, then again after a restart
    Then the owner saw one logging-failure notice before the restart and one after
    And the evidence, kept outside the failed log, shows the gate unchanged

  @failure @AIR-019 @AIR-040 @AT-19 @AT-40 @OD-101 @OD-102
  Scenario: On the unit, an ACC registration failure and a logging failure each show their own notice
    Given an app start on the owner's unit, with the parking check on, in which ACC registration failed
    When logging also fails, more than once, in that app start
    Then the owner saw exactly one registration-failure notice and exactly one logging-failure notice in that app start
    And the evidence, kept outside the failed log, shows video blocked throughout that app start

  @failure @AIR-021 @AIR-040 @AT-21 @AT-40 @OD-58 @OD-103 @OD-105
  Scenario: On the unit, with the check off, a logging failure shows only the logging-failure notice
    Given an app start on the owner's unit, with the installed receiver.conf holding "video.require_park=false"
    When logging fails, more than once, in that app start
    Then the owner saw exactly one logging-failure notice and no registration-failure notice in that app start
    And the evidence, kept outside the failed log, shows the parking check off and the gate never blocking video
