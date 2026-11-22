import argparse
import os
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import BytesIO, StringIO
from unittest.mock import Mock, patch

from PIL import Image, ImageDraw

from import_product_photos import (
    explain_candidate, find_candidate, image_url_rejection_reason, inspect_image_quality,
    parse_product_ids, run, safe_image_url, valid_gtin,
)


BARCODE = "8901234567890"
IMAGE = "https://images.openfoodfacts.org/images/products/890/123/456/7890/front_en.4.400.jpg"
API_URL = "https://world.openfoodfacts.org/api/v3/product/" + BARCODE + ".json"


def make_valid_image_bytes():
    image = Image.new("RGB", (640, 640), "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((145, 70, 495, 570), fill=(45, 75, 105), outline=(10, 10, 10), width=8)
    buffer = BytesIO()
    image.save(buffer, format="JPEG", quality=92)
    return buffer.getvalue()


VALID_IMAGE_BYTES = make_valid_image_bytes()
FULL_IMAGE = IMAGE.replace(".400.jpg", ".full.jpg")


def valid_image_response():
    return FakeResponse(
        200, {}, FULL_IMAGE,
        content=VALID_IMAGE_BYTES,
        headers={"Content-Type": "image/jpeg"},
    )


class FakeResponse:
    def __init__(self, status_code, payload, url, content=b"", headers=None):
        self.status_code = status_code
        self.payload = payload
        self.url = url
        self.content = content
        self.headers = headers or {}

    def json(self):
        return self.payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"Unexpected HTTP {self.status_code} in test")


class ProductPhotoImporterTests(unittest.TestCase):
    def test_gtin_check_digit_is_validated_before_source_lookup(self):
        self.assertTrue(valid_gtin(BARCODE))
        self.assertTrue(valid_gtin("00000604"))
        self.assertFalse(valid_gtin("8901234567891"))
        self.assertFalse(valid_gtin("12345"))

    def test_only_https_allowlisted_images_with_matching_barcode_are_accepted(self):
        self.assertTrue(safe_image_url(IMAGE, BARCODE))
        self.assertFalse(safe_image_url(IMAGE.replace("https://", "http://"), BARCODE))
        self.assertFalse(safe_image_url(IMAGE.replace("images.openfoodfacts.org", "example.com"), BARCODE))
        self.assertFalse(safe_image_url(IMAGE.replace("890/123/456/7890", "111/222/333/4444"), BARCODE))
        wrong_host = IMAGE.replace("images.openfoodfacts.org", "cdn.example.test")
        self.assertIn("cdn.example.test", image_url_rejection_reason(wrong_host, BARCODE))
        wrong_path = IMAGE.replace("/images/products/", "/images/other/")
        self.assertIn("/images/products/", image_url_rejection_reason(wrong_path, BARCODE))
        wrong_barcode = IMAGE.replace("890/123/456/7890", "111/222/333/4444")
        self.assertIn("normalized matching barcode", image_url_rejection_reason(wrong_barcode, BARCODE))

        # Open Facts requires GTIN-12 / GTIN-8 values to be left-padded to 13
        # digits before splitting them into image directory segments.
        upc = "036000291452"
        padded_upc_image = (
            "https://images.openfoodfacts.org/images/products/003/600/029/1452/"
            "front_en.2.400.jpg"
        )
        self.assertTrue(safe_image_url(padded_upc_image, upc))
        self.assertFalse(safe_image_url(padded_upc_image, BARCODE))

    def test_candidate_requires_india_in_source_countries(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        payload = {"product": {
            "code": BARCODE,
            "countries_tags": ["en:united-states"],
            "product_name": "Grocery pack",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}
        candidate, reason = explain_candidate(item, payload, API_URL)
        self.assertIsNone(candidate)
        self.assertIn("does not tag India", reason)

    def test_candidate_prefers_full_size_image_variant(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        payload = {"product": {"code": BARCODE,
            "countries_tags": ["en:india"],
            "product_name": "Grocery pack",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {
                "display": {"en": IMAGE},
            }},
        }}
        candidate = find_candidate(item, payload, API_URL)
        self.assertEqual(candidate["imageUrl"], IMAGE)
        self.assertEqual(candidate["qualityImageUrl"], FULL_IMAGE)

    def test_image_quality_gate_accepts_clear_white_background_full_image(self):
        session = Mock()
        session.get.return_value = valid_image_response()
        report, reason = inspect_image_quality(session, FULL_IMAGE, BARCODE)
        self.assertIsNone(reason)
        self.assertGreaterEqual(report["width"], 600)
        self.assertGreaterEqual(report["cleanBackgroundCorners"], 3)
        session.get.assert_called_once()

    def test_image_quality_gate_rejects_low_resolution_image(self):
        tiny = Image.new("RGB", (400, 400), "white")
        buffer = BytesIO()
        tiny.save(buffer, format="JPEG")
        session = Mock()
        session.get.return_value = FakeResponse(
            200, {}, FULL_IMAGE, content=buffer.getvalue(),
            headers={"Content-Type": "image/jpeg"},
        )
        report, reason = inspect_image_quality(session, FULL_IMAGE, BARCODE)
        self.assertIsNone(report)
        self.assertIn("resolution is too small", reason)

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
            "countries_tags": ["en:india"],
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
            "countries_tags": ["en:india"],
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
            "countries_tags": ["en:india"],
            "brands": "Different Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}
        wrong_pack = {"product": {
            "code": BARCODE,
            "countries_tags": ["en:india"],
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

    def test_brand_formatting_and_diacritics_are_normalized_without_fuzzy_matching(self):
        def candidate_for(local_brand, source_brand):
            item = {"barcode": BARCODE, "name": "Grocery", "brand": local_brand, "packSize": "200 ml"}
            payload = {"product": {
                "code": BARCODE,
            "countries_tags": ["en:india"], "product_name": "Grocery pack",
                "brands": source_brand, "quantity": "200ml",
                "selected_images": {"front": {"display": {"en": IMAGE}}},
            }}
            return find_candidate(item, payload, API_URL)

        self.assertIsNotNone(candidate_for("Coca cola", "Coca-Cola"))
        self.assertIsNotNone(candidate_for("Nestle", "Nestlé"))
        self.assertIsNotNone(candidate_for("KitKat", "Kit Kat"))
        self.assertIsNone(candidate_for("Coca-Cola", "Pepsi-Cola"))

    def test_equivalent_pack_units_are_normalized_but_real_differences_are_rejected(self):
        def candidate_for(local_pack, source_pack):
            item = {"barcode": BARCODE, "name": "Grocery", "brand": "Sample Brand", "packSize": local_pack}
            payload = {"product": {
                "code": BARCODE,
            "countries_tags": ["en:india"], "product_name": "Grocery pack", "brands": "Sample Brand", "quantity": source_pack,
                "selected_images": {"front": {"display": {"en": IMAGE}}},
            }}
            return find_candidate(item, payload, API_URL)

        equivalent_pairs = (
            ("100 g", "100gm"), ("200 ml", "200ml"), ("1 g", "1000 mg"),
            ("1000 ml", "1 l"), ("90 g", "90 g / 3.2 OZ"), ("200 g", "2 x 100gm"),
            ("473.176 ml", "16 FL OZ (473 ml)"),
        )
        for local_pack, source_pack in equivalent_pairs:
            with self.subTest(local_pack=local_pack, source_pack=source_pack):
                self.assertIsNotNone(candidate_for(local_pack, source_pack))

        mismatched_pairs = (
            ("100 g", "200 g"), ("100 g", "100 ml"), ("100 g", "2 x 100 g"),
        )
        for local_pack, source_pack in mismatched_pairs:
            with self.subTest(local_pack=local_pack, source_pack=source_pack):
                self.assertIsNone(candidate_for(local_pack, source_pack))

    def test_missing_source_product_name_is_skipped_instead_of_using_apniko_name(self):
        item = {
            "barcode": BARCODE, "name": "Apniko catalogue name",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        payload = {"product": {
            "code": BARCODE,
            "countries_tags": ["en:india"],
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}

        candidate, reason = explain_candidate(item, payload, API_URL)

        self.assertIsNone(candidate)
        self.assertEqual(reason, "source record has no product name")

    def test_absent_front_image_is_skipped_not_guessed(self):
        item = {
            "barcode": BARCODE, "name": "Grocery",
            "brand": "Sample Brand", "packSize": "1 kg",
        }
        self.assertIsNone(find_candidate(
            item, {"product": {
                "code": BARCODE,
            "countries_tags": ["en:india"], "brands": "Sample Brand", "quantity": "1 kg",
                "selected_images": {},
            }}, API_URL))

    def test_product_id_allowlist_only_submits_selected_candidate(self):
        other_barcode = "8901234567891"
        targets = FakeResponse(200, {
            "content": [
                {"productId": 26, "barcode": other_barcode, "name": "Do not submit",
                 "brand": "Sample Brand", "packSize": "1 kg"},
                {"productId": 29, "barcode": BARCODE, "name": "Selected grocery",
                 "brand": "Sample Brand", "packSize": "1 kg"},
            ],
            "last": True,
        }, "http://localhost:8080/api/admin/catalogue/photo-import-targets")
        found = FakeResponse(200, {"product": {
            "code": BARCODE,
            "countries_tags": ["en:india"],
            "product_name": "Selected grocery",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}, API_URL)
        saved = FakeResponse(200, {"status": "PENDING"},
                             "http://localhost:8080/api/admin/catalogue/products/29/photo-candidate")

        session = Mock()
        session.get.side_effect = [targets, found, valid_image_response()]
        session.post.return_value = saved
        args = argparse.Namespace(
            api_url="http://localhost:8080",
            limit=100,
            page=0,
            delay_seconds=4.1,
            apply=True,
            product_ids={29},
        )
        output = StringIO()
        with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-admin-token"}):
            with patch("import_product_photos.time.sleep"), redirect_stdout(output):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        self.assertIn("Product ID allowlist: 29", output.getvalue())
        self.assertIn("CANDIDATE product #29", output.getvalue())
        self.assertNotIn("CANDIDATE product #26", output.getvalue())
        self.assertIn("Inspected: 1; candidates: 1; submitted: 1", output.getvalue())
        session.post.assert_called_once()
        self.assertTrue(session.post.call_args.args[0].endswith("/api/admin/catalogue/products/29/photo-candidate"))
        self.assertNotIn("qualityImageUrl", session.post.call_args.kwargs["json"])

    def test_product_id_allowlist_fails_when_id_is_not_in_target_page(self):
        targets = FakeResponse(200, {
            "content": [
                {"productId": 26, "barcode": BARCODE, "name": "Grocery",
                 "brand": "Sample Brand", "packSize": "1 kg"},
            ],
            "last": True,
        }, "http://localhost:8080/api/admin/catalogue/photo-import-targets")
        session = Mock()
        session.get.return_value = targets
        args = argparse.Namespace(
            api_url="http://localhost:8080",
            limit=10,
            page=0,
            delay_seconds=4.1,
            apply=True,
            product_ids={999},
        )
        output = StringIO()
        with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-admin-token"}):
            with redirect_stdout(output), redirect_stderr(output):
                result = run(args, session=session)

        self.assertEqual(result, 2)
        self.assertIn("999", output.getvalue())
        self.assertEqual(session.get.call_count, 1)
        session.post.assert_not_called()

    def test_parse_product_ids_requires_unique_positive_integers(self):
        self.assertEqual(parse_product_ids("26,29,31"), {26, 29, 31})
        for invalid in ("", "26,,29", "26,zero", "0,29", "26,26"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(argparse.ArgumentTypeError):
                    parse_product_ids(invalid)

    def test_previously_retired_image_conflict_is_skipped_without_aborting_batch(self):
        targets = FakeResponse(200, {
            "content": [{
                "productId": 42, "barcode": BARCODE, "name": "Grocery",
                "brand": "Sample Brand", "packSize": "1 kg",
            }],
            "last": True,
        }, "http://localhost:8080/api/admin/catalogue/photo-import-targets")
        found = FakeResponse(200, {"product": {
            "code": BARCODE,
            "countries_tags": ["en:india"],
            "product_name": "Grocery pack",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}, API_URL)
        conflict = FakeResponse(
            409, {"error": "This exact image URL was previously rejected or retired"},
            "http://localhost:8080/api/admin/catalogue/products/42/photo-candidate",
        )
        session = Mock()
        session.get.side_effect = [targets, found, valid_image_response()]
        session.post.return_value = conflict
        args = argparse.Namespace(
            api_url="http://localhost:8080",
            limit=10,
            page=0,
            delay_seconds=4.1,
            apply=True,
            product_ids=None,
        )
        output = StringIO()
        with patch.dict(os.environ, {"APNIKO_ADMIN_TOKEN": "test-admin-token"}):
            with patch("import_product_photos.time.sleep"), redirect_stdout(output):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        self.assertIn("already rejected or retired", output.getvalue())
        self.assertIn("Inspected: 1; candidates: 1; submitted: 0", output.getvalue())
        session.post.assert_called_once()

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
            "countries_tags": ["en:india"],
            "product_name": "Grocery pack",
            "brands": "Sample Brand",
            "quantity": "1 kg",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
        }}, API_URL)

        session = Mock()
        session.get.side_effect = [targets, not_found, found, valid_image_response()]
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
