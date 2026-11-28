package com.localcart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCatalogImportService.class);
    private static final String MENDELEY_SOURCE = "Mendeley Indian Packaged Foods Dataset (2026)";
    private static final String MENDELEY_LICENSE = "CC-BY-4.0";
    private static final String MENDELEY_URL = "https://data.mendeley.com/datasets/rhswn9z9xr/1";
    private static final String MENDELEY_DOI = "10.17632/rhswn9z9xr.1";
    private static final Set<String> SUPPORTED_MRP_SOURCES = Set.of("pack", "brand", "gov");

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductCatalogueSourceRecordRepository sourceRecordRepository;
    private final ProductCatalogueQualityService catalogueQualityService;
    private final ObjectMapper objectMapper;
    private final com.localcart.config.ApprovedCatalogueSourceProperties approvedSource;

    public ProductCatalogImportService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductCatalogueSourceRecordRepository sourceRecordRepository,
            ProductCatalogueQualityService catalogueQualityService,
            ObjectMapper objectMapper,
            com.localcart.config.ApprovedCatalogueSourceProperties approvedSource) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.sourceRecordRepository = sourceRecordRepository;
        this.catalogueQualityService = catalogueQualityService;
        this.objectMapper = objectMapper;
        this.approvedSource = approvedSource;
    }

    public ProductImportBatchResponse importBatch(ProductImportBatchRequest request) {
        validateSource(request);
        boolean mendeleySource = MENDELEY_SOURCE.equals(request.sourceName().trim());

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Product> seenInBatch = new HashMap<>();

        for (int index = 0; index < request.products().size(); index++) {
            ProductImportItem item = request.products().get(index);
            try {
                validateItem(item, mendeleySource);
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
                sourceRecord.setSourceMetadataJson(item.sourceMetadataJson());
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
                LOGGER.warn("Rejected catalogue row {}: {}", index + 1, exception.getMessage());
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

        String sourceName = requiredText(request.sourceName(), "sourceName", 80);
        String sourceUrl = requiredText(request.sourceUrl(), "sourceUrl", 500);
        String sourceLicense = requiredText(request.sourceLicense(), "sourceLicense", 80);
        String permissionReference = requiredText(request.permissionReference(), "permissionReference", 500);
        String attribution = requiredText(request.attribution(), "attribution", 500);
        String normalizedAttribution = attribution.toLowerCase(Locale.ROOT);

        if (MENDELEY_SOURCE.equals(sourceName)) {
            URI uri;
            try {
                uri = URI.create(sourceUrl);
            } catch (RuntimeException exception) {
                throw new BadRequestException("Source URL must be the approved Mendeley dataset URL");
            }
            boolean approvedMendeleyUrl = "https".equalsIgnoreCase(uri.getScheme())
                    && "data.mendeley.com".equalsIgnoreCase(uri.getHost())
                    && "/datasets/rhswn9z9xr/1".equals(uri.getPath())
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
            if (!approvedMendeleyUrl || !MENDELEY_URL.equals(sourceUrl)) {
                throw new BadRequestException("Only the approved Mendeley dataset version 1 URL is accepted");
            }
            if (!MENDELEY_LICENSE.equals(sourceLicense)) {
                throw new BadRequestException("Only the Mendeley Indian Packaged Foods dataset under CC-BY-4.0 is accepted");
            }
            if (!MENDELEY_DOI.equalsIgnoreCase(permissionReference)
                    || !normalizedAttribution.contains(MENDELEY_DOI)
                    || !normalizedAttribution.contains("cc by 4.0")
                    || !normalizedAttribution.contains("apniko modifications")) {
                throw new BadRequestException(
                        "Attribution must retain the dataset DOI, CC BY 4.0 and disclose Apniko modifications");
            }
        } else {
            URI uri;
            try {
                uri = URI.create(sourceUrl);
            } catch (RuntimeException exception) {
                throw new BadRequestException("Approved retail source URL must be a valid HTTPS URL");
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new BadRequestException("Approved retail source URL must use HTTPS without credentials, query or fragment");
            }
            if (approvedSource == null
                    || !approvedSource.matches(sourceName, sourceUrl, sourceLicense, permissionReference)) {
                throw new BadRequestException(
                        "Retail catalogue source is not configured as an owner-approved commercial source");
            }
            if (!normalizedAttribution.contains(sourceName.toLowerCase(Locale.ROOT))
                    || !normalizedAttribution.contains(sourceLicense.toLowerCase(Locale.ROOT))
                    || !normalizedAttribution.contains(permissionReference.toLowerCase(Locale.ROOT))
                    || !normalizedAttribution.contains("apniko modifications")) {
                throw new BadRequestException(
                        "Attribution must identify the approved source, licence, permission reference and Apniko modifications");
            }
        }

        if (!Boolean.TRUE.equals(request.reuseRightsVerified())
                || !Boolean.TRUE.equals(request.commercialUsePermitted())) {
            LOGGER.warn("Blocked catalogue import because reuse/commercial-use attestations were not confirmed");
            throw new org.springframework.security.access.AccessDeniedException(
                    "Import blocked: reuse_rights_verified and commercial_use_permitted must both be true");
        }
    }

    private void validateItem(ProductImportItem item, boolean mendeleySource) {
        if (item == null) {
            throw new IllegalArgumentException("product row is empty");
        }
        if (mendeleySource && ((item.barcode() != null && !item.barcode().isBlank())
                || (item.packSize() != null && !item.packSize().isBlank())
                || item.netQuantity() != null
                || (item.netQuantityUnit() != null && !item.netQuantityUnit().isBlank())
                || item.mrp() != null
                || (item.mrpSource() != null && !item.mrpSource().isBlank()))) {
            throw new IllegalArgumentException(
                    "Mendeley reference rows must not populate unverified barcode, retail pack-size, quantity or price fields");
        }
        if (!mendeleySource) {
            if (normalizeBarcode(item.barcode()) == null) {
                throw new IllegalArgumentException("Approved retail imports require a verified GTIN barcode");
            }
            requiredText(item.packSize(), "packSize", 80);
            if (item.netQuantity() == null || item.netQuantityUnit() == null || item.netQuantityUnit().isBlank()) {
                throw new IllegalArgumentException("Approved retail imports require netQuantity and netQuantityUnit");
            }
        }
        requiredText(item.name(), "name", 120);
        requiredText(item.category(), "category", 80);
        requiredText(item.subcategory(), "subcategory", 100);
        String sourceRecordId = requiredText(item.sourceRecordId(), "sourceRecordId", 160);
        String identityDiscriminator = requiredText(item.identityDiscriminator(), "identityDiscriminator", 160);
        if (!sourceRecordId.equals(identityDiscriminator)) {
            throw new IllegalArgumentException("identityDiscriminator must match the stable source record ID");
        }
        requiredText(item.sourceMetadataJson(), "sourceMetadataJson", 8000);
        optionalText(item.brand(), "brand", 100);
        optionalText(item.packSize(), "packSize", 80);
        optionalText(item.netQuantityUnit(), "netQuantityUnit", 20);
        optionalText(item.variantGroupKey(), "variantGroupKey", 120);
        optionalText(item.description(), "description", 1000);
        optionalText(item.sourceReviewReason(), "sourceReviewReason", 500);
        try {
            JsonNode metadata = objectMapper.readTree(item.sourceMetadataJson());
            if (metadata == null || !metadata.isObject()) {
                throw new IllegalArgumentException("sourceMetadataJson must be a JSON object");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("sourceMetadataJson must contain valid JSON");
        }

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

        // Mendeley is a nutrition/reference dataset, not a retail-SKU feed.
        // It contains no verified GTIN or retail pack size, so none of its rows
        // may become customer-visible until those facts are independently supplied.
        boolean mendeleyReferenceRow = MENDELEY_SOURCE.equals(request.sourceName().trim());
        boolean retailIdentityIncomplete = blank(product.getBarcode()) || blank(product.getPackSize());
        if (mendeleyReferenceRow && retailIdentityIncomplete) {
            String reason = "Reference dataset row only: verified retail GTIN and pack size are not supplied. "
                    + "Complete the retail identity from an approved source before publishing.";
            if (item.sourceReviewReason() != null && !item.sourceReviewReason().isBlank()) {
                reason += " Source quality review: " + item.sourceReviewReason().trim();
            }
            if (product.getCatalogueReviewStatus() != ProductCatalogueReviewStatus.NEEDS_REVIEW) {
                product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.NEEDS_REVIEW);
                changed = true;
            }
            if (!Objects.equals(product.getCatalogueReviewReason(), reason)) {
                product.setCatalogueReviewReason(reason);
                changed = true;
            }
        } else if (product.getCatalogueReviewedAt() == null
                && (authoritative || product.getCatalogueReviewStatus() == null)) {
            ProductCatalogueQualityService.Assessment assessment;
            if (!mendeleyReferenceRow) {
                String reason = "Approved retail source import is pending administrator review before customer publication.";
                if (item.sourceReviewReason() != null && !item.sourceReviewReason().isBlank()) {
                    reason += " Source quality review: " + item.sourceReviewReason().trim();
                }
                assessment = new ProductCatalogueQualityService.Assessment(
                        ProductCatalogueReviewStatus.NEEDS_REVIEW, reason);
            } else {
                assessment = item.sourceReviewReason() != null && !item.sourceReviewReason().isBlank()
                        ? new ProductCatalogueQualityService.Assessment(
                                ProductCatalogueReviewStatus.NEEDS_REVIEW,
                                item.sourceReviewReason().trim())
                        : catalogueQualityService.assess(product);
            }
            if (product.getCatalogueReviewStatus() != assessment.status()) {
                product.setCatalogueReviewStatus(assessment.status());
                changed = true;
            }
            if (!Objects.equals(product.getCatalogueReviewReason(), assessment.reason())) {
                product.setCatalogueReviewReason(assessment.reason());
                changed = true;
            }
        }
        // A source refresh that changes an already-reviewed product must send it
        // back to moderation. Do not let an imported edit silently inherit an
        // administrator's approval of the previous product data.
        if (!mendeleyReferenceRow && changed && product.getCatalogueReviewedAt() != null) {
            product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.NEEDS_REVIEW);
            product.setCatalogueReviewReason(
                    "Approved retail source data changed after the previous review; administrator re-review is required.");
            product.setCatalogueReviewedAt(null);
            product.setCatalogueReviewedBy(null);
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
                : String.join("|", "source-record", normalized(item.sourceRecordId()),
                    normalized(item.identityDiscriminator()));
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
