package com.localcart.service;

import com.localcart.entity.Product;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(Product product) {
        product.setImageUrl(normalizeImageUrl(product.getImageUrl()));
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product updateProductImage(Long productId, String imageUrl) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        product.setImageUrl(normalizeImageUrl(imageUrl));
        return productRepository.save(product);
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String normalized = imageUrl.trim();
        if (normalized.length() > 2048) {
            throw new BadRequestException("Product image URL cannot exceed 2048 characters");
        }

        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new BadRequestException("Product image URL must be a valid HTTPS URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Product image URL must be a valid HTTPS URL");
        }

        return normalized;
    }
}
