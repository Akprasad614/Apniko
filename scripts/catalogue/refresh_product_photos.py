#!/usr/bin/env python3
"""Disabled legacy Open Facts refresh entrypoint.

Apniko no longer imports product photos from Open Facts or third-party catalogue
aggregators. Use the authorised supplier CSV importer only when explicit written
image permission and source metadata are available.
"""
import sys


def main() -> int:
    print(
        "Open Facts photo refresh is disabled for Apniko. "
        "Use scripts/catalogue/import_authorized_product_photos.py with "
        "manufacturer/supplier images and explicit written permission.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
