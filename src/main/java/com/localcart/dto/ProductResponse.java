package com.localcart.dto;

import com.localcart.entity.Product;
import com.localcart.entity.ProductImageReviewStatus;

import java.math.BigDecimal;

/**
 * Safe catalogue projection for shopkeeper product selection.
 * Only approved photo URLs are returned; pending candidates stay private.
 */
public class ProductResponse {

    private Long id;
    private String name;
    private String category;
    private String subcategory;
    private String brand;
    private String barcode;
    private String packSize;
    private BigDecimal mrp;
    private String imageUrl;
    private String imageSourceName;

    public ProductResponse() {
    }

    public ProductResponse(Long id, String name, String category) {
        this.id = id;
        this.name = name;
        this.category = category;
    }

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.category = product.getCategory();
        response.subcategory = product.getSubcategory();
        response.brand = product.getBrand();
        response.barcode = product.getBarcode();
        response.packSize = product.getPackSize();
        response.mrp = product.getMrp();

        var photo = product.getPhoto();
        if (photo != null
                && photo.getReviewStatus() == ProductImageReviewStatus.APPROVED
                && photo.getApprovedImageUrl() != null) {
            response.imageUrl = photo.getApprovedImageUrl();
            response.imageSourceName = photo.getSourceName();
        }
        return response;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public String getBrand() { return brand; }
    public String getBarcode() { return barcode; }
    public String getPackSize() { return packSize; }
    public BigDecimal getMrp() { return mrp; }
    public String getImageUrl() { return imageUrl; }
    public String getImageSourceName() { return imageSourceName; }
}
