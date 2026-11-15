@S1
Feature: The runner launches HU-Test, waits for the owner, and always stops
  The runner starts the verified UxPlay build as "HU-Test", offering H.264 only and a 1920x720
  display, with its log captured. It waits for the owner, for at most 10 minutes from launch,
  and stops UxPlay however the test ends (AIR-006, AIR-007, AIR-013; OD-08, OD-14).

  @offline @normal @AIR-006 @AT-06
  Scenario: The launch command offers H.264 only, as HU-Test, at 1920x720
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner launches the receiver
    Then the command runs "C:\uxplay-s1\build\uxplay.exe"
    And the command includes "-n HU-Test" and "-nh"
    And the command includes "-s 1920x720@60"
    And the command includes "-d"
    And the command does not include "-h265"
    And the effective command is recorded in the run

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

  @offline @failure @AIR-006 @AT-06
  Scenario: A build that isn't the verified one is refused
    Given the build at "C:\uxplay-s1\build\uxplay.exe" whose SHA-256 differs from its provenance
    When the runner launches the receiver
    Then the launch is refused naming the SHA-256 mismatch
    And no firewall rule is created

  @offline @failure @AIR-006 @AT-06
  Scenario: UxPlay that fails to start is reported and cleaned up
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And UxPlay exits at once with code 1
    When the runner launches the receiver
    Then the run reports that UxPlay failed to start, with exit code 1
    And cleanup runs

  @offline @normal @AIR-007 @AT-07
  Scenario: The runner tells the owner exactly what to do on the iPhone
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner launches the receiver
    Then the owner is told to open Control Center, tap Screen Mirroring and choose "HU-Test"
    And the owner is told how to end the test

  @offline @boundary @AIR-007 @AIR-011 @AT-07 @AT-11 @OD-08
  Scenario: The timeout is 10 minutes from launch and a connection doesn't restart it
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And a controlled clock
    When the runner launches the receiver at second 0
    And an iPhone connects at second 300 and reconnects at second 590
    Then UxPlay is stopped at second 600 with the reason "timeout"
    And the run records the launch time, the deadline at second 600 and the end at second 600

  @offline @normal @AIR-007 @AIR-011 @AT-07 @AT-11
  Scenario: The owner can end the test early
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And a controlled clock
    When the runner launches the receiver at second 0
    And the owner ends the test at second 120
    Then UxPlay is stopped at second 120 with the reason "owner ended"
    And the run records the deadline at second 600 and the end at second 120

  @offline @failure @AIR-007 @AT-07
  Scenario: A UxPlay process that won't stop is reported
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the UxPlay process ignores the stop request
    When the runner launches the receiver
    And the owner ends the test
    Then the run reports the UxPlay process that is still running, with its process ID
    And cleanup still removes the firewall rule
