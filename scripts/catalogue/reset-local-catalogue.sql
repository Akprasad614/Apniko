-- Local development reset only. Run via scripts/catalogue/reset-local-catalogue.sh.
-- The shell helper creates a full mysqldump before this file is executed.
-- Preserve users, roles, administrator accounts, shops and customer addresses.

SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

DELETE FROM inventory_movements;
DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM shop_inventory;

-- Order lines point to products. Clear local development order/payment data too
-- so no historical row retains a product from the retired catalogue.
DELETE FROM order_items;
DELETE FROM order_status_history;
DELETE FROM payments;
DELETE FROM notifications WHERE related_order_id IS NOT NULL;
DELETE FROM orders;

-- Clear every active photo, pending candidate, photo audit snapshot and
-- catalogue-source provenance row. Photos are URL metadata, not binary files.
DELETE FROM product_photo_review_history;
DELETE FROM product_catalogue_source_records;
DELETE FROM product_photos;

DELETE FROM products;

-- Remove the old hard-coded category tree. BigBasket taxonomy is created only
-- when a permitted BigBasket CSV is deliberately imported.
DELETE FROM product_categories WHERE parent_id IS NOT NULL;
DELETE FROM product_categories;

UPDATE notification_sync_state SET last_processed_history_id = 0;

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

SELECT
    (SELECT COUNT(*) FROM products) AS products_remaining,
    (SELECT COUNT(*) FROM product_photos) AS product_photos_remaining,
    (SELECT COUNT(*) FROM product_photo_review_history) AS photo_history_remaining,
    (SELECT COUNT(*) FROM product_catalogue_source_records) AS catalogue_source_records_remaining,
    (SELECT COUNT(*) FROM product_categories) AS product_categories_remaining,
    (SELECT COUNT(*) FROM shop_inventory) AS shop_inventory_remaining,
    (SELECT COUNT(*) FROM cart_items) AS cart_items_remaining,
    (SELECT COUNT(*) FROM order_items) AS order_items_remaining,
    (SELECT COUNT(*) FROM orders) AS orders_remaining;
