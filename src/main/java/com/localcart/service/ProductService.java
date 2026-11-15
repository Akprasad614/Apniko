package com.localcart.service;

import com.localcart.entity.Product;
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
