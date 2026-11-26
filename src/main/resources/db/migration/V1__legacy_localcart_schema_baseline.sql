-- Baseline schema captured from the pre-Flyway localcart database on 2026-11-26.
-- This migration intentionally represents the legacy schema only. V2 upgrades it
-- to the current Apniko entity model. Existing databases must be baselined at v1
-- only after verifying that they match this captured schema.

SET NAMES utf8mb4;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','CUSTOMER','DELIVERY_PARTNER','SHOPKEEPER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `shops` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `owner_name` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `delivery_mode` enum('CUSTOMER_PICKUP','PLATFORM_DELIVERY','SHOP_DELIVERY') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrduswa89ayj0poad3l70nag19` (`owner_id`),
  CONSTRAINT `FKrduswa89ayj0poad3l70nag19` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(80) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `image_url` varchar(2048) DEFAULT NULL,
  `barcode` varchar(14) DEFAULT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `catalogue_imported_at` datetime(6) DEFAULT NULL,
  `catalogue_source` varchar(80) DEFAULT NULL,
  `catalogue_source_license` varchar(80) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `identity_key` varchar(64) DEFAULT NULL,
  `mrp` decimal(12,2) DEFAULT NULL,
  `mrp_source` varchar(40) DEFAULT NULL,
  `mrp_verified_at` datetime(6) DEFAULT NULL,
  `net_quantity` decimal(12,3) DEFAULT NULL,
  `net_quantity_unit` varchar(20) DEFAULT NULL,
  `pack_size` varchar(80) DEFAULT NULL,
  `subcategory` varchar(100) DEFAULT NULL,
  `variant_group_key` varchar(120) DEFAULT NULL,
  `catalogue_review_reason` varchar(500) DEFAULT NULL,
  `catalogue_review_status` enum('ACTIVE','HIDDEN','NEEDS_REVIEW') DEFAULT NULL,
  `catalogue_reviewed_at` datetime(6) DEFAULT NULL,
  `catalogue_reviewed_by` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqfr8vf85k3q1xinifvsl1eynf` (`barcode`),
  UNIQUE KEY `UKcfuq7fmke1p0gfovajkuikuho` (`identity_key`),
  KEY `idx_products_brand_name` (`brand`,`name`),
  KEY `idx_products_category_subcategory` (`category`,`subcategory`),
  KEY `idx_products_catalogue_review_status` (`catalogue_review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_categories_slug` (`slug`),
  KEY `FKnhstaep8s818kydkq4teq8v4e` (`parent_id`),
  CONSTRAINT `FKnhstaep8s818kydkq4teq8v4e` FOREIGN KEY (`parent_id`) REFERENCES `product_categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `customer_addresses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_line1` varchar(180) NOT NULL,
  `address_line2` varchar(180) DEFAULT NULL,
  `city` varchar(80) NOT NULL,
  `is_default` bit(1) NOT NULL,
  `label` varchar(40) NOT NULL,
  `landmark` varchar(120) DEFAULT NULL,
  `locality` varchar(120) NOT NULL,
  `phone` varchar(24) NOT NULL,
  `postal_code` varchar(10) NOT NULL,
  `recipient_name` varchar(100) NOT NULL,
  `state` varchar(80) NOT NULL,
  `customer_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `default_address` bit(1) NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_customer_addresses_customer_id` (`customer_id`),
  KEY `idx_customer_addresses_customer` (`customer_id`),
  CONSTRAINT `FKqptcrpu3x8qle4pawfritva6o` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `carts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `shop_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrmmyb38lqu7idlmmqrgcqvcpf` (`customer_id`,`shop_id`),
  KEY `FKuw3798o19ixwd5do7dt385sw` (`shop_id`),
  CONSTRAINT `FK99i1rh5nm7r3f1b3wdcuq5h57` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKuw3798o19ixwd5do7dt385sw` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `shop_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `available` bit(1) NOT NULL,
  `price` double NOT NULL,
  `stock_quantity` int NOT NULL,
  `product_id` bigint NOT NULL,
  `shop_id` bigint NOT NULL,
  `low_stock_threshold` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKl5lgmdexge62eoj3bnh32ccfg` (`shop_id`,`product_id`),
  KEY `FKrngcllyow40gbh8suugrkma52` (`product_id`),
  CONSTRAINT `FK2ts8yypltl1ju8l4qb4dcpvrj` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`),
  CONSTRAINT `FKrngcllyow40gbh8suugrkma52` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cart_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int NOT NULL,
  `cart_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6oue0maw421roerltnxn16a38` (`cart_id`,`product_id`),
  KEY `FK1re40cjegsfvw58xrkdp6bac6` (`product_id`),
  CONSTRAINT `FK1re40cjegsfvw58xrkdp6bac6` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKpcttvuq4mxppo8sxggjtn5i2c` FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_movements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `movement_type` enum('ADJUSTMENT','CANCELLATION_RESTOCK','INITIAL_STOCK','ORDER_DEDUCTION','RESTOCK') NOT NULL,
  `new_quantity` int NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `performed_by_name` varchar(120) DEFAULT NULL,
  `performed_by_role` varchar(40) DEFAULT NULL,
  `performed_by_user_id` bigint DEFAULT NULL,
  `previous_quantity` int NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(180) NOT NULL,
  `quantity_change` int NOT NULL,
  `shop_id` bigint NOT NULL,
  `inventory_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_inventory_movement_inventory_date` (`inventory_id`,`created_at`),
  KEY `idx_inventory_movement_shop_date` (`shop_id`,`created_at`),
  CONSTRAINT `FKbpma01ohu92djwvwu6o0coleq` FOREIGN KEY (`inventory_id`) REFERENCES `shop_inventory` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_mode` enum('CUSTOMER_PICKUP','PLATFORM_DELIVERY','SHOP_DELIVERY') DEFAULT NULL,
  `status` enum('ACCEPTED','CANCELLED','DELIVERED','OUT_FOR_DELIVERY','PLACED','PREPARING','READY') DEFAULT NULL,
  `total_amount` double NOT NULL,
  `shop_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `delivery_partner_id` bigint DEFAULT NULL,
  `delivery_address_line1` varchar(255) DEFAULT NULL,
  `delivery_address_line2` varchar(255) DEFAULT NULL,
  `delivery_city` varchar(80) DEFAULT NULL,
  `delivery_address_label` varchar(30) DEFAULT NULL,
  `delivery_landmark` varchar(120) DEFAULT NULL,
  `delivery_locality` varchar(120) DEFAULT NULL,
  `delivery_phone` varchar(20) DEFAULT NULL,
  `delivery_postal_code` varchar(6) DEFAULT NULL,
  `delivery_recipient_name` varchar(100) DEFAULT NULL,
  `delivery_state` varchar(80) DEFAULT NULL,
  `address_line1` varchar(255) DEFAULT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `label` varchar(255) DEFAULT NULL,
  `landmark` varchar(255) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) DEFAULT NULL,
  `recipient_name` varchar(255) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK21gttsw5evi5bbsvleui69d7r` (`shop_id`),
  KEY `FKsjfs85qf6vmcurlx43cnc16gy` (`customer_id`),
  KEY `FKtb0olpml31x7swbu0hleci67x` (`delivery_partner_id`),
  CONSTRAINT `FK21gttsw5evi5bbsvleui69d7r` FOREIGN KEY (`shop_id`) REFERENCES `shops` (`id`),
  CONSTRAINT `FKsjfs85qf6vmcurlx43cnc16gy` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKtb0olpml31x7swbu0hleci67x` FOREIGN KEY (`delivery_partner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `price` double NOT NULL,
  `quantity` int NOT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbioxgbv59vetrxe0ejfubep1w` (`order_id`),
  KEY `FKocimc7dtr037rh4ls4l95nlfi` (`product_id`),
  CONSTRAINT `FKbioxgbv59vetrxe0ejfubep1w` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKocimc7dtr037rh4ls4l95nlfi` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `changed_at` datetime(6) NOT NULL,
  `changed_by_email` varchar(180) DEFAULT NULL,
  `changed_by_name` varchar(120) DEFAULT NULL,
  `changed_by_role` varchar(40) DEFAULT NULL,
  `changed_by_user_id` bigint DEFAULT NULL,
  `new_status` enum('ACCEPTED','CANCELLED','DELIVERED','OUT_FOR_DELIVERY','PLACED','PREPARING','READY') NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `previous_status` enum('ACCEPTED','CANCELLED','DELIVERED','OUT_FOR_DELIVERY','PLACED','PREPARING','READY') DEFAULT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_status_history_order` (`order_id`),
  KEY `idx_order_status_history_changed_at` (`changed_at`),
  CONSTRAINT `FKnmcbg3mmbt8wfva97ra40nmp3` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(12,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `currency` varchar(3) NOT NULL,
  `gateway_order_id` varchar(100) DEFAULT NULL,
  `gateway_payment_id` varchar(100) DEFAULT NULL,
  `gateway_refund_id` varchar(100) DEFAULT NULL,
  `method` enum('COD','RAZORPAY') NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','FAILED','PAID','PENDING','REFUNDED','REFUND_PENDING') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8vo36cen604as7etdfwmyjsxt` (`order_id`),
  CONSTRAINT `FK81gagumt0r8y3rmudcgpbk42l` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification_sync_state` (
  `id` bigint NOT NULL,
  `last_processed_history_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `message` varchar(1000) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `recipient_user_id` bigint NOT NULL,
  `related_order_id` bigint DEFAULT NULL,
  `source_key` varchar(140) NOT NULL,
  `title` varchar(140) NOT NULL,
  `type` enum('DELIVERY','ORDER','PAYMENT','SYSTEM') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKg2ticxahay431w7nj7d6gjrvu` (`source_key`),
  KEY `idx_notifications_recipient_created` (`recipient_user_id`,`created_at`),
  KEY `idx_notifications_recipient_read` (`recipient_user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_catalogue_source_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attribution` varchar(500) NOT NULL,
  `imported_at` datetime(6) NOT NULL,
  `reported_mrp` decimal(12,2) DEFAULT NULL,
  `reported_mrp_source` varchar(40) DEFAULT NULL,
  `source_license` varchar(80) NOT NULL,
  `source_modified_at` datetime(6) DEFAULT NULL,
  `source_name` varchar(80) NOT NULL,
  `source_record_id` varchar(160) NOT NULL,
  `source_url` varchar(500) NOT NULL,
  `product_id` bigint NOT NULL,
  `commercial_use_permitted` bit(1) DEFAULT NULL,
  `permission_reference` varchar(500) DEFAULT NULL,
  `reuse_rights_verified` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_catalogue_source_record` (`source_name`,`source_record_id`),
  KEY `idx_catalogue_source_product` (`product_id`),
  CONSTRAINT `FKtwlxgbqxidwjpwdpb6ggmei2` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_photo_review_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `authorization_reference` varchar(500) DEFAULT NULL,
  `candidate_attribution` varchar(500) DEFAULT NULL,
  `candidate_image_url` varchar(1000) DEFAULT NULL,
  `candidate_license` varchar(80) DEFAULT NULL,
  `candidate_source_name` varchar(100) DEFAULT NULL,
  `candidate_source_url` varchar(500) DEFAULT NULL,
  `candidate_submitted_at` datetime(6) DEFAULT NULL,
  `outcome` varchar(16) NOT NULL,
  `product_barcode` varchar(14) DEFAULT NULL,
  `product_brand` varchar(100) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(200) DEFAULT NULL,
  `product_pack_size` varchar(80) DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) NOT NULL,
  `reviewed_by` varchar(120) DEFAULT NULL,
  `source_pack_size` varchar(80) DEFAULT NULL,
  `source_product_brand` varchar(100) DEFAULT NULL,
  `source_product_name` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_photo_history_product` (`product_id`),
  KEY `idx_photo_history_reviewed_at` (`reviewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_photos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_image_url` varchar(1000) DEFAULT NULL,
  `image_attribution` varchar(500) DEFAULT NULL,
  `candidate_attribution` varchar(500) DEFAULT NULL,
  `candidate_barcode` varchar(14) DEFAULT NULL,
  `candidate_image_url` varchar(1000) DEFAULT NULL,
  `candidate_license` varchar(80) DEFAULT NULL,
  `candidate_product_name` varchar(200) DEFAULT NULL,
  `candidate_source_name` varchar(100) DEFAULT NULL,
  `candidate_source_url` varchar(500) DEFAULT NULL,
  `image_license` varchar(80) DEFAULT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `review_status` enum('APPROVED','PENDING','REJECTED') DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` varchar(120) DEFAULT NULL,
  `source_name` varchar(100) DEFAULT NULL,
  `source_url` varchar(500) DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `authorization_reference` varchar(500) DEFAULT NULL,
  `candidate_authorization_reference` varchar(500) DEFAULT NULL,
  `candidate_pack_size` varchar(80) DEFAULT NULL,
  `candidate_product_brand` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_photo_product` (`product_id`),
  KEY `idx_product_photo_review_status` (`review_status`),
  CONSTRAINT `FKk6euo1c1uosxm44vy24qbw05j` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
