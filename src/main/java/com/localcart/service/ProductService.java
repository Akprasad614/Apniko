package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.entity.ProductCatalogueReviewStatus;
import com.localcart.dto.ProductResponse;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new BadRequestException("Product name is required");
        }
        product.setName(product.getName().trim());
        if (product.getCategory() != null) {
            product.setCategory(product.getCategory().trim());
        }
        if (product.getBrand() != null) {
            product.setBrand(product.getBrand().trim());
        }
        if (product.getSubcategory() != null) {
            product.setSubcategory(product.getSubcategory().trim());
        }
        if (product.getPackSize() != null) {
            product.setPackSize(product.getPackSize().trim());
        }
        if (product.getDescription() != null) {
            product.setDescription(product.getDescription().trim());
        }

        if (product.getMrp() == null) {
            if (product.getMrpSource() != null && !product.getMrpSource().isBlank()) {
                throw new BadRequestException("MRP source cannot be set without an MRP value");
            }
            product.setMrpSource(null);
        } else {
            if (product.getMrp().signum() <= 0
                    || product.getMrp().compareTo(new java.math.BigDecimal("1000000")) > 0
                    || product.getMrp().scale() > 2) {
                throw new BadRequestException("MRP must be between ₹0.01 and ₹10,00,000 with at most 2 decimals");
            }
            String mrpSource = product.getMrpSource() == null
                    ? "" : product.getMrpSource().trim().toLowerCase(Locale.ROOT);
            if (!java.util.Set.of("pack", "brand", "gov", "off").contains(mrpSource)) {
                throw new BadRequestException("MRP source must be pack, brand, gov or off");
            }
            product.setMrpSource(mrpSource);
        }

        if (product.getNetQuantity() != null
                && (product.getNetQuantity().signum() <= 0
                || product.getNetQuantity().compareTo(new java.math.BigDecimal("1000000")) > 0
                || product.getNetQuantity().scale() > 3)) {
            throw new BadRequestException("Net quantity must be positive and have at most 3 decimal places");
        }
        if (product.getNetQuantity() != null
                && (product.getNetQuantityUnit() == null
                || !java.util.Set.of("g", "kg", "ml", "l", "pcs", "unit")
                        .contains(product.getNetQuantityUnit().trim().toLowerCase(Locale.ROOT)))) {
            throw new BadRequestException("Net quantity unit must be g, kg, ml, l, pcs or unit");
        }

        String barcode = product.getBarcode();
        if (barcode != null && !barcode.isBlank()) {
            barcode = barcode.trim();
            if (!barcode.matches("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")
                    || !validCheckDigit(barcode)) {
                throw new BadRequestException("Barcode must be a valid GTIN-8, 12, 13 or 14");
            }
            if (productRepository.findByBarcode(barcode).isPresent()) {
                throw new BadRequestException("A product with this barcode already exists");
            }
            product.setBarcode(barcode);
        } else {
            product.setBarcode(null);
        }

        String key = identityKey(product);
        if (productRepository.findByIdentityKey(key).isPresent()) {
            throw new BadRequestException("A product with the same brand, name and pack size already exists");
        }
        product.setIdentityKey(key);
        if (product.getCatalogueReviewStatus() == null) {
            product.setCatalogueReviewStatus(ProductCatalogueReviewStatus.ACTIVE);
        }
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getCatalogueProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> getCatalogueProductById(Long id) {
        return productRepository.findById(id).map(ProductResponse::from);
    }

    private String identityKey(Product product) {
        String value = product.getBarcode() == null
                ? String.join("|", normalize(product.getBrand()), normalize(product.getName()),
                    normalize(product.getPackSize()), normalize(product.getVariantGroupKey()))
                : "gtin|" + product.getBarcode();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create product identity", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean validCheckDigit(String code) {
        int sum = 0;
        int weight = 3;
        for (int index = code.length() - 2; index >= 0; index--) {
            sum += Character.digit(code.charAt(index), 10) * weight;
            weight = weight == 3 ? 1 : 3;
        }
        return (10 - sum % 10) % 10 == Character.digit(code.charAt(code.length() - 1), 10);
    }
}
