@S2 @unit
Feature: The parking gate on the owner's head unit
  These scenarios need the receiver installed on the owner's unit (260206 CA). They never run from
  the PC suite: their evidence is the receiver's logs and the owner's observations, saved to the
  stick. Offline results never stand in for them (AT-16 to AT-27, AT-35 to AT-40).

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

  @failure @AIR-019 @AT-19 @OD-56
  Scenario: After a resume from sleep, video waits for a fresh on-time Park reading
    Given the receiver is mirroring parked, with the parking check on
    When the head unit sleeps and resumes
    Then the stick evidence shows video blocked from the resume until a fresh on-time reply of 1
    And any untested fault or exact-timeout case is listed as unverified

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
    And no stock restriction setting changed

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
    And the owner saw no on-screen message about the parking check

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
      | edited | action                                                          | state |
      | false  | reinstalls the package                                          | on    |
      | true   | installs a package rebuilt to ship "video.require_park=false"   | off   |

  @normal @AIR-038 @AT-38 @OD-59
  Scenario: Regulation state is logged and doesn't decide video
    Given the receiver is mirroring parked, with the parking check on
    When the stock regulation state changes during the test, without the receiver or the owner modifying or spoofing it
    Then the stick evidence holds each regulation state with the gate state beside it
    And untested regulation states are listed as unverified

  @boundary @AIR-039 @AT-39 @OD-57
  Scenario: The logs stay within 5 MiB on the unit
    Given the receiver has logged enough to reach the cap
    Then the stick evidence holds timestamped listings of the log folder showing two files, at most 5242880 bytes together, and the oldest file deleted

  @failure @AIR-040 @AT-40 @OD-58
  Scenario: A logging failure on the unit shows one notice and doesn't change the gate
    Given the receiver is mirroring parked, with the parking check on
    When logging fails twice in one app start, then again after a restart
    Then the owner saw one notice before the restart and one after
    And the evidence, kept outside the failed log, shows the gate unchanged
