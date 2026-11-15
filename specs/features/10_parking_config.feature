@S2
Feature: The parking flag in receiver.conf, and what each start records
  The flag is video.require_park in config/receiver.conf under the package's install folder. It is
  read strictly at every start; anything missing or unclear turns the check on. An update or
  reinstall restores the shipped file. Every start and every gate change is logged, and nothing on
  screen says the check is off (AIR-023, AIR-025, AIR-026, AIR-035, AIR-036; OD-43 to OD-45,
  OD-54, OD-55, OD-60).

  File contents are written as lines separated by " ; ".

  @offline @boundary @AIR-023 @AT-23 @OD-55
  Scenario Outline: receiver.conf is read strictly, and anything unclear turns the check on
    Given the installed receiver.conf holds "<lines>"
    When the receiver starts
    Then the parking check is <state>

    Examples:
      | lines                                                   | state |
      | video.require_park=true                                 | on    |
      | video.require_park=false                                | off   |
      | video.require_park=False                                | on    |
      | video.require_park=FALSE                                | on    |
      | video.require_park=no                                   | on    |
      | video.require_park=                                     | on    |
      | other.key=false                                         | on    |
      | video.require_park=false ; video.require_park=true      | on    |
      | video.require_park=true ; video.require_park=false      | on    |
      | video.require_park=false ; video.require_park=false     | off   |

  @offline @boundary @AIR-023 @AT-23 @OD-55
  Scenario: Whitespace around the line is trimmed
    Given the installed receiver.conf holds "  video.require_park=false  "
    When the receiver starts
    Then the parking check is off

  @offline @failure @AIR-023 @AT-23 @OD-43
  Scenario Outline: A missing or unreadable receiver.conf turns the check on, even after a run with it off
    Given the previous run had the parking check off
    And the installed receiver.conf is <state>
    When the receiver starts
    Then the parking check is on

    Examples:
      | state      |
      | missing    |
      | unreadable |

  @offline @normal @AIR-023 @AT-23 @OD-43
  Scenario: The flag is read from the package's install folder at every start
    Given the package is installed in /data/nemesis/apps/ with receiver.conf holding "video.require_park=false"
    When the receiver starts
    Then it read the flag from config/receiver.conf in that install folder

  @offline @normal @AIR-023 @AT-23 @OD-43
  Scenario: An edit takes effect at the next start, not during the running one
    Given the installed receiver.conf holds "video.require_park=true"
    And the receiver is running
    When the installed receiver.conf is changed to hold "video.require_park=false"
    Then the parking check stays on while the receiver keeps running
    And after the receiver restarts the parking check is off

  @offline @normal @AIR-035 @AT-35 @OD-44 @OD-54
  Scenario Outline: An update or reinstall replaces a local edit with the shipped file
    Given the installed receiver.conf was edited to hold "video.require_park=false"
    When the package is <action> successfully, shipping "video.require_park=<shipped>"
    Then the installed receiver.conf holds "video.require_park=<shipped>"
    And at the next start the parking check is <state>

    Examples:
      | action      | shipped | state |
      | updated     | true    | on    |
      | reinstalled | true    | on    |
      | updated     | false   | off   |
      | reinstalled | false   | off   |

  @offline @failure @AIR-035 @AT-35 @OD-60
  Scenario Outline: An install that fails before the shipped file is in place leaves the check on
    Given the installed receiver.conf was edited to hold "video.require_park=false"
    When an update fails and leaves receiver.conf <state>
    Then at the next start the parking check is on
    And the update is not reported as a successful replacement

    Examples:
      | state      |
      | missing    |
      | unreadable |

  @offline @normal @AIR-036 @AIR-025 @AT-36 @AT-25 @OD-45
  Scenario: With the check off, nothing on screen says so, and every start logs it
    Given the installed receiver.conf holds "video.require_park=false"
    When the receiver starts twice
    Then no on-screen message or indicator mentioned the parking check
    And each start's record gives the parking check as off, read from the file

  @offline @normal @AIR-025 @AT-25 @OD-43
  Scenario Outline: Every start records the flag, where it came from, the initial gate state and why
    Given the installed receiver.conf <state>
    When the receiver starts
    Then its start record gives the parking check as <check>, from <source>, with the initial gate state and the reason
    And a failed read is named without logging any other contents of the file

    Examples:
      | state                                       | check | source        |
      | holds "video.require_park=true"             | on    | the file      |
      | holds "video.require_park=false"            | off   | the file      |
      | is missing                                  | on    | the fallback  |
      | is unreadable                               | on    | the fallback  |
      | holds "video.require_park=maybe ; pin=1234" | on    | the fallback  |

  @offline @normal @AIR-026 @AT-26
  Scenario: Every gate change is logged with the state before, the state after and why
    Given the parking check is on and video is permitted after an on-time Park reading at 0 ms
    When the car leaves Park at 500 ms and returns to Park at 1500 ms
    And the receiver runs for 2500 ms
    Then the log has one record for each gate change, in order, each with the previous state, the new state and the reason

  @offline @normal @AIR-026 @AT-26
  Scenario: A flag that changed across a restart is logged as the startup value
    Given the previous run had the parking check on, and its log is gone
    And the installed receiver.conf holds "video.require_park=false"
    When the receiver starts
    Then the start record gives the parking check as off, as the startup value
    And it gives the previous value as unknown
