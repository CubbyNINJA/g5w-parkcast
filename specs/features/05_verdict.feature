@S1
Feature: GO, NO-GO or INCONCLUSIVE, with the reason and the lines behind it
  The evaluator gives each session a verdict and the run an overall one. Only an H.264 mirroring
  stream that actually started is GO. Damaged evidence is never GO (AIR-010, AIR-011; OD-09,
  OD-10, OD-12, OD-13, OD-17 to OD-19).

  @offline @normal @AIR-010 @AIR-011 @AT-10 @AT-11
  Scenario Outline: A single complete session's verdict
    Given a complete session in which <what happened>
    When the evaluator judges the run
    Then the session's verdict is <verdict> with the reason "<reason>"
    And the run's verdict is <verdict>
    And the verdict cites the log lines it rests on

    Examples:
      | what happened                                        | verdict      | reason                               |
      | an iPhone connected and H.264 mirroring started      | GO           | H.264 mirroring started              |
      | an iPhone connected and requested HEVC               | NO-GO        | HEVC requested                       |
      | an iPhone connected and no video stream started      | NO-GO        | connected but no H.264 mirroring     |
      | no iPhone ever connected                             | INCONCLUSIVE | no iPhone connected                  |

  @offline @failure @AIR-010 @AT-10
  Scenario Outline: Things short of a started H.264 stream are never GO
    Given a complete session in which <what happened>
    When the evaluator judges the run
    Then the run's verdict is not GO

    Examples:
      | what happened                                              |
      | an iPhone connected but sent nothing else                  |
      | only audio played                                          |
      | the receiver advertised H.264 and no iPhone connected      |

  @offline @boundary @AIR-010 @AT-10 @OD-19
  Scenario: A session that requests HEVC, then starts H.264, is GO with the request noted
    Given a complete session in which an iPhone requested HEVC and then H.264 mirroring started
    When the evaluator judges the run
    Then the session's verdict is GO with the reason "H.264 mirroring started"
    And the reason notes the earlier HEVC request

  @offline @normal @AIR-010 @AT-10 @OD-12
  Scenario Outline: The run is GO if any complete session is GO, whatever the order
    Given complete sessions with verdicts <sessions>
    When the evaluator judges the run
    Then the run's verdict is GO
    And every session's verdict is listed

    Examples:
      | sessions                    |
      | GO, NO-GO                   |
      | NO-GO, GO                   |
      | INCONCLUSIVE, NO-GO, GO     |

  @offline @boundary @AIR-010 @AT-10 @OD-18
  Scenario: NO-GO and INCONCLUSIVE sessions with no GO make the run NO-GO
    Given complete sessions with verdicts INCONCLUSIVE, NO-GO
    When the evaluator judges the run
    Then the run's verdict is NO-GO
    And every session's verdict is listed

  @offline @failure @AIR-009 @AIR-010 @AT-09 @AT-10 @OD-17
  Scenario: A damaged log is INCONCLUSIVE even when one session looks GO
    Given a complete session in which an iPhone connected and H.264 mirroring started
    And the log as a whole is truncated
    When the evaluator judges the run
    Then the run's verdict is INCONCLUSIVE with the reason "evidence error"
    And the limitation "truncated" is named

  @offline @failure @AIR-009 @AT-09 @OD-13
  Scenario: A session whose codec can't be identified is an evidence error
    Given a complete session in which an iPhone connected and mirroring started with no codec line
    When the evaluator judges the run
    Then the session's verdict is INCONCLUSIVE with the reason "evidence error"
    And the limitation "codec not identified" is named

  @offline @normal @AIR-008 @AIR-010 @AT-08 @AT-10 @OD-09 @OD-10
  Scenario Outline: The owner's notes are kept but never change a verdict
    Given a complete session in which an iPhone connected and requested HEVC
    And the owner's note "<note>" is "<value>"
    When the evaluator judges the run
    Then the run's verdict is NO-GO
    And the note "<note>" is kept with the value "<value>"

    Examples:
      | note          | value |
      | sound played  | yes   |
      | sound played  | no    |
      | YouTube check | works |

  @offline @boundary @AIR-011 @AT-11
  Scenario: A verdict that rests on something missing names how far the log was read
    Given a complete session in which an iPhone connected and no video stream started
    And the run ended at second 600 by the timeout
    When the evaluator judges the run
    Then the reason states the log was read to its last line and the run ended at second 600
    And the verdict cites no line as showing a video stream
