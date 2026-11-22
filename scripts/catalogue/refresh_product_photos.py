#!/usr/bin/env python3
"""Securely authenticate and run the Phase 3 Open Facts photo lookup.

Dry-run is the default. --apply only submits pending review candidates; it never approves
or publishes a photograph.
"""
from __future__ import annotations

import argparse
import getpass
import os
import sys

import requests

from import_product_photos import parser as importer_parser, run as run_importer


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--api-url", default=os.environ.get("APNIKO_API_URL", "http://localhost:8080"))
    result.add_argument("--limit", type=int, default=100)
    result.add_argument("--page", type=int, default=0)
    result.add_argument("--delay-seconds", type=float, default=4.1)
    result.add_argument("--max-retries", type=int, default=3)
    result.add_argument("--apply", action="store_true", help="Submit strict-match candidates to the pending admin review queue")
    result.add_argument("--product-ids", help="Optional comma-separated Apniko product IDs")
    return result


def main() -> int:
    args = parser().parse_args()
    if not 1 <= args.limit <= 100 or args.page < 0 or args.delay_seconds < 4.0 or not 0 <= args.max_retries <= 6:
        print("Use --limit 1..100, non-negative --page, --delay-seconds >= 4 and --max-retries 0..6.", file=sys.stderr)
        return 2

    email = input("Administrator email: ").strip()
    if not email:
        print("Administrator email is required.", file=sys.stderr)
        return 2
    password = getpass.getpass("Administrator password: ")
    if not password:
        print("Administrator password is required.", file=sys.stderr)
        return 2

    client = requests.Session()
    try:
        response = client.post(
            args.api_url.rstrip("/") + "/api/auth/login",
            json={"email": email, "password": password},
            headers={"Accept": "application/json"},
            timeout=(5, 20),
        )
        response.raise_for_status()
        payload = response.json()
        token = payload.get("token")
        if payload.get("role") != "ADMIN" or not token:
            print("Login did not return an authenticated ADMIN session.", file=sys.stderr)
            return 3

        os.environ["APNIKO_ADMIN_TOKEN"] = token
        importer_args = [
            "--api-url", args.api_url,
            "--limit", str(args.limit),
            "--page", str(args.page),
            "--delay-seconds", str(args.delay_seconds),
            "--max-retries", str(args.max_retries),
        ]
        if args.product_ids:
            importer_args.extend(["--product-ids", args.product_ids])
        if args.apply:
            importer_args.append("--apply")

        print("Authenticated as administrator. JWT will not be printed.")
        print("Import mode:", "APPLY (submits pending candidates)" if args.apply else "DRY RUN (no Apniko changes)")
        return run_importer(importer_parser().parse_args(importer_args), session=client)
    except requests.RequestException as error:
        print("Authentication or lookup request failed:", error, file=sys.stderr)
        return 4
    finally:
        password = ""
        os.environ.pop("APNIKO_ADMIN_TOKEN", None)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
