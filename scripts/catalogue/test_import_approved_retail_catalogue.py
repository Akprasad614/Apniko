import argparse
import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

import import_approved_retail_catalogue as importer


class ApprovedRetailCatalogueImporterTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.path = Path(self.temp_dir.name) / "catalogue.csv"

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_csv(self, rows, extra_fields=()):
        fields = list(importer.CSV_FIELDS) + list(extra_fields)
        with self.path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            for row in rows:
                writer.writerow({field: row.get(field, "") for field in fields})

    def valid_row(self, **overrides):
        row = {
            "source_record_id": "partner:sku-1001",
            "name": "Parle-G Gold",
            "brand": "Parle",
            "barcode": "8901058001235",
            "pack_size": "100 g",
            "net_quantity": "100",
            "net_quantity_unit": "g",
            "variant_group_key": "parle-g",
            "description": "Glucose biscuits",
            "category": "SNACKS",
            "subcategory": "BISCUITS",
            "mrp": "20.00",
            "mrp_source": "pack",
            "source_modified_at": "",
        }
        row.update(overrides)
        return row

    def test_normalizes_repeated_whitespace_in_csv_values(self):
        self.assertEqual("Parle G Gold", importer.clean("  Parle   G\tGold  "))

    def test_maps_verified_retail_identity_and_keeps_row_pending_review(self):
        self.write_csv([self.valid_row()])
        products, stats = importer.load_csv(self.path)

        self.assertEqual(1, stats["rows"])
        self.assertEqual(1, stats["pending_admin_review"])
        self.assertEqual("8901058001235", products[0]["barcode"])
        self.assertEqual("100 g", products[0]["packSize"])
        self.assertEqual("100", products[0]["netQuantity"])
        self.assertEqual("partner:sku-1001", products[0]["identityDiscriminator"])
        self.assertIn("administrator verification", products[0]["sourceReviewReason"])
        self.assertIn("sourceValues", products[0]["sourceMetadataJson"])

    def test_rejects_invalid_gtin_check_digit(self):
        self.write_csv([self.valid_row(barcode="8901058001234")])
        with self.assertRaisesRegex(ValueError, "invalid GTIN/check digit"):
            importer.load_csv(self.path)

    def test_rejects_duplicate_source_record_ids(self):
        self.write_csv([
            self.valid_row(),
            self.valid_row(name="Parle-G Family Pack"),
        ])
        with self.assertRaisesRegex(ValueError, "repeats source_record_id"):
            importer.load_csv(self.path)

    def test_rejects_unexpected_image_or_unknown_columns(self):
        self.write_csv([self.valid_row()], extra_fields=("image_url",))
        with self.assertRaisesRegex(ValueError, "unexpected columns"):
            importer.load_csv(self.path)

    def test_rejects_missing_required_retail_quantity(self):
        self.write_csv([self.valid_row(net_quantity="")])
        with self.assertRaisesRegex(ValueError, "missing net_quantity"):
            importer.load_csv(self.path)

    def test_dry_run_does_not_call_api(self):
        self.write_csv([self.valid_row()])
        session = Mock()
        args = argparse.Namespace(
            file=self.path,
            source_name="Approved Retail Partner",
            source_url="https://catalogue.partner.example/catalogue",
            source_license="COMMERCIAL-PARTNER-LICENSE",
            permission_reference="CONTRACT-2026-APNIKO-01",
            attribution="Approved Retail Partner; COMMERCIAL-PARTNER-LICENSE; "
                        "CONTRACT-2026-APNIKO-01; Apniko modifications.",
            api_url="https://api.apniko.example",
            batch_size=100,
            license_confirmed=False,
            commercial_use_confirmed=False,
            apply=False,
        )

        self.assertEqual(0, importer.run(args, session=session))
        session.post.assert_not_called()


if __name__ == "__main__":
    unittest.main()
