package com.localcart.repository;

/** Projection used by analytics queries to aggregate product sales. */
public interface TopProductSalesProjection {

    Long getShopId();

    String getShopName();

    Long getProductId();

    String getProductName();

    Long getUnitsSold();

    Double getOrderValue();
}
