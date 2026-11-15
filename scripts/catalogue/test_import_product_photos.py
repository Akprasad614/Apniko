import unittest

from import_product_photos import find_candidate, safe_image_url


BARCODE = "8901234567890"
IMAGE = "https://images.openfoodfacts.org/images/products/890/123/456/7890/front_en.4.400.jpg"
API_URL = "https://world.openfoodfacts.org/api/v3/product/" + BARCODE + ".json"


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
        item = {"barcode": BARCODE, "name": "Grocery"}
        payload = {"product": {
            "code": BARCODE,
            "product_name": "Grocery pack",
            "selected_images": {"front": {"display": {"en": IMAGE}}},
            "photographers_tags": ["verified-contributor"],
        }}
        candidate = find_candidate(item, payload, API_URL)
        self.assertEqual(candidate["matchedBarcode"], BARCODE)
        self.assertEqual(candidate["license"], "CC BY-SA")
        self.assertIn("verified-contributor", candidate["attribution"])
        self.assertEqual(candidate["sourceUrl"], "https://world.openfoodfacts.org/product/" + BARCODE)

    def test_absent_front_image_is_skipped_not_guessed(self):
        item = {"barcode": BARCODE, "name": "Grocery"}
        self.assertIsNone(find_candidate(
            item, {"product": {"code": BARCODE, "selected_images": {}}}, API_URL))


if __name__ == "__main__":
    unittest.main()
