"""S3: tools for the on-unit receiver. Run `python tools/s3.py --help`."""

import sys

from airplay_s3.cli import main

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
