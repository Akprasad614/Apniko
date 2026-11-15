package com.localcart.service;

import com.localcart.dto.CreateProductCategoryRequest;
import com.localcart.dto.ProductCategoryResponse;
import com.localcart.entity.ProductCategory;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.ProductCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

    @Mock private ProductCategoryRepository repository;
    @InjectMocks private ProductCategoryService service;

    @Test
    void returnsTreeWithSubcategories() {
        ProductCategory root = new ProductCategory("Beverages", "beverages", null);
        ReflectionTestUtils.setField(root, "id", 1L);
        ProductCategory child = new ProductCategory("Tea & Coffee", "beverages-tea-coffee", root);
        ReflectionTestUtils.setField(child, "id", 2L);

        when(repository.findByParentIsNullAndActiveTrueOrderByNameAsc())
                .thenReturn(List.of(root));
        when(repository.findByParent_IdAndActiveTrueOrderByNameAsc(1L))
                .thenReturn(List.of(child));

        List<ProductCategoryResponse> result = service.getCategoryTree();

        assertEquals(1, result.size());
        assertEquals("Beverages", result.get(0).name());
        assertEquals(1, result.get(0).subcategories().size());
        assertEquals("Tea & Coffee", result.get(0).subcategories().get(0).name());
    }

    @Test
    void createsUniqueSubcategoryUnderRoot() {
        ProductCategory root = new ProductCategory("Beverages", "beverages", null);
        ReflectionTestUtils.setField(root, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(root));
        when(repository.existsBySlug("beverages-tea-coffee")).thenReturn(false);
        when(repository.save(any(ProductCategory.class))).thenAnswer(invocation -> {
            ProductCategory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        ProductCategoryResponse response = service.createCategory(
                new CreateProductCategoryRequest("Tea & Coffee", 1L));

        assertEquals("Tea & Coffee", response.name());
        assertEquals("beverages-tea-coffee", response.slug());
    }

    @Test
    void rejectsThirdLevelCategory() {
        ProductCategory root = new ProductCategory("Beverages", "beverages", null);
        ReflectionTestUtils.setField(root, "id", 1L);
        ProductCategory child = new ProductCategory("Tea", "beverages-tea", root);
        ReflectionTestUtils.setField(child, "id", 2L);
        when(repository.findById(2L)).thenReturn(Optional.of(child));

        assertThrows(BadRequestException.class, () -> service.createCategory(
                new CreateProductCategoryRequest("Green Tea", 2L)));
    }

    @Test
    void rejectsMissingParent() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createCategory(
                new CreateProductCategoryRequest("Tea", 99L)));
    }
}
