-- Upgrade the captured legacy schema to the current Apniko JPA mappings.
-- Legacy address values are retained; only obsolete requiredness is relaxed.
-- Legacy photo tables and products.image_url are deliberately retained as
-- unmapped legacy data until every deployment environment is independently audited.

ALTER TABLE customer_addresses
    MODIFY COLUMN locality varchar(120) DEFAULT NULL,
    MODIFY COLUMN is_default bit(1) DEFAULT NULL;

ALTER TABLE shops
    ADD COLUMN latitude double DEFAULT NULL,
    ADD COLUMN longitude double DEFAULT NULL;

ALTER TABLE product_catalogue_source_records
    ADD COLUMN source_metadata_json text DEFAULT NULL;

-- Append enum values to preserve the ordinal of every existing value.
ALTER TABLE payments
    MODIFY COLUMN method enum('COD','RAZORPAY','KHATHA') NOT NULL,
    MODIFY COLUMN status enum('CANCELLED','FAILED','PAID','PENDING','REFUNDED','REFUND_PENDING','REVERSED') NOT NULL;

CREATE TABLE khatha_accounts (
    id bigint NOT NULL AUTO_INCREMENT,
    shop_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    credit_limit decimal(12,2) NOT NULL,
    outstanding_balance decimal(12,2) NOT NULL,
    credit_balance decimal(12,2) NOT NULL,
    status enum('OFFERED','ACTIVE','PAUSED','CLOSED') NOT NULL,
    repayment_days int NOT NULL,
    terms_version int NOT NULL,
    accepted_terms_version int NOT NULL,
    due_date date DEFAULT NULL,
    accepted_at datetime(6) DEFAULT NULL,
    created_at datetime(6) DEFAULT NULL,
    updated_at datetime(6) DEFAULT NULL,
    version bigint NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_khatha_shop_customer (shop_id, customer_id),
    KEY idx_khatha_customer (customer_id),
    KEY idx_khatha_shop_status (shop_id, status),
    CONSTRAINT fk_khatha_accounts_shop
        FOREIGN KEY (shop_id) REFERENCES shops (id),
    CONSTRAINT fk_khatha_accounts_customer
        FOREIGN KEY (customer_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE khatha_ledger_entries (
    id bigint NOT NULL AUTO_INCREMENT,
    account_id bigint NOT NULL,
    entry_type enum('PURCHASE_DEBIT','REPAYMENT_CREDIT','ORDER_REVERSAL','ADJUSTMENT_CREDIT','ADJUSTMENT_DEBIT') NOT NULL,
    amount decimal(12,2) NOT NULL,
    outstanding_after decimal(12,2) NOT NULL,
    credit_balance_after decimal(12,2) DEFAULT NULL,
    credit_balance_change decimal(12,2) DEFAULT NULL,
    outstanding_change decimal(12,2) DEFAULT NULL,
    source_type varchar(32) NOT NULL,
    source_id varchar(80) NOT NULL,
    description varchar(500) NOT NULL,
    actor_name varchar(120) DEFAULT NULL,
    actor_email varchar(120) DEFAULT NULL,
    actor_role varchar(24) DEFAULT NULL,
    due_date date DEFAULT NULL,
    created_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_khatha_ledger_source (account_id, source_type, source_id),
    KEY idx_khatha_ledger_account_created (account_id, created_at),
    CONSTRAINT fk_khatha_ledger_account
        FOREIGN KEY (account_id) REFERENCES khatha_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE khatha_repayments (
    id bigint NOT NULL AUTO_INCREMENT,
    account_id bigint NOT NULL,
    amount decimal(12,2) NOT NULL,
    channel enum('CASH_AT_SHOP','UPI_TRANSFER') NOT NULL,
    status enum('PENDING','CONFIRMED','REJECTED') NOT NULL,
    idempotency_key varchar(80) NOT NULL,
    reference varchar(120) DEFAULT NULL,
    note varchar(500) DEFAULT NULL,
    acted_by_name varchar(120) DEFAULT NULL,
    decision_reason varchar(500) DEFAULT NULL,
    created_at datetime(6) DEFAULT NULL,
    confirmed_at datetime(6) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_khatha_repayment_key (idempotency_key),
    KEY idx_khatha_repayment_account_status (account_id, status),
    CONSTRAINT fk_khatha_repayments_account
        FOREIGN KEY (account_id) REFERENCES khatha_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE khatha_audit_logs (
    id bigint NOT NULL AUTO_INCREMENT,
    account_id bigint NOT NULL,
    action varchar(40) NOT NULL,
    reason varchar(500) NOT NULL,
    actor_user_id bigint NOT NULL,
    actor_name varchar(120) NOT NULL,
    actor_email varchar(120) NOT NULL,
    actor_role varchar(24) NOT NULL,
    created_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_khatha_audit_account_created (account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE khatha_balance_allocations (
    id bigint NOT NULL AUTO_INCREMENT,
    account_id bigint NOT NULL,
    debit_entry_id bigint NOT NULL,
    credit_entry_id bigint NOT NULL,
    amount decimal(12,2) NOT NULL,
    allocation_type enum('ALLOCATED','RELEASED') NOT NULL,
    created_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_khatha_debit_credit_allocation (debit_entry_id, credit_entry_id, allocation_type),
    KEY idx_khatha_allocation_debit (debit_entry_id),
    KEY idx_khatha_allocation_credit (credit_entry_id),
    CONSTRAINT fk_khatha_allocations_account
        FOREIGN KEY (account_id) REFERENCES khatha_accounts (id),
    CONSTRAINT fk_khatha_allocations_debit_entry
        FOREIGN KEY (debit_entry_id) REFERENCES khatha_ledger_entries (id),
    CONSTRAINT fk_khatha_allocations_credit_entry
        FOREIGN KEY (credit_entry_id) REFERENCES khatha_ledger_entries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
