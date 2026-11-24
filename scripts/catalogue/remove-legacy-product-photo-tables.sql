-- Remove the obsolete product-photo tables only after a verified backup.
-- This script is intentionally guarded and is for a development database only.
-- It stops if either legacy table contains data so that audit rows are not lost.

DELIMITER //

CREATE PROCEDURE apniko_remove_empty_legacy_product_photo_tables()
BEGIN
    DECLARE photo_rows BIGINT DEFAULT 0;
    DECLARE history_rows BIGINT DEFAULT 0;

    IF DATABASE() IS NULL OR LOWER(DATABASE()) REGEXP 'prod|production' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing to remove legacy photo tables from a production-looking or unknown database.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'product_photos'
    ) THEN
        SELECT COUNT(*) INTO photo_rows FROM product_photos;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'product_photo_review_history'
    ) THEN
        SELECT COUNT(*) INTO history_rows FROM product_photo_review_history;
    END IF;

    IF photo_rows > 0 OR history_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy photo tables contain rows. Export and review them before cleanup; no tables were dropped.';
    END IF;

    SET FOREIGN_KEY_CHECKS = 0;
    DROP TABLE IF EXISTS product_photo_review_history;
    DROP TABLE IF EXISTS product_photos;
    SET FOREIGN_KEY_CHECKS = 1;
END//

DELIMITER ;

CALL apniko_remove_empty_legacy_product_photo_tables();
DROP PROCEDURE apniko_remove_empty_legacy_product_photo_tables;
