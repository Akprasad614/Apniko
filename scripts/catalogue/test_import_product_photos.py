import argparse
import os
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import Mock, patch

from import_product_photos import explain_candidate, find_candidate, run, safe_image_url


BARCODE = "8901234567890"
IMAGE = "https://images.openfoodfacts.org/images/products/890/123/456/7890/front_en.4.400.jpg"
API_URL = "https://world.openfoodfacts.org/api/v3/product/" + BARCODE + ".json"


class FakeResponse:
    def __init__(self, status_code, payload, url):
        self.status_code = status_code
        self.payload = payload
        self.url = url

    def json(self):
        return self.payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"Unexpected HTTP {self.status_code} in test")


class ProductPhotoImporterTests(unittest.TestCase):
    def test_only_https_allowlisted_images_with_matching_barcode_are_accepted(self):
        self.assertTrue(safe_image_url(IMAGE, BARCODE))
        self.assertFalse(safe_image_url(IMAGE.replace("https://", "http://"), BARCODE))
        self.assertFalse(safe_image_url(IMAGE.replace("images.openfoodfacts.org", "example.com"), BARCODE))
        self.assertFalse(safe_image_url(IMAGE.replace("890/123/456/7890", "111/222/333/4444"), BARCODE))

    def test_source_barcode_must_match_exactly(self):
        item = {"barcode": BARCODE, "name": "Grocery"}
        payload = {"product": {"code": "8901234567891", "selected_images": {"front": {"display": {"en": IMAGE}}}}}
        self.assertIsNone(find_candidate(item, payload, API_URL))

    def test_candidate_keeps_source_licence_attribution_and_exact_gtin(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        payload = {"product": {
            "code": BARCODE,
            "product_name": "Grocery pack",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
            "photographers_tags": ["verified-contributor"],
        }}
        candidate = find_candidate(item, payload, API_URL)
        self.assertEqual(candidate["matchedBarcode"], BARCODE)
        self.assertEqual(candidate["license"], "CC BY-SA")
        self.assertEqual(candidate["sourceProductBrand"], "Sample Brand")
        self.assertEqual(candidate["sourcePackSize"], "1 kg")
        self.assertIsNone(candidate["authorizationReference"])
        self.assertIn("verified-contributor", candidate["attribution"])
        self.assertEqual(candidate["sourceUrl"], "https://world.openfoodfacts.org/product/" + BARCODE)

    def test_candidate_requires_exact_brand_and_pack_size(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        for source_brand, source_pack in (
            ("Different Brand", "1 kg"),
            ("Sample Brand", "500 g"),
            ("", "1 kg"),
            ("Sample Brand", ""),
        ):
            payload = {"product": {
                "code": BARCODE,
                "brands": source_brand,
                "quantity": source_pack,
                "selected_images": {"front": {"display": {"en": IMAGE}}},
            }}
            self.assertIsNone(find_candidate(item, payload, API_URL))

    def test_skip_reason_identifies_brand_or_pack_mismatch(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        wrong_brand = {"product": {
            "code": BARCODE,
            "brands": "Different Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}
        wrong_pack = {"product": {
            "code": BARCODE,
            "brands": "Sample Brand",
            "quantity": "500 g",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}

        candidate, reason = explain_candidate(item, wrong_brand, API_URL)
        self.assertIsNone(candidate)
        self.assertIn("brand mismatch", reason)

        candidate, reason = explain_candidate(item, wrong_pack, API_URL)
        self.assertIsNone(candidate)
        self.assertIn("pack-size mismatch", reason)

    def test_absent_front_image_is_skipped_not_guessed(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        self.assertIsNone(find_candidate(
            item, {"product": {
                "code": BARCODE, "brands": "Sample Brand", "quantity": "1 kg",
                "selected_images": {},
            }}, API_URL))

    def test_unknown_open_facts_barcode_is_skipped_and_batch_continues(self):
        missing_barcode = "00000604"
        targets = FakeResponse(200, {
            "content": [
                {"productId": 1, "barcode": missing_barcode, "name": "Unknown product"},
                {"productId": 2, "barcode": BARCODE, "name": "Grocery",
                 "brand": "Sample Brand", "packSize": "1 kg"},
            ],
            "last": True,
        }, "http://localhost:8080/api/admin/catalogue/photo-import-targets")
        not_found = FakeResponse(
            404, {}, "https://world.openfoodfacts.org/api/v3/product/00000604.json"
        )
        found = FakeResponse(200, {"product": {
            "code": BARCODE,
            "product_name": "Grocery pack",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}, API_URL)

        session = Mock()
        session.get.side_effect = [targets, not_found, found]
        args = argparse.Namespace(
            api_url="http://localhost:8080",
            limit=10,
            page=0,
            delay_seconds=4.1,
            apply=False,
        )
        output = StringIO()
        with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-admin-token"}):
            with patch("import_product_photos.time.sleep"), redirect_stdout(output):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        self.assertIn("SKIP 00000604: no Open Facts product record", output.getvalue())
        self.assertIn("CANDIDATE product #2 · GTIN " + BARCODE, output.getvalue())
        self.assertIn("Apniko : Grocery · Sample Brand · 1 kg", output.getvalue())
        self.assertIn("Source : Grocery pack · Sample Brand · 1 kg", output.getvalue())
        self.assertIn("Inspected: 2; candidates: 1; submitted: 0", output.getvalue())
        session.post.assert_not_called()


if __name__ == "__main__":
    unittest.main()
