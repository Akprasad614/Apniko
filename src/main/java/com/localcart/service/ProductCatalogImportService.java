package com.localcart.service;

import com.localcart.dto.ProductImportBatchRequest;
import com.localcart.dto.ProductImportBatchResponse;
import com.localcart.dto.ProductImportItem;
import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueSourceRecord;
import com.localcart.entity.ProductCategory;
import com.localcart.entity.ProductCatalogueReviewStatus;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductCatalogueSourceRecordRepository;
import com.localcart.repository.ProductCategoryRepository;
import com.localcart.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.text.Normalizer;
import java.util.*;

@Service
@Transactional
public class ProductCatalogImportService {

    private static final String BIGBASKET_SOURCE = "BigBasket authorised dataset";
    private static final String BIGBASKET_PERMISSION = "BIGBASKET-WRITTEN-PERMISSION";
    private static final Set<String> SUPPORTED_MRP_SOURCES = Set.of("pack", "brand", "gov");

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductCatalogueSourceRecordRepository sourceRecordRepository;
    private final ProductCatalogueQualityService catalogueQualityService;

    public ProductCatalogImportService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductCatalogueSourceRecordRepository sourceRecordRepository,
            ProductCatalogueQualityService catalogueQualityService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.catalogueQualityService = catalogueQualityService;
    }

    public ProductImportBatchResponse importBatch(ProductImportBatchRequest request) {
        validateSource(request);

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Product> seenInBatch = new HashMap<>();

        for (int index = 0; index < request.products().size(); index++) {
            ProductImportItem item = request.products().get(index);
            try {
                validateItem(item);
                String barcode = normalizeBarcode(item.barcode());
                String identityKey = identityKey(item, barcode);
                ProductCategory category = findCategory(item.category(), item.subcategory());

                String matchKey = barcode == null ? "identity:" + identityKey : "barcode:" + barcode;
                Product product = seenInBatch.get(matchKey);
                boolean created = false;

                if (product == null) {
                    product = barcode == null
                            ? productRepository.findByIdentityKey(identityKey).orElse(null)
                            : productRepository.findByBarcode(barcode).orElse(null);
                }

                if (product == null) {
                    product = new Product();
                    created = true;
                }

                boolean sameSource = request.sourceName().equals(product.getCatalogueSource());
                boolean changed = applyProductData(product, item, barcode, identityKey,
                        category, request, created || sameSource);

                String recordId = requiredText(item.sourceRecordId(), "sourceRecordId", 160);
                ProductCatalogueSourceRecord sourceRecord =
                        sourceRecordRepository.findBySourceNameAndSourceRecordId(
                                request.sourceName(), recordId).orElseGet(ProductCatalogueSourceRecord::new);

                if (sourceRecord.getProduct() != null
                        && !Objects.equals(sourceRecord.getProduct().getId(), product.getId())) {
                    throw new IllegalArgumentException("Source record is already linked to another product");
                }

                sourceRecord.setProduct(product);
                sourceRecord.setSourceName(request.sourceName().trim());
                sourceRecord.setSourceRecordId(recordId);
                sourceRecord.setSourceUrl(request.sourceUrl().trim());
                sourceRecord.setSourceLicense(request.sourceLicense().trim());
                sourceRecord.setAttribution(request.attribution().trim());
                sourceRecord.setReuseRightsVerified(request.reuseRightsVerified());
                sourceRecord.setCommercialUsePermitted(request.commercialUsePermitted());
                sourceRecord.setPermissionReference(request.permissionReference().trim());
                sourceRecord.setSourceModifiedAt(item.sourceModifiedAt());
                sourceRecord.setReportedMrp(item.mrp());
                sourceRecord.setReportedMrpSource(normalizeMrpSource(item.mrpSource()));
                sourceRecord.setImportedAt(Instant.now());

                if (changed || created) {
                    product = productRepository.save(product);
                }
                sourceRecordRepository.save(sourceRecord);
                seenInBatch.put(matchKey, product);

                if (created) {
                    inserted++;
                } else if (changed) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                errors.add("Row " + (index + 1) + ": " + exception.getMessage());
            }
        }

        return new ProductImportBatchResponse(
                request.products().size(), inserted, updated, unchanged, errors.size(),
                List.copyOf(errors.subList(0, Math.min(errors.size(), 100)))
        );
    }

    private void validateSource(ProductImportBatchRequest request) {
        if (request == null || request.products() == null || request.products().isEmpty()) {
            throw new BadRequestException("At least one product is required");
        }
        if (request.products().size() > 500) {
            throw new BadRequestException("Import batches cannot exceed 500 products");
        }
        URI uri;
        try {
            uri = URI.create(request.sourceUrl().trim());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Source URL must be a valid HTTPS URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String expectedPath = "/datasets/surajjha101/bigbasket-entire-product-list-28k-datapoints";
        boolean kaggleDataset = (host.equals("www.kaggle.com") || host.equals("kaggle.com"))
                && (path.equals(expectedPath) || path.equals(expectedPath + "/"));
        boolean officialBigBasketRoot = (host.equals("www.bigbasket.com") || host.equals("bigbasket.com"))
                && (path.isEmpty() || path.equals("/"));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getQuery() != null || uri.getFragment() != null
                || (!kaggleDataset && !officialBigBasketRoot)) {
            throw new BadRequestException("Source URL must be the approved BigBasket dataset page or official BigBasket source");
        }
        if (!BIGBASKET_SOURCE.equals(request.sourceName().trim())
                || !BIGBASKET_PERMISSION.equals(request.sourceLicense().trim())) {
            throw new BadRequestException("Only the permission-gated BigBasket dataset source is accepted");
        }
        if (!Boolean.TRUE.equals(request.reuseRightsVerified())
                || !Boolean.TRUE.equals(request.commercialUsePermitted())) {
            throw new BadRequestException("Commercial import blocked: reuse_rights_verified and commercial_use_permitted must both be true");
        }
        String permissionReference = requiredText(request.permissionReference(), "permissionReference", 500);
        String attribution = requiredText(request.attribution(), "attribution", 500);
        String normalizedAttribution = attribution.toLowerCase(Locale.ROOT);
        if (!normalizedAttribution.contains("commercial use: authorised")
                || !normalizedAttribution.contains("permission reference: "
                        + permissionReference.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Attribution must state authorised commercial use and include the exact written permission reference");
        }
    }

    private void validateItem(ProductImportItem item) {
        if (item == null) {
            throw new IllegalArgumentException("product row is empty");
        }
        if ((item.barcode() != null && !item.barcode().isBlank())
                || (item.packSize() != null && !item.packSize().isBlank())
                || item.netQuantity() != null
                || (item.netQuantityUnit() != null && !item.netQuantityUnit().isBlank())
                || item.mrp() != null
                || (item.mrpSource() != null && !item.mrpSource().isBlank())) {
            throw new IllegalArgumentException(
                    "BigBasket reference rows must not include unverified barcode, pack-size, quantity or price fields");
        }
        requiredText(item.name(), "name", 120);
        requiredText(item.category(), "category", 80);
        requiredText(item.subcategory(), "subcategory", 100);
        requiredText(item.sourceRecordId(), "sourceRecordId", 160);
        optionalText(item.brand(), "brand", 100);
        optionalText(item.packSize(), "packSize", 80);
        optionalText(item.netQuantityUnit(), "netQuantityUnit", 20);
        optionalText(item.variantGroupKey(), "variantGroupKey", 120);
        optionalText(item.description(), "description", 1000);

        if (item.netQuantity() != null
                && (item.netQuantity().signum() <= 0
                || item.netQuantity().compareTo(new BigDecimal("1000000")) > 0
                || item.netQuantity().scale() > 3)) {
            throw new IllegalArgumentException("netQuantity must be positive and have at most 3 decimal places");
        }
        if (item.netQuantity() != null && (item.netQuantityUnit() == null
                || !Set.of("g", "kg", "ml", "l", "pcs", "unit").contains(item.netQuantityUnit().trim().toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("netQuantityUnit must be g, kg, ml, l, pcs or unit");
        }
        validateMrp(item.mrp(), item.mrpSource());
    }

    private void validateMrp(BigDecimal mrp, String mrpSource) {
        if (mrp == null) {
            if (mrpSource != null && !mrpSource.isBlank()) {
                throw new IllegalArgumentException("mrpSource cannot be set without an MRP value");
            }
            return;
        }
        if (mrp.signum() <= 0 || mrp.compareTo(new BigDecimal("1000000")) > 0 || mrp.scale() > 2) {
            throw new IllegalArgumentException("MRP must be between ₹0.01 and ₹10,00,000 with at most 2 decimals");
        }
        normalizeMrpSource(mrpSource);
    }

    private String normalizeMrpSource(String value) {
        if (value == null || value.isBlank()) {
            if (value == null) return null;
            throw new IllegalArgumentException("MRP source is required when MRP is supplied");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_MRP_SOURCES.contains(normalized)) {
            throw new IllegalArgumentException("MRP source must be pack, brand or gov");
        }
        return normalized;
    }

    private ProductCategory findCategory(String categoryName, String subcategoryName) {
        String parentName = requiredText(categoryName, "category", 80);
        String childName = requiredText(subcategoryName, "subcategory", 100);
        ProductCategory parent = categoryRepository
                .findByNameIgnoreCaseAndParentIsNull(parentName)
                .orElseGet(() -> {
                    ProductCategory created = new ProductCategory(
                            parentName, categorySlug(parentName), null);
                    return categoryRepository.save(created);
                });
        return categoryRepository.findByNameIgnoreCaseAndParent_Id(
                        childName, parent.getId())
                .orElseGet(() -> categoryRepository.save(new ProductCategory(
                        childName, categorySlug(parent.getSlug() + "-" + childName), parent)));
    }

    private String categorySlug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.length() > 120 ? slug.substring(0, 120) : slug;
    }

    private boolean applyProductData(Product product,
                                     ProductImportItem item,
                                     String barcode,
                                     String identityKey,
                                     ProductCategory category,
                                     ProductImportBatchRequest request,
                                     boolean authoritative) {
        boolean changed = false;
        changed |= setIfAllowed(product.getName(), item.name().trim(),
                product::setName, authoritative || blank(product.getName()));
        changed |= setIfAllowed(product.getCategory(), category.getParent().getName(),
                product::setCategory, authoritative || blank(product.getCategory()));
        changed |= setIfAllowed(product.getSubcategory(), category.getName(),
                product::setSubcategory, authoritative || blank(product.getSubcategory()));
        changed |= setIfAllowed(product.getBrand(), clean(item.brand()),
                product::setBrand, authoritative || blank(product.getBrand()));
        changed |= setIfAllowed(product.getBarcode(), barcode,
                product::setBarcode, authoritative || blank(product.getBarcode()));
        changed |= setIfAllowed(product.getPackSize(), clean(item.packSize()),
                product::setPackSize, authoritative || blank(product.getPackSize()));
        changed |= setIfAllowed(product.getNetQuantity(), item.netQuantity(),
                product::setNetQuantity, authoritative || product.getNetQuantity() == null);
        changed |= setIfAllowed(product.getNetQuantityUnit(), clean(item.netQuantityUnit()),
                product::setNetQuantityUnit, authoritative || blank(product.getNetQuantityUnit()));
        changed |= setIfAllowed(product.getVariantGroupKey(), clean(item.variantGroupKey()),
                product::setVariantGroupKey, authoritative || blank(product.getVariantGroupKey()));
        changed |= setIfAllowed(product.getDescription(), clean(item.description()),
                product::setDescription, authoritative || blank(product.getDescription()));

        if (item.mrp() != null && (authoritative || product.getMrp() == null)) {
            String source = normalizeMrpSource(item.mrpSource());
            if (!Objects.equals(product.getMrp(), item.mrp())
                    || !Objects.equals(product.getMrpSource(), source)) {
                product.setMrp(item.mrp());
                product.setMrpSource(source);
                // Source modification time is tracked on the source record; it is not proof
                // that the printed MRP itself was independently verified.
                changed = true;
            }
        }

        if (authoritative || blank(product.getCatalogueSource())) {
            if (!Objects.equals(product.getCatalogueSource(), request.sourceName().trim())) {
                product.setCatalogueSource(request.sourceName().trim());
                changed = true;
            }
            if (!Objects.equals(product.getCatalogueSourceLicense(), request.sourceLicense().trim())) {
                product.setCatalogueSourceLicense(request.sourceLicense().trim());
                changed = true;
            }
        }
        if (!Objects.equals(product.getIdentityKey(), identityKey)) {
            product.setIdentityKey(identityKey);
            changed = true;
        }

        // Automatic checks apply to imported records until an administrator
        // explicitly reviews them. A later source refresh must not undo that decision.
        if (product.getCatalogueReviewedAt() == null
                && (authoritative || product.getCatalogueReviewStatus() == null)) {
            ProductCatalogueQualityService.Assessment assessment =
                    catalogueQualityService.assess(product);
            ProductCatalogueReviewStatus nextStatus = assessment.status();
            if (product.getCatalogueReviewStatus() != nextStatus) {
                product.setCatalogueReviewStatus(nextStatus);
                changed = true;
            }
            if (!Objects.equals(product.getCatalogueReviewReason(), assessment.reason())) {
                product.setCatalogueReviewReason(assessment.reason());
                changed = true;
            }
        }
        product.setCatalogueImportedAt(Instant.now());
        return changed;
    }

    private <T> boolean setIfAllowed(T current, T incoming,
                                     java.util.function.Consumer<T> setter,
                                     boolean allowed) {
        if (allowed && incoming != null && !Objects.equals(current, incoming)) {
            setter.accept(incoming);
            return true;
        }
        return false;
    }

    private String normalizeBarcode(String value) {
        if (value == null || value.isBlank()) return null;
        String barcode = value.trim().replaceAll("\\s+", "");
        if (!barcode.matches("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")) {
            throw new IllegalArgumentException("barcode must be an 8, 12, 13 or 14 digit GTIN");
        }
        int sum = 0;
        int weight = 3;
        for (int index = barcode.length() - 2; index >= 0; index--) {
            sum += Character.digit(barcode.charAt(index), 10) * weight;
            weight = weight == 3 ? 1 : 3;
        }
        int expected = (10 - (sum % 10)) % 10;
        if (expected != Character.digit(barcode.charAt(barcode.length() - 1), 10)) {
            throw new IllegalArgumentException("barcode has an invalid GTIN check digit");
        }
        return barcode;
    }

    private String identityKey(ProductImportItem item, String barcode) {
        String identity = barcode != null
                ? "gtin|" + barcode
                : String.join("|", normalized(item.brand()), normalized(item.name()),
                    normalized(item.packSize()), normalized(item.variantGroupKey()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create product identity", exception);
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void optionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
