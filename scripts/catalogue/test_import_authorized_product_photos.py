import argparse
import csv
import os
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import Mock, patch

from import_authorized_product_photos import (
    allowed_image_host,
    run,
    valid_gtin,
    valid_https_url,
)


BARCODE = "8901234567890"
IMAGE = "https://images.brand.example/products/masoor-1kg/front.jpg"
SOURCE = "https://brand.example/products/masoor-dal"


class FakeResponse:
    def __init__(self, payload, status_code=200):
        self.payload = payload
        self.status_code = status_code

    def json(self):
        return self.payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"Unexpected HTTP {self.status_code}")


class AuthorizedProductPhotoImporterTests(unittest.TestCase):
    def write_csv(self, directory: str, **overrides: str) -> Path:
        row = {
            "barcode": BARCODE,
            "source_product_name": "Masoor Dal",
            "source_brand": "Sample Brand",
            "source_pack_size": "1 kg",
            "image_url": IMAGE,
            "source_url": SOURCE,
            "authorization_reference": "Supplier agreement SUP-2026-01",
            "attribution": "Image supplied by Sample Brand",
        }
        row.update(overrides)
        path = Path(directory) / "supplier-photos.csv"
        with path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(row))
            writer.writeheader()
            writer.writerow(row)
        return path

    def test_validates_gtin_check_digits_and_https_urls(self):
        self.assertTrue(valid_gtin(BARCODE))
        self.assertFalse(valid_gtin("8901234567891"))
        self.assertTrue(valid_https_url(SOURCE))
        self.assertFalse(valid_https_url("http://brand.example/products/masoor-dal"))
        self.assertFalse(valid_https_url(SOURCE + "?download=1"))

    def test_only_source_domain_or_configured_authorised_cdn_is_accepted(self):
        self.assertTrue(allowed_image_host(IMAGE, SOURCE, set()))
        self.assertTrue(allowed_image_host(
            "https://cdn.apniko-supplier.example/masoor.jpg",
            SOURCE,
            {"cdn.apniko-supplier.example"},
        ))
        self.assertFalse(allowed_image_host(
            "https://unrelated.example/masoor.jpg", SOURCE, set()
        ))

    def test_dry_run_requires_exact_brand_and_pack_match_without_posting(self):
        targets = FakeResponse({
            "content": [{
                "productId": 42,
                "barcode": BARCODE,
                "brand": "Sample Brand",
                "packSize": "1 kg",
            }],
            "last": True,
        })
        session = Mock()
        session.get.return_value = targets
        args = argparse.Namespace(
            file=None,
            api_url="http://localhost:8080",
            limit=100,
            apply=False,
        )

        with tempfile.TemporaryDirectory() as directory:
            args.file = self.write_csv(directory)
            output = StringIO()
            with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-token"}), redirect_stdout(output):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        self.assertIn("READY " + BARCODE, output.getvalue())
        self.assertIn("submitted: 0", output.getvalue())
        session.post.assert_not_called()

    def test_rejects_mismatched_brand_and_pack_size(self):
        targets = FakeResponse({
            "content": [{
                "productId": 42,
                "barcode": BARCODE,
                "brand": "Sample Brand",
                "packSize": "500 g",
            }],
            "last": True,
        })
        session = Mock()
        session.get.return_value = targets
        args = argparse.Namespace(
            file=None,
            api_url="http://localhost:8080",
            limit=100,
            apply=False,
        )

        with tempfile.TemporaryDirectory() as directory:
            args.file = self.write_csv(directory)
            output = StringIO()
            with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-token"}), redirect_stdout(output):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        self.assertIn("source pack size does not exactly match Apniko", output.getvalue())
        session.post.assert_not_called()

    def test_apply_submits_permission_reference_and_brand_pack_evidence(self):
        targets = FakeResponse({
            "content": [{
                "productId": 42,
                "barcode": BARCODE,
                "brand": "Sample Brand",
                "packSize": "1 kg",
            }],
            "last": True,
        })
        session = Mock()
        session.get.return_value = targets
        session.post.return_value = FakeResponse({"status": "PENDING"})
        args = argparse.Namespace(
            file=None,
            api_url="http://localhost:8080",
            limit=100,
            apply=True,
        )

        with tempfile.TemporaryDirectory() as directory:
            args.file = self.write_csv(directory)
            with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-token"}):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        session.post.assert_called_once()
        payload = session.post.call_args.kwargs["json"]
        self.assertEqual(payload["matchedBarcode"], BARCODE)
        self.assertEqual(payload["sourceProductBrand"], "Sample Brand")
        self.assertEqual(payload["sourcePackSize"], "1 kg")
        self.assertEqual(payload["license"], "MANUFACTURER_AUTHORIZED")
        self.assertEqual(payload["authorizationReference"], "Supplier agreement SUP-2026-01")


if __name__ == "__main__":
    unittest.main()
