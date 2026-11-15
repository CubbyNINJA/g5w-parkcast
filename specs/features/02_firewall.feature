@S1
Feature: A temporary firewall rule for uxplay.exe only
  This PC's network is Public, which blocks the iPhone's inbound connections. The runner opens
  exactly one rule, for the built uxplay.exe, for the length of a test, and always takes it away
  again. Rules it doesn't own are never touched (AIR-003 to AIR-005, AIR-030; OD-05).

  Background:
    Given the build root recorded in the provenance is "C:\uxplay-s1\"
    And the runner owns a rule only if it is named "NEMESIS-AirPlay-S1" and its program is a uxplay.exe under that root

  @offline @normal @AIR-003 @AIR-030 @AT-03 @AT-30
  Scenario: Every leftover owned rule is removed before the run's rule is created
    Given the firewall holds 2 owned rules left by earlier runs
    And the firewall holds an unrelated rule named "Steam"
    When a test run starts
    Then both owned rules are removed, and their removal checked, before the run's rule is created
    And the rule named "Steam" is unchanged

  @offline @failure @AIR-003 @AT-03
  Scenario: A leftover rule that can't be removed stops the run before launch
    Given the firewall holds 1 owned rule left by an earlier run
    And removing firewall rules fails
    When a test run starts
    Then the run stops naming the remaining rule "NEMESIS-AirPlay-S1"
    And no new rule is created
    And UxPlay is never launched

  @offline @failure @AIR-030 @AT-30
  Scenario: A rule with the runner's name but another program is reported and left alone
    Given the firewall holds a rule named "NEMESIS-AirPlay-S1" for the program "C:\Tools\other.exe"
    When a test run starts
    Then the run stops naming that rule and its program "C:\Tools\other.exe"
    And that rule is unchanged
    And no new rule is created
    And UxPlay is never launched

  @offline @normal @AIR-004 @AIR-030 @AT-04 @AT-30
  Scenario: The run's rule admits only this build's uxplay.exe, inbound, on the active profile
    Given the active network profile is "Public"
    And the verified build at "C:\uxplay-s1\build\uxplay.exe"
    When a test run creates its firewall rule
    Then the run creates exactly one rule
    And that rule is inbound and allows only the program "C:\uxplay-s1\build\uxplay.exe"
    And that rule applies to the "Public" profile
    And that rule names no port or port range

  @offline @failure @AIR-004 @AT-04
  Scenario: A rule that can't be created stops the run before launch
    Given creating firewall rules fails
    When a test run starts
    Then UxPlay is never launched
    And the run reports that the firewall rule could not be created
    And cleanup runs

  @offline @normal @AIR-005 @AIR-030 @AT-05 @AT-30
  Scenario Outline: Only the run's rule is removed, and the removal checked, however the run ends
    Given the firewall holds an unrelated rule named "Steam"
    And a test run whose firewall rule was created
    When the run ends by <ending>
    Then the run's rule is removed
    And the removal is checked by listing the firewall rules again
    And the rule named "Steam" is unchanged

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

  @host @normal @AIR-003 @AIR-004 @AIR-005 @AIR-030 @AT-03 @AT-04 @AT-05 @AT-30
  Scenario: The real rule's lifecycle on this PC when the owner cancels
    Given this PC's Windows Firewall
    And an owned rule deliberately left from an earlier run
    When a test run starts and the owner cancels it at once
    Then the firewall rule lists from before, during and after the run are saved
    And they show the leftover rule removed before the run's rule was created
    And they show the run's rule inbound, for only the built uxplay.exe and on the Public profile while the run was active
    And they show no owned rule after the run
    And they show every rule the runner doesn't own unchanged

  @host @failure @AIR-005 @AIR-030 @AT-05 @AT-30
  Scenario: The real rule is removed when UxPlay dies mid-run
    Given this PC's Windows Firewall
    When a test run starts and its UxPlay process is killed from outside the runner
    Then the run reports UxPlay exiting mid-test
    And the firewall rule lists from before, during and after the run are saved
    And they show the run's rule while the run was active and no owned rule after it
    And they show every rule the runner doesn't own unchanged
