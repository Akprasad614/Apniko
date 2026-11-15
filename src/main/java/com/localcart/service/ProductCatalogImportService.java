package com.localcart.service;

import com.localcart.dto.ProductImportBatchRequest;
import com.localcart.dto.ProductImportBatchResponse;
import com.localcart.dto.ProductImportItem;
import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueSourceRecord;
import com.localcart.entity.ProductCategory;
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
import java.util.*;

@Service
@Transactional
public class ProductCatalogImportService {

    private static final Set<String> SUPPORTED_MRP_SOURCES =
            Set.of("pack", "brand", "gov", "off");
    private static final Set<String> SUPPORTED_LICENSES = Set.of(
            "ODbL-1.0", "DbCL-1.0", "CC0-1.0", "CC-BY-4.0",
            "CC-BY-SA-4.0", "CC-BY-SA-3.0", "Public-Domain"
    );

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductCatalogueSourceRecordRepository sourceRecordRepository;

    public ProductCatalogImportService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductCatalogueSourceRecordRepository sourceRecordRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.sourceRecordRepository = sourceRecordRepository;
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
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new BadRequestException("Source URL must use HTTPS");
        }
        if (!SUPPORTED_LICENSES.contains(request.sourceLicense().trim())) {
            throw new BadRequestException("Unsupported licence identifier; document the licence before importing");
        }
        requiredText(request.sourceName(), "sourceName", 80);
        requiredText(request.attribution(), "attribution", 500);
    }

    private void validateItem(ProductImportItem item) {
        if (item == null) {
            throw new IllegalArgumentException("product row is empty");
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
            throw new IllegalArgumentException("MRP source must be pack, brand, gov or off");
        }
        return normalized;
    }

    private ProductCategory findCategory(String categoryName, String subcategoryName) {
        ProductCategory parent = categoryRepository
                .findByNameIgnoreCaseAndParentIsNull(categoryName.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown category '" + categoryName + "'"));
        return categoryRepository.findByNameIgnoreCaseAndParent_Id(
                        subcategoryName.trim(), parent.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "subcategory '" + subcategoryName + "' does not belong to " + categoryName));
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
