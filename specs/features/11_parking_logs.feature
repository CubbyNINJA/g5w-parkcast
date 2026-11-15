@S2
Feature: Regulation evidence, the log cap and logging failures
  The stock regulation state is logged but never decides video. Receiver logs live in
  /data/nemesis/logs/airplay-receiver/, two rotated files capped at 5,242,880 bytes together. A
  logging failure never changes the gate and shows at most one notice per app start (AIR-038 to
  AIR-040; OD-47, OD-49, OD-57 to OD-59).

  Clock times, "runs for", "within 10 ms" and the default Park getter behaviour are as defined in
  09_parking_gate.feature.

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

  @offline @boundary @AIR-039 @AT-39 @OD-57
  Scenario Outline: The logs are capped at 5 MiB together, dropping the oldest whole file
    Given the receiver's two log files hold <before> bytes together
    When <more> more bytes are logged
    Then the logs are in /data/nemesis/logs/airplay-receiver/
    And <outcome>
    And the logs together never hold more than 5242880 bytes
    And no file was trimmed

    Examples:
      | before  | more | outcome                                      |
      | 5242878 | 1    | both files are kept                          |
      | 5242879 | 1    | reaching 5242880 deleted the oldest file     |
      | 5242870 | 100  | crossing 5242880 deleted the oldest file     |

  @offline @failure @AIR-040 @AT-40 @OD-58
  Scenario Outline: A logging failure shows one notice per app start and never changes the gate
    Given the parking check is <check> and mirrored video is <video>
    When logging fails by <fault>, recovers, and then fails again
    Then exactly one on-screen logging notice was shown
    And the gate's decision didn't change

    Examples:
      | check | video     | fault                           |
      | on    | permitted | the log file failing to create  |
      | on    | blocked   | a write failing                 |
      | off   | permitted | rotation failing to delete      |
      | on    | permitted | a write failing                 |

  @offline @normal @AIR-040 @AT-40 @OD-58
  Scenario: After a restart, the first logging failure shows a new notice
    Given one logging notice was shown during the previous app start
    When the receiver restarts and logging fails
    Then exactly one on-screen logging notice was shown since the restart
