package com.localcart.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localcart.entity.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductResponseTest {

    @Test
    void productResponseContainsVerifiedTextFieldsOnly() throws JsonProcessingException {
        Product product = new Product();
        product.setName("Aashirvaad Atta");
        product.setBrand("Aashirvaad");
        product.setCategory("Foodgrains, Oil & Masala");
        product.setSubcategory("Atta & Flour");
        product.setBarcode("8901725113017");
        product.setPackSize("5 kg");

        ProductResponse response = ProductResponse.from(product);

        assertEquals("Aashirvaad Atta", response.getName());
        assertEquals("Aashirvaad", response.getBrand());
        assertEquals("Foodgrains, Oil & Masala", response.getCategory());
        assertEquals("Atta & Flour", response.getSubcategory());
        assertEquals("8901725113017", response.getBarcode());
        assertEquals("5 kg", response.getPackSize());

        String json = new ObjectMapper().writeValueAsString(response);
        assertFalse(json.contains("image"));
        assertFalse(json.contains("photo"));
        assertFalse(json.contains("ImageUrl"));
    }
}
