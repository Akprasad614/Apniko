import tempfile
import unittest
from pathlib import Path

from import_catalogue import (
    category_for,
    load_packSense,
    pack_size,
    packsense_item,
    valid_gtin,
)


class CatalogueImportTests(unittest.TestCase):
    def test_gtin_check_digit_validation(self):
        self.assertTrue(valid_gtin("4006381333931"))
        self.assertFalse(valid_gtin("4006381333932"))
        self.assertFalse(valid_gtin("12345"))

    def test_pack_size_normalization(self):
        self.assertEqual(pack_size("500.00", "grams"), "500 g")
        self.assertEqual(pack_size("1", "litre"), "1 l")
        self.assertIsNone(pack_size("500", "unknown"))

    def test_india_retail_category_mapping(self):
        self.assertEqual(category_for("Beverages", "Tea - coffee"), ("Beverages", "Tea, Coffee & Cocoa"))
        self.assertEqual(category_for("Dairy", "Milk"), ("Dairy, Bakery & Eggs", "Milk & Dairy"))
        self.assertEqual(category_for("unknown"), ("Other Essentials", "Unclassified Packaged Goods"))

    def test_packsense_row_requires_name_valid_gtin_and_clean_status(self):
        valid_row = {
            "code": "4006381333931",
            "product_name": "Sample Tea",
            "brands": "Sample Brand",
            "product_quantity": "250",
            "product_quantity_unit": "g",
            "pnns_groups_1": "Beverages",
            "pnns_groups_2": "Tea and coffee",
            "existence_status": "present_in_snapshot",
            "open_error_flags": "0",
            "last_modified_t": "1793700000",
        }
        item = packsense_item(valid_row)
        self.assertIsNotNone(item)
        self.assertEqual(item["category"], "Beverages")
        self.assertEqual(item["subcategory"], "Tea, Coffee & Cocoa")
        self.assertEqual(item["packSize"], "250 g")
        self.assertIsNone(item["mrp"])

        invalid = dict(valid_row, existence_status="unverified")
        self.assertIsNone(packsense_item(invalid))
        invalid = dict(valid_row, open_error_flags="1")
        self.assertIsNone(packsense_item(invalid))
        invalid = dict(valid_row, code="4006381333932")
        self.assertIsNone(packsense_item(invalid))

    def test_load_packsense_deduplicates_by_gtin(self):
        row = (
            "code,product_name,brands,product_quantity,product_quantity_unit,"
            "pnns_groups_1,pnns_groups_2,existence_status,open_error_flags,last_modified_t\n"
            "4006381333931,Sample Tea,Sample Brand,250,g,Beverages,Tea and coffee,present_in_snapshot,0,1793700000\n"
            "4006381333931,Sample Tea Duplicate,Sample Brand,250,g,Beverages,Tea and coffee,present_in_snapshot,0,1793700000\n"
        )
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "catalogue.csv"
            path.write_text(row, encoding="utf-8")
            items, stats = load_packSense(path)
        self.assertEqual(len(items), 1)
        self.assertEqual(stats["duplicate_rows"], 1)


if __name__ == "__main__":
    unittest.main()
