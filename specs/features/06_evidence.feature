@S1
Feature: What the run keeps, what it publishes, and what it claims
  The full run record stays private under runs/. Only the verdict and a redacted summary are
  committed. Nothing showing the phone's screen is ever saved, and a PC result never claims
  anything about the head unit (AIR-012 to AIR-014; OD-11, OD-15, OD-21).

  @offline @normal @AIR-012 @AT-12
  Scenario: A finished run keeps its whole record under runs/
    Given a finished run with a GO verdict
    When the evidence is written
    Then the run's folder under "runs/" holds the log, the analyser report, every session verdict and the run verdict
    And "runs/" is excluded from version control

  @offline @normal @AIR-012 @AT-12 @OD-11 @OD-21
  Scenario: The owner types in the iOS version and build when the log lacks it
    Given a finished run whose log shows no iOS version
    When the evidence is written
    Then the owner is asked for the full iOS version with its build number, from Settings > General > About
    And the answer "27.0 (24A5260a)" is recorded as "owner-entered"

  @offline @failure @AIR-012 @AT-12
  Scenario: A missing iOS version is marked missing, never guessed
    Given a finished run whose log shows no iOS version
    And the owner gives no answer
    When the evidence is written
    Then the iOS version is recorded as missing
    And the evidence is marked incomplete

  @offline @normal @AIR-012 @AT-12
  Scenario: The published summary leaves out the iPhone's name, deviceID and addresses
    Given a finished run whose log names "Owner iPhone" with deviceID "AA:BB:CC:DD:EE:FF" from address "192.168.1.57"
    When the redacted summary is written to "docs/evidence/"
    Then the summary contains the verdict, the model "iPhone17,1" and the iOS version
    And the summary contains neither "Owner iPhone", "AA:BB:CC:DD:EE:FF" nor "192.168.1.57"

  @offline @failure @AIR-013 @AT-13
  Scenario: No video frame, screenshot or recording is ever written
    Given a finished run with a GO verdict
    When the evidence is written
    Then the run's folder holds no video, image or stream dump file

  @offline @normal @AIR-014 @AT-14
  Scenario Outline: Every result says which tier it comes from
    Given a <tier> result with a GO verdict
    When the summary is written
    Then the summary names the tier "<tier>"
    And the summary says the head unit is untested

    Examples:
      | tier    |
      | offline |
      | pc      |

  @offline @failure @AIR-014 @AT-14
  Scenario: A fixture result can't be published as proof the iPhone works
    Given an offline result with a GO verdict
    When the summary is written
    Then the summary does not claim the owner's iPhone mirrors to the receiver

  @pc @normal @AIR-006 @AIR-007 @AIR-008 @AIR-010 @AIR-011 @AIR-012 @AIR-013 @AIR-014 @AT-06 @AT-07 @AT-08 @AT-10 @AT-11 @AT-12 @AT-13 @AT-14 @OD-15
  Scenario: The live test: the owner's iPhone mirrors to HU-Test
    Given the verified build and this PC's network
    And the owner's iPhone on iOS 27 on the same network
    When the runner launches "HU-Test" and the owner starts Screen Mirroring to it
    And the owner ends the test
    Then a session verdict and a run verdict are recorded, each with its reason and cited lines
    And the owner's sound note and the iOS version with its build are recorded
    And the run's folder holds no video, image or stream dump file
    And the firewall rule is gone
    And the summary names the tier "pc" and says the head unit is untested
