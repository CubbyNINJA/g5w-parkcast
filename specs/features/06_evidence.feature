@S1
Feature: What the run keeps, what it publishes, and what it claims
  The full run record stays private under runs/. Only the verdict and a redacted summary are
  committed. Nothing showing the phone's screen is ever saved, and a PC result never claims
  anything about the head unit. The live test is judged twice: first that its saved results
  agree with its own log, then whether that evidence is a GO (AIR-012 to AIR-014; OD-11, OD-15,
  OD-21).

  @offline @normal @AIR-012 @AT-12
  Scenario: A finished run keeps its whole record under runs/
    Given a finished run with a GO verdict
    When the evidence is written
    Then the run's folder under "runs/" holds the log, the analyser report, every session verdict and the run verdict
    And "runs/" is excluded from version control

  @offline @normal @AIR-012 @AT-12 @OD-11 @OD-21
  Scenario: The owner types in the iOS version and build when the log lacks both
    Given a finished run whose log shows no iOS version
    When the evidence is written
    Then the owner is asked for the full iOS version with its build number, from Settings > General > About
    And the answer "27.0 (24A5260a)" is recorded as "owner-entered"

  @offline @boundary @AIR-012 @AT-12 @OD-21
  Scenario: A known iOS version without its build still needs the owner's input
    Given a synthetic run record with iOS version "27.0" and no build number
    When the evidence is written
    Then the owner is asked for the full iOS version with its build number, from Settings > General > About
    And the answer "27.0 (24A5260a)" is recorded exactly, as "owner-entered"

  @offline @failure @AIR-012 @AT-12 @OD-21
  Scenario: A version-only answer leaves the build evidence incomplete
    Given a synthetic run record with no iOS version or build
    When the owner answers only "27.0"
    Then the build number is recorded as missing
    And the evidence is marked incomplete

  @offline @failure @AIR-012 @AT-12
  Scenario: A missing iOS version is marked missing, never guessed
    Given a finished run whose log shows no iOS version
    And the owner gives no answer
    When the evidence is written
    Then the iOS version is recorded as missing
    And the evidence is marked incomplete

  @offline @normal @AIR-012 @AT-12
  Scenario: The published summary leaves out the iPhone's name, deviceID and addresses
    Given a finished run whose log names "Fixture iPhone" with deviceID "AA:BB:CC:DD:EE:FF" from address "192.168.1.57"
    When the redacted summary is written to "docs/evidence/"
    Then the summary contains the verdict, the model "iPhone17,1" and the iOS version
    And the summary contains neither "Fixture iPhone", "AA:BB:CC:DD:EE:FF" nor "192.168.1.57"

  @offline @failure @AIR-013 @AT-13
  Scenario: No video frame, screenshot or recording is written anywhere the run writes
    Given a finished run with a GO verdict
    When the evidence is written
    Then the run's folder, the build tree and the runner's working directory hold no video, image or stream dump file
    And the recorded launch command holds none of "-vdmp", "-admp", "-vrtp" or "-artp"

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

  @pc @normal @AIR-005 @AIR-006 @AIR-007 @AIR-011 @AIR-012 @AIR-013 @AIR-028 @AT-05 @AT-06 @AT-07 @AT-11 @AT-12 @AT-13 @AT-28 @OD-15
  Scenario: The live test: the owner's iPhone mirrors to HU-Test
    Given the verified build and this PC's network
    And the owner's iPhone on iOS 27 on the same network
    When the runner launches "HU-Test", the owner starts Screen Mirroring to it and then ends the test at the console
    Then the command completed and created exactly one new run folder under "runs/"
    And the live run's folder under "runs/" holds the log, the analyser report, every session verdict and the run verdict
    And the recorded launch command is the one the offline launch scenarios specify
    And the run records the launch time, the deadline 600 seconds later, the end time and the reason "owner ended"
    And no UxPlay process launched by the run is still running
    And no rule the runner owns remains in the firewall
    And the log starts with the runner's start marker and ends with its end marker and a newline
    And the owner's sound note and the iOS version with its build, marked "owner-entered", are recorded
    And the live run's folder, the build tree and the working directory hold no video, image or stream dump file

  @pc @boundary @AIR-007 @AIR-011 @AIR-012 @AIR-013 @AT-07 @AT-11 @AT-12 @AT-13 @OD-08
  Scenario: A live run reaches the 10-minute timeout
    Given the verified build and this PC's network
    And the owner's iPhone on iOS 27 on the same network
    When the runner launches "HU-Test", the owner starts Screen Mirroring to it and leaves it until the timeout
    Then the command completed and created exactly one new run folder under "runs/"
    And the run records the launch time, the deadline 600 seconds later, the end time and the reason "timeout"
    And no UxPlay process launched by the run is still running
    And the owner's sound note and the iOS version with its build, marked "owner-entered", are recorded
    And the live run's folder, the build tree and the working directory hold no video, image or stream dump file

  @pc @normal @AIR-008 @AIR-009 @AIR-010 @AIR-011 @AIR-029 @AT-08 @AT-09 @AT-10 @AT-11 @AT-29
  Scenario: The live run's saved results agree with its own log
    Given the most recent live run under "runs/"
    When its retained log is analysed and judged again
    Then every observation in the saved report matches the one re-derived from the log
    And every cited line number holds the message the observation names
    And every saved session verdict and reason matches the re-derived one
    And the saved run verdict matches the re-derived one

  @pc @normal @AIR-010 @AIR-014 @AT-10 @AT-14
  Scenario: The H.264 feasibility gate needs an evidenced GO
    Given the most recent live run under "runs/" whose saved results agree with its own log
    Then the saved run verdict is GO
    And a GO session cites a "*** codec is h264" line in the retained log
    And the summary names the tier "pc", the build's commit, the iOS version with its build, and says the head unit is untested
