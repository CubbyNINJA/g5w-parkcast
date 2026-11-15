@S1 @OD-28
Feature: One command, with predictable exits and no side effects on bad input
  The S1 tools are one command, "python tools/s1.py", with the commands check, build, run,
  analyse, evaluate and summary. Usage errors change nothing. Detectable provenance and
  output-location failures stop before firewall changes or receiver launch; failures after rule
  creation follow cleanup and exit 1 (AIR-031, OD-29).

  @offline @normal @AIR-031 @AT-31
  Scenario Outline: Help prints usage and does nothing else
    When "s1.py <arguments>" runs
    Then it exits with code 0
    And it prints usage naming "<shown>"
    And no build starts, no firewall rule changes, UxPlay is never launched and no file is created or changed

    Examples:
      | arguments         | shown    |
      | --help            | s1.py    |
      | check --help      | check    |
      | build --help      | build    |
      | run --help        | run      |
      | analyse --help    | analyse  |
      | evaluate --help   | evaluate |
      | summary --help    | summary  |

  @offline @failure @AIR-031 @AT-31
  Scenario Outline: A usage error exits 2 and changes nothing
    When "s1.py <arguments>" runs
    Then it exits with code 2
    And it prints usage on stderr
    And no build starts, no firewall rule changes, UxPlay is never launched and no file is created or changed

    Examples:
      | arguments     | case                     |
      |               | no command               |
      | frobnicate    | unknown command          |
      | run --bogus   | unknown option           |
      | analyse       | missing log argument     |
      | evaluate      | missing run folder       |

  @offline @failure @AIR-031 @AT-31
  Scenario Outline: A run without usable build provenance stops before the firewall
    Given the build provenance file is <state>
    When "s1.py run" runs
    Then it exits with code 1 naming the provenance file
    And no firewall rule changes and UxPlay is never launched

    Examples:
      | state                |
      | missing              |
      | unreadable           |
      | not valid provenance |

  @offline @failure @AIR-031 @AT-31
  Scenario Outline: An output location that can't be written fails before anything starts
    Given <location> can't be written
    When "s1.py <command>" runs
    Then it exits with code 1 naming that location
    And no firewall rule changes and UxPlay is never launched
    And no partial output file is left behind

    Examples:
      | location           | command                          |
      | the runs folder    | run                              |
      | the report path    | analyse fixture.log -o report.json |
      | the verdict path   | evaluate fixture-run             |

  @offline @normal @AIR-031 @AT-31
  Scenario: The documented script is the command's entry point
    When this project's interpreter runs "tools/s1.py --help" as a subprocess
    Then the subprocess exits with code 0
    And its output names the commands check, build, run, analyse, evaluate and summary
    And no file in the project, apart from its .venv and Python caches, is created or changed

  @offline @boundary @AIR-031 @AT-31
  Scenario: A NO-GO verdict is a completed command, not a failure
    Given a fixture run whose evidence makes its verdict NO-GO
    When "s1.py evaluate fixture-run" runs
    Then it exits with code 0
    And the saved run verdict is NO-GO
