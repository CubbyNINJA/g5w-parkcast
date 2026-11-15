@S1
Feature: The Gherkin exception stays inside this project
  This project's tests use Gherkin and pytest-bdd from its own .venv. hmg_nemesis's main suite
  stays stdlib Python plus pytest (AIR-015; OD-03, OD-07).

  @offline @normal @AIR-015 @AT-15
  Scenario: This project's suite runs from its own environment with pytest-bdd
    Given this project's test environment
    Then pytest-bdd is importable there
    And the feature files are bound by "tests/test_features.py"

  @offline @failure @AIR-015 @AT-15
  Scenario: Nothing outside this project uses Gherkin or pytest-bdd
    Given hmg_nemesis's source tree outside "Sources/airplay-receiver/"
    Then no Python file there imports pytest_bdd
    And no ".feature" file exists there

  @offline @boundary @AIR-015 @AT-15
  Scenario: The main suite skips this project when collected from outside its environment
    Given an interpreter without pytest-bdd
    When it collects this project's tests
    Then it collects nothing and reports no error
