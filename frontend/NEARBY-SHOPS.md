# Nearby shop discovery

## Customer experience

The customer storefront does not request precise location on page load. Customers choose **Use my location** when they want nearby ordering suggestions.

The browser Geolocation API returns the customer's coordinates after explicit permission. The frontend calculates straight-line distance with the Haversine formula and sorts geocoded shops nearest-first. It uses the customer's location only in the current page session; it is not sent to the backend or stored against the customer account.

Shops without coordinates remain visible after geocoded shops. If location permission is declined or unavailable, Apniko keeps the original shop ordering and product search continues to work.

The distance shown is an approximate straight-line distance, not driving distance, delivery time or a guarantee that the shop serves the customer's address.

## Shopkeeper setup

Each shop can store optional WGS84 latitude and longitude values.

- During shop creation, the shopkeeper may capture the device location.
- Existing shopkeepers can open **Shop** in the workspace and update the shop profile.
- Capture the location while physically at the shop for the most reliable result.
- Latitude and longitude must be supplied together and are range-validated by the backend.
- The values are stored on the `shops` record and returned in marketplace shop and product search responses.

Older shop records remain valid with null coordinates. They continue to appear in discovery but cannot be distance-ranked until their owners add a location.

## Boundaries

- The customer can use the device location without creating or saving an address.
- Existing delivery address selection remains separate from discovery location.
- The current Shop model does not define a delivery radius or operating hours. Therefore distance ranking does not label a shop as open, promise delivery coverage or calculate a delivery ETA.
- True delivery eligibility and route ETA need delivery-zone/radius rules and a routing service in a later phase.
