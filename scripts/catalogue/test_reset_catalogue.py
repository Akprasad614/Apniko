import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RESET_SCRIPT = ROOT / "scripts" / "catalogue" / "reset-local-catalogue.sh"
RESET_SQL = ROOT / "scripts" / "catalogue" / "reset-local-catalogue.sql"


class CatalogueResetSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = RESET_SCRIPT.read_text(encoding="utf-8")
        cls.sql = RESET_SQL.read_text(encoding="utf-8")

    def test_full_database_backup_precedes_destructive_sql(self):
        backup = self.shell.index("mysqldump --single-transaction --routines --triggers")
        reset = self.shell.index("reset-local-catalogue.sql")
        confirmation = self.shell.index("RESET CATALOGUE")
        self.assertLess(confirmation, backup)
        self.assertLess(backup, reset)
        self.assertIn('test -s "$BACKUP_FILE"', self.shell)

    def test_physical_product_images_are_archived_out_of_project(self):
        self.assertIn('UPLOADS_DIR="$ROOT_DIR/uploads/products"', self.shell)
        self.assertIn('mv "$UPLOADS_DIR" "$BACKUP_DIR/product-uploads"', self.shell)

    def test_catalogue_snapshots_and_reports_are_archived(self):
        self.assertIn('mv "$ROOT_DIR/data/catalogue" "$BACKUP_DIR/catalogue-files"', self.shell)

    def test_product_and_photo_data_is_cleared(self):
        for table in (
            "inventory_movements",
            "cart_items",
            "carts",
            "shop_inventory",
            "order_items",
            "order_status_history",
            "payments",
            "orders",
            "product_photo_review_history",
            "product_catalogue_source_records",
            "product_photos",
            "products",
            "product_categories",
        ):
            with self.subTest(table=table):
                self.assertIn(f"DELETE FROM {table};", self.sql)

    def test_catalogue_sequences_are_reset(self):
        for table in ("products", "product_photos", "product_photo_review_history",
                      "product_catalogue_source_records", "product_categories", "shop_inventory"):
            with self.subTest(table=table):
                self.assertIn(f"ALTER TABLE {table} AUTO_INCREMENT = 1;", self.sql)

    def test_reset_does_not_drop_database_or_user_accounts(self):
        self.assertNotIn("DROP DATABASE", self.sql.upper())
        self.assertNotIn("delete from users", self.sql.lower())
        self.assertNotIn("delete from roles", self.sql.lower())
        self.assertNotIn("delete from shops", self.sql.lower())
        self.assertNotIn("delete from customer_addresses", self.sql.lower())


if __name__ == "__main__":
    unittest.main()
