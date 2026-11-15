@S2
Feature: The parking flag in receiver.conf, and what each start records
  The flag is video.require_park in config/receiver.conf under the package's install folder. It is
  read strictly at every start; anything missing or unclear turns the check on. An update or
  reinstall restores the shipped file. Every start and every gate change is logged, and nothing on
  screen says the check is off. An explicit false also writes "parking check off" as the start's
  first record (AIR-023, AIR-025, AIR-026, AIR-035, AIR-036, AIR-040; OD-43 to OD-45, OD-54,
  OD-55, OD-60, OD-68 to OD-71, OD-75, OD-79 to OD-81, OD-85, OD-88, OD-103 to OD-105).

  File contents are written as lines separated by " ; ". Clock times, "runs for", "within 10 ms",
  the default Park getter behaviour and the two on-screen notices are as defined in
  09_parking_gate.feature.

  "The start record contains X" matches the literal text X anywhere in the start record, so the
  order of the record's fields is never asserted.

  @offline @boundary @AIR-023 @AT-23 @OD-55 @OD-68 @OD-69 @OD-79 @OD-80 @OD-85 @OD-88
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
      | video.require_park = false                              | on    |
      | video.require_park =false                               | on    |
      | video.require_park= false                               | on    |
      | video.require_park = false ; video.require_park=false   | on    |
      | video.require_park=false ; video.require_park = false   | on    |
      | video.require_park = false ; video.require_park = false | on    |
      | video.require_park = false ; video.require_park =false  | on    |
      | video.require_park=false ; video.require_park=true      | on    |
      | video.require_park=true ; video.require_park=false      | on    |
      | video.require_park=false ; video.require_park=maybe     | on    |
      | video.require_park=maybe ; video.require_park=false     | on    |
      | video.require_park=false ; video.require_park=False     | on    |
      | video.require_park=false ; video.require_park=false     | off   |
      | video.require_park=true ; video.require_park=true       | on    |

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

    Examples:
      | state                                                                  | check | source       | initial   | reason                               |
      | holds "video.require_park=true"                                        | on    | the file     | blocked   | reason=read from the file            |
      | holds "video.require_park=false"                                       | off   | the file     | permitted | reason=read from the file            |
      | is missing                                                             | on    | the fallback | blocked   | reason=the missing file              |
      | is unreadable                                                          | on    | the fallback | blocked   | reason=the unreadable file           |
      | is unreadable, and its bytes are "video.require_park=false ; pin=1234" | on    | the fallback | blocked   | reason=the unreadable file           |
      | holds "video.require_park=maybe ; pin=1234"                            | on    | the fallback | blocked   | reason=the unrecognised value        |
      | holds "video.require_park = false"                                     | on    | the fallback | blocked   | reason=the unrecognised value        |
      | holds "video.require_park = false ; video.require_park = false"        | on    | the fallback | blocked   | reason=the unrecognised value        |
      | holds "other.key=false"                                                | on    | the fallback | blocked   | reason=the missing key               |
      | holds "video.require_park=false ; video.require_park=true"             | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park=false ; video.require_park=maybe"            | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park=false ; video.require_park=False"            | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park = false ; video.require_park=false"          | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park=false ; video.require_park =false"           | on    | the fallback | blocked   | reason=the conflicting duplicate key |
      | holds "video.require_park = false ; video.require_park =false"         | on    | the fallback | blocked   | reason=the conflicting duplicate key |

  @offline @failure @AIR-025 @AT-25
  Scenario Outline: A fallback start names its cause without logging other contents of the file
    Given the installed receiver.conf <state>
    When the receiver starts
    Then the start record contains "<reason>"
    And no record contains "1234"

    Examples:
      | state                                                                  | reason                        |
      | holds "video.require_park=maybe ; pin=1234"                            | reason=the unrecognised value |
      | is unreadable, and its bytes are "video.require_park=false ; pin=1234" | reason=the unreadable file    |

  @offline @normal @AIR-025 @AT-25 @OD-103 @OD-105
  Scenario: An explicit false writes "parking check off" as the start's first record, apart from its start record
    Given the installed receiver.conf holds "video.require_park=false"
    When the receiver starts
    Then the first record of the start contains "parking check off"
    And that record is not the start record
    And the start record comes after it and contains "reason=read from the file"
    And exactly one record contains "parking check off"

  @offline @normal @AIR-025 @AT-25 @OD-103 @OD-105
  Scenario: "parking check off" follows an earlier start's records and precedes every record of the new start
    Given the log file holds the records of an earlier synthetic start
    And the installed receiver.conf holds "video.require_park=false"
    When the receiver starts
    Then the earlier start's records come first, unchanged
    And the next record contains "parking check off"
    And it precedes the new start's start record, its startup flag record and any ACC registration record

  @offline @normal @AIR-025 @AT-25 @OD-103 @OD-105
  Scenario Outline: Only an explicit false writes "parking check off"
    Given the previous run had the parking check <previous>
    And the installed receiver.conf <state>
    When the receiver starts
    Then the parking check is on
    And no record of the new start contains "parking check off"

    Examples:
      | previous | state                                                      |
      | on       | holds "video.require_park=true"                            |
      | on       | is missing                                                 |
      | on       | is unreadable                                              |
      | on       | holds "other.key=false"                                    |
      | on       | holds "video.require_park=false ; video.require_park=true" |
      | on       | holds "video.require_park=maybe"                           |
      | off      | is missing                                                 |

  @offline @failure @AIR-025 @AIR-040 @AT-25 @AT-40 @OD-103 @OD-105
  Scenario: A failed "parking check off" write is not retried, and the start goes on with the check off
    Given the installed receiver.conf holds "video.require_park=false"
    And writing the "parking check off" record fails, and every later write succeeds
    When the receiver runs for 2000 ms with mirroring active
    Then exactly one logging-failure notice was shown
    And no registration-failure notice was shown
    And the parking check is off
    And the start record was written
    And the parking gate never blocked video
    And no record contains "parking check off"

  @offline @normal @AIR-026 @AT-26 @OD-71 @OD-104
  Scenario: Every gate change is logged with the state before, the state after and why
    Given the parking check is on, on a supported build
    When the car leaves Park at 500 ms and returns to Park at 1500 ms
    And the receiver runs for 2500 ms
    Then the log has exactly three gate-change records, in this order:
      | previous  | new       | reason                                    |
      | blocked   | permitted | the Park reading of 1 received at 10 ms   |
      | permitted | blocked   | the Park reading of 0 received at 610 ms  |
      | blocked   | permitted | the Park reading of 1 received at 1610 ms |
    And the first of them, blocked to permitted after start, is logged as a transition

  @offline @normal @AIR-026 @AT-26 @OD-104 @OD-105
  Scenario: A gate change caused by a Park reading names its value and receipt time
    Given the parking check is on, on a supported build
    And the Park getter call starting at 0 ms replies 1 at 150 ms
    And the car leaves Park at 900 ms, and the call starting at 1000 ms replies 0 at 1150 ms
    When the receiver runs for 1500 ms
    Then the gate-change record from blocked to permitted names a Park value of 1 received at 150 ms
    And the gate-change record from permitted to blocked names a Park value of 0 received at 1150 ms

  @offline @normal @AIR-026 @AT-26 @OD-104 @OD-105
  Scenario Outline: A gate change with any other cause names that cause and no Park reading
    Given <setup>
    When the receiver runs for 3000 ms
    Then <record> names <cause> as its cause
    And that record gives no Park value or receipt time as its cause

    Examples:
      | setup                                                                                                                                           | record                                   | cause              |
      | the parking check is on and video is permitted after an on-time Park reading at 0 ms, and the Park getter call starting at 200 ms never replies | the gate-change record that blocks video | the 200 ms timeout |
      | the parking check is on and video is permitted after an on-time Park reading at 0 ms, and ACC status changes to 6 at 300 ms                     | the gate-change record that blocks video | sleep entry        |
      | the previous run had the parking check on, and the installed receiver.conf now holds "video.require_park=false"                                 | the startup flag record                  | the startup value  |

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
      | previous | now   | state | earlier                                |
      | on       | false | off   | the earlier run's log is still present |
      | on       | false | off   | no log from the earlier run is present |
      | off      | true  | on    | the earlier run's log is still present |
      | off      | false | off   | the earlier run's log is still present |
