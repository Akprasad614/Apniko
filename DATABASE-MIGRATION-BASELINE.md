# Apniko database migration baseline

## Scope and source

V1 is a schema-only representation of the 18-table `localcart` schema exported from the owner's MySQL instance on 26 November 2026. It contains table definitions, indexes and foreign keys only; it contains no application rows, database-creation statements, GTID state or destructive table drops.

V2 upgrades that captured legacy shape to the current backend entity mappings. Never edit a migration after it has been applied to a shared environment; add a new forward migration instead.

## Changes in V2

- Retains existing `customer_addresses.locality` and `is_default` values, but makes those legacy, unmapped columns nullable. The current application writes `default_address` and does not populate those old columns. This avoids blocking new address inserts without losing the existing row's values.
- Adds nullable `shops.latitude` and `shops.longitude` columns used by the current `Shop` entity.
- Adds nullable `product_catalogue_source_records.source_metadata_json` (`TEXT`) used by the current catalogue provenance entity.
- Appends `KHATHA` to the payment method enum and `REVERSED` to the payment status enum, preserving the order of existing enum values.
- Creates the five missing Khatha tables: `khatha_accounts`, `khatha_ledger_entries`, `khatha_repayments`, `khatha_audit_logs` and `khatha_balance_allocations`.

The old `products.image_url`, `product_photos` and `product_photo_review_history` schema objects are intentionally retained. The inspected local database had zero products and zero rows in either photo table, and the current application has no active product-photo entity/service. One local count is not proof that every future target database is empty. Any cleanup must be a separate reviewed migration after each target is checked and backed up.

## Migration execution model

Flyway is the schema migration mechanism; Hibernate uses `validate` and must not update or create the application schema automatically.

- A genuinely empty database receives V1 followed by V2 when Flyway migrates.
- An existing non-empty database must be backed up and verified against V1, then explicitly baselined at version 1 before V2 is applied. Automatic baseline-on-migrate is disabled to avoid silently stamping an unknown schema.
- The `prod` profile disables Flyway inside the running application. Shared staging/production migrations are run explicitly with a separate DDL-capable migration identity using the Flyway Maven plugin. The application runtime identity remains DML-only.

## Existing database procedure

Do not baseline an arbitrary non-empty database. Before setting a baseline marker:

1. Take a consistent full backup, copy it to encrypted off-host storage and verify its checksum.
2. Restore the backup to a disposable database. Record MySQL version, charset/collation, tables, views, columns, indexes, foreign keys, triggers, routines, events and any existing migration history.
3. Confirm the restored database matches the captured V1 schema. If it differs or has already been migrated, do not baseline it with this version; prepare a separate reviewed migration path.
4. Test the exact baseline and upgrade path on the disposable copy. Confirm existing address values and all application data remain intact.
5. Use a dedicated DDL-capable migration identity. Do not grant schema-change privileges to the application runtime user.

Export the operator-approved URL and migration username in the shell, then enter the migration password interactively:

```bash
export FLYWAY_URL="$DB_URL"
export FLYWAY_USER="$DB_MIGRATION_USERNAME"
read -rsp "Flyway migration password: " FLYWAY_PASSWORD
export FLYWAY_PASSWORD
echo

# Inspect the target before changing it.
./mvnw -B -ntp flyway:info

# Existing schema only: run after verifying it matches the V1 snapshot.
./mvnw -B -ntp flyway:baseline -Dflyway.baselineVersion=1

# Apply pending forward migrations and inspect the result.
./mvnw -B -ntp flyway:migrate
./mvnw -B -ntp flyway:info

unset FLYWAY_PASSWORD
```

For an empty database, skip the baseline command and run `flyway:migrate` directly so V1 and V2 execute. Never run Flyway `clean` against a shared database.

After migration, start the app with the `prod` profile. Hibernate validation must pass. The migration process and application runtime remain separate: the migration user changes schema; the runtime user performs only required application DML.

## Verification

The backend CI job uses a fresh MySQL 8.4 service. Its Spring Boot integration test exercises Flyway migration from an empty database and Hibernate validation against the discovered JPA mappings. This repository-level test does not replace restoring and testing the owner's actual database in isolated staging.

Phase 14 remains open until the migration has been tested and applied in staging, real-environment acceptance tests pass, and migration/backup evidence is recorded in `PHASE14-STAGING-READINESS.md`.
