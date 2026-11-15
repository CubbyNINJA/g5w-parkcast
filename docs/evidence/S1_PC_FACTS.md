# Slice S1 evidence: facts for the PC feasibility test (2026-11-14)

Facts gathered before slice S1. Each line says where it comes from. Nothing here is a requirement.

## This PC

- Windows 11 Pro 10.0.26200. Ethernet only, `192.168.1.100/24` on interface "Ethernet 6".
  Windows marks this network **Public**, which blocks unsolicited inbound connections by
  default (`Get-NetConnectionProfile`).
- Apple's **Bonjour Service** is installed and running (`Get-Service "Bonjour Service"`).
  `C:\Program Files\Bonjour` exists. The Bonjour **SDK** is not installed.
- **MSYS2** was installed on 2026-11-14 with `winget install MSYS2.MSYS2` (installer 20260927,
  hash verified) to `C:\msys64`, then updated with `pacman -Syu` twice. No other MSYS2 packages
  have been added yet (OD-05).
- The owner's iPhone is on the home Wi-Fi of the same network. The PC is wired (OD-06). The
  iPhone's iOS version isn't known yet (AMB-03).

## UxPlay (upstream README at FDH2/UxPlay, read 2026-11-14)

- UxPlay is an AirPlay-mirror and AirPlay-audio server. It runs on Windows, built under MSYS2
  with MinGW-64. There are no Windows binaries: GitHub releases v1.73.5 to v1.73.7 carry only
  `uxplay.spec` and `PKGBUILD` files.
- Windows build, per the README: open a UCRT64 terminal and install
  `mingw-w64-ucrt-x86_64-cmake`, `mingw-w64-ucrt-x86_64-gcc`, `git`,
  `mingw-w64-ucrt-x86_64-libplist`, `mingw-w64-ucrt-x86_64-gstreamer` and
  `mingw-w64-ucrt-x86_64-gst-plugins-base`. Then run `mkdir build`, `cd build`, `cmake ..` and
  `ninja`. The output is `build/uxplay.exe`.
- UxPlay 1.74 (experimental) has its own mDNS, so the Bonjour SDK isn't needed. To use Bonjour
  instead, configure with `cmake -DUSE_DNS_SD=1`, which needs the SDK.
- Showing H.264 video also needs a decoder plugin. Base plugins alone may not include one, so
  the good, bad and libav plugin sets are probably needed. This is unverified until the build.
- HEVC is offered only with `-h265`. Without it, the receiver offers H.264 only.
- `-s WxH@fps` sets the display size the receiver advertises. `-n name` sets its name.
- UxPlay issue #535: one iOS 27 beta iPhone failed to mirror until `-h265` was enabled.

## The head unit (owner's 260206 CA unit; see `AGENTS.md` "Target facts")

- No hardware HEVC: `OMX.TCC.hevcdec` is never registered (gen5-codec-patch README, tested on a
  2023 Ioniq 5). H.264 decodes in hardware with `OMX.TCC.avcdec`.
- The screen is 1920×720. AirPlay mirroring audio is AAC-ELD. That's the format UxPlay decodes,
  but it hasn't been measured on the owner's iPhone.

## The question slice S1 answers (AMB-02)

Will the owner's iPhone start a screen-mirroring session, using H.264 video, with a receiver that
offers no HEVC and advertises 1920×720? If it won't, the project stops at S1.
