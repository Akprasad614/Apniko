package com.localcart.dto;

public class CartItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String category;
    private String imageUrl;
    private String imageSourceName;
    private String imageSourceUrl;
    private String imageLicense;
    private String imageAttribution;
    private double price;
    private int quantity;
    private double lineTotal;
    private int availableStock;
    private boolean available;

    public CartItemResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(double lineTotal) {
        this.lineTotal = lineTotal;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}