package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.exception.BadRequestException;
import com.localcart.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(Product product) {
        if (product == null) {
            throw new BadRequestException("Product details are required");
        }

        product.setImageUrl(normalizeImageUrl(product.getImageUrl()));
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String normalized = imageUrl.trim();
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.toLowerCase(Locale.ROOT).equals("https")
                    || scheme.toLowerCase(Locale.ROOT).equals("http"))) {
                throw new IllegalArgumentException("Unsupported image URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Product image URL must be a valid HTTP or HTTPS URL");
        }

        return normalized;
    }
}
