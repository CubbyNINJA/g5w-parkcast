@S3
Feature: The AirPlay receiver's runtime, with doubles
  The receiver checks the hotspot address read-only, then listens only on 192.168.0.1 at TCP
  35000-35001 and UDP 35000-35002. It wakes mdnsd through NsdManager and advertises ParkCast through
  the dns_sd client. It takes H.264 Screen Mirroring only, decodes it with OMX.TCC.avcdec and plays no
  sound. The S2 gate decides every decoded frame. A start failure blocks video, keeps the screen
  black, shows one notice and leaves the activity open. Stop runs in a fixed order, and native log
  records pass an allowlist filter before any write (AIR-048 to AIR-073, AIR-079
  to AIR-083; OD-148, OD-150, OD-151, OD-153, OD-155, OD-158, OD-161, OD-164, OD-167, OD-169 to
  OD-171, OD-174 to OD-178).

  Clock times, "runs for", "within 10 ms", the default Park getter behaviour, the ACC registration
  steps, the two on-screen notices and their texts, the UI double, "shown at S", "an attempt fails"
  and the default focus order at 0 ms are as defined in 09_parking_gate.feature. The focus double,
  the broadcast double, the lifecycle double, "covered" and "held" are as defined in
  11_parking_logs.feature.

  These scenarios run under Cucumber-JVM against the receiver's plain-Java runtime, with each
  Android, network and native adapter replaced by a recording double (OD-177). Unless a step says
  otherwise, the parking check is on, ro.build.product is CVEV.CAN.0000.V015.011.260206, the car is
  in Park, and the receiver's window gains focus at 0 ms and keeps it, so it is not covered after
  0 ms.

  The interface double answers the receiver's read-only interface query. Unless a step says
  otherwise, it reports interface wlan0, up, holding IPv4 192.168.0.1 and no other address, and no
  other interface holding 192.168.0.1. It records each query and the time it returned.

  The socket double records every socket create, bind, listen, accept, connect, send and close,
  with its protocol, address, port and the time it returned. A port "is taken" when a bind to it
  fails with EADDRINUSE. "The five ports" are TCP 35000, TCP 35001, UDP 35000, UDP 35001 and UDP
  35002.

  The fake NsdManager is what getSystemService(Context.NSD_SERVICE) returns. It records every call.
  Unless a step says otherwise, it delivers onDiscoveryStarted 10 ms after discoverServices returns,
  and it delivers no onServiceFound or onServiceLost callback unless a step says so. The fake dns_sd
  stands in for the socket /dev/socket/mdnsd and the dns_sd client protocol over it. It records each
  connect attempt with its time and result, each registration with its service type, instance
  name, TXT record, automatic-renaming flag and time, and each withdrawal. Unless a step says
  otherwise, the first connect attempt succeeds and every registration succeeds under the name
  given.

  The fake native library stands in for the UxPlay-based protocol library. It records every call
  the receiver makes into it, with its arguments and time, and where file descriptors 1 and 2 point
  at each call. It can deliver its callbacks and native log records at stated times, and it reports
  the device id 0A1B2C3D4E5F. "A session starts at T ms" means that at T ms the library double
  reports an accepted Screen Mirroring connection, calls video_set_codec_and_size for H.264 at
  1920x720, and then calls video_process with one H.264 access unit every 16 ms. "The session is
  ended" means the receiver asked the library double to end that session, and no access unit of it
  reached the decoder afterwards. "Java" is the receiver's Java side, whose received native events
  the harness records.

  The fake codec factory records createByCodecName and any codec-list lookup. The fake codec records
  configure with its Surface, start, every queued input, every releaseOutputBuffer call with its
  render flag and time, stop and release, and can throw on create, configure or at a stated time.
  Unless a step says otherwise, each queued access unit gives one output buffer 5 ms later. The black
  cover is an opaque black view over the receiver's own Surface. "The screen is black" means the
  black cover is showing and no output buffer has been released with render true since video was
  last blocked.

  The fake file system holds the fake app-data directory, the log directory
  /data/nemesis/logs/airplay-receiver/, the config path, /proc/net/tcp and a fake stick. It records
  every create, write, mkdir, chmod, chown, rename and delete with its path, mode and time, and can
  make any of them throw. "The log" is the receiver's log files in the log directory. "The start
  marker" and "the end marker" are AIR-071(a)'s and (c)'s records. "A record that is exactly T"
  means a log line that is the uptime in milliseconds as decimal digits, one space, and then T byte
  for byte, with nothing after it (AIR-071(g)). The MirrorSession and VehicleBus
  doubles are the ones from S2_GATE.md, and record every call made on them.

  "The static scan" reads the receiver's app sources, native sources, compiled classes and
  AndroidManifest.xml: the whole app, not only the gate. "Finds no X" means no reference to X by
  name in any of them.

  "The start-failure notice for C" is a notice whose add call has window type TYPE_TOAST and a view
  from the toast-view factory, whose flags include FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE and
  not FLAG_KEEP_SCREEN_ON, and whose view's text is exactly "ParkCast can't start: C. Exit and try
  again." (OD-161, OD-167). "The activity is not finished" means no finish() or
  finishAndRemoveTask() call was recorded and the lifecycle double saw no onDestroy. "The process
  stays alive" means no System.exit(), Process.killProcess() or uncaught exception was recorded. "The
  key file" is the file in the fake app-data directory whose path the receiver passes to raop_init2
  as keyfile. "The withheld count" is the number of native records dropped under AIR-073(c), as the
  receiver logs it at session end. In table cells, \n stands for a line feed and \r for a carriage
  return.

  @offline @normal @AIR-048 @AT-48 @OD-169 @OD-174
  Scenario: The five ports are bound on 192.168.0.1 only, after the hotspot check and before advertising
    When the receiver runs for 1000 ms
    Then the interface double was queried exactly once, and that query returned before any bind
    And the socket double recorded exactly five binds, one to each of the five ports, each to address 192.168.0.1
    And no socket was bound to TCP 35002, to 0.0.0.0 or to any IPv6 address, and no IPv6 socket was created
    And all five binds returned before the first dns_sd registration
    And the log has "advertise ok"

  @offline @failure @AIR-048 @AIR-069 @AT-48 @OD-150 @OD-169 @OD-174 @OD-175 @OD-185
  Scenario Outline: A taken port stops the start, and no other port is tried
    Given <proto> port <port> is taken
    When the receiver runs for 10000 ms
    Then the log has a record that is exactly "port busy <proto> <port>"
    And no dns_sd registration was sent and no accept call was made
    And no bind was attempted on any port outside the five ports
    And every one of the five ports that the receiver bound was closed by 0 ms
    And mirrored video stays blocked throughout and the screen is black throughout
    And exactly one start-failure notice add call was made, at exactly 0 ms, for "port busy"
    And the process stays alive and the activity is not finished

    Examples:
      | proto | port  |
      | tcp   | 35000 |
      | tcp   | 35001 |
      | udp   | 35000 |
      | udp   | 35001 |
      | udp   | 35002 |

  @offline @failure @AIR-048 @AIR-069 @AT-48 @OD-150 @OD-169 @OD-174 @OD-175
  Scenario Outline: A missing hotspot address stops the start, with no fallback, wait or repeat
    Given the interface double reports <interfaces>
    When the receiver runs for 60000 ms
    Then the log has "hotspot address missing"<class>
    And the interface double was queried exactly once
    And no bind, no dns_sd registration and no accept call was made
    And mirrored video stays blocked throughout and the screen is black throughout
    And exactly one start-failure notice add call was made, at exactly 0 ms, for "hotspot address missing"
    And its view's text contains neither "wlan0" nor "192.168.0" nor "Exception"
    And it is on screen at 4999 ms, and exactly one removal call for it was made, at exactly 5000 ms
    And the process stays alive and the activity is not finished

    Examples:
      | interfaces                                                        | class                                    |
      | no wlan0                                                          |                                          |
      | wlan0 down, holding 192.168.0.1                                   |                                          |
      | wlan0 up, holding only 192.168.0.2                                |                                          |
      | wlan0 up with no address, and 192.168.0.1 on interface ap0        |                                          |
      | a query that throws java.net.SocketException                      | , naming "java.net.SocketException"      |

  @offline @normal @AIR-049 @AT-49 @OD-164
  Scenario Outline: The static scan finds nothing that touches DiPO, mdnsd or the network daemons
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                                                                                      |
      | bind, connect or send to port 7000                                                             |
      | bind to port 5001, 5020, 6000, 6011, 6020 or 6030                                              |
      | connect or send to port 5001, 5020, 6000, 6011, 6020 or 6030                                   |
      | stop, signal or reconfiguration of DiPO, mdnsd, hostapd, dnsmasq or wpa_supplicant             |
      | /dev/socket/mdns other than as part of /dev/socket/mdnsd                                       |
      | ndc                                                                                            |
      | ctl.start                                                                                      |
      | ctl.stop                                                                                       |
      | setprop                                                                                        |
      | su call                                                                                        |
      | nemesis root call                                                                              |
      | registerService on NsdManager                                                                  |
      | resolveService on NsdManager                                                                   |

  @offline @normal @AIR-049 @AT-49 @OD-164
  Scenario: A full run never binds, connects or sends to the CarPlay or DiPO ports
    Given a session starts at 1000 ms
    When the receiver runs for 5000 ms and then exit() is called
    Then the socket double recorded no bind, connect or send to TCP 7000
    And no explicit bind to TCP 5001 or 6030, or to UDP 5020, 6000, 6011 or 6020

  @offline @normal @AIR-050 @AT-50 @OD-148 @OD-164 @OD-171
  Scenario: mdnsd is woken through NsdManager, then ParkCast is registered over dns_sd
    When the receiver runs for 1000 ms
    Then these were recorded in this order: the five binds, getSystemService(Context.NSD_SERVICE), discoverServices("_airplay._tcp", NsdManager.PROTOCOL_DNS_SD, listener), onDiscoveryStarted, a successful dns_sd connect to /dev/socket/mdnsd, the log record "mdnsd up", the two dns_sd registrations, the log record "advertise ok"
    And one registration is of type "_airplay._tcp" with instance name exactly "ParkCast"
    And the other is of type "_raop._tcp" with instance name exactly "0A1B2C3D4E5F@ParkCast"
    And each registration carried its complete TXT record when it was sent, with automatic renaming off
    And no registration was sent before its TXT record was complete
    And the fake native library recorded no call that starts its built-in mDNS responder
    And registerService and resolveService were never called on the fake NsdManager

  @offline @normal @AIR-050 @AT-50 @OD-164
  Scenario: Discovery results are ignored and not logged
    Given the fake NsdManager delivers onServiceFound for "Fixture Speaker" at 500 ms and onServiceLost for it at 700 ms
    When the receiver runs for 1000 ms
    Then no log record contains "Fixture Speaker"
    And the receiver made no call on the fake NsdManager after onDiscoveryStarted

  @offline @boundary @AIR-050 @AT-50 @OD-164 @OD-185
  Scenario Outline: onDiscoveryStarted counts only within 5,000 ms of discoverServices
    Given discoverServices returns at 0 ms
    And the fake NsdManager delivers onDiscoveryStarted <when>
    When the receiver runs for 12000 ms
    Then <outcome>

    Examples:
      | when       | outcome                                                                                                                    |
      | at 4999 ms | a dns_sd connect attempt was made at 4999 ms and the log has "advertise ok"                                                |
      | at 5000 ms | the log has a record that is exactly "advertise failed reason=discovery-timeout code=none", and no dns_sd connect was made |
      | at 5001 ms | the log has a record that is exactly "advertise failed reason=discovery-timeout code=none", and no dns_sd connect was made |
      | never      | the log has a record that is exactly "advertise failed reason=discovery-timeout code=none", and no dns_sd connect was made |

  @offline @boundary @AIR-050 @AT-50 @OD-164
  Scenario: The dns_sd socket is retried every 250 ms, and one that appears at 4,750 ms connects
    Given onDiscoveryStarted arrives at 0 ms
    And /dev/socket/mdnsd is missing until 4750 ms of the connect window
    When the receiver runs for 6000 ms
    Then dns_sd connect attempts were made only at 0, 250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500, 2750, 3000, 3250, 3500, 3750, 4000, 4250, 4500 and 4750 ms of the connect window
    And only the attempt at 4750 ms succeeded
    And the log has "mdnsd up" and then "advertise ok"

  @offline @boundary @AIR-050 @AT-50 @OD-164 @OD-185 @OD-186
  Scenario: A dns_sd socket still missing at the end of the 5,000 ms window fails the advertise
    Given onDiscoveryStarted arrives at 0 ms
    And /dev/socket/mdnsd is missing for the whole run
    When the receiver runs for 8000 ms
    Then dns_sd connect attempts were made only at 0, 250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500, 2750, 3000, 3250, 3500, 3750, 4000, 4250, 4500, 4750 and 5000 ms of the connect window, 21 attempts in all
    And the log has a record that is exactly "advertise failed reason=ENOENT code=none"
    And the log has no "mdnsd up"

  @offline @failure @AIR-050 @AIR-069 @AT-50 @OD-150 @OD-164 @OD-167 @OD-185 @OD-186
  Scenario Outline: Every advertise failure is logged, undone and ends the start, with no retry or fallback
    Given <fault>
    When the receiver runs for 20000 ms
    Then the log has a record that is exactly "advertise failed <reason>"
    And <withdrawn>
    And discovery was stopped with stopServiceDiscovery if discoverServices had returned
    And no accept call was made, and no registration, discoverServices call or dns_sd connect was made after the log record "advertise failed"
    And the fake native library recorded no call that starts its built-in mDNS responder
    And mirrored video stays blocked throughout and the screen is black throughout
    And exactly one start-failure notice add call was made, for "advertise failed"
    And the process stays alive and the activity is not finished

    Examples:
      | fault                                                                                   | reason                              | withdrawn                                    |
      | getSystemService(Context.NSD_SERVICE) returns null                                      | reason=null-manager code=none       | no registration was made                     |
      | getSystemService(Context.NSD_SERVICE) throws SecurityException                          | reason=security-exception code=none | no registration was made                     |
      | onStartDiscoveryFailed is delivered with error code 0                                   | reason=discovery-failed code=0      | no registration was made                     |
      | onDiscoveryStarted never arrives                                                        | reason=discovery-timeout code=none  | no registration was made                     |
      | /dev/socket/mdnsd is missing for the whole connect window                               | reason=ENOENT code=none             | no registration was made                     |
      | /dev/socket/mdnsd refuses with EACCES for the whole connect window                      | reason=EACCES code=none             | no registration was made                     |
      | /dev/socket/mdnsd refuses with EPERM for the whole connect window                       | reason=EPERM code=none              | no registration was made                     |
      | /dev/socket/mdnsd refuses with ECONNREFUSED for the whole connect window                | reason=ECONNREFUSED code=none       | no registration was made                     |
      | every dns_sd connect throws ETIMEDOUT for the whole connect window                      | reason=unreachable code=none        | no registration was made                     |
      | the _airplay._tcp TXT record cannot be built                                            | reason=txt-build code=none          | no registration was made                     |
      | the _airplay._tcp registration returns dns_sd error code -65540                         | reason=register code=-65540         | no registration is left in place             |
      | the _airplay._tcp registration gets a name-conflict reply with dns_sd error code -65548 | reason=name-conflict code=-65548    | no registration is left in place             |
      | the _raop._tcp registration returns dns_sd error code -65540                            | reason=register code=-65540         | the _airplay._tcp registration was withdrawn |

  @offline @failure @AIR-050 @AT-50 @OD-161 @OD-164
  Scenario: A dns_sd connection that closes after advertise ok is logged only
    Given the fake dns_sd closes its connection at 3000 ms
    When the receiver runs for 20000 ms
    Then the log has "advertise ok" and then "advertise lost"
    And no dns_sd connect attempt or registration was made after 3000 ms
    And no notice add call was made

  @offline @normal @AIR-050 @AIR-070 @AT-50 @AT-70 @OD-164
  Scenario: Stop withdraws both registrations, then calls stopServiceDiscovery
    Given the log has "advertise ok"
    When exit() is called at 2000 ms
    Then the _airplay._tcp and _raop._tcp registrations were both withdrawn
    And stopServiceDiscovery was called after both withdrawals

  @offline @normal @AIR-051 @AT-51
  Scenario: The advertised display is 1920 by 720 at 60 frames per second
    When the receiver runs for 1000 ms
    Then the display fields the receiver advertises are width 1920, height 720 and refresh rate 60

  @offline @boundary @AIR-051 @AT-51
  Scenario Outline: A stream of another shape is shown whole and centred, with black bars
    Given a session starts at 1000 ms with stream geometry <stream>
    When the receiver runs for 2000 ms
    Then the video is drawn in a box of <box> on the 1920x720 screen, at x <x> and y 0, within 1 px
    And the rest of the screen is black
    And the log records the stream geometry <stream>

    Examples:
      | stream    | box      | x          |
      | 1080x1920 | 405x720  | 757 or 758 |
      | 1920x1080 | 1280x720 | 320        |
      | 1920x720  | 1920x720 | 0          |

  @offline @normal @AIR-052 @AT-52
  Scenario: The advertised features carry no HEVC bit
    When the receiver runs for 1000 ms
    Then the advertised features carry no HEVC bit
    And the fake native library recorded no -h265 option or HEVC setting

  @offline @failure @AIR-052 @AT-52
  Scenario: An HEVC stream never reaches the decoder, and the session ends
    Given a session starts at 1000 ms, but video_set_codec_and_size reports HEVC with an HEVC codec-configuration fixture
    When the receiver runs for 3000 ms
    Then no byte of the HEVC fixture was queued to the fake codec
    And the log has "hevc refused"
    And the session is ended

  @offline @failure @AIR-053 @AT-53 @OD-185
  Scenario Outline: URL, HLS and DRM requests are refused and logged by kind, without the URL
    Given the library double delivers a <kind> request fixture for "http://192.168.0.50:7100/fixture-marker-7c1e.m3u8" at 2000 ms
    When the receiver runs for 3000 ms
    Then the request was refused
    And the log has a record that is exactly "refused <token>"
    And no log record contains "fixture-marker-7c1e" or "192.168.0.50"

    Examples:
      | kind | token |
      | URL  | url   |
      | HLS  | hls   |
      | DRM  | drm   |

  @offline @normal @AIR-054 @AT-54 @OD-153
  Scenario Outline: The receiver has no audio output
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                   |
      | AudioTrack                  |
      | AudioManager                |
      | requestAudioFocus           |
      | abandonAudioFocus           |
      | AudioFocusRequest           |

  @offline @normal @AIR-054 @AIR-055 @AT-54 @AT-55 @OD-153
  Scenario: Audio packets are discarded and written nowhere
    Given a session starts at 1000 ms
    And the library double calls audio_process with the audio fixture bytes "PARKCAST-AUDIO-FIXTURE-55" at 1500 ms
    When the receiver runs for 3000 ms
    Then audio_process returned
    And no log file, no file in the fake app-data directory and no file on the fake stick contains "PARKCAST-AUDIO-FIXTURE-55"
    And no audio event reached Java

  @offline @normal @AIR-055 @AT-55 @OD-174
  Scenario: Standard output and error point at /dev/null before the first library call
    Given the fake native library prints "STDIO-MARKER-91" to stdout and to stderr on every call
    When the receiver runs for 2000 ms
    Then at the receiver's first call into the fake native library, descriptors 1 and 2 pointed at /dev/null
    And no log file, no file on the fake stick and no logcat output contains "STDIO-MARKER-91"

  @offline @normal @AIR-055 @AT-55
  Scenario Outline: Each callback the library needs is set, and returns without waiting on Java
    Given a session starts at 1000 ms
    And the Java side is held busy from 1500 ms to 2500 ms
    When the library double calls <callback> with its fixture at 2000 ms
    Then <callback> returned before 2500 ms, without a crash
    And the Examples' callbacks equal the list produced by the @host unguarded-callback check

    Examples:
      | callback                       |
      | video_process                  |
      | video_set_codec_and_size       |
      | video_pause                    |
      | video_resume                   |
      | audio_process                  |
      | conn_feedback                  |
      | video_reset                    |
      | conn_reset                     |
      | audio_set_client_volume        |
      | on_video_play                  |
      | on_video_scrub                 |
      | on_video_rate                  |
      | on_video_stop                  |
      | on_video_acquire_playback_info |
      | on_video_playlist_remove       |

  @offline @normal @AIR-055 @AT-55
  Scenario: video_set_codec_and_size accepts H.264 and passes the geometry on
    When a session starts at 1000 ms
    Then video_set_codec_and_size returned a value of 0 or more
    And Java received the stream geometry 1920x720

  @offline @boundary @AIR-055 @AT-55
  Scenario: A callback after stop returns is dropped
    Given a session starts at 1000 ms
    And exit() is called at 2000 ms
    When the library double calls video_process with an access unit 1 ms after stop returns
    Then no native event reached Java after stop returned
    And no input was queued to the fake codec after stop returned

  @offline @failure @AIR-055 @AIR-069 @AT-55
  Scenario: A failed stdio redirect stops the start with no library call and no notice
    Given pointing descriptor 1 at /dev/null fails
    When the receiver runs for 10000 ms
    Then the log has "stdio redirect failed"
    And the fake native library recorded no call
    And mirrored video stays blocked throughout and the screen is black throughout
    And no notice add call was made
    And the process stays alive and the activity is not finished

  @offline @failure @AIR-055 @AIR-069 @AT-55 @AT-69 @OD-175
  Scenario: A failed stdio redirect after a missing hotspot address still gives the hotspot notice
    Given the interface double reports no wlan0
    And pointing descriptor 1 at /dev/null fails
    When the receiver runs for 10000 ms
    Then the log has "hotspot address missing"
    And the log has "stdio redirect failed"
    And the fake native library recorded no call
    And exactly one start-failure notice add call was made, at exactly 0 ms, for "hotspot address missing"
    And the process stays alive and the activity is not finished

  @offline @normal @AIR-056 @AT-56 @OD-150
  Scenario: The decoder is created by name only and renders to the activity's own Surface
    When a session starts at 1000 ms
    Then the fake codec factory recorded exactly one call, createByCodecName("OMX.TCC.avcdec")
    And createByCodecName was not called before video_set_codec_and_size reported the H.264 configuration
    And it recorded no codec-list lookup
    And the fake codec was configured with the receiver activity's own Surface

  @offline @failure @AIR-056 @AIR-069 @AT-56 @OD-150 @OD-161
  Scenario Outline: A decoder that cannot be created or configured ends the session and the start
    Given the fake codec <fault>
    And a session starts at 1000 ms
    When the receiver runs for 10000 ms
    Then the log has "decoder unavailable" naming "<class>"
    And the fake codec factory recorded no other create call
    And no output buffer was released with render true, and the screen is black throughout
    And the session is ended
    And exactly one start-failure notice add call was made, at exactly 1000 ms, for "decoder unavailable"
    And the process stays alive and the activity is not finished

    Examples:
      | fault                                                  | class                           |
      | factory throws java.io.IOException on create           | java.io.IOException             |
      | throws IllegalStateException on configure              | java.lang.IllegalStateException |

  @offline @failure @AIR-056 @AIR-069 @AT-56 @AT-69 @OD-150 @OD-161
  Scenario: A mid-session decoder error releases the decoder and is logged only
    Given a session starts at 1000 ms
    And the fake codec throws MediaCodec.CodecException at 3000 ms
    When the receiver runs for 20000 ms
    Then the fake codec was released
    And the log has "decoder error"
    And the session is ended
    And the fake codec factory recorded no create call after 3000 ms
    And no notice add call was made and no notice is held

  @offline @normal @AIR-057 @AT-57 @OD-150 @OD-158
  Scenario: While the gate blocks, every buffer is released without render and the cover is shown
    Given a session starts at 500 ms
    And the car leaves Park at 1000 ms
    When the receiver runs for 2000 ms
    Then every output buffer released before the Park getter call started at 1000 ms returned was released with render true
    And every output buffer released after it returned was released with render false
    And the black cover was showing from no later than 1260 ms
    And the screen is black from 1260 ms to 2000 ms

  @offline @normal @AIR-057 @AT-57 @OD-150
  Scenario: Frames are drained while blocked, so there is no backlog when video returns
    Given a session starts at 500 ms
    And the car leaves Park at 1000 ms and returns to Park at 2000 ms
    When the receiver runs for 3000 ms
    Then the fake codec released an output buffer for every access unit queued from 1000 ms to 2000 ms, each with render false
    And the first output buffer released with render true after the gate next permits video came from an access unit queued after the gate permitted video
    And the library double saw the connection kept, with no close, from 500 ms to 3000 ms
    And the MirrorSession and VehicleBus doubles recorded zero calls

  @offline @normal @AIR-058 @AT-58 @OD-63
  Scenario: The Park getter is called every 200 ms on a worker thread, one call at a time
    Given each getGearPStatus() call takes 150 ms to reply, except the one started at 400 ms, which takes 350 ms
    When the receiver runs for 1000 ms
    Then VehicleInfoManager.getGearPStatus() was called only at 0, 200, 400, 750 and 950 ms
    And every call ran on a thread other than the main thread
    And no call started while another was in flight

  @offline @failure @AIR-058 @AT-58 @OD-185
  Scenario Outline: A failed or late Park getter call gives unavailable, logged with its class
    Given the getGearPStatus() call started at 200 ms <fault>
    When the receiver runs for 1000 ms
    Then the gate received "unavailable" for that call
    And the log has a Park reading "unavailable" naming <named>
    And mirrored video is blocked from no later than 650 ms

    Examples:
      | fault                                                          | named                                     |
      | does not reply within 200 ms                                   | "java.util.concurrent.TimeoutException"   |
      | throws SecurityException                                       | "java.lang.SecurityException"             |
      | throws android.os.DeadObjectException                          | "android.os.DeadObjectException"          |
      | fails because VehicleInfoManager's class is missing            | "java.lang.NoClassDefFoundError"          |

  @offline @normal @AIR-058 @AT-58
  Scenario Outline: Nothing asks the BCM for the gear status
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                    |
      | BCM gear-status method       |
      | REQ_GEAR_STATUS              |

  @offline @normal @AIR-059 @AT-59
  Scenario: The ACC broadcast is registered on a worker thread, delivered on the main looper
    When the receiver runs for 1000 ms
    Then the ACC registerReceiver call for com.hkmc.intent.action.ACTION_ACC_STATUS_CHANGED ran on a thread other than the main thread
    And it passed no permission and a Handler on the main looper

  @offline @normal @AIR-059 @AT-59
  Scenario Outline: Nothing registers for system-off responses or uses micom power transaction 14
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                         |
      | registerSystemOffResponder        |
      | micom power transaction 14        |

  @offline @failure @AIR-059 @AT-59
  Scenario Outline: A failed ACC registration gives AIR-019's record and notice
    Given <failure>
    When the receiver runs for 10000 ms
    Then the log has "ACC registration failed" naming "<cause>"
    And the registration-failure notice is shown at 0 ms
    And mirrored video stays blocked throughout

    Examples:
      | failure                                                  | cause            |
      | ACC registration throws an exception at 0 ms             | exception        |
      | ACC registration returns no sticky broadcast at 0 ms     | no sticky state  |

  @offline @normal @AIR-060 @AT-60
  Scenario Outline: REVERSE_ON is registered in code only, and nothing reads the reverse-off state
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                                                      |
      | com.hkmc.intent.action.REVERSE_ON receiver in the manifest     |
      | REVERSE_OFF                                                    |
      | isRearViewOn                                                   |

  @offline @normal @AIR-060 @AT-60
  Scenario: The REVERSE_ON receiver is registered synchronously in onCreate
    When the lifecycle double drives onCreate, onStart and onResume at 0 ms
    Then the registerReceiver call for com.hkmc.intent.action.REVERSE_ON returned before onCreate returned, on the main thread
    And its IntentFilter has only the action com.hkmc.intent.action.REVERSE_ON

  @offline @normal @AIR-060 @AIR-041 @AT-60
  Scenario: The S2 reverse scenarios pass against the Android REVERSE_ON adapter
    Given the scenarios selected by the tag expression "@S2 and @AIR-041 and @offline" are bound to the Android REVERSE_ON receiver adapter driven by a fake broadcast source
    When those scenarios run
    Then every one of them passes against that adapter

  @offline @normal @AIR-061 @AT-61
  Scenario: Notices are toast windows and need no overlay permission
    When the static scan runs
    Then it finds no SYSTEM_ALERT_WINDOW and no TYPE_SYSTEM_ALERT
    And it finds Toast.makeText used only with getView(), never with show()

  @offline @failure @AIR-061 @AT-61 @OD-161
  Scenario: A notice add that throws is logged and does not change the gate's decision
    Given the parking check is on, on a supported build
    And the Park getter replies 1 within 10 ms
    And the UI double throws on add for the first logging-failure notice attempt
    And every log write fails from 1000 ms
    When the receiver runs for 3000 ms
    Then the failed add is logged
    And mirrored video stays permitted from 10 ms throughout

  @offline @boundary @AIR-061 @AIR-069 @AT-61 @AT-69 @OD-161
  Scenario: A start-failure notice that falls due while covered is held, then added at uncover
    Given the interface double reports wlan0 down
    And the receiver's window does not gain focus until 3000 ms
    When the receiver runs for 20000 ms
    Then no notice create or add call was made before 3000 ms
    And exactly one start-failure notice add call was made, at exactly 3000 ms, for "hotspot address missing"
    And it is on screen at 7999 ms, and exactly one removal call for it was made, at exactly 8000 ms

  @offline @failure @AIR-062 @AT-62
  Scenario Outline: A missing or unwritable log directory is a logging failure, and nothing is made outside it
    Given the log directory /data/nemesis/logs/airplay-receiver/ <state>
    When the receiver runs for 10000 ms
    Then exactly one logging-failure notice add call was made
    And the fake file system recorded no mkdir, chmod or chown on any path outside /data/nemesis/logs/airplay-receiver/
    And mirrored video stays permitted from 10 ms throughout

    Examples:
      | state                         |
      | does not exist                |
      | exists but every write throws |

  @offline @normal @AIR-062 @AIR-039 @AT-62
  Scenario: The S2 rotation scenarios pass against the Android LogFiles adapter
    Given the scenarios selected by the tag expression "@S2 and @AIR-039 and @offline" are bound to the Android LogFiles adapter over a fake file system rooted at /data/nemesis/logs/airplay-receiver/
    When those scenarios run
    Then every one of them passes against that adapter
    And the fake file system recorded no write outside /data/nemesis/logs/airplay-receiver/

  @offline @normal @AIR-063 @AT-63
  Scenario: The parking setting is read from the one config path, and the receiver never writes it
    When the receiver runs for 1000 ms
    Then the fake file system recorded config reads only of /data/nemesis/apps/airplay.receiver/config/receiver.conf
    And no create or write of that path

  @offline @failure @AIR-063 @AIR-071 @AT-63 @OD-178
  Scenario: With no config file, the parking check is on
    Given /data/nemesis/apps/airplay.receiver/config/receiver.conf does not exist
    When the receiver runs for 1000 ms
    Then the start marker has "require_park=true"
    And the gate's start record has the effective value true with "reason=the missing file"
    And the fake file system recorded no create of that path

  @offline @normal @AIR-064 @AIR-071 @AT-64 @OD-178
  Scenario: The fixture build product counts as supported and is in the start marker
    Given ro.build.product is CVEV.CAN.0000.V015.011.260206
    When the receiver runs for 1000 ms
    Then mirrored video is permitted from 10 ms
    And the start marker has "product=CVEV.CAN.0000.V015.011.260206"

  @offline @failure @AIR-064 @AT-64
  Scenario Outline: An empty or unreadable ro.build.product counts as unidentified
    Given ro.build.product <read>
    And Build.PRODUCT is "CVEV.CAN.0000.V015.011.260206"
    When the receiver runs for 1000 ms
    Then mirrored video stays blocked throughout

    Examples:
      | read                                      |
      | is the empty string                       |
      | throws IllegalArgumentException when read |

  @offline @normal @AIR-064 @AT-64
  Scenario: The build is identified from ro.build.product, never Build.PRODUCT
    When the static scan runs
    Then it finds no Build.PRODUCT

  @offline @normal @AIR-071 @AT-71 @OD-178
  Scenario: Every marker is written in AIR-071's exact text, and a new app start numbers sessions from 1
    Given a session starts at 1000 ms
    And the library double delivers the allowlisted record "Mirroring initialized successfully" after reporting the accepted connection and before calling video_set_codec_and_size
    And the library double reports at 3000 ms that the iPhone ended the connection
    When the receiver runs for 4000 ms and then exit() is called
    And the receiver is then started again with the car out of Park, a session starts at 1000 ms of that app start, and it runs for 2000 ms
    Then the first app start's log, with the logger's prefix taken off each record, has these records in this order, each byte for byte:
      | record                                                                                                                          |
      | parkcast start app=<version> commit=<git commit> uxplay=<UxPlay commit> product=CVEV.CAN.0000.V015.011.260206 require_park=true |
      | session 1 connection accepted model=<iPhone model>                                                                              |
      | Mirroring initialized successfully                                                                                              |
      | session 1 codec h264 1920x720                                                                                                   |
      | decoder OMX.TCC.avcdec configured                                                                                               |
      | session 1 first frame rendered                                                                                                  |
      | session 1 end reason=iphone disconnected withheld=0                                                                             |
      | parkcast stop reason=owner exit                                                                                                 |
    And each <field> in those records holds the build's or the library double's value, with no other text
    And the second app start's log has "session 1 connection accepted model=" and "session 1 first frame withheld by gate"
    And the second app start's log has no "session 2"

  @offline @normal @AIR-071 @AT-71 @OD-185 @OD-186
  Scenario Outline: Each way a session ends is logged with its own reason
    Given <cause>
    When the receiver runs for 4000 ms and then exit() is called
    Then the log has exactly one record that begins "session 1 end "
    And the log has a record that is exactly "session 1 end reason=<reason> withheld=0"

    Examples:
      | cause                                                                                                           | reason              |
      | a session starts at 1000 ms, and the library double reports at 2000 ms that the iPhone ended the connection     | iphone disconnected |
      | a session starts at 1000 ms, but video_set_codec_and_size reports HEVC with an HEVC codec-configuration fixture | hevc refused        |
      | the fake codec factory throws java.io.IOException on create, and a session starts at 1000 ms                    | decoder unavailable |
      | a session starts at 1000 ms, and the fake codec throws MediaCodec.CodecException at 2000 ms                     | decoder error       |
      | a session starts at 1000 ms and is still open when exit() is called                                             | stop                |

  @offline @normal @AIR-071 @AT-71 @OD-185 @OD-186
  Scenario Outline: Each way the app stops is logged with its own reason, as the last record
    Given a session starts at 1000 ms
    When the receiver runs for 2000 ms and then <stop>
    Then the last record of the log is exactly "parkcast stop reason=<reason>"
    And the log has exactly one record that begins "parkcast stop "

    Examples:
      | stop                                                      | reason     |
      | exit() is called                                          | owner exit |
      | the lifecycle double drives onPause, onStop and onDestroy | destroy    |

  @offline @normal @AIR-065 @AT-65
  Scenario: The Scheduler is timed by SystemClock.uptimeMillis()
    When the static scan runs
    Then the Scheduler's Android adapter uses Handler with SystemClock.uptimeMillis()
    And the gate's sources read no time from System.currentTimeMillis(), SystemClock.elapsedRealtime() or System.nanoTime()

  @offline @normal @AIR-065 @AT-65
  Scenario: A wall-clock jump changes neither reading ages nor the 250 ms clear deadline
    Given a session starts at 500 ms
    And the wall clock jumps forward by 3600000 ms at 900 ms, and the uptime clock does not
    And the car leaves Park at 1000 ms
    When the receiver runs for 2000 ms
    Then mirrored video stays permitted from 10 ms until the Park getter call started at 1000 ms returned
    And the black cover was showing from no later than 1260 ms of uptime

  @offline @normal @AIR-066 @AT-66
  Scenario: Nothing sets the stock regulation state
    When the static scan runs
    Then it finds no setter method of com.hkmc.regulation
    And com.hkmc.regulation is used only to receive notifications

  @offline @normal @AIR-066 @AIR-038 @AT-66
  Scenario: The S2 regulation-state scenarios pass against the Android regulation adapter
    Given the scenarios selected by the tag expression "@S2 and @AIR-038 and @offline" are bound to the Android regulation adapter backed by a fake com.hkmc.regulation service
    When those scenarios run
    Then every one of them passes against that adapter
    And the fake service recorded no setter call

  @offline @normal @AIR-067 @AT-67
  Scenario: The S2_GATE forbidden list, widened to the whole app, finds nothing
    When the static scan runs with the S2_GATE.md forbidden list
    Then it finds no AudioManager, audio-focus or audio-session API, HUD display id, vehicle setter, CAN access, stock regulation setter, registerSystemOffResponder, micom power transaction 14, REVERSE_OFF or isRearViewOn

  @offline @normal @AIR-067 @AT-67
  Scenario: The gate makes no call on MirrorSession or VehicleBus across a full run
    Given a session starts at 500 ms
    And the car leaves Park at 1000 ms and returns to Park at 2000 ms
    When the receiver runs for 3000 ms and then exit() is called
    Then the MirrorSession and VehicleBus doubles recorded zero calls
    And their audio and connection traces are unbroken

  @offline @normal @AIR-068 @AT-68
  Scenario Outline: No background start, no system changes
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                                                 |
      | android.intent.action.BOOT_COMPLETED receiver             |
      | service element in the manifest                           |
      | receiver element in the manifest                          |
      | AlarmManager                                              |
      | SystemProperties.set                                      |
      | remount                                                   |
      | WifiManager configuration or hotspot method               |
      | WifiConfiguration                                         |

  @offline @normal @AIR-068 @AT-68
  Scenario: A full run writes only to the log directory and app-data
    Given a session starts at 1000 ms
    When the receiver runs for 5000 ms and then exit() is called
    Then every create, write, rename and delete the fake file system recorded is inside /data/nemesis/logs/airplay-receiver/ or the fake app-data directory

  @offline @failure @AIR-069 @AT-69 @OD-150
  Scenario: An uncaught exception writes a crash record, then the process ends
    Given a session starts at 1000 ms
    When a java.lang.IllegalStateException is thrown uncaught on the main thread at 2000 ms
    Then the log has a "crash" record naming "java.lang.IllegalStateException", with the receiver's own stack frames
    And then the process ended
    And the lifecycle double recorded no new start of the receiver in the next 60000 ms

  @offline @failure @AIR-069 @AT-69
  Scenario Outline: Nothing restarts the receiver automatically
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                                              |
      | AlarmManager                                           |
      | START_STICKY or START_REDELIVER_INTENT                 |
      | startActivity or startService naming the receiver      |
      | Thread.setDefaultUncaughtExceptionHandler that relaunches |

  @offline @failure @AIR-069 @AT-69 @OD-150 @OD-161 @OD-167 @OD-175 @OD-176
  Scenario Outline: Each of the five start failures gives one notice with the bare cause
    Given <fault>
    When the receiver runs for 20000 ms
    Then the log has "<cause>"
    And mirrored video stays blocked throughout and the screen is black throughout
    And exactly one start-failure notice add call was made, at exactly <at> ms, for "<cause>"
    And it is on screen at <last> ms, and exactly one removal call for it was made, at exactly <removed> ms
    And no Toast.show() call, other window type, dialog, notification or activity was recorded
    And the activity is not finished

    Examples:
      | fault                                                          | cause                    | at   | last | removed |
      | TCP port 35001 is taken                                        | port busy                | 0    | 4999 | 5000    |
      | onStartDiscoveryFailed is delivered with error code 0 at 10 ms | advertise failed         | 10   | 5009 | 5010    |
      | the fake codec factory throws on create and a session starts at 1000 ms | decoder unavailable | 1000 | 5999 | 6000    |
      | the interface double reports wlan0 down                        | hotspot address missing  | 0    | 4999 | 5000    |
      | the key file exists and is zero-length                         | identity key unavailable | 0    | 4999 | 5000    |

  @offline @failure @AIR-069 @AT-69
  Scenario: A start failure outside the five causes is logged, with no notice
    Given the fake native library's start call throws java.lang.IllegalStateException
    When the receiver runs for 20000 ms
    Then the log names that start failure
    And mirrored video stays blocked throughout and the screen is black throughout
    And no notice add call was made
    And the activity is not finished

  @offline @boundary @AIR-069 @AT-69 @OD-161
  Scenario: A cover start removes the start-failure notice, and it is not shown again
    Given the interface double reports wlan0 down
    And the receiver's window loses focus at 2000 ms and gains focus at 3000 ms
    When the receiver runs for 20000 ms
    Then exactly one start-failure notice add call was made, at exactly 0 ms
    And exactly one removal call for it was made, at exactly 2000 ms
    And no notice add call was made after 2000 ms

  @offline @boundary @AIR-069 @AT-69 @OD-161
  Scenario: Exit while the start-failure notice is held shows nothing and does not wait
    Given the interface double reports wlan0 down
    And the receiver's window does not gain focus
    When exit() is called at 2000 ms
    And the receiver is then started again with wlan0 up holding 192.168.0.1, and runs for 10000 ms
    Then no notice create or add call was made in either app start
    And the first app start wrote its end marker at 2000 ms

  @offline @boundary @AIR-069 @AT-69 @OD-161 @OD-175 @OD-176
  Scenario: Two start failures in one app start give one notice, for the first cause logged
    Given the interface double reports wlan0 down
    And the key file exists and is zero-length
    When the receiver runs for 20000 ms
    Then the log has "hotspot address missing" and then "identity key unavailable"
    And exactly one start-failure notice add call was made, for "hotspot address missing"

  @offline @boundary @AIR-061 @AIR-069 @AT-61 @AT-69 @OD-161
  Scenario: A start-failure notice add that throws is logged and not retried
    Given the interface double reports wlan0 down
    And the UI double throws on add for the start-failure notice
    And the receiver's window loses focus at 2000 ms and gains focus at 3000 ms
    When the receiver runs for 20000 ms
    Then the failed add is logged
    And exactly one start-failure notice add attempt was made, at exactly 0 ms
    And mirrored video stays blocked throughout

  @offline @normal @AIR-070 @AIR-050 @AT-70 @OD-158 @OD-164
  Scenario Outline: Stop runs its eight steps in order
    Given a session starts at 1000 ms
    When <trigger> at 3000 ms
    Then the doubles recorded these steps in this order: video blocked, accepting stopped, the session ended, both dns_sd registrations withdrawn, stopServiceDiscovery called, the fake codec released, all five ports closed, the end marker written
    And the end marker is the log's last record

    Examples:
      | trigger                                   |
      | the lifecycle double delivers onDestroy   |
      | exit() is called                          |

  @offline @failure @AIR-070 @AT-70 @OD-164
  Scenario Outline: A failing stop step is logged, and every later step still runs
    Given a session starts at 1000 ms
    And the stop step "<step>" throws
    When exit() is called at 3000 ms
    Then the log has a record of that step failing
    And every stop step after it was recorded, in order, ending with the end marker

    Examples:
      | step                                |
      | block video                         |
      | stop accepting connections          |
      | end the session                     |
      | withdraw the mDNS registrations     |
      | stopServiceDiscovery                |
      | release the decoder                 |
      | close all five ports                |

  @offline @boundary @AIR-070 @AIR-050 @AT-70 @OD-164
  Scenario: A stop after advertise failed before discovery started skips the discovery step quietly
    Given getSystemService(Context.NSD_SERVICE) returns null
    When exit() is called at 2000 ms
    Then stopServiceDiscovery was not called and the log has no error record for the discovery step
    And the log has no error record for the registration, session or decoder steps
    And all five ports were closed, and then the end marker was written

  @offline @normal @AIR-072 @AT-72 @OD-174
  Scenario: The log callback and debug level are set before raop_init2 and never changed
    Given a session starts at 1000 ms
    When the receiver runs for 5000 ms and then exit() is called
    Then the fake native library recorded the log callback call and the log level call, with level debug, before raop_init2
    And it recorded no other log level call

  @offline @boundary @AIR-072 @AT-72 @OD-174
  Scenario Outline: A native record the filter cannot handle is dropped and counted, never written
    Given the library double delivers the allowlisted record "FILTER-EDGE-3d9a" <when>
    When the receiver runs until the session ends
    Then no log file contains "FILTER-EDGE-3d9a"
    And the withheld count includes that record

    Examples:
      | when                                      |
      | before the filter is ready                |
      | while the filter throws on every record   |

  @offline @failure @AIR-073 @AT-73 @OD-155
  Scenario: Only allowlisted native records are written, with addresses redacted and the rest counted
    Given a session starts at 1000 ms
    And the library double delivers these native records at 2000 ms:
      | record                                                          |
      | an allowlisted record carrying IPv4 192.168.0.50                |
      | an allowlisted record carrying IPv6 fe80::1c2b:3a4d:5e6f:7081   |
      | an allowlisted record carrying MAC 02:1a:2b:3c:4d:5e            |
      | a record off the allowlist carrying the name "Fixture Phone"    |
      | a record off the allowlist carrying "0A1B2C3D4E5F"              |
      | a record off the allowlist reading "fixture unknown text"       |
    And the session ends at 3000 ms
    Then the three allowlisted records are written with each address replaced by "[redacted]"
    And no log record contains "192.168.0.50", "fe80::1c2b:3a4d:5e6f:7081", "02:1a:2b:3c:4d:5e", "Fixture Phone", "0A1B2C3D4E5F" or "fixture unknown text"
    And the withheld count logged at session end is 3

  @offline @failure @AIR-073 @AT-73 @OD-155 @OD-170
  Scenario Outline: An allowlisted record that carries a forbidden item is dropped whole, never redacted
    Given a session starts at 1000 ms
    And the library double delivers at 2000 ms an allowlisted record that also carries <carried>
    And the session ends at 3000 ms
    Then no part of that record was written
    And the withheld count logged at session end is 1

    Examples:
      | carried                                    |
      | "pin 4821"                                 |
      | "PIN: 4821"                                |
      | "spinning"                                 |
      | "User-Agent: AirPlay/fixture"              |
      | "deviceid=0A1B2C3D4E5F"                    |
      | "Device ID 0A1B2C3D4E5F"                   |
      | "<?xml version=\"1.0\"?>"                  |
      | "<plist version=\"1.0\">"                  |
      | "aesiv 00112233445566778899aabbccddeeff"   |
      | "ekey 00112233"                            |
      | "aeskey 00112233"                          |
      | "ECDH 00112233"                            |
      | the key file's name                        |
      | "line one\nline two"                       |
      | "line one\rline two"                       |

  @offline @boundary @AIR-073 @AT-73 @OD-185
  Scenario Outline: Header lines and raw key material never reach the log
    Given a session starts at 500 ms
    And the library double delivers at 600 ms an allowlisted record, changed so that <change> and otherwise still on the allowlist
    When the receiver runs for 1000 ms and then exit() is called
    Then no part of that record was written
    And the withheld count logged at session end is 1

    Examples:
      | change                                           |
      | it starts with "CSeq: 3 "                        |
      | it starts with "X-Apple-ProtocolVersion: 1 "     |
      | it holds the byte 0x01                           |
      | it holds a 32-character run of hex digits        |
      | it holds a 44-character run of base64 characters |

  @offline @boundary @AIR-073 @AT-73 @OD-185
  Scenario: A 31-character hex run and a 43-character base64 run are kept
    Given a session starts at 500 ms
    And the library double delivers at 600 ms an allowlisted record holding a 31-character run of hex digits, and at 700 ms one holding a 43-character run of base64 characters
    When the receiver runs for 1000 ms and then exit() is called
    Then both records are written unchanged
    And the withheld count logged at session end is 0

  @offline @boundary @AIR-079 @AT-79 @OD-151 @OD-171
  Scenario Outline: The CarPlay listener check reads /proc/net/tcp and logs one line
    Given /proc/net/tcp is a fixture <listener>, with the marker "PROC-NET-MARKER" in another line
    When the receiver runs for 1000 ms
    Then the log has "<record>"
    And no log record contains "PROC-NET-MARKER" or any other line of the fixture
    And the socket double recorded no connect to port 7000
    And the log has "advertise ok"
    And no notice add call was made

    Examples:
      | listener                                                  | record                    |
      | with a local TCP 7000 listener (local port 1B58, state 0A) | carplay listener present  |
      | with no local TCP 7000 listener                            | carplay listener absent   |

  @offline @boundary @AIR-079 @AT-79 @OD-151 @OD-158 @OD-185
  Scenario Outline: An unreadable or malformed /proc/net/tcp is logged as unknown, and the start goes on
    Given /proc/net/tcp <fault>
    When the receiver runs for 1000 ms
    Then the log has "carplay listener unknown" naming the exception class <class>
    And the log has "advertise ok"
    And no notice add call was made

    Examples:
      | fault                                | class                             |
      | throws java.io.IOException when read | "java.io.IOException"             |
      | holds a malformed fixture            | "java.lang.NumberFormatException" |

  @offline @normal @AIR-080 @AT-80
  Scenario Outline: The receiver never takes the screen back
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                                      |
      | moveTaskToFront                                |
      | startActivity naming the receiver's own activity |
      | FLAG_ACTIVITY_REORDER_TO_FRONT                 |

  @offline @normal @AIR-080 @AT-80
  Scenario: After focus is lost the receiver stays behind and shows no notice
    Given the interface double reports wlan0 down
    And the receiver's window loses focus at 0 ms, after the default focus gain, and never regains it
    When the receiver runs for 20000 ms
    Then no notice add call was made
    And no moveTaskToFront, startActivity or requestFocus call was recorded

  @offline @normal @AIR-081 @AT-81
  Scenario Outline: Nothing uses a second display
    When the static scan runs
    Then it finds no <forbidden>

    Examples:
      | forbidden                    |
      | Presentation                 |
      | DisplayManager               |
      | createVirtualDisplay         |
      | setLaunchDisplayId           |

  @offline @normal @AIR-082 @AT-82
  Scenario Outline: The manifest has no launcher or home entry
    When the static scan reads AndroidManifest.xml
    Then it finds no <category> category

    Examples:
      | category                         |
      | android.intent.category.LAUNCHER |
      | android.intent.category.HOME     |

  @offline @normal @AIR-083 @AT-83 @OD-170 @OD-176
  Scenario: The first start creates an owner-only key file, and the second reuses it unchanged
    Given the fake app-data directory holds no key file
    When the receiver runs for 1000 ms and exit() is called
    And the receiver is started again and runs for 1000 ms
    Then in the first start, raop_init2 was passed as keyfile a path inside the fake app-data directory
    And a key file was created at that path, readable and writable only by the app's uid
    And in the second start, raop_init2 was passed the same keyfile path
    And the key file's SHA-256 hash after the second start equals its hash after the first
    And the fake file system recorded no write, rename, chmod or delete of the key file in the second start
    And no log record contains the key file's path or name

  @offline @failure @AIR-083 @AIR-069 @AT-83 @AT-69 @OD-170 @OD-176
  Scenario Outline: A key file that cannot be used stops the start and is left as it is
    Given <fault>
    When the receiver runs for 20000 ms
    Then the log has "identity key unavailable" naming <named>
    And no bind, no dns_sd registration and no accept call was made
    And <file>
    And mirrored video stays blocked throughout and the screen is black throughout
    And exactly one start-failure notice add call was made, at exactly 0 ms, for "identity key unavailable"
    And its view's text contains neither the key file's path nor its name nor <named>
    And the activity is not finished

    Examples:
      | fault                                                              | named                         | file                                                         |
      | the key file exists and every read of it throws IOException        | "java.io.IOException"         | the key file's bytes and mode are unchanged                  |
      | the key file exists and is zero-length                             | the library error             | the key file's bytes and mode are unchanged                  |
      | the key file holds the corrupt key fixture                         | the library error             | the key file's bytes and mode are unchanged                  |
      | no key file exists and creating it throws IOException              | "java.io.IOException"         | no rename to the key file's path was recorded                |

  @offline @failure @AIR-083 @AIR-073 @AT-83 @AT-73 @OD-170
  Scenario Outline: A native record carrying the key file's path, name or a key is dropped and counted
    Given a session starts at 1000 ms
    And the library double delivers at 2000 ms an allowlisted record that also carries <carried>
    And the session ends at 3000 ms
    Then no part of that record was written
    And the withheld count logged at session end is 1

    Examples:
      | carried                           |
      | the key file's path               |
      | the key file's name               |
      | the fake key fixture's bytes      |
