@S1
Feature: The analyser reads what UxPlay actually logged
  The analyser turns a UxPlay 1.74 log into observations, each tied to the line it came from.
  Anything the log doesn't show stays unknown; it's never filled in from what was advertised or
  expected. Setup isn't video: video arrives with the `width_source` line and starts with the
  codec line (AIR-008, AIR-009, AIR-028, AIR-029; message texts in
  docs/evidence/UXPLAY_LOG_SIGNATURES.md; excerpts and line numbers per specs/README.md).

  @offline @normal @AIR-008 @AIR-009 @AIR-029 @AT-08 @AT-09 @AT-29
  Scenario: An H.264 mirroring session is observed in full
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      Client identified as User-Agent: AirPlay/935.3.1
      Mirroring initialized successfully
      raop_rtp_mirror accepting client
      raop_rtp_mirror width_source = 1920.000000 height_source = 886.000000 x_offset = 0.000000 y_offset = 0.000000 width = 1920.000000 height = 886.000000
      *** codec is h264: geometry is (w,h)=(1920,886) (source); (x,y)=(0,0); (w,h)=(1920,886)
      ct=8 spf=480 usingScreen=1 isMedia=0  audioFormat=0x1000000
      """
    When the analyser reads the log
    Then an iPhone connection is observed at line 1 with model "iPhone17,1"
    And a mirroring video stream arriving is observed at line 5
    And mirroring video started is observed at line 6
    And the video codec is "H.264", observed at line 6
    And the stream resolution is 1920x886, observed at line 6
    And the audio format is "ct=8 audioFormat=0x1000000", observed at line 7
    And the iOS version is unknown

  @offline @boundary @AIR-008 @AIR-029 @AT-08 @AT-29
  Scenario: Successful mirroring setup and an accepted connection alone don't prove video
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      Mirroring initialized successfully
      raop_rtp_mirror accepting client
      """
    When the analyser reads the log
    Then a mirroring video stream arriving is not observed
    And mirroring video started is not observed
    And the video codec is unknown
    And the stream resolution is unknown

  @offline @normal @AIR-008 @AIR-029 @AT-08 @AT-29
  Scenario: An HEVC request is told apart from an HEVC stream
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      Mirroring initialized successfully
      raop_rtp_mirror accepting client
      raop_rtp_mirror width_source = 1920.000000 height_source = 886.000000 x_offset = 0.000000 y_offset = 0.000000 width = 1920.000000 height = 886.000000
      raop_rtp_mirror: received type 0x01 packet with no payload:
      this indicates non-h264 video but Airplay features bit 42 (SupportsScreenMultiCodec) is not set
      use startup option "-h265" to set this bit and support h265 (4K) video
      """
    When the analyser reads the log
    Then a mirroring video stream arriving is observed at line 4
    And an HEVC request is observed at line 5
    And no HEVC stream is observed
    And mirroring video started is not observed
    And the video codec is unknown

  @offline @normal @AIR-008 @AT-08
  Scenario: The advertised display size is never reported as the stream size
    Given the receiver advertised 1920x720 at 60 fps
    And a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      *** codec is h264: geometry is (w,h)=(1170,2532) (source); (x,y)=(0,0); (w,h)=(332,720)
      """
    When the analyser reads the log
    Then the stream resolution is 1170x2532, observed at line 2
    And the advertised display size is 1920x720, from the launch configuration

  @offline @normal @AIR-008 @AT-08
  Scenario: A connection with no video and audio-only activity are told apart from mirroring
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      ct=2 spf=352 usingScreen=0 isMedia=1  audioFormat=0x40000
      """
    When the analyser reads the log
    Then an iPhone connection is observed at line 1 with model "iPhone17,1"
    And mirroring video started is not observed
    And the video codec is unknown
    And the audio format is "ct=2 audioFormat=0x40000", observed at line 2

  @offline @normal @AIR-009 @AT-09
  Scenario: Two connections in one log are two sessions
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      *** ERROR lost connection with client (network problem?)
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      *** codec is h264: geometry is (w,h)=(1920,886) (source); (x,y)=(0,0); (w,h)=(1920,886)
      """
    When the analyser reads the log
    Then 2 sessions are found, starting at lines 1 and 3
    And session 2's video codec is "H.264", observed at line 4
    And session 1's video codec is unknown

  @offline @boundary @AIR-009 @AT-09
  Scenario: Lines the analyser doesn't recognise are ignored, not guessed at
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      some future UxPlay message mentioning h264 and 1920x1080
      """
    When the analyser reads the log
    Then the video codec is unknown
    And the stream resolution is unknown

  @offline @normal @AIR-028 @AT-28
  Scenario: A wrapped excerpt is a complete log
    Given a UxPlay log excerpt with these lines:
      """
      connection request from Fixture iPhone (iPhone17,1) with deviceID = AA:BB:CC:DD:EE:FF
      """
    When the analyser reads the log
    Then the report records the log as complete
    And the report names no evidence error

  @offline @failure @AIR-009 @AIR-028 @AT-09 @AT-28 @OD-13
  Scenario Outline: An unreadable or incomplete log is flagged as an evidence error
    Given <damaged log>
    When the analyser reads the log
    Then the report flags an evidence error naming "<limitation>"

    Examples:
      | damaged log                                      | limitation      |
      | a log file that can't be read                    | unreadable      |
      | a log that isn't valid UTF-8 text                | unreadable      |
      | a log without the runner's start marker          | no start marker |
      | a log with no UxPlay version banner              | no UxPlay banner |
      | a log without the runner's end marker            | truncated       |
      | a log whose last line has no newline             | truncated       |
      | a log with neither the banner nor the end marker | no UxPlay banner |
