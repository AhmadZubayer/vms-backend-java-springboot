# Stark Vehicle Management API

Run from `vms-backend` with Java 17 and `mvnw.cmd spring-boot:run`. Copy `.env.example` to `.env` first; every environment-specific value, the database URL and the allowed browser origins included, is read from there. Spring loads the local `.env` file, and Flyway applies pending migrations to PostgreSQL when the app starts.

| Local setting | Value |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL JDBC URL and credentials |
| `APP_JWT_SECRET` | Base64-encoded signing key of at least 32 random bytes |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | First-run operator credentials. When the user email is new, the app saves a bcrypt hash in PostgreSQL. |
| `APP_TOKEN_LIFETIME_SECONDS` | Access token lifetime in seconds. Optional, defaults to `900` (15 minutes). |
| `SERVER_PORT` | Port the API listens on. Optional, defaults to `8080`. |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | Comma-separated browser origins allowed to call `/api/**`. Required: no origin is compiled in, and start-up fails if it is empty. `*` is allowed as a wildcard, so `http://localhost:*` covers the port a Flutter web dev server picks and `https://vms.example.com` names a deployed client. |
| `APP_MAX_PASSWORD_BYTES` | Longest accepted password in UTF-8 bytes. Optional, defaults to `72`; bcrypt ignores input beyond 72 bytes, so higher values are rejected at start-up. |

## Deploy to Render

Render has no native Java runtime, so the service runs from the `Dockerfile` in
this folder: it builds the jar on a JDK image and runs it on a JRE image.

1. Push this repository to GitHub.
2. In Render, **New > Web Service**, pick the repository, and choose the
   **Docker** runtime. Render reads the `Dockerfile`; leave the build and start
   commands empty. (Or use **New > Blueprint**, which reads `render.yaml` and
   pre-fills the service and its variable names.)
3. Set the health check path to `/api/health`.
4. Add the environment variables under **Environment**: `DB_URL`,
   `DB_USERNAME`, `DB_PASSWORD`, `APP_JWT_SECRET`, `APP_ADMIN_EMAIL`,
   `APP_ADMIN_PASSWORD`, and `APP_CORS_ALLOWED_ORIGIN_PATTERNS`. Do not set
   `PORT`; Render injects it and the app binds to it.
5. Deploy. Flyway applies any pending migrations on the first boot, and the
   admin account is created if that email is new.

The database stays wherever it already lives, as long as `DB_URL` is reachable
from Render and keeps `sslmode=require`. A Render PostgreSQL instance works too:
take its external connection details and write `DB_URL` in JDBC form,
`jdbc:postgresql://host/database?sslmode=require`.

Set `APP_CORS_ALLOWED_ORIGIN_PATTERNS` to the origin that serves the Flutter
client, for example `https://vms.example.com`, and point the client's
`API_BASE_URL` at the Render URL. On the free plan the service sleeps when idle,
so the first request after a pause takes a few seconds.

## Sign in

In Postman, send a JSON request to `POST http://localhost:8080/api/auth/login` with `userEmail` and `password` from your operator settings. Copy `data.accessToken` and select **Bearer Token** under Authorization for vehicle requests. Tokens expire after 15 minutes by default (`APP_TOKEN_LIFETIME_SECONDS`).

## API documentation

Swagger UI is served at `http://localhost:8080/docs`, which redirects to `/swagger-ui/index.html`, and the OpenAPI document is at `/v3/api-docs`. Both are open without a token. Use **Authorize** and paste the `accessToken` from a login to try the vehicle routes.

## Response shape

Every successful response is formatted by `SendResponse`, which wraps its payload in a `data` field, including `GET /api/health`, which answers with `status`, `databaseConnected`, and `database` so a client can tell a running server from a reachable database. Failures stay as RFC 7807 `application/problem+json` and are not wrapped.

## Auth routes

| Method and path | Result |
| --- | --- |
| `POST /api/auth/login` | Exchange `userEmail` and `password` for an access token |
| `GET /api/auth/me` | Read the signed-in account for the bearer token |

There is no logout route. Access tokens are self-contained and short lived, so signing out is the client discarding its token; the server keeps no session to end. A token that leaks stays usable until it expires, which is why `APP_TOKEN_LIFETIME_SECONDS` defaults to 15 minutes. If you ever need an immediate server-side revocation, store each token's `jti` in Redis or a table on logout and reject those ids in the `JwtDecoder` validator.

## Vehicle routes

| Method and path | Result |
| --- | --- |
| `POST /api/vehicles` | Register a vehicle |
| `GET /api/vehicles?page=0&size=20` | List vehicles |
| `GET /api/vehicles?q=toyota` | Search vehicles |
| `GET /api/vehicles?status=ACTIVE` | Filter vehicles by status |
| `GET /api/vehicles/stats` | Count every vehicle and how many sit in each status |
| `GET /api/vehicles?sort=status,asc` | Sort vehicles |
| `GET /api/vehicles/{id}` | Read a vehicle |
| `PUT /api/vehicles/{id}` | Replace the six vehicle details |
| `DELETE /api/vehicles/{id}` | Delete a vehicle |

`status` keeps only vehicles in that status, ignoring case, and combines with `q`; an unknown value is rejected with `400`. `GET /api/vehicles/stats` answers with `total` and a `byStatus` object that lists every status, including the ones at zero.

`q` matches part of a vehicle number, brand, company, model, type, or status, ignoring case; leaving it out lists every vehicle. `sort` takes a field and optional direction, such as `sort=status,desc`, and repeats for tie-breakers; sortable fields are `id`, `vehicleNumber`, `brand`, `company`, `model`, `type`, and `status`. Any other field is rejected with `400`. `size` defaults to 20 and is capped at 100.

The JSON body for vehicle registration and updates has `vehicleNumber`, `brand`, `company`, `model`, `type`, and `status`. Accepted types are `CAR`, `SEDAN`, `SUV`, `HATCHBACK`, `PICKUP`, `VAN`, `BUS`, `TRUCK`, `MOTORCYCLE`, and `OTHER`. Accepted statuses are `ACTIVE`, `INACTIVE`, `MAINTENANCE`, and `RETIRED`. Vehicle numbers are unique regardless of case or surrounding spaces; a duplicate is rejected with `409`, as described below.

## Duplicate vehicle numbers

Uniqueness is enforced by PostgreSQL, not by application code, and the `409` the
client sees is a translation of that constraint.

### The constraint

`V1__create_vehicles.sql` indexes the lowercased, trimmed value rather than the
column itself:

```sql
CREATE UNIQUE INDEX uq_vehicles_vehicle_number
    ON vehicles (lower(btrim(vehicle_number)));
```

So `DHAKA-1001`, `dhaka-1001` and `  DHAKA-1001  ` are the same vehicle number.
Keeping the rule in the database also closes the race a check-then-insert in
Java would leave open: two requests arriving together cannot both find the
number free and then both insert it. One of them loses, at the index.

`VehicleService` calls `strip()` on the incoming number before saving, and uses
`saveAndFlush`, so the insert reaches PostgreSQL during the request instead of
at transaction commit. That is what allows the failure to be caught and
translated, rather than surfacing later as an unhandled `500`.

### The translation

`GlobalExceptionHandler` turns the violation of that one index into a `409`, and
anything else into a logged `500`:

```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
    if (violatedConstraint(exception).equals(VEHICLE_NUMBER_INDEX)) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A vehicle with that number already exists."
        );
    }

    logger.error("Unexpected database constraint violation", exception);
    return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "The database could not save the change."
    );
}
```

`violatedConstraint` reads the constraint name from the Hibernate
`ConstraintViolationException` cause, so only this index produces the duplicate
message; an unrelated integrity failure is never mislabelled as a duplicate.

### What a client sees

```
POST /api/vehicles  {"vehicleNumber": "DHAKA-1001", ...}   201 Created
POST /api/vehicles  {"vehicleNumber": "dhaka-1001", ...}   409 Conflict
```

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A vehicle with that number already exists.",
  "instance": "/api/vehicles"
}
```

The body is `application/problem+json`, like every other failure, and carries no
`errors` array because the clash is with a stored row rather than with the shape
of the request. `PUT /api/vehicles/{id}` answers the same way, so renaming a
vehicle to a number another one already holds is also a `409`.
