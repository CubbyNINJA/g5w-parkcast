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
    And the log's last line is the runner's end marker naming "<reason>"
    And the log's last line is still the runner's end marker
    And the run record says the firewall was "owner-handled"
    And the run record doesn't say an allowance was granted, declined, present or removed

    Examples:
      | ending                    | reason          |
      | the owner ending the test | owner ended     |
      | the 10-minute timeout     | timeout         |
      | UxPlay failing to start   | failed to start |
      | UxPlay exiting mid-test   | uxplay exited   |
      | a keyboard interrupt      | interrupted     |

  @offline @boundary @AIR-031 @AIR-033 @AT-31 @AT-33 @OD-41
  Scenario Outline: The reminder doesn't change the run command's exit code
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the run will end by <ending>
    When "s1.py run" runs
    Then it exits with code <code>
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"

    Examples:
      | ending                                 | code |
      | the owner ending the test              | 0    |
      | the 10-minute timeout                  | 0    |
      | UxPlay exiting mid-test                | 0    |
      | a keyboard interrupt                   | 130  |
      | UxPlay failing to start                | 1    |
      | UxPlay's output failing to be captured | 1    |

  @offline @failure @AIR-028 @AIR-033 @AT-28 @AT-33 @OD-42
  Scenario: The reminder is still given when UxPlay won't stop
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the UxPlay process ignores the stop request
    When the run ends by the owner ending the test
    Then the run reports the UxPlay process that is still running, with its process ID
    And the log's last line is the runner's end marker naming "owner ended"
    And the end marker names the process ID still running
    And the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And the reminder came after the run reported the process it couldn't stop

  @offline @failure @AIR-006 @AIR-028 @AIR-033 @AT-06 @AT-28 @AT-33 @OD-42
  Scenario: A capture failure whose UxPlay won't stop names the process, with the reminder
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And UxPlay starts but its output can't be attached to the run's log
    And the UxPlay process ignores the stop request
    When the runner attempts to launch the receiver
    Then the run reports the diagnostic capture failure
    And the run reports the UxPlay process that is still running, with its process ID
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

  @offline @failure @AIR-033 @AT-33
  Scenario: Through the command, an unwritable run record still gets the reminder and no claim
    Given the verified build at "C:\uxplay-s1\build\uxplay.exe"
    And the run will end by the owner ending the test
    And the run record can't be written once UxPlay is running
    When "s1.py run" runs
    Then the owner is reminded to remove "uxplay.exe" at "C:\uxplay-s1\build\uxplay.exe" from "Windows Defender Firewall > Allowed apps"
    And no run record exists
    And the console output doesn't claim a record was made

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

  @offline @failure @AIR-032 @AT-32 @OD-31
  Scenario Outline: No command, usage error or preflight stop starts a firewall command
    Given every process the S1 tools start is recorded
    And <condition>
    When "s1.py <arguments>" runs
    Then none of the recorded processes is a firewall command

    Examples:
      | arguments                          | condition                                            |
      | --help                             | nothing else is set up                               |
      | frobnicate                         | nothing else is set up                               |
      | run --bogus                        | nothing else is set up                               |
      | check                              | nothing else is set up                               |
      | build                              | nothing else is set up                               |
      | summary                            | nothing else is set up                               |
      | run                                | the build provenance file is missing                 |
      | run                                | the runs folder can't be written                     |
      | analyse fixture.log -o report.json | the report path can't be written                     |
      | evaluate fixture-run               | a fixture run whose evidence makes its verdict NO-GO |

  @offline @failure @AIR-032 @AT-32 @OD-31
  Scenario Outline: No refused, uncaptured or uncreated launch starts a firewall command
    Given <build>
    And every process the S1 tools start is recorded
    And <condition>
    When the runner attempts to launch the receiver
    Then none of the recorded processes is a firewall command

    Examples:
      | build                                                                                  | condition                                                       |
      | the build at "C:\uxplay-s1\build\uxplay.exe" whose SHA-256 differs from its provenance | nothing else is set up                                          |
      | the verified build at "C:\uxplay-s1\build\uxplay.exe"                                 | UxPlay starts but its output can't be attached to the run's log |
      | the verified build at "C:\uxplay-s1\build\uxplay.exe"                                 | creating the UxPlay process raises an operating-system error    |

  @offline @normal @AIR-032 @AT-32 @OD-31 @OD-36
  Scenario: The tools hold no firewall-changing command and never touch another window
    Given the S1 tools' source under "tools/"
    Then no file there names a firewall-changing command
    And no file there sends input to, focuses or closes another window
