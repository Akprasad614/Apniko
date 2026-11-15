package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCatalogueQualityServiceTest {

    private final ProductCatalogueQualityService service =
            new ProductCatalogueQualityService();

    @Test
    void flagsPlaceholderAndNonDescriptiveNames() {
        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW,
                service.assess(product("....", "Other Essentials",
                        "Unclassified Packaged Goods")).status());
        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW,
                service.assess(product("07.05.25", "Other Essentials",
                        "Unclassified Packaged Goods")).status());
        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW,
                service.assess(product("[removed]", "Other Essentials",
                        "Unclassified Packaged Goods")).status());
    }

    @Test
    void flagsPackagingAndOcrNoise() {
        Product product = product(
                "031 MN2D ONTACT CONSUMER EXPIRY DATE NET WEIGHT 375 g "
                        + "BATCH No. MADE IN",
                "Other Essentials", "Unclassified Packaged Goods");

        var assessment = service.assess(product);

        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW, assessment.status());
        assertEquals("Product name appears to contain packaging or OCR text",
                assessment.reason());
    }

    @Test
    void flagsSupplementsForAdminReviewRatherThanDeletingThem() {
        var assessment = service.assess(product(
                "100% Micellar Casein", "Other Essentials",
                "Unclassified Packaged Goods"));

        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW, assessment.status());
        assertEquals("Sports nutrition or supplement product needs catalogue review",
                assessment.reason());
    }

    @Test
    void flagsUnclassifiedCategory() {
        var assessment = service.assess(product(
                "Suhana Schezwan Chutney", "Other Essentials",
                "Unclassified Packaged Goods"));

        assertEquals(ProductCatalogueReviewStatus.NEEDS_REVIEW, assessment.status());
        assertEquals("Product category is unclassified", assessment.reason());
    }

    @Test
    void keepsRecognizedRetailProductsActive() {
        assertEquals(ProductCatalogueReviewStatus.ACTIVE,
                service.assess(product("Suhana Schezwan Chutney",
                        "Gourmet & World Food", "Sauces, Spreads & Condiments")).status());
        assertEquals(ProductCatalogueReviewStatus.ACTIVE,
                service.assess(product("20g Protein Bar Cranberry Blast",
                        "Snacks & Branded Foods", "Breakfast & Cereals")).status());
        assertEquals(ProductCatalogueReviewStatus.ACTIVE,
                service.assess(product("Kachi Ghani Mustard Oil",
                        "Foodgrains, Oil & Masala", "Edible Oils & Ghee")).status());
    }

    private Product product(String name, String category, String subcategory) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setSubcategory(subcategory);
        return product;
    }
}
