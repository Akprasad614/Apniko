import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import refresh_all_product_photos as refresh


BARCODE = "8901234567890"
BEAUTY_IMAGE = (
    "https://images.openbeautyfacts.org/images/products/890/123/456/7890/front_en.4.400.jpg"
)
BEAUTY_FULL_IMAGE = BEAUTY_IMAGE.replace(".400.jpg", ".full.jpg")


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


class FullPhotoRefreshTests(unittest.TestCase):
    def test_fingerprint_includes_policy_version_and_product_identity(self):
        product = {
            "productId": 101,
            "barcode": "8901234567890",
            "name": "Sample Grocery",
            "brand": "Sample Brand",
            "packSize": "1 kg",
        }
        original = refresh.fingerprint(product)
        self.assertEqual(len(original), 64)
        self.assertEqual(original, refresh.fingerprint(product))
        changed = {**product, "packSize": "500 g"}
        self.assertNotEqual(original, refresh.fingerprint(changed))

    def test_lookup_falls_back_to_open_beauty_facts_after_food_facts_404(self):
        product = {
            "productId": 202,
            "barcode": BARCODE,
            "name": "Sample face cream",
            "brand": "Sample Brand",
            "packSize": "50 g",
        }
        missing = FakeResponse(
            404,
            {},
            f"https://world.openfoodfacts.org/api/v3/product/{BARCODE}.json",
        )
        beauty = FakeResponse(
            200,
            {"product": {
                "code": BARCODE,
                "countries_tags": ["en:india"],
                "product_name": "Sample face cream",
                "brands": "Sample Brand",
                "quantity": "50 g",
                "selected_images": {"front": {"display": {"en": BEAUTY_IMAGE}}},
            }},
            f"https://world.openbeautyfacts.org/api/v3/product/{BARCODE}.json",
        )
        session = Mock()
        session.get.side_effect = [missing, beauty]
        quality = {
            "width": 1200,
            "height": 1200,
            "bytes": 300000,
            "cleanBackgroundCorners": 4,
            "sharpnessScore": 30.0,
        }

        with patch.object(refresh, "inspect_image_quality", return_value=(quality, None)) as inspect:
            candidate, image_quality, reason, service_unavailable = (
                refresh.lookup_candidate_across_sources(
                    session,
                    product,
                    BARCODE,
                    delay_seconds=0,
                    max_retries=0,
                )
            )

        self.assertFalse(service_unavailable)
        self.assertIsNone(reason)
        self.assertEqual(candidate["sourceName"], "Open Beauty Facts")
        self.assertEqual(candidate["imageUrl"], BEAUTY_IMAGE)
        self.assertEqual(candidate["qualityImageUrl"], BEAUTY_FULL_IMAGE)
        self.assertEqual(image_quality, quality)
        self.assertEqual(session.get.call_count, 2)
        self.assertEqual(inspect.call_args.args[1], BEAUTY_FULL_IMAGE)

    def test_lookup_reports_rejection_reason_from_each_family_database(self):
        product = {
            "productId": 203,
            "barcode": BARCODE,
            "name": "Sample face cream",
            "brand": "Expected Brand",
            "packSize": "50 g",
        }
        food_mismatch = FakeResponse(
            200,
            {"product": {
                "code": BARCODE,
                "countries_tags": ["en:india"],
                "product_name": "Sample face cream",
                "brands": "Different Brand",
                "quantity": "50 g",
            }},
            f"https://world.openfoodfacts.org/api/v3/product/{BARCODE}.json",
        )
        missing_responses = [
            FakeResponse(
                404,
                {},
                f"https://{host}/api/v3/product/{BARCODE}.json",
            )
            for host in (
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
                "world.openproductsfacts.org",
            )
        ]
        session = Mock()
        session.get.side_effect = [food_mismatch, *missing_responses]

        candidate, quality, reason, service_unavailable = (
            refresh.lookup_candidate_across_sources(
                session,
                product,
                BARCODE,
                delay_seconds=0,
                max_retries=0,
            )
        )

        self.assertIsNone(candidate)
        self.assertIsNone(quality)
        self.assertFalse(service_unavailable)
        self.assertIn("world.openfoodfacts.org: brand mismatch", reason)
        self.assertIn("world.openbeautyfacts.org: no record", reason)
        self.assertIn("world.openpetfoodfacts.org: no record", reason)
        self.assertIn("world.openproductsfacts.org: no record", reason)
        self.assertEqual(session.get.call_count, 4)

    def test_open_facts_family_lookup_includes_all_four_databases(self):
        hosts = [url.split("/")[2] for url in (
            f"https://{host}/api/v3/product/{{barcode}}.json" for host in refresh.SOURCES
        )]
        self.assertEqual(hosts, [
            "world.openfoodfacts.org",
            "world.openbeautyfacts.org",
            "world.openpetfoodfacts.org",
            "world.openproductsfacts.org",
        ])

    def test_checkpoint_round_trip_uses_atomic_json_file(self):
        with tempfile.TemporaryDirectory() as directory:
            original_path = refresh.CHECKPOINT
            refresh.CHECKPOINT = Path(directory) / "processed.json"
            try:
                state = {"101": refresh.fingerprint({
                    "productId": 101,
                    "barcode": "8901234567890",
                    "name": "Sample Grocery",
                    "brand": "Sample Brand",
                    "packSize": "1 kg",
                })}
                refresh.save_checkpoint(state)
                self.assertEqual(refresh.load_checkpoint(), state)
                self.assertFalse(Path(directory, "processed.json.tmp").exists())
            finally:
                refresh.CHECKPOINT = original_path


if __name__ == "__main__":
    unittest.main()
