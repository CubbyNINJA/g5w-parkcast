@S1
Feature: A checked, pinned UxPlay build on this PC
  The PC test needs a UxPlay 1.74 build that can be repeated. The build checks its prerequisites
  before it starts, records exactly what it built, and never passes off a stale or partial
  executable as its own output (AIR-001, AIR-002; OD-05, OD-14, OD-20).

  @offline @normal @AIR-001 @AT-01
  Scenario: A complete prerequisite inventory lets the build start
    Given a prerequisite inventory with every required MSYS2 UCRT64 tool and package
    And the inventory lists a GStreamer H.264 decoder
    When the prerequisite check runs
    Then the check passes
    And the build may start

  @offline @failure @AIR-001 @AT-01
  Scenario Outline: A missing prerequisite is named and stops the build before it starts
    Given a prerequisite inventory with every required MSYS2 UCRT64 tool and package
    And the inventory lists a GStreamer H.264 decoder
    But "<item>" is missing from the inventory
    When the prerequisite check runs
    Then the check fails naming "<item>"
    And the build is never invoked

    Examples:
      | item                                   |
      | the UCRT64 environment                 |
      | mingw-w64-ucrt-x86_64-cmake            |
      | mingw-w64-ucrt-x86_64-ninja            |
      | mingw-w64-ucrt-x86_64-gcc              |
      | mingw-w64-ucrt-x86_64-libplist         |
      | mingw-w64-ucrt-x86_64-gstreamer        |
      | mingw-w64-ucrt-x86_64-gst-plugins-base |
      | git                                    |
      | a GStreamer H.264 decoder              |

  @offline @normal @AIR-002 @AT-02
  Scenario: A successful build records its provenance and pins the commit
    Given upstream master is at commit "05b0275cb1b726592700e47a6e0307544b3d3a52" with version "1.74"
    When the build procedure runs and the build succeeds
    Then the provenance records commit "05b0275cb1b726592700e47a6e0307544b3d3a52"
    And the provenance records the dependency versions, the build commands, the executable path and its SHA-256
    And the source and build trees are outside version control

  @offline @boundary @AIR-002 @AT-02
  Scenario: A repeated build uses the recorded commit even after master moves
    Given a recorded build of commit "05b0275cb1b726592700e47a6e0307544b3d3a52"
    And upstream master has since moved to commit "1111111111111111111111111111111111111111"
    When the build procedure is repeated from its recorded provenance
    Then it builds commit "05b0275cb1b726592700e47a6e0307544b3d3a52"

  @offline @failure @AIR-002 @AT-02
  Scenario: A failed build never presents an earlier executable as its output
    Given an executable left by an earlier build in the build tree
    When the build procedure runs and the build fails
    Then the failure is reported
    And no executable is recorded as this build's output
    And the earlier executable is not selected for a test run

  @offline @failure @AIR-002 @AT-02
  Scenario Outline: An interrupted build can't supply a verified executable
    Given a build that has written a partial executable
    When the build ends by <ending>
    Then the failure is reported
    And the partial executable is not recorded as a successful build output
    And it can't be selected for a test run

    Examples:
      | ending               |
      | a compiler failure   |
      | a keyboard interrupt |

  @offline @failure @AIR-002 @AT-02 @OD-20
  Scenario: master that is no longer UxPlay 1.74 stops the build and asks the owner
    Given upstream master is at commit "2222222222222222222222222222222222222222" with version "1.75"
    When the build procedure runs
    Then it stops before building
    And it asks the owner which version to build, naming version "1.75"

  @host @normal @AIR-001 @AIR-002 @AT-01 @AT-02
  Scenario: The real build on this PC, built twice from clean directories
    Given this PC's MSYS2 UCRT64 environment
    When the prerequisite check runs and its inventory is saved
    And the build procedure runs from a clean build directory
    And it is repeated from a second clean build directory at the recorded commit
    Then both builds report UxPlay version "1.74" at the same commit
    And each build's provenance is saved with its commit, dependency versions, build commands, executable path and SHA-256
    And neither build tree is inside version control
