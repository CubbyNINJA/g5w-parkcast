@S1
Feature: A temporary firewall rule for uxplay.exe only
  This PC's network is Public, which blocks the iPhone's inbound connections. The runner opens
  exactly one rule, for the built uxplay.exe, for the length of a test, and always takes it away
  again. Rules it doesn't own are never touched (AIR-003 to AIR-005; OD-05).

  Background:
    Given the runner's firewall rules are named "NEMESIS-AirPlay-S1"

  @offline @normal @AIR-003 @AT-03
  Scenario: A leftover rule from an interrupted run is removed before the next run
    Given the firewall holds a rule named "NEMESIS-AirPlay-S1" from an earlier run
    And the firewall holds an unrelated rule named "Steam"
    When a test run starts
    Then the rule named "NEMESIS-AirPlay-S1" is removed before UxPlay is launched
    And the rule named "Steam" is unchanged

  @offline @failure @AIR-003 @AT-03
  Scenario: A leftover rule that can't be removed stops the run before launch
    Given the firewall holds a rule named "NEMESIS-AirPlay-S1" from an earlier run
    And removing firewall rules fails
    When a test run starts
    Then the run stops naming the remaining rule "NEMESIS-AirPlay-S1"
    And UxPlay is never launched

  @offline @normal @AIR-004 @AT-04
  Scenario: The run's rule admits only this build's uxplay.exe, inbound, on the active profile
    Given the active network profile is "Public"
    And the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When a test run creates its firewall rule
    Then the rule is inbound and allows only the program "C:\uxplay-s1\build\uxplay.exe"
    And the rule applies to the "Public" profile
    And no rule allows any other program or a whole port range

  @offline @failure @AIR-004 @AT-04
  Scenario: A rule that can't be created stops the run before launch
    Given creating firewall rules fails
    When a test run starts
    Then UxPlay is never launched
    And the run reports that the firewall rule could not be created
    And cleanup runs

  @offline @normal @AIR-005 @AT-05
  Scenario Outline: The rule is removed and the removal checked however the run ends
    Given a test run whose firewall rule was created
    When the run ends by <ending>
    Then the rule named "NEMESIS-AirPlay-S1" is removed
    And the removal is checked by listing the firewall rules again

    Examples:
      | ending                    |
      | the owner ending the test |
      | the 10-minute timeout     |
      | UxPlay failing to start   |
      | UxPlay exiting mid-test   |
      | a keyboard interrupt      |

  @offline @failure @AIR-005 @AT-05
  Scenario: A rule that can't be removed is reported, never claimed as cleaned up
    Given a test run whose firewall rule was created
    And removing firewall rules fails
    When the run ends by the owner ending the test
    Then the run reports the residual rule "NEMESIS-AirPlay-S1" and its program path
    And the run does not report a clean cleanup

  @pc @normal @AIR-003 @AIR-004 @AIR-005 @AT-03 @AT-04 @AT-05
  Scenario: The real rule's lifecycle on this PC
    Given this PC's Windows Firewall
    And a rule named "NEMESIS-AirPlay-S1" deliberately left from an earlier run
    When a test run starts and is cancelled by the owner
    Then the leftover rule was removed before launch
    And the run's rule was inbound, program-only and on the Public profile while the run was active
    And after the run no rule named "NEMESIS-AirPlay-S1" exists
