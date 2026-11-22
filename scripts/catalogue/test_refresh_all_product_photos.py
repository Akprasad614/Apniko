import tempfile
import unittest
from pathlib import Path

import refresh_all_product_photos as refresh


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
