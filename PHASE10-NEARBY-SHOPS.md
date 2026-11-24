# Phase 10 — customer GPS and nearby-shop discovery

**Implementation status: code and CI acceptance added on the Phase 5/8/10 completion branch.**
**Real-store pin coverage: requires the operator to verify and save a coordinate for each shop.**

## Shop coordinate ownership

Each shop may have an optional latitude/longitude. Coordinates are store data, not customer data.

- An administrator can enter a verified pin in the Shops section, or capture the browser's current device location while physically standing at that shop.
- The admin must check that the pin belongs to the actual store entrance before saving.
- The admin API validates latitude (-90..90) and longitude (-180..180).
- Older shop create/update requests may omit coordinates. An update that omits both values preserves an existing pin; a half-supplied pair is rejected.
- Public shop responses expose the pin only as map location data; customer identity and phone fields are not included.

## Customer nearby search

On an explicit tap, the browser requests location permission. The customer selects a 2, 5, 10, 20 or 50 km radius. Apniko sends latitude, longitude and radius in a POST request body, not query parameters. The backend:

1. Validates coordinate and radius bounds.
2. Excludes inactive shops and shops without both verified coordinates.
3. Computes Haversine distance in kilometres.
4. Filters to the selected radius and sorts nearest first.
5. Returns the distance rounded to one decimal place.

Customer coordinates are used to answer that request and are not written to the customer profile or shop database. A customer who denies location permission can continue to use the regular shop list.

## Endpoint

`POST /api/customer/marketplace/shops/nearby`

Request body:

```json
{
  "latitude": 12.9716,
  "longitude": 77.5946,
  "radiusKm": 10
}
```

The endpoint returns the same public shop fields plus coordinates and `distanceKm`. Search radius is capped at 50 km.

## Development and production schema

Development uses the existing Hibernate update workflow to create nullable `shops.latitude` and `shops.longitude` columns when the application is next started against the development database.

**Do not treat this as a production migration.** The production profile uses Hibernate `validate`; the Phase 8 production baseline/review gate remains in force. The owner-reviewed versioned migration must include both nullable shop coordinate columns before the production database is initialized. Do not apply an inferred migration to production.

## Verification

The backend CI executes `scripts/verify-phase10-geolocation.sh` and the Maven test suite. The service tests cover nearest-first ordering, radius exclusion, inactive and unpinned shops, and coordinate/radius validation.
