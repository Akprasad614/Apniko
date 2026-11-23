package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProductCatalogueQualityService {

    private static final Pattern PLACEHOLDER_NAME = Pattern.compile(
            "^(?:user|removed|null|undefined|unknown|not available|n a|na)$"
    );
    private static final Pattern SUPPLEMENT_TERMS = Pattern.compile(
            "(?:^| )(?:whey|casein|creatine|glucosamine|theanine|mass gainer|pre workout|"
                    + "protein powder|dietary supplement|sports nutrition|amino acid)(?: |$)"
    );
    private static final List<String> LABEL_NOISE_MARKERS = List.of(
            "contact consumer", "expiry date", "batch no", "batch number",
            "net weight", "mrp", "best before", "manufactured by",
            "for customer care", "store in a cool"
    );

    public Assessment assess(Product product) {
        String name = normalize(product.getName());
        if (name.isBlank()) {
            return needsReview("Product name is missing");
        }

        String lettersOnly = name.replaceAll("[^a-z]", "");
        if (lettersOnly.length() < 3 || PLACEHOLDER_NAME.matcher(name).matches()) {
            return needsReview("Product name looks incomplete or like a placeholder");
        }

        long labelMarkers = LABEL_NOISE_MARKERS.stream()
                .filter(name::contains)
                .count();
        if (labelMarkers >= 2 || (name.length() > 100 && labelMarkers >= 1)) {
            return needsReview("Product name appears to contain packaging or OCR text");
        }

        String combined = (name + " " + normalize(product.getBrand())).trim();
        if (SUPPLEMENT_TERMS.matcher(combined).find()) {
            return needsReview("Sports nutrition or supplement product needs catalogue review");
        }

        if ("other essentials".equalsIgnoreCase(product.getCategory())
                || "unclassified packaged goods".equalsIgnoreCase(product.getSubcategory())) {
            return needsReview("Product category is unclassified");
        }

        return new Assessment(ProductCatalogueReviewStatus.ACTIVE, null);
    }

    private Assessment needsReview(String reason) {
        return new Assessment(ProductCatalogueReviewStatus.NEEDS_REVIEW, reason);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record Assessment(ProductCatalogueReviewStatus status, String reason) {
    }
}
