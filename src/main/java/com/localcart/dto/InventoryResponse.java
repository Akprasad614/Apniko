package com.localcart.dto;

import java.math.BigDecimal;

public class InventoryResponse {

    private Long id;
    private Long shopId;
    private String shopName;
    private Long productId;
    private String productName;
    private String category;
    private String subcategory;
    private String brand;
    private String barcode;
    private String packSize;
    private BigDecimal mrp;
    private double price;
    private int stockQuantity;
    private boolean available;
    private String imageUrl;
    private String imageSourceName;
    private String imageSourceUrl;
    private String imageLicense;
    private String imageAttribution;
    private int lowStockThreshold;
    private boolean lowStock;

    public InventoryResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getPackSize() { return packSize; }
    public void setPackSize(String packSize) { this.packSize = packSize; }

    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String value) { this.imageUrl = value; }
    public String getImageSourceName() { return imageSourceName; }
    public void setImageSourceName(String value) { this.imageSourceName = value; }
    public String getImageSourceUrl() { return imageSourceUrl; }
    public void setImageSourceUrl(String value) { this.imageSourceUrl = value; }
    public String getImageLicense() { return imageLicense; }
    public void setImageLicense(String value) { this.imageLicense = value; }
    public String getImageAttribution() { return imageAttribution; }
    public void setImageAttribution(String value) { this.imageAttribution = value; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isLowStock() { return lowStock; }
    public void setLowStock(boolean lowStock) { this.lowStock = lowStock; }
}
