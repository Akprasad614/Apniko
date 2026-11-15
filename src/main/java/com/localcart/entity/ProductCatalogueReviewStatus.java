package com.localcart.entity;

/**
 * Controls whether a master catalogue product can appear in customer discovery.
 * Existing rows with a null status remain visible for backwards compatibility.
 */
public enum ProductCatalogueReviewStatus {
    ACTIVE,
    NEEDS_REVIEW,
    HIDDEN
}
