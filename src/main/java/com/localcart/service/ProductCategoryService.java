package com.localcart.service;

import com.localcart.dto.CreateProductCategoryRequest;
import com.localcart.dto.ProductCategoryResponse;
import com.localcart.entity.ProductCategory;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;

@Service
@Transactional
public class ProductCategoryService {

    private final ProductCategoryRepository repository;

    public ProductCategoryService(ProductCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getCategoryTree() {
        return repository.findByParentIsNullAndActiveTrueOrderByNameAsc()
                .stream()
                .map(parent -> new ProductCategoryResponse(
                        parent.getId(),
                        parent.getName(),
                        parent.getSlug(),
                        repository.findByParent_IdAndActiveTrueOrderByNameAsc(parent.getId())
                                .stream()
                                .map(child -> new ProductCategoryResponse(
                                        child.getId(), child.getName(), child.getSlug(), List.of()))
                                .toList()
                ))
                .toList();
    }

    public ProductCategoryResponse createCategory(CreateProductCategoryRequest request) {
        String name = request.name().trim().replaceAll("\\s+", " ");
        if (name.length() > 100) {
            throw new BadRequestException("Category name cannot exceed 100 characters");
        }

        ProductCategory parent = null;
        String slug = slug(name);
        if (request.parentCategoryId() != null) {
            parent = repository.findById(request.parentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
            if (parent.getParent() != null) {
                throw new BadRequestException("Only one subcategory level is supported");
            }
            slug = parent.getSlug() + "-" + slug;
        }

        if (repository.existsBySlug(slug)) {
            throw new BadRequestException("A category with this name already exists");
        }

        ProductCategory saved = repository.save(new ProductCategory(name, slug, parent));
        return new ProductCategoryResponse(saved.getId(), saved.getName(), saved.getSlug(), List.of());
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
