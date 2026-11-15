"""S1: the PC feasibility test for an AirPlay mirroring receiver. Run `python tools/s1.py --help`."""

import sys

from airplay_s1.cli import main

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
