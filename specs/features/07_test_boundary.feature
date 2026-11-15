@S1
Feature: The Gherkin exception stays inside this project
  This project's tests use Gherkin and pytest-bdd from its own .venv. hmg_nemesis's main suite
  stays stdlib Python plus pytest (AIR-015; OD-03, OD-07). The offline scenarios check synthetic
  layouts; the host scenarios check this PC's real environment and repository.

  @offline @normal @AIR-015 @AT-15
  Scenario: A layout whose only Gherkin is inside this project passes the boundary check
    Given a synthetic repository with feature files and pytest_bdd imports only under "Sources/airplay-receiver/"
    When the boundary check runs on it
    Then the check passes

  @offline @failure @AIR-015 @AT-15
  Scenario Outline: Gherkin outside this project fails the boundary check
    Given a synthetic repository with feature files and pytest_bdd imports only under "Sources/airplay-receiver/"
    But it also has <extension> at "<path>"
    When the boundary check runs on it
    Then the check fails naming "<path>"

    Examples:
      | extension                           | path                              |
      | a ".feature" file                   | Sources/tests/unit/login.feature  |
      | a Python file importing pytest_bdd  | Sources/tests/unit/test_steps.py  |

  @offline @boundary @AIR-015 @AT-15
  Scenario: The tests skip cleanly where pytest-bdd can't be imported
    Given an interpreter in which importing pytest_bdd fails
    When it collects this project's tests
    Then it collects nothing and reports no error

  @host @normal @AIR-015 @AT-15
  Scenario: This project's suite really runs from its own .venv
    Given this project's .venv
    When its interpreter collects this project's tests
    Then the interpreter's path is inside this project's ".venv"
    And pytest-bdd is importable there
    And the feature files are bound by "tests/test_features.py"
    And the interpreter path and the pytest and pytest-bdd versions are saved

  @host @normal @AIR-015 @AT-15
  Scenario: The real repository keeps the main suite free of Gherkin
    Given hmg_nemesis's real source tree
    When the boundary check runs on it
    Then the check passes
    And the main suite's requirements name neither pytest-bdd nor any Gherkin package
