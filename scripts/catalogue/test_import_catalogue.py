import csv
import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import Mock, patch

from import_catalogue import (
    DOI, EXPECTED_HEADERS, SOURCE_LICENSE, SOURCE_NAME,
    load_mendeley_csv, run, validate_api_url, sha256_file,
)

ROW_COUNT = 852


class MendeleyImportTests(unittest.TestCase):
    def source_row(self, row_id, **overrides):
        row = {field: "" for field in EXPECTED_HEADERS}
        row.update({
            "S.No": str(row_id),
            "Item name": f"Sample Food {row_id}",
            "Brand_Name": "SAMPLE BRAND",
            "Category": "SNACKS",
            "Sub_Category": "CHIPS",
            "Ingredients": "Potato, oil, salt",
            "Serving_Size_g": "100.0",
            "Calories_kcal": "450.0",
            "Carbohydrates_g": "50.0",
            "Proteins_g": "5.0",
            "Total_Fat_g": "20.0",
            "Saturated_Fat_g": "5.0",
            "Trans_Fat_g": "0.1",
            "Sugar_g": "2.0",
            "Sodium_mg": "350.0",
            "Dietary_Fiber_g": "4.0",
            "Cholesterol_mg": "0",
            "Calcium_mg": "20.0",
            "Vitamin": "",
            "Iron_mg": "1.0",
            "Price_INR": "50.0",
            "Source_of_Data": "",
            "Collection_Date": "",
            "Collected_By": "",
            "Remarks": "",
        })
        row.update(overrides)
        return row

    def make_csv(self, folder, rows=None, headers=EXPECTED_HEADERS):
        path = Path(folder) / "packaged_foods_india.csv"
        rows = rows if rows is not None else [
            self.source_row(i) for i in range(1, ROW_COUNT + 1)
        ]
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=headers)
            writer.writeheader()
            writer.writerows([
                {key: value for key, value in row.items() if key in headers}
                for row in rows
            ])
        return path

    def args(self, path, **overrides):
        values = {
            "file": path,
            "api_url": "http://localhost:8080",
            "limit": 0,
            "batch_size": 100,
            "license_confirmed": False,
            "commercial_use_confirmed": False,
            "permission_reference": DOI,
            "apply": False,
        }
        values.update(overrides)
        return Namespace(**values)

    def test_maps_catalogue_identity_and_preserves_raw_source_values(self):
        with tempfile.TemporaryDirectory() as folder:
            rows = [self.source_row(i) for i in range(1, ROW_COUNT + 1)]
            rows[0].update({
                "Serving_Size_g": "100",
                "Price_INR": "39.00",
                "Ingredients": "  Potato, salt  ",
            })
            path = self.make_csv(folder, rows)
            records, stats = load_mendeley_csv(path)

        item = records[0]
        self.assertEqual(item["name"], "Sample Food 1")
        self.assertEqual(item["brand"], "SAMPLE BRAND")
        self.assertEqual(item["category"], "SNACKS")
        self.assertEqual(item["subcategory"], "CHIPS")
        self.assertEqual(item["sourceRecordId"], "mendeley-ipf-2026-v1:0001")
        self.assertEqual(item["identityDiscriminator"], item["sourceRecordId"])
        self.assertIsNone(item["barcode"])
        self.assertIsNone(item["packSize"])
        self.assertIsNone(item["netQuantity"])
        self.assertIsNone(item["mrp"])
        self.assertIsNone(item["description"])
        self.assertEqual(stats["eligible"], ROW_COUNT)
        self.assertEqual(stats["review_required_rows"], ROW_COUNT)
        self.assertIn("verified retail GTIN/barcode and retail pack size are not supplied", item["sourceReviewReason"])

        metadata = json.loads(item["sourceMetadataJson"])
        self.assertEqual(metadata["sourceValues"]["Ingredients"], "  Potato, salt  ")
        self.assertEqual(metadata["sourceValues"]["Price_INR"], "39.00")
        self.assertEqual(metadata["dataset"]["doi"], DOI)
        self.assertIn("not current shop price", metadata["fieldHandling"]["Price_INR"])
        self.assertIn("not retail pack size", metadata["fieldHandling"]["Serving_Size_g"])

    def test_repeated_name_brand_rows_are_kept_separate_and_flagged(self):
        with tempfile.TemporaryDirectory() as folder:
            rows = [self.source_row(i) for i in range(1, ROW_COUNT + 1)]
            rows[0]["Item name"] = rows[1]["Item name"] = "Duplicate Product"
            rows[0]["Brand_Name"] = rows[1]["Brand_Name"] = "SAME BRAND"
            path = self.make_csv(folder, rows)
            records, stats = load_mendeley_csv(path)

        self.assertEqual(stats["duplicate_identity_groups"], 1)
        self.assertEqual(stats["duplicate_identity_rows"], 2)
        self.assertEqual(stats["review_required_rows"], ROW_COUNT)
        self.assertIn("Repeated item name and brand", records[0]["sourceReviewReason"])
        self.assertIn("Repeated item name and brand", records[1]["sourceReviewReason"])
        self.assertNotEqual(records[0]["sourceRecordId"], records[1]["sourceRecordId"])

    def test_less_than_values_preserved_and_malformed_values_flagged(self):
        with tempfile.TemporaryDirectory() as folder:
            rows = [self.source_row(i) for i in range(1, ROW_COUNT + 1)]
            rows[0]["Trans_Fat_g"] = "<0.1"
            rows[1]["Proteins_g"] = "5.928.8"
            path = self.make_csv(folder, rows)
            records, stats = load_mendeley_csv(path)

        self.assertEqual(stats["rows_with_less_than_values"], 1)
        self.assertEqual(stats["rows_with_malformed_numeric_values"], 1)
        values = json.loads(records[0]["sourceMetadataJson"])["sourceValues"]
        self.assertEqual(values["Trans_Fat_g"], "<0.1")
        self.assertIn("Malformed numeric source value", records[1]["sourceReviewReason"])

    def test_preserves_suspect_source_taxonomy_and_flags_it_for_review(self):
        with tempfile.TemporaryDirectory() as folder:
            rows = [self.source_row(i) for i in range(1, ROW_COUNT + 1)]
            rows[0]["Category"] = "ICEREAMES"
            path = self.make_csv(folder, rows)
            records, stats = load_mendeley_csv(path)

        self.assertEqual(records[0]["category"], "ICEREAMES")
        self.assertIn("appears misspelled", records[0]["sourceReviewReason"])

    def test_rejects_schema_that_differs_from_readme(self):
        with tempfile.TemporaryDirectory() as folder:
            headers = tuple(field for field in EXPECTED_HEADERS if field != "Brand_Name")
            path = self.make_csv(folder, headers=headers)
            with self.assertRaisesRegex(ValueError, "does not match supplied README"):
                load_mendeley_csv(path)

    def test_rejects_duplicate_source_row_ids(self):
        with tempfile.TemporaryDirectory() as folder:
            rows = [self.source_row(i) for i in range(1, ROW_COUNT + 1)]
            rows[1]["S.No"] = "1"
            path = self.make_csv(folder, rows)
            with self.assertRaisesRegex(ValueError, "S.No contains duplicates"):
                load_mendeley_csv(path)

    def test_dry_run_never_calls_api(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            session = Mock()
            result = run(self.args(path), session=session)
        self.assertEqual(result, 0)
        session.post.assert_not_called()

    def test_apply_requires_explicit_licence_and_commercial_scope_confirmation(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            session = Mock()
            result = run(self.args(path, apply=True), session=session)
        self.assertEqual(result, 2)
        session.post.assert_not_called()

    def test_apply_rejects_csv_that_differs_from_inspected_source(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            session = Mock()
            with patch.dict("os.environ", {"APNIKO_ADMIN_TOKEN": "test-token"}):
                result = run(self.args(
                    path, apply=True, license_confirmed=True,
                    commercial_use_confirmed=True,
                ), session=session)
        self.assertEqual(result, 2)
        session.post.assert_not_called()

    def test_apply_sends_mendeley_provenance_and_never_sets_retail_facts(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            session = Mock()
            response = Mock()
            response.json.return_value = {
                "inserted": 1, "updated": 0, "unchanged": 0, "rejected": 0, "errors": []
            }
            response.raise_for_status.return_value = None
            session.post.return_value = response
            with patch.dict("os.environ", {"APNIKO_ADMIN_TOKEN": "test-token"}):
                with patch("import_catalogue.APPROVED_SOURCE_SHA256", sha256_file(path)):
                    result = run(self.args(
                        path, apply=True, license_confirmed=True,
                        commercial_use_confirmed=True,
                    ), session=session)

        self.assertEqual(result, 0)
        payload = session.post.call_args.kwargs["json"]
        self.assertEqual(payload["sourceName"], SOURCE_NAME)
        self.assertEqual(payload["sourceLicense"], SOURCE_LICENSE)
        self.assertEqual(payload["sourceUrl"], "https://data.mendeley.com/datasets/rhswn9z9xr/1")
        self.assertTrue(payload["reuseRightsVerified"])
        self.assertTrue(payload["commercialUsePermitted"])
        item = payload["products"][0]
        self.assertIsNone(item["mrp"])
        self.assertIsNone(item["packSize"])
        self.assertIsNone(item["barcode"])
        self.assertNotIn("imageUrl", item)
        self.assertTrue(item["sourceMetadataJson"].startswith("{"))

    def test_api_url_requires_https_except_localhost(self):
        self.assertEqual(validate_api_url("http://localhost:8080/"), "http://localhost:8080")
        with self.assertRaises(ValueError):
            validate_api_url("http://api.example.com")


if __name__ == "__main__":
    unittest.main()
