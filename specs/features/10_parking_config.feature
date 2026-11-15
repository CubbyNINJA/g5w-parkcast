@S2
Feature: The parking flag in receiver.conf, and what each start records
  The flag is video.require_park in config/receiver.conf under the package's install folder. It is
  read strictly at every start; anything missing or unclear turns the check on. An update or
  reinstall restores the shipped file. Every start and every gate change is logged, and nothing on
  screen says the check is off (AIR-023, AIR-025, AIR-026, AIR-035, AIR-036; OD-43 to OD-45,
  OD-54, OD-55, OD-60, OD-68 to OD-71, OD-75, OD-79 to OD-81, OD-85, OD-88).

  File contents are written as lines separated by " ; ". Clock times, "runs for", "within 10 ms"
  and the default Park getter behaviour are as defined in 09_parking_gate.feature.

  "The start record contains X" matches the literal text X anywhere in the start record, so the
  order of the record's fields is never asserted.

  @offline @boundary @AIR-023 @AT-23 @OD-55 @OD-68 @OD-69 @OD-79 @OD-80 @OD-85 @OD-88
  Scenario Outline: receiver.conf is read strictly, and anything unclear turns the check on
    Given the installed receiver.conf holds "<lines>"
    When the receiver starts
    Then the parking check is <state>

    Examples:
      | lines                                                     | state |
      | video.require_park=true                                   | on    |
      | video.require_park=false                                  | off   |
      | video.require_park=False                                  | on    |
      | video.require_park=FALSE                                  | on    |
      | video.require_park=no                                     | on    |
      | video.require_park=                                       | on    |
      | other.key=false                                           | on    |
      | video.require_park = false                                | on    |
      | video.require_park =false                                 | on    |
      | video.require_park= false                                 | on    |
      | video.require_park = false ; video.require_park=false     | on    |
      | video.require_park=false ; video.require_park = false     | on    |
      | video.require_park = false ; video.require_park = false   | on    |
      | video.require_park = false ; video.require_park =false    | on    |
      | video.require_park=false ; video.require_park=true        | on    |
      | video.require_park=true ; video.require_park=false        | on    |
      | video.require_park=false ; video.require_park=maybe       | on    |
      | video.require_park=maybe ; video.require_park=false       | on    |
      | video.require_park=false ; video.require_park=False       | on    |
      | video.require_park=false ; video.require_park=false       | off   |
      | video.require_park=true ; video.require_park=true         | on    |

  @offline @boundary @AIR-023 @AT-23 @OD-68 @OD-69 @OD-80
  Scenario Outline: An identical duplicate is accepted even when one copy has whitespace around the whole line
    Given the installed receiver.conf holds the line "video.require_park=<value>" twice
    And the <which> occurrence has two spaces before it and two spaces after it
    When the receiver starts
    Then the parking check is <state>

    Examples:
      | value | which  | state |
      | false | first  | off   |
      | false | second | off   |
      | true  | first  | on    |

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
    Given the package is installed in /data/nemesis/apps/<id>/ with config/receiver.conf holding "video.require_park=false", and a decoy package/config/receiver.conf holding "video.require_park=true"
    When the receiver starts
    Then the parking check is off

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

  @offline @failure @AIR-035 @AT-35 @OD-60 @OD-70
  Scenario Outline: An install that fails before the shipped file is in place leaves the check on
    Given the installed receiver.conf was edited to hold "video.require_park=false"
    When an update fails and leaves receiver.conf <state>
    Then at the next start the parking check is on

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

  @offline @normal @AIR-025 @AT-25 @OD-43 @OD-75 @OD-79 @OD-81 @OD-85 @OD-88
  Scenario Outline: Every start records the flag, where it came from, the initial gate state and why
    Given the installed receiver.conf <state>
    When the receiver starts
    Then its start record gives the parking check as <check>, from <source>, with the initial gate state as <initial>
    And the start record contains "<reason>", whatever the order of its fields
    And a failed read is named without logging any other contents of the file

    Examples:
      | state                                                           | check | source       | initial   | reason                               |
      | holds "video.require_park=true"                                 | on    | the file     | blocked   | reason=read from the file            |
      | holds "video.require_park=false"                                | off   | the file     | permitted | reason=read from the file            |
      | is missing                                                      | on    | the fallback | blocked   | reason=the missing file              |
      | is unreadable                                                   | on    | the fallback | blocked   | reason=the unreadable file           |
      | holds "video.require_park=maybe ; pin=1234"                     | on    | the fallback | blocked   | reason=the unrecognised value        |
      | holds "video.require_park = false"                              | on    | the fallback | blocked   | reason=the unrecognised value        |
      | holds "video.require_park = false ; video.require_park = false" | on    | the fallback | blocked   | reason=the unrecognised value        |
      | holds "other.key=false"                                         | on    | the fallback | blocked   | reason=the missing key               |
      | holds "video.require_park=false ; video.require_park=true"      | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park=false ; video.require_park=maybe"     | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park=false ; video.require_park=False"     | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park = false ; video.require_park=false"   | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park=false ; video.require_park =false"    | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park = false ; video.require_park =false"  | on    | the fallback | blocked   | reason=the conflicting duplicate key |

  @offline @normal @AIR-026 @AT-26 @OD-71
  Scenario: Every gate change is logged with the state before, the state after and why
    Given the parking check is on, on a supported build
    When the car leaves Park at 500 ms and returns to Park at 1500 ms
    And the receiver runs for 2500 ms
    Then the log has exactly three gate-change records, in this order:
      | previous  | new       | reason                                         |
      | blocked   | permitted | the on-time Park reading received at 10 ms     |
      | permitted | blocked   | the poll that saw the car leave Park           |
      | blocked   | permitted | the poll that saw the car back in Park         |
    And the first of them, blocked to permitted after start, is logged as a transition

  @offline @normal @AIR-026 @AT-26 @OD-71
  Scenario Outline: A flag that changed across a restart is logged as the startup value
    Given the previous run had the parking check <previous>
    And <earlier>
    And the installed receiver.conf holds "video.require_park=<now>"
    When the receiver starts
    Then the start record gives the parking check as <state>, as the startup value
    And it gives the previous value as unknown
    And the receiver read no state from the earlier run or its log

    Examples:
      | previous | now   | state | earlier                                    |
      | on       | false | off   | the earlier run's log is still present     |
      | on       | false | off   | no log from the earlier run is present     |
      | off      | true  | on    | the earlier run's log is still present     |
      | off      | false | off   | the earlier run's log is still present     |
