import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from import_catalogue import load_bigbasket_csv, normalize_row, run


class BigBasketImportTests(unittest.TestCase):
    def row(self, **overrides):
        value = {
            "index": "19",
            "product": "Fresho Bhendi",
            "category": "Fruits & Vegetables",
            "sub_category": "Fresh Vegetables",
            "brand": "Fresho",
            "type": "Regular",
            "description": "Fresh okra",
            "sale_price": "48.00",
            "market_price": "59.00",
            "rating": "4.2",
        }
        value.update(overrides)
        return value

    def test_bigbasket_row_imports_only_catalogue_identity_fields(self):
        item = normalize_row(self.row(), 2)

        self.assertEqual(item["name"], "Fresho Bhendi")
        self.assertEqual(item["brand"], "Fresho")
        self.assertEqual(item["category"], "Fruits & Vegetables")
        self.assertEqual(item["subcategory"], "Fresh Vegetables")
        self.assertEqual(item["variantGroupKey"], "Regular")
        self.assertEqual(item["description"], "Fresh okra")
        self.assertTrue(item["sourceRecordId"].startswith("bigbasket:"))
        self.assertIsNone(item["barcode"])
        self.assertIsNone(item["packSize"])
        self.assertIsNone(item["mrp"])
        self.assertNotIn("imageUrl", item)
        self.assertNotIn("sale_price", item)
        self.assertNotIn("market_price", item)
        self.assertNotIn("rating", item)

    def test_row_without_bigbasket_index_gets_stable_hash_record_id(self):
        row = self.row()
        row.pop("index")
        first = normalize_row(row, 2)
        second = normalize_row(row, 99)
        self.assertEqual(first["sourceRecordId"], second["sourceRecordId"])

    def test_csv_loader_counts_duplicates_and_missing_names(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "BigBasket.csv"
            fields = [
                "index", "product", "category", "sub_category",
                "brand", "type", "description", "sale_price",
                "market_price", "rating",
            ]
            with path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=fields)
                writer.writeheader()
                writer.writerow(self.row())
                writer.writerow(self.row())
                writer.writerow(self.row(index="20", product=""))
            rows, stats = load_bigbasket_csv(path)

        self.assertEqual(len(rows), 1)
        self.assertEqual(stats, {
            "received": 3,
            "eligible": 1,
            "skipped_missing_name": 1,
            "duplicate_source_rows": 1,
        })

    def args(self, path, **overrides):
        values = {
            "file": path,
            "api_url": "http://localhost:8080",
            "source_url": "https://www.kaggle.com/datasets/surajjha101/bigbasket-entire-product-list-28k-datapoints",
            "permission_reference": None,
            "permission_confirmed": False,
            "limit": 0,
            "batch_size": 250,
            "apply": False,
        }
        values.update(overrides)
        from argparse import Namespace
        return Namespace(**values)

    def make_csv(self, folder):
        path = Path(folder) / "BigBasket.csv"
        fields = list(self.row().keys())
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerow(self.row())
        return path

    def test_dry_run_never_calls_api_or_needs_a_permission_reference(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            session = Mock()
            result = run(self.args(path), session=session)

        self.assertEqual(result, 0)
        session.post.assert_not_called()

    def test_apply_is_blocked_without_explicit_permission_confirmation(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            with patch.dict("os.environ", {"APNIKO_ADMIN_TOKEN": "test-token"}):
                result = run(self.args(path, apply=True), session=Mock())

        self.assertEqual(result, 2)

    def test_apply_posts_only_bigbasket_identity_fields_and_permission_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.make_csv(folder)
            session = Mock()
            response = Mock()
            response.json.return_value = {
                "inserted": 1, "updated": 0, "unchanged": 0, "rejected": 0, "errors": []
            }
            response.raise_for_status.return_value = None
            session.post.return_value = response
            args = self.args(
                path,
                apply=True,
                permission_reference="SUPPLIER-GRANT-2026-01",
                permission_confirmed=True,
            )
            with patch.dict("os.environ", {"APNIKO_ADMIN_TOKEN": "test-token"}):
                result = run(args, session=session)

        self.assertEqual(result, 0)
        payload = session.post.call_args.kwargs["json"]
        self.assertEqual(payload["sourceName"], "BigBasket authorised dataset")
        self.assertEqual(payload["sourceLicense"], "BIGBASKET-WRITTEN-PERMISSION")
        self.assertTrue(payload["reuseRightsVerified"])
        self.assertTrue(payload["commercialUsePermitted"])
        self.assertEqual(payload["permissionReference"], "SUPPLIER-GRANT-2026-01")
        self.assertIn("Commercial use: authorised", payload["attribution"])
        self.assertIn("Permission reference: SUPPLIER-GRANT-2026-01", payload["attribution"])
        product = payload["products"][0]
        self.assertIsNone(product["barcode"])
        self.assertIsNone(product["packSize"])
        self.assertIsNone(product["mrp"])
        self.assertNotIn("imageUrl", product)


if __name__ == "__main__":
    unittest.main()
