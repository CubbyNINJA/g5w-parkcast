@S1
Feature: The firewall is the owner's, through Windows' own prompt
  The S1 tools never change the firewall. Windows asks the owner when UxPlay first listens. The
  runner says so before launch, and every run that launched UxPlay ends by reminding the owner how
  to remove the allowance (AIR-032 to AIR-034; OD-31, OD-33 to OD-38).

  @offline @normal @AIR-034 @AT-34 @OD-34
  Scenario: The owner is told about Windows' prompt before UxPlay starts
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the runner launches the receiver
    Then before UxPlay started, the owner was told that Windows may ask to allow "uxplay.exe"
    And that notice said to tick "Public networks" and click Allow
    And that notice said no prompt may appear if an allowance or block already exists

  @offline @boundary @AIR-033 @AIR-034 @AT-33 @AT-34 @OD-35
  Scenario: A run that stops before launch gives no firewall notice or reminder
    Given the build at "C:\uxplay-s1\build\uxplay.exe" whose SHA-256 differs from its provenance
    When the runner launches the receiver
    Then UxPlay is never launched
    And the owner is given no firewall notice or reminder

  @offline @normal @AIR-033 @AT-33 @OD-33 @OD-35 @OD-38
  Scenario Outline: Every run that launched UxPlay ends with the reminder
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When the run ends by <ending>
    Then the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And the reminder says a declined or closed prompt may have left a block entry to remove too
    And the reminder came after UxPlay had stopped and after the log's end marker
    And the log's last line is still the runner's end marker

    Examples:
      | ending                    |
      | the owner ending the test |
      | the 10-minute timeout     |
      | UxPlay failing to start   |
      | UxPlay exiting mid-test   |
      | a keyboard interrupt      |

  @offline @failure @AIR-033 @AT-33
  Scenario: The reminder is still given when UxPlay won't stop
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the UxPlay process ignores the stop request
    When the run ends by the owner ending the test
    Then the run reports the UxPlay process that is still running, with its process ID
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And the reminder came after the run reported the process it couldn't stop

  @offline @failure @AIR-033 @AT-33
  Scenario: The reminder is still given when the run record can't be written
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the run record can't be written once UxPlay is running
    When the run ends by the owner ending the test
    Then the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And no run record exists
    And the run doesn't claim a record was made

  @offline @normal @AIR-033 @AT-33 @OD-37
  Scenario: The run record and the summary say the owner handled the firewall
    Given a finished run with a GO verdict
    When the evidence is written
    And the summary is written for the tier "pc"
    Then the run record says the firewall was "owner-handled"
    And the run record doesn't say an allowance was granted, declined, present or removed
    And the summary says the firewall allowance was handled by the owner through Windows' prompt

  @offline @failure @AIR-032 @AT-32 @OD-31
  Scenario Outline: No ending starts a firewall command
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And every process the S1 tools start is recorded
    When the run ends by <ending>
    Then none of the recorded processes is a firewall command

    Examples:
      | ending                    |
      | the owner ending the test |
      | the 10-minute timeout     |
      | UxPlay failing to start   |
      | UxPlay exiting mid-test   |
      | a keyboard interrupt      |

  @offline @normal @AIR-032 @AT-32 @OD-31 @OD-36
  Scenario: The tools hold no firewall-changing command and never touch another window
    Given the S1 tools' source under "tools/"
    Then no file there names a firewall-changing command
    And no file there sends input to, focuses or closes another window
