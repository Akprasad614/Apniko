package com.localcart.service;

import com.localcart.dto.ProductPhotoCandidateRequest;
import com.localcart.dto.ProductPhotoImportTargetResponse;
import com.localcart.dto.ProductPhotoReviewRequest;
import com.localcart.dto.ProductPhotoReviewResponse;
import com.localcart.dto.ProductPhotoRolloutReportResponse;
import com.localcart.dto.ProductPhotoCandidateRetirementResponse;
import com.localcart.entity.Product;
import com.localcart.entity.ProductImageReviewStatus;
import com.localcart.entity.ProductPhoto;
import com.localcart.entity.ProductPhotoReviewHistory;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ConflictException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductPhotoRepository;
import com.localcart.repository.ProductPhotoReviewHistoryRepository;
import com.localcart.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class ProductPhotoService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SOURCES = Map.of(
            "world.openfoodfacts.org", "Open Food Facts",
            "world.openbeautyfacts.org", "Open Beauty Facts",
            "world.openpetfoodfacts.org", "Open Pet Food Facts",
            "world.openproductsfacts.org", "Open Products Facts"
    );

    private static final String AUTHORIZED_BRAND_SOURCE = "Brand / supplier authorised catalogue";

    private static final Pattern PACK_SIZE_PATTERN = Pattern.compile(
            "(?:(?<count>\\d+(?:[.,]\\d+)?)\\s*[x×]\\s*)?"
                    + "(?<amount>\\d+(?:[.,]\\d+)?)\\s*"
                    + "(?<unit>fluid\\s*ounces?|fl\\.?\\s*oz\\.?|milligrams?|mg|micrograms?|mcg|"
                    + "kilograms?|kg|grams?|gm|g|milliliters?|millilitres?|ml|liters?|litres?|l|"
                    + "pieces?|pcs|counts?|ct)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private record NormalizedPackSize(String dimension, BigDecimal amount) {}

    @Value("${APNIKO_CATALOGUE_PHOTO_AUTHORIZED_IMAGE_HOSTS:${apniko.catalogue.photo-authorized-image-hosts:}}")
    private String authorizedImageHosts = "";

    private final ProductRepository productRepository;
    private final ProductPhotoRepository photoRepository;
    private final ProductPhotoReviewHistoryRepository historyRepository;

    public ProductPhotoService(
            ProductRepository productRepository,
            ProductPhotoRepository photoRepository,
            ProductPhotoReviewHistoryRepository historyRepository) {
        this.productRepository = productRepository;
        this.photoRepository = photoRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductPhotoReviewResponse> getReviewQueue(int page, int size) {
        validatePage(page, size);
        return photoRepository.findByReviewStatus(
                ProductImageReviewStatus.PENDING,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductPhotoImportTargetResponse> getImportTargets(int page, int size) {
        validatePage(page, size);
        return productRepository.findProductsMissingApprovedPhoto(
                ProductImageReviewStatus.REJECTED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(product -> new ProductPhotoImportTargetResponse(
                        product.getId(), product.getName(), product.getBrand(),
                        product.getBarcode(), product.getPackSize()));
    }

    @Transactional(readOnly = true)
    public ProductPhotoRolloutReportResponse getRolloutReport() {
        long totalProducts = productRepository.count();
        long productsWithBarcode = productRepository.countProductsWithBarcode();
        long approvedPhotos = photoRepository.countByReviewStatusAndApprovedImageUrlIsNotNull(
                ProductImageReviewStatus.APPROVED);
        long pending = photoRepository.countByReviewStatus(ProductImageReviewStatus.PENDING);
        long rejected = photoRepository.countByReviewStatus(ProductImageReviewStatus.REJECTED);
        long openFacts = photoRepository.countApprovedOpenFactsPhotos(ProductImageReviewStatus.APPROVED);
        long supplierAuthorized = photoRepository.countByReviewStatusAndLicense(
                ProductImageReviewStatus.APPROVED, "MANUFACTURER_AUTHORIZED");
        long eligibleWithBarcode = productRepository.countProductsWithBarcodeMissingApprovedPhoto(
                ProductImageReviewStatus.REJECTED);
        return new ProductPhotoRolloutReportResponse(
                totalProducts,
                productsWithBarcode,
                Math.max(0, totalProducts - approvedPhotos),
                approvedPhotos,
                pending,
                rejected,
                openFacts,
                supplierAuthorized,
                eligibleWithBarcode,
                historyRepository.countByOutcome("RETIRED")
        );
    }

    public ProductPhotoCandidateRetirementResponse retirePendingCandidates(
            String reason, String adminEmail, boolean confirmation) {
        if (!confirmation) {
            throw new BadRequestException("Explicit confirmation is required before retiring pending photo candidates");
        }
        String normalizedReason = requiredText(reason, 500, "Retirement reason");
        String reviewer = adminEmail == null || adminEmail.isBlank() ? "admin" : adminEmail.trim();
        int retired = 0;
        int preservedApproved = 0;
        Instant now = Instant.now();

        // Always process page zero: each batch is moved out of PENDING before the next fetch.
        while (true) {
            Page<ProductPhoto> batch = photoRepository.findByReviewStatus(
                    ProductImageReviewStatus.PENDING,
                    PageRequest.of(0, MAX_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            if (batch.isEmpty()) break;

            for (ProductPhoto photo : batch.getContent()) {
                historyRepository.save(ProductPhotoReviewHistory.from(
                        photo, "RETIRED", normalizedReason, reviewer, now));
                boolean hasApprovedPhoto = photo.getApprovedImageUrl() != null
                        && !photo.getApprovedImageUrl().isBlank();
                photo.setReviewStatus(hasApprovedPhoto
                        ? ProductImageReviewStatus.APPROVED
                        : ProductImageReviewStatus.REJECTED);
                photo.setReviewReason(normalizedReason);
                photo.setReviewedAt(now);
                photo.setReviewedBy(reviewer);
                photo.clearCandidate();
                if (hasApprovedPhoto) preservedApproved++;
                retired++;
            }
            photoRepository.saveAll(batch.getContent());
        }

        return new ProductPhotoCandidateRetirementResponse(
                retired,
                preservedApproved,
                "Pending candidates retired with an audit record. Products without an approved image are eligible for a fresh lookup.");
    }


    public ProductPhotoReviewResponse submitCandidate(Long productId, ProductPhotoCandidateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        String barcode = product.getBarcode();
        if (barcode == null || barcode.isBlank()) {
            throw new BadRequestException("A verified GTIN barcode is required before looking up a product photo");
        }
        if (!isValidGtin(barcode)) {
            throw new BadRequestException("The Apniko catalogue barcode must be a valid 8, 12, 13 or 14 digit GTIN");
        }
        if (!barcode.equals(request.matchedBarcode().trim())) {
            throw new BadRequestException("The source barcode does not exactly match this catalogue product");
        }

        URI sourceUri = parseHttpsUri(request.sourceUrl(), "Source page");
        String requestedSource = request.sourceName().trim();
        String sourceHost = sourceUri.getHost().toLowerCase(Locale.ROOT);
        String sourceName = SOURCES.get(sourceHost);
        URI imageUri = parseHttpsUri(request.imageUrl(), "Image");
        String license = request.license().trim()
                .replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");

        if (sourceUri.getQuery() != null || sourceUri.getFragment() != null) {
            throw new BadRequestException("Source product URL must not contain a query string or fragment");
        }

        if (AUTHORIZED_BRAND_SOURCE.equals(requestedSource)) {
            if (!"MANUFACTURER AUTHORIZED".equalsIgnoreCase(license)) {
                throw new BadRequestException("Brand/supplier photos require the MANUFACTURER_AUTHORIZED permission record");
            }
            requiredText(request.authorizationReference(), 500, "Manufacturer/supplier permission reference");
            String sourceBrand = requiredText(request.sourceProductBrand(), 100, "Source product brand");
            String sourcePack = requiredText(request.sourcePackSize(), 80, "Source pack size");
            if (!sameBrand(sourceBrand, product.getBrand())
                    || !samePackSize(sourcePack, product.getPackSize())) {
                throw new BadRequestException("Supplier brand and pack size must exactly match the Apniko catalogue product");
            }
            if (!isAuthorizedImageHost(imageUri.getHost(), sourceHost)
                    || imageUri.getQuery() != null || imageUri.getFragment() != null) {
                throw new BadRequestException("Brand/supplier image must use the product source domain or a configured authorised image host");
            }
            sourceName = AUTHORIZED_BRAND_SOURCE;
        } else {
            if (sourceName == null || !sourceName.equals(requestedSource)
                    || !("/product/" + barcode).equals(sourceUri.getPath())
                    || sourceUri.getQuery() != null || sourceUri.getFragment() != null) {
                throw new BadRequestException("Use the canonical Open Facts product page for the exact barcode");
            }
            String sourceBrand = requiredText(request.sourceProductBrand(), 100, "Source product brand");
            String sourcePack = requiredText(request.sourcePackSize(), 80, "Source pack size");
            if (!sameBrand(sourceBrand, product.getBrand())
                    || !samePackSize(sourcePack, product.getPackSize())) {
                throw new BadRequestException("Open Facts brand and pack size must exactly match the Apniko catalogue product");
            }
            String imageHost = "images." + sourceHost.substring("world.".length());
            String imageDigits = imageUri.getPath().replaceAll("[^0-9]", "");
            if (!imageHost.equalsIgnoreCase(imageUri.getHost())
                    || !imageUri.getPath().contains("/images/products/")
                    || !imageDigits.startsWith(barcode)
                    || imageUri.getQuery() != null || imageUri.getFragment() != null) {
                throw new BadRequestException("Image must be hosted by the matching Open Facts image server and barcode");
            }
            if (!"CC BY SA".equalsIgnoreCase(license)) {
                throw new BadRequestException("Open Facts product images must use the CC BY-SA licence");
            }
        }

        if (historyRepository.existsByProductIdAndCandidateImageUrlAndOutcomeIn(
                productId, imageUri.toString(), List.of("REJECTED", "RETIRED"))) {
            throw new ConflictException(
                    "This exact image URL was previously rejected or retired for this product. Submit a different source image.");
        }

        ProductPhoto photo = photoRepository.findByProductId(productId).orElseGet(ProductPhoto::new);
        if (photo.getReviewStatus() == ProductImageReviewStatus.PENDING) {
            throw new BadRequestException("This product already has a photograph waiting for review");
        }
        photo.setProduct(product);
        product.setPhoto(photo);
        photo.setCandidateImageUrl(imageUri.toString());
        photo.setCandidateSourceName(sourceName);
        photo.setCandidateSourceUrl(sourceUri.toString());
        photo.setCandidateLicense(license.equalsIgnoreCase("MANUFACTURER AUTHORIZED")
                ? "MANUFACTURER_AUTHORIZED" : "CC BY-SA");
        photo.setCandidateAttribution(requiredText(request.attribution(), 500, "Attribution"));
        photo.setCandidateProductName(requiredText(request.sourceProductName(), 200, "Source product name"));
        photo.setCandidateProductBrand(optionalText(request.sourceProductBrand(), 100, "Source product brand"));
        photo.setCandidatePackSize(optionalText(request.sourcePackSize(), 80, "Source pack size"));
        photo.setCandidateAuthorizationReference(optionalText(request.authorizationReference(), 500, "Permission reference"));
        photo.setCandidateBarcode(barcode);
        photo.setReviewStatus(ProductImageReviewStatus.PENDING);
        photo.setSubmittedAt(Instant.now());
        photo.setReviewedAt(null);
        photo.setReviewedBy(null);
        photo.setReviewReason(null);
        return toResponse(photoRepository.save(photo));
    }

    public ProductPhotoReviewResponse reviewCandidate(
            Long productId, ProductPhotoReviewRequest request, String adminEmail) {
        if (request == null || request.status() == null
                || request.status() == ProductImageReviewStatus.PENDING) {
            throw new BadRequestException("Choose APPROVED or REJECTED for the photo review");
        }
        if (request.status() == ProductImageReviewStatus.APPROVED
                && !request.verificationConfirmed()) {
            throw new BadRequestException(
                    "Confirm the exact product match and image-use permission before approving this photograph");
        }
        ProductPhoto photo = photoRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo candidate not found"));
        if (photo.getReviewStatus() != ProductImageReviewStatus.PENDING
                || photo.getCandidateImageUrl() == null) {
            throw new BadRequestException("This product has no pending photograph");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (request.status() == ProductImageReviewStatus.REJECTED && reason.isBlank()) {
            throw new BadRequestException("A reason is required when rejecting a product photo");
        }

        historyRepository.save(ProductPhotoReviewHistory.from(
                photo,
                request.status().name(),
                reason,
                adminEmail == null || adminEmail.isBlank() ? "admin" : adminEmail.trim(),
                Instant.now()));

        if (request.status() == ProductImageReviewStatus.APPROVED) {
            photo.setApprovedImageUrl(photo.getCandidateImageUrl());
            photo.setSourceName(photo.getCandidateSourceName());
            photo.setSourceUrl(photo.getCandidateSourceUrl());
            photo.setLicense(photo.getCandidateLicense());
            photo.setAttribution(photo.getCandidateAttribution());
            photo.setAuthorizationReference(photo.getCandidateAuthorizationReference());
            photo.setReviewStatus(ProductImageReviewStatus.APPROVED);
            photo.setReviewReason(null);
        } else {
            // Keep the last approved image intact when a replacement candidate is rejected.
            photo.setReviewStatus(photo.getApprovedImageUrl() == null
                    ? ProductImageReviewStatus.REJECTED : ProductImageReviewStatus.APPROVED);
            photo.setReviewReason(reason);
        }
        photo.clearCandidate();
        photo.setReviewedAt(Instant.now());
        photo.setReviewedBy(adminEmail == null || adminEmail.isBlank() ? "admin" : adminEmail.trim());
        return toResponse(photoRepository.save(photo));
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new BadRequestException("Page number cannot be negative");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private URI parseHttpsUri(String value, String label) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException exception) {
            throw new BadRequestException(label + " URL must be a valid HTTPS URL");
        }
    }

    private String requiredText(String value, int maxLength, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank() || text.length() > maxLength) {
            throw new BadRequestException(label + " is required and must not exceed " + maxLength + " characters");
        }
        return text;
    }

    private String optionalText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BadRequestException(label + " must not exceed " + maxLength + " characters");
        }
        return text;
    }

    private boolean isValidGtin(String value) {
        if (value == null || !value.matches("(?:\\d{8}|\\d{12}|\\d{13}|\\d{14})")) return false;
        int total = 0;
        int weight = 3;
        for (int index = value.length() - 2; index >= 0; index--) {
            total += Character.digit(value.charAt(index), 10) * weight;
            weight = weight == 3 ? 1 : 3;
        }
        return (10 - total % 10) % 10 == Character.digit(value.charAt(value.length() - 1), 10);
    }

    private boolean sameBrand(String left, String right) {
        if (left == null || right == null) return false;
        String normalizedLeft = normalizeBrand(left);
        String normalizedRight = normalizeBrand(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeBrand(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private boolean samePackSize(String left, String right) {
        NormalizedPackSize normalizedLeft = normalizePackSize(left);
        NormalizedPackSize normalizedRight = normalizePackSize(right);
        if (normalizedLeft != null || normalizedRight != null) {
            return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
        }
        if (left == null || right == null) return false;
        return left.trim().replaceAll("\\s+", " ")
                .equalsIgnoreCase(right.trim().replaceAll("\\s+", " "));
    }

    private NormalizedPackSize normalizePackSize(String value) {
        if (value == null) return null;
        Matcher matcher = PACK_SIZE_PATTERN.matcher(value);
        if (!matcher.find()) return null;

        BigDecimal amount = new BigDecimal(matcher.group("amount").replace(',', '.'));
        if (matcher.group("count") != null) {
            amount = amount.multiply(new BigDecimal(matcher.group("count").replace(',', '.')));
        }

        String unit = matcher.group("unit").toLowerCase(Locale.ROOT);
        String dimension;
        BigDecimal factor;
        boolean usFluidOunce = unit.matches("fl\\.?\\s*oz\\.?") || unit.startsWith("fluid");
        if (unit.equals("mg") || unit.startsWith("milligram")) {
            dimension = "mass";
            factor = new BigDecimal("0.001");
        } else if (unit.equals("mcg") || unit.startsWith("microgram")) {
            dimension = "mass";
            factor = new BigDecimal("0.000001");
        } else if (unit.equals("g") || unit.equals("gm") || unit.startsWith("gram")) {
            dimension = "mass";
            factor = BigDecimal.ONE;
        } else if (unit.equals("kg") || unit.startsWith("kilogram")) {
            dimension = "mass";
            factor = new BigDecimal("1000");
        } else if (usFluidOunce) {
            dimension = "volume";
            factor = new BigDecimal("29.5735295625");
        } else if (unit.equals("ml") || unit.startsWith("milliliter") || unit.startsWith("millilitre")) {
            dimension = "volume";
            factor = BigDecimal.ONE;
        } else if (unit.equals("l") || unit.startsWith("liter") || unit.startsWith("litre")) {
            dimension = "volume";
            factor = new BigDecimal("1000");
        } else if (unit.equals("pcs") || unit.equals("piece") || unit.equals("pieces")
                || unit.equals("count") || unit.equals("counts") || unit.equals("ct")) {
            dimension = "count";
            factor = BigDecimal.ONE;
        } else {
            return null;
        }

        BigDecimal normalizedAmount = amount.multiply(factor);
        if (usFluidOunce) {
            // Keep three decimal places so 16 US fl oz equals its 473.176 ml metric equivalent.
            normalizedAmount = normalizedAmount.setScale(3, RoundingMode.HALF_UP);
        }
        return new NormalizedPackSize(dimension, normalizedAmount.stripTrailingZeros());
    }

    private boolean isAuthorizedImageHost(String imageHost, String sourceHost) {
        String image = imageHost == null ? "" : imageHost.toLowerCase(Locale.ROOT);
        String source = sourceHost == null ? "" : sourceHost.toLowerCase(Locale.ROOT);
        if (image.equals(source) || image.endsWith("." + source)) return true;
        if (authorizedImageHosts == null || authorizedImageHosts.isBlank()) return false;
        for (String allowed : authorizedImageHosts.split(",")) {
            if (image.equals(allowed.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private ProductPhotoReviewResponse toResponse(ProductPhoto photo) {
        Product product = photo.getProduct();
        return new ProductPhotoReviewResponse(
                product.getId(), product.getName(), product.getBrand(), product.getBarcode(),
                product.getPackSize(), photo.getCandidateProductName(), photo.getCandidateProductBrand(),
                photo.getCandidatePackSize(), photo.getCandidateAuthorizationReference(),
                photo.getAuthorizationReference(), photo.getCandidateImageUrl(), photo.getCandidateSourceName(), photo.getCandidateSourceUrl(),
                photo.getCandidateLicense(),
                photo.getCandidateAttribution(), photo.getApprovedImageUrl(), photo.getReviewStatus(),
                photo.getSubmittedAt(), photo.getReviewedAt(), photo.getReviewedBy(), photo.getReviewReason());
    }
}
