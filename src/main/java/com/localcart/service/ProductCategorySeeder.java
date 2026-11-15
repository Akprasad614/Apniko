package com.localcart.service;

import com.localcart.entity.ProductCategory;
import com.localcart.repository.ProductCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;

@Component
public class ProductCategorySeeder implements CommandLineRunner {

    private final ProductCategoryRepository repository;

    public ProductCategorySeeder(ProductCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<SeedCategory> categories = List.of(
                new SeedCategory("Fruits & Vegetables", List.of(
                        "Fresh Fruits", "Fresh Vegetables", "Leafy Greens", "Herbs & Seasonings")),
                new SeedCategory("Dairy, Bakery & Eggs", List.of(
                        "Milk & Dairy", "Curd, Paneer & Cheese", "Bread & Bakery", "Eggs")),
                new SeedCategory("Foodgrains, Oil & Masala", List.of(
                        "Rice & Rice Products", "Atta & Flours", "Pulses & Lentils",
                        "Edible Oils & Ghee", "Spices & Masalas", "Salt & Sugar")),
                new SeedCategory("Snacks & Branded Foods", List.of(
                        "Biscuits & Cookies", "Chips & Namkeen", "Chocolates & Confectionery",
                        "Instant Foods & Noodles", "Breakfast & Cereals")),
                new SeedCategory("Beverages", List.of(
                        "Water, Juices & Soft Drinks", "Tea, Coffee & Cocoa", "Health Drinks")),
                new SeedCategory("Cleaning & Household", List.of(
                        "Laundry & Detergents", "Dishwashing", "Home & Surface Cleaning",
                        "Paper & Disposable Products")),
                new SeedCategory("Beauty & Hygiene", List.of(
                        "Skin Care", "Hair Care", "Oral Care", "Bath & Body", "Personal Hygiene")),
                new SeedCategory("Baby Care", List.of(
                        "Diapers & Wipes", "Baby Food", "Baby Bath & Skin Care")),
                new SeedCategory("Gourmet & World Food", List.of(
                        "Sauces, Spreads & Condiments", "Baking Ingredients", "Imported & Specialty Foods")),
                new SeedCategory("Kitchen, Garden & Pets", List.of(
                        "Kitchen Essentials", "Garden Supplies", "Pet Food & Supplies")),
                new SeedCategory("Eggs, Meat & Fish", List.of(
                        "Poultry & Meat", "Fish & Seafood", "Ready-to-Cook")),
                new SeedCategory("Other Essentials", List.of(
                        "General Merchandise", "Unclassified Packaged Goods"))
        );

        for (SeedCategory seed : categories) {
            ProductCategory parent = repository.findBySlug(slug(seed.name()))
                    .orElseGet(() -> repository.save(new ProductCategory(
                            seed.name(), slug(seed.name()), null)));
            for (String childName : seed.children()) {
                String childSlug = slug(seed.name()) + "-" + slug(childName);
                repository.findBySlug(childSlug).orElseGet(() -> repository.save(
                        new ProductCategory(childName, childSlug, parent)));
            }
        }
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private record SeedCategory(String name, List<String> children) {
    }
}
