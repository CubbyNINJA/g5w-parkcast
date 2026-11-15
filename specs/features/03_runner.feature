@S1
Feature: The runner launches HU-Test, waits for the owner, and always stops
  The runner starts the verified UxPlay build as "HU-Test", offering H.264 only and a 1920x720
  display, with its log captured. It waits for the owner, for at most 10 minutes from launch,
  stops UxPlay however the test ends, and only then gives the firewall reminder (AIR-006, AIR-007,
  AIR-013, AIR-028, AIR-033; OD-08, OD-14, OD-31).

  @offline @normal @AIR-006 @AIR-028 @AT-06 @AT-28
  Scenario: The launch command offers H.264 only, as HU-Test, at 1920x720, with its log captured
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner launches the receiver
    Then the command runs "C:\uxplay-s1\build\uxplay.exe"
    And the command includes "-n HU-Test" and "-nh"
    And the command includes "-s 1920x720@60"
    And the command includes "-d"
    And the command does not include "-h265"
    And the effective command is recorded in the run
    And the run's log starts with the runner's start marker, written before UxPlay was launched
    And UxPlay's output is captured to the run's log after the start marker

  @offline @normal @AIR-013 @AT-13
  Scenario Outline: The launch never asks UxPlay to dump or forward the stream
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner launches the receiver
    Then the command does not include "<option>"

    Examples:
      | option |
      | -vdmp  |
      | -admp  |
      | -vrtp  |
      | -artp  |

  @offline @failure @AIR-013 @AIR-031 @AT-13 @AT-31 @OD-28
  Scenario Outline: A request to pass a capture option to UxPlay is refused
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner is started with the extra argument "<option>"
    Then it exits with code 2 and prints usage
    And UxPlay is never launched

    Examples:
      | option |
      | -vdmp  |
      | -admp  |
      | -vrtp  |
      | -artp  |

  @offline @failure @AIR-006 @AT-06
  Scenario: A build that isn't the verified one is refused
    Given the build at "C:\uxplay-s1\build\uxplay.exe" whose SHA-256 differs from its provenance
    When the runner launches the receiver
    Then the launch is refused naming the SHA-256 mismatch
    And UxPlay is never launched

  @offline @failure @AIR-006 @AIR-033 @AT-06 @AT-33
  Scenario: UxPlay that fails to start is reported, with the reminder
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And UxPlay exits at once with code 1
    When the runner launches the receiver
    Then the run reports that UxPlay failed to start, with exit code 1
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"

  @offline @failure @AIR-006 @AIR-033 @AT-06 @AT-33 @OD-35
  Scenario: UxPlay's output that can't be captured stops the run, with the reminder
    Given the verified build and writable run outputs
    And UxPlay starts but its output can't be attached to the run's log
    When the runner attempts to launch the receiver
    Then the run reports the diagnostic capture failure
    And no UxPlay process launched by the run remains running
    And the run is not reported as successfully started
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"

  @offline @failure @AIR-006 @AIR-033 @AT-06 @AT-33 @OD-35
  Scenario: A process that can't be created is reported, with no reminder
    Given the verified build and writable run outputs
    And creating the UxPlay process raises an operating-system error
    When the runner attempts to launch the receiver
    Then the run reports the process-creation error
    And the run is not reported as successfully started
    And no UxPlay process launched by the run remains running
    And the owner is given no firewall reminder

  @offline @normal @AIR-007 @AT-07
  Scenario: The runner tells the owner exactly what to do on the iPhone
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner launches the receiver
    Then the owner is told to open Control Center, tap Screen Mirroring and choose "HU-Test"
    And the owner is told how to end the test

  @offline @boundary @AIR-007 @AIR-011 @AIR-028 @AT-07 @AT-11 @AT-28 @OD-08 @AIR-033 @AT-33
  Scenario: The timeout is 10 minutes from launch and a connection doesn't restart it
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And a controlled clock
    When the runner launches the receiver at second 0
    And an iPhone connects at second 300 and reconnects at second 590
    Then UxPlay is stopped at second 600 with the reason "timeout"
    And the firewall reminder comes only after UxPlay has stopped
    And the log's last line is the runner's end marker naming "timeout"
    And the run records the launch time, the deadline at second 600 and the end at second 600

  @offline @boundary @AIR-007 @AIR-011 @AT-07 @AT-11 @OD-08 @AIR-033 @AT-33
  Scenario: Video starting just before the deadline doesn't extend the test
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And a controlled clock
    When the runner launches the receiver at second 0
    And an iPhone connects at second 590
    And mirroring video starts at second 599
    Then UxPlay is stopped at second 600 with the reason "timeout"
    And the firewall reminder comes only after UxPlay has stopped
    And the run records the launch at second 0, the deadline at second 600 and the end at second 600

  @offline @normal @AIR-007 @AIR-011 @AIR-028 @AT-07 @AT-11 @AT-28 @AIR-033 @AT-33
  Scenario: The owner can end the test early
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And a controlled clock
    When the runner launches the receiver at second 0
    And the owner ends the test at second 120
    Then UxPlay is stopped at second 120 with the reason "owner ended"
    And the firewall reminder comes only after UxPlay has stopped
    And the log's last line is the runner's end marker naming "owner ended"
    And the run records the deadline at second 600 and the end at second 120

  @offline @failure @AIR-007 @AIR-028 @AIR-033 @AT-07 @AT-28 @AT-33 @OD-42
  Scenario: A UxPlay process that won't stop is reported
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the UxPlay process ignores the stop request
    When the runner launches the receiver
    And the owner ends the test
    Then the run reports the UxPlay process that is still running, with its process ID
    And the log's last line is the runner's end marker naming "owner ended"
    And the end marker names the process ID still running
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"

  @offline @failure @AIR-028 @AT-28 @OD-50
  Scenario: UxPlay output cut off mid-line still leaves the end marker on its own line
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And UxPlay's output stops partway through a line, with no final newline
    When the run ends by the owner ending the test
    Then the log's last line is the runner's end marker naming "owner ended"
    And the cut-off output stays on its own line just before the end marker
