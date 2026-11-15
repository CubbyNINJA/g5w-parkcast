@S1
Feature: GO, NO-GO or INCONCLUSIVE, with the reason and the lines behind it
  The evaluator gives each session a verdict and the run an overall one. Only an H.264 mirroring
  stream that actually started is GO. Damaged evidence is never GO (AIR-009 to AIR-011,
  AIR-029; OD-09, OD-10, OD-12, OD-13, OD-17 to OD-19, OD-25, OD-26).

  @offline @normal @AIR-010 @AIR-011 @AIR-029 @AT-10 @AT-11 @AT-29 @OD-25
  Scenario Outline: A single complete session's verdict
    Given a complete log with one session in which <what happened>
    When the evaluator judges the run
    Then the session's verdict is <verdict> with the reason "<reason>"
    And the run's verdict is <verdict>
    And the verdict cites the log lines it rests on

    Examples:
      | what happened                                    | verdict | reason                           |
      | H.264 mirroring started                          | GO      | H.264 mirroring started          |
      | the iPhone requested HEVC                        | NO-GO   | HEVC requested                   |
      | no video stream arrived                          | NO-GO   | connected but no H.264 mirroring |
      | only mirroring setup and the connection were logged | NO-GO | connected but no H.264 mirroring |

  @offline @boundary @AIR-010 @AT-10 @OD-26
  Scenario: A run with no session is INCONCLUSIVE because no iPhone connected
    Given a complete log in which no iPhone ever connected
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "no iPhone connected"
    And no session verdict is listed

  @offline @failure @AIR-010 @AT-10
  Scenario Outline: Things short of a started H.264 stream are never GO
    Given a complete log in which <what happened>
    When the evaluator judges the run
    Then the run's verdict is not GO

    Examples:
      | what happened                                         |
      | an iPhone connected but sent nothing else             |
      | only audio played                                     |
      | the receiver advertised H.264 and no iPhone connected |

  @offline @boundary @AIR-010 @AT-10 @OD-19
  Scenario: A session that requests HEVC, then starts H.264, is GO with the request noted
    Given a complete log with one session in which the iPhone requested HEVC and then H.264 mirroring started
    When the evaluator judges the run
    Then the session's verdict is GO with the reason "H.264 mirroring started"
    And the reason notes the earlier HEVC request

  @offline @failure @AIR-009 @AIR-029 @AT-09 @AT-29 @OD-25
  Scenario: A stream that arrived with no codec line is an evidence error
    Given a complete log with one session in which a video stream arrived and no codec line followed
    When the evaluator judges the run
    Then the session's verdict is INCONCLUSIVE with the reason "evidence error"
    And the limitation "codec not identified" is named
    And the run's verdict is INCONCLUSIVE

  @offline @normal @AIR-010 @AT-10 @OD-12 @OD-18 @OD-26
  Scenario Outline: The run's verdict comes from its sessions, whatever their order
    Given an error-free run with complete sessions whose verdicts are <sessions>
    When the evaluator judges the run
    Then the run's verdict is <verdict>
    And every session's verdict is listed unchanged

    Examples:
      | sessions                   | verdict      |
      | GO, NO-GO                  | GO           |
      | NO-GO, GO                  | GO           |
      | INCONCLUSIVE, NO-GO, GO    | GO           |
      | GO, INCONCLUSIVE           | GO           |
      | INCONCLUSIVE, GO           | GO           |
      | GO, GO                     | GO           |
      | NO-GO, INCONCLUSIVE        | NO-GO        |
      | INCONCLUSIVE, NO-GO        | NO-GO        |
      | NO-GO, NO-GO               | NO-GO        |
      | INCONCLUSIVE               | INCONCLUSIVE |
      | INCONCLUSIVE, INCONCLUSIVE | INCONCLUSIVE |

  @offline @failure @AIR-009 @AIR-010 @AIR-028 @AT-09 @AT-10 @AT-28 @OD-17
  Scenario Outline: Whole-log damage overrides otherwise decisive session evidence
    Given a run containing a complete session whose verdict is <session verdict>
    And the log as a whole is <damage>
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "evidence error"
    And the limitation "<limitation>" is named

    Examples:
      | session verdict | damage            | limitation |
      | GO              | truncated         | truncated  |
      | GO              | partly unreadable | unreadable |
      | NO-GO           | truncated         | truncated  |
      | NO-GO           | partly unreadable | unreadable |

  @offline @boundary @AIR-008 @AIR-010 @AT-08 @AT-10 @OD-09 @OD-10
  Scenario Outline: Changing an owner observation changes neither verdict level
    Given a complete synthetic run with one session whose verdict is <verdict>
    When only the "<note>" observation changes from "<before>" to "<after>"
    Then the session verdict remains <verdict>
    And the run verdict remains <verdict>
    And the updated observation is retained

    Examples:
      | verdict | note          | before | after |
      | GO      | sound played  | yes    | no    |
      | NO-GO   | sound played  | no     | yes   |
      | GO      | YouTube check | works  | fails |
      | NO-GO   | YouTube check | fails  | works |

  @offline @boundary @AIR-011 @AT-11
  Scenario: A verdict that rests on something missing names how far the log was read
    Given a complete log with one session in which no video stream arrived
    And the run ended at second 600 by the timeout
    When the evaluator judges the run
    Then the reason states the log was read to its last line and the run ended at second 600
    And the verdict cites no line as showing a video stream
