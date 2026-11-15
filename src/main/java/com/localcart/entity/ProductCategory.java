package com.localcart.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "product_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_categories_slug", columnNames = "slug")
})
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    @Column(nullable = false)
    private boolean active = true;

    public ProductCategory() {
    }

    public ProductCategory(String name, String slug, ProductCategory parent) {
        this.name = name;
        this.slug = slug;
        this.parent = parent;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public ProductCategory getParent() { return parent; }
    public void setParent(ProductCategory parent) { this.parent = parent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
