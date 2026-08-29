# My Duck Store

An online shop for rubber ducks, built as two modules:

| Module        | Scope                                          | Interface           |
| ------------- | ---------------------------------------------- | ------------------- |
| **Warehouse** | Create / list / edit / logically delete ducks   | REST API + React UI |
| **Store**     | Resolve price, decide packaging, compute total  | REST API only       |

The store is deliberately API-only, as the exercise specifies.

---

## Tech Stack

| Layer    | Technology                                                            |
| -------- | --------------------------------------------------------------------- |
| Backend  | Java 21, Spring Boot 4.1.1, Spring Data JPA, Bean Validation, Flyway  |
| Database | PostgreSQL 16 (via Docker Compose)                                    |
| Frontend | React 19, Vite, react-bootstrap 2                                     |
| Testing  | JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers                    |

---

## Prerequisites

- **Java 21+**
- **Docker** (for the database, and for the Testcontainers-based tests)
- **Node.js 20+**

---

## Quickstart

All three commands start from the `my-duck-store/` directory.

```bash
cd my-duck-store

# 1. Start the database (PostgreSQL 16 on host port 5433)
docker compose up -d

# 2. Start the backend  -> http://localhost:8080
cd backend
./mvnw spring-boot:run

# 3. Start the frontend -> http://localhost:5173   (in a second terminal)
cd frontend
npm install        # first time only
npm run dev
```

Flyway creates the schema on first boot, so there is no manual database setup.
Swagger UI: <http://localhost:8080/swagger-ui.html> · OpenAPI JSON: `/v3/api-docs`

The Vite dev server proxies `/api` to `:8080`, so no CORS configuration is needed in development.

Host port **5433** is used because a locally installed PostgreSQL commonly holds 5432.

### Environment variables

Defaults match `docker-compose.yml`, so a clean checkout runs with no configuration. Override for
any other database:

| Variable      | Default                                      |
| ------------- | -------------------------------------------- |
| `DB_URL`      | `jdbc:postgresql://localhost:5433/duckstore` |
| `DB_USERNAME` | `duckstore`                                  |
| `DB_PASSWORD` | `duckstore`                                  |

No credentials are committed beyond these local-only development defaults.

---

## Running the tests

```bash
cd my-duck-store/backend
./mvnw test          # 103 tests

cd ../frontend
npm run lint
npm run build
```

Docker must be running: the integration tests start a real PostgreSQL 16 container via
Testcontainers. The guarantees they check — the partial unique index, `ON CONFLICT DO UPDATE`,
row locking under concurrency — are properties of PostgreSQL, so testing them against an in-memory
database would prove nothing.

| Test class                     | What it proves                                                        |
| ------------------------------ | --------------------------------------------------------------------- |
| `PricingCalculationTest`       | Every pricing rule, to the cent, including the 100/1000 boundaries    |
| `PackagingTest`                | All 5 sizes x 3 shipping modes — package type and protection material |
| `DuckServiceTest`              | Add/edit/delete rules, including the edit fold                        |
| `DuckApiContractTest`          | HTTP status codes and the error response shape                        |
| `DuckWarehouseIntegrationTest` | Listing order, logical deletion vs. the unique index, price resolution|
| `DuckMergeIntegrationTest`     | The merge invariant under genuinely concurrent writes                 |
| `QuoteServiceTest`             | Price resolution, and that quoting never writes                       |
| `PricingEngineTest`            | Rule order, omitted rules, and that the engine rounds once            |

Expected monetary values in `PricingCalculationTest` were computed by hand from the exercise rules,
not captured from a run, so they remain a valid oracle if the pricing internals are refactored.

---

## Warehouse API

| Method   | Endpoint             | Description                                                       |
| -------- | -------------------- | ----------------------------------------------------------------- |
| `GET`    | `/api/v1/ducks`      | All active ducks, sorted by quantity ascending                    |
| `POST`   | `/api/v1/ducks`      | Add duck — merges quantities if same colour + size + price exists |
| `PUT`    | `/api/v1/ducks/{id}` | Update price and quantity only                                    |
| `DELETE` | `/api/v1/ducks/{id}` | Logical delete (row stays in the database with `deleted = true`)  |

### Add a duck

```bash
curl -X POST http://localhost:8080/api/v1/ducks \
  -H 'Content-Type: application/json' \
  -d '{"color":"Red","size":"XLarge","price":22.00,"quantity":21}'
```

`201 Created` for a new record; **`200 OK` when the quantities were merged** into an existing duck
with the same colour, size and price:

```jsonc
// POST the identical body a second time:
{ "id": 1, "color": "Red", "size": "XLarge", "price": 22.00, "quantity": 42 }   // 200 OK
```

### Edit a duck (price and quantity only)

```bash
curl -X PUT http://localhost:8080/api/v1/ducks/1 \
  -H 'Content-Type: application/json' \
  -d '{"price":200.00,"quantity":42}'
```

Colour and size are read-only after creation — enforced by the **shape of the request body**
(`UpdateDuckRequest` has no colour or size field), not by a runtime check that could be bypassed.

If the new price matches another active duck of the same colour and size, the two **fold** into one
record — see decision #2 below.

### Delete a duck

```bash
curl -X DELETE http://localhost:8080/api/v1/ducks/1     # 204 No Content
```

The row is never physically removed: it is flagged `deleted = true`, disappears from every listing,
and is ignored by store price resolution.

---

## Store API

| Method | Endpoint                | Description                                    |
| ------ | ----------------------- | ---------------------------------------------- |
| `POST` | `/api/v1/orders/quote`  | Price a potential order — no stock is consumed |

```bash
curl -X POST http://localhost:8080/api/v1/orders/quote \
  -H 'Content-Type: application/json' \
  -d '{"color":"Red","size":"Medium","quantity":5,"country":"USA","shippingMode":"Air"}'
```

```json
{
  "packageType": "Cardboard",
  "protectionMaterials": ["Polystyrene balls"],
  "total": 208.50,
  "breakdown": [
    { "description": "Base cost (5 × $10.00)",      "amount": 50.00 },
    { "description": "Cardboard packaging (−1%)",   "amount": -0.50 },
    { "description": "Destination USA (+18%)",      "amount": 9.00 },
    { "description": "Air shipping ($30 × 5 units)", "amount": 150.00 }
  ]
}
```

### Valid values

| Field          | Accepted values                                            |
| -------------- | ---------------------------------------------------------- |
| `color`        | `Red`, `Green`, `Yellow`, `Black`                          |
| `size`         | `XLarge`, `Large`, `Medium`, `Small`, `XSmall`             |
| `shippingMode` | `Air`, `Land`, `Sea`                                       |
| `country`      | Any non-blank string — unknown countries attract 15%       |

Colour, size and shipping mode are accepted in any case (`Red`, `RED`, `red`).

---

## Business rules

### Packaging and protection

| Size          | Package   | Air               | Land              | Sea                                         |
| ------------- | --------- | ----------------- | ----------------- | ------------------------------------------- |
| XLarge, Large | Wood      | Polystyrene balls | Polystyrene balls | Moisture-absorbing beads + bubble wrap bags |
| Medium        | Cardboard | Polystyrene balls | Polystyrene balls | Moisture-absorbing beads + bubble wrap bags |
| Small, XSmall | Plastic   | Bubble wrap bags  | Polystyrene balls | Moisture-absorbing beads + bubble wrap bags |

### Pricing

| Rule                               | Effect                                               |
| ---------------------------------- | ---------------------------------------------------- |
| Base                               | `quantity × unit price`                              |
| Quantity **more than** 100         | −20%                                                 |
| Wood packaging (XLarge / Large)    | +5%                                                  |
| Plastic packaging (Small / XSmall) | +10%                                                 |
| Cardboard packaging (Medium)       | −1%                                                  |
| Destination USA                    | +18%                                                 |
| Destination Bolivia                | +13%                                                 |
| Destination India                  | +19%                                                 |
| Any other destination              | +15%                                                 |
| Sea shipping                       | +$400 flat                                           |
| Land shipping                      | +$10 per unit                                        |
| Air shipping                       | +$30 per unit; −15% on the air charge above 1,000 units |

---

## Key design decisions

Where the exercise is ambiguous, these are the readings chosen and why.

### 1. The merge rule is enforced by the database, not by service code

A `find`-then-`insert` in Java is not atomic: two concurrent add requests can both find nothing and
both insert. The invariant is therefore owned by PostgreSQL:

```sql
CREATE UNIQUE INDEX uq_duck_active_color_size_price
    ON duck (color, size, price) WHERE deleted = FALSE;
```

and every add goes through one atomic statement:

```sql
INSERT INTO duck (...) VALUES (...)
ON CONFLICT (color, size, price) WHERE deleted = false
DO UPDATE SET quantity = duck.quantity + EXCLUDED.quantity;
```

PostgreSQL evaluates the conflict and the increment inside a single statement, holding a row lock
for its duration, so concurrent callers serialise on the row instead of racing. A duplicate row is
physically impossible, and no increment can be lost.

The index **must** be partial (`WHERE deleted = FALSE`). Deletion is logical, so deleted rows stay
in the table; a plain unique index would permanently block re-stocking a combination that had once
been deleted.

`DuckMergeIntegrationTest` proves this against a real database, including the exercise's own
scenario: 100 units in stock, then adds of 50 and 30 arriving simultaneously, giving **180** —
never 150 or 130.

### 2. Editing a price into an existing price point **folds** the two records

The invariant is "at most one active duck per colour + size + price". Editing "Red / XLarge / $22"
to $200 when "Red / XLarge / $200" already exists would break it, so the edit is treated as what it
actually is — moving stock onto an existing price point. The edited row is logically deleted and its
quantity is added atomically to the surviving row, whose id is returned.

This is the same outcome as deleting the row and re-adding the stock at the new price, and it keeps
the invariant true on **every** write path rather than only on add. A price edit that collides with
nothing keeps its own id, because an id is a stable identifier and a routine price correction should
not renumber the record.

### 3. Multiple prices for one colour and size → the **lowest active price**

The order carries no price, and the warehouse may legitimately hold the same duck at several
prices. The quote uses the **lowest active** unit price: deterministic, independent of insertion
order and stock levels, trivial to explain, and the customer-friendly reading. Deleted rows are
excluded, so deleting the cheapest batch raises subsequent quotes.

### 4. A quote does not check or consume stock

The exercise describes a pricing calculation; there is no order entity and nothing is reserved.
Quoting 500 units against 3 in stock therefore succeeds. Only a colour + size with **no active
stock at all** fails, with `422 Unprocessable Content` — the request is well-formed, so it is not
a `400`.

### 5. Every percentage applies to the base subtotal; they do not compound

The rules are each phrased as a percentage "of the total cost", with no stated order, so applying
them all to the same base — `quantity × unit price` — is the reading that makes the wording
self-consistent and the result independent of rule ordering. The only rule that applies to
something else is the air-freight reduction, which the text explicitly scopes to the air charge.

### 6. Each breakdown line is rounded to 2 dp; the total is their exact sum

Rounding once per line and summing guarantees the itemised breakdown always adds up to the total
shown. Money is `BigDecimal` end to end and `NUMERIC(12,2)` in the database — never a binary float.

### 7. Listing is sorted by quantity ascending, with id as a tie-break

The exercise says "sort by quantity" without a direction. Ascending surfaces low stock first, which
is the useful view for a warehouse. The id tie-break makes the order stable across calls.

### 8. `country` is a string, not an enum

"Any other destination → 15%" requires an open set. Matching is trimmed and upper-cased under
`Locale.ROOT` rather than the default locale, so a server running in a Turkish locale still matches
`India` — a locale-sensitive upper-case turns `i` into a dotted capital there.

### 9. `201 Created` on insert, `200 OK` on merge

Lets a client tell which path was taken. Under simultaneous adds of a brand-new duck both may
report `201`; the stored quantity is exact regardless, and only the status label is approximate.

### 10. API version prefix lives in `application.yaml`

`api.prefix` drives every route (`@RequestMapping("${api.prefix}/ducks")`), so bumping v1 to v2 is
a one-line change and Swagger UI keeps its root paths.

---

## Validation and error handling

Validation is declarative (Bean Validation on the request records) and the **backend is
authoritative**. The React form validates the same fields for immediate feedback, but every rule is
enforced server-side; nothing relies on the UI.

`GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place that maps a failure onto an
HTTP status, so the service layer imports nothing from `org.springframework.http` and every error
shares one shape:

```json
{
  "timestamp": "2026-08-29T08:23:57.911Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed. See 'details' for the offending fields.",
  "path": "/api/v1/ducks",
  "details": [
    { "field": "price",    "message": "must be at least 0.01" },
    { "field": "quantity", "message": "must be greater than or equal to 0" },
    { "field": "size",     "message": "must not be null" }
  ]
}
```

| Status | `error`            | When                                                     |
| ------ | ------------------ | -------------------------------------------------------- |
| 400    | `VALIDATION_ERROR` | A field failed Bean Validation; `details` names each one |
| 400    | `MALFORMED_REQUEST`| Unparseable body, unknown enum value, bad path variable  |
| 404    | `NOT_FOUND`        | No such duck, or it has been logically deleted           |
| 409    | `CONFLICT`         | A concurrent request changed the same duck — retry       |
| 422    | `NO_STOCK`         | No active stock for the requested colour + size          |
| 500    | `INTERNAL_ERROR`   | Anything unexpected                                      |

`error` is a stable machine-readable code; branch on that rather than on `message`.

Anything a client can fix is reported precisely — an unknown colour returns
`Unknown color: 'Purple'. Valid values: Red, Green, Yellow, Black`. Anything it cannot fix is logged
in full server-side and reduced to a generic sentence: **stack traces, SQL statements and database
constraint names never reach a caller.** All database access goes through JPA and parameter binding,
so user input is never concatenated into SQL.

---

## Architecture

```
web/         controllers + request/response DTOs   — HTTP only, no business logic
  ↓
service/     business rules and transaction boundaries
  ↓
repository/  Spring Data JPA + the atomic upsert
  ↓
domain/      entities and enums
```

```
my-duck-store/
├── docker-compose.yml
├── backend/
│   └── src/main/java/com/myduckstore/
│       ├── common/web/      ApiError, GlobalExceptionHandler
│       ├── config/          OpenApiConfig
│       ├── warehouse/
│       │   ├── domain/      Duck, Color, Size
│       │   ├── repository/  DuckRepository
│       │   ├── service/     DuckService + exceptions
│       │   └── web/         DuckController + dto/
│       └── store/
│           ├── domain/      PackageType, ProtectionMaterial, ShippingMode
│           ├── service/     QuoteService, NoStockException
│           │   ├── packaging/  PackagingPolicy
│           │   └── pricing/    PricingRule + 5 rules, PricingEngine, PricingContext
│           └── web/         QuoteController + dto/
└── frontend/src/
    ├── components/  DuckTable, DuckForm, DeleteDialog
    └── hooks/       useDucks   — the single place that talks to the API
```

The two modules share only the `Color` and `Size` enums. Flyway owns the schema; Hibernate runs
with `ddl-auto=validate` and never alters it — `update` would silently skip the partial index the
merge rule depends on.

### Patterns and practices used

- **Repository** — persistence behind an interface; every finder filters `deleted = false`, so
  "deleted ducks never appear" is a property of the queries rather than something each caller must
  remember.
- **DTOs at the boundary** — JPA entities are never serialised. `DuckResponse` cannot leak the
  internal `deleted` flag, and `UpdateDuckRequest` makes colour and size *unrepresentable* in an
  edit rather than merely rejected.
- **Static factory + `@JsonValue`/`@JsonCreator` on enums** — parsing, case-insensitive matching and
  the human-readable label live with the type, so validity is decided in one place.
- **Strategy** for pricing (§3c) — each family of pricing rules is one `PricingRule`
  implementation; `PricingEngine` applies them in a declared order and owns the rounding. Detailed
  below.
- **Exhaustive switch expressions** over `Size` and `ShippingMode` for packaging and protection —
  adding a size or mode becomes a compile error until every branch is handled, instead of a silent
  fall-through.
- **Centralised exception translation** — one `@RestControllerAdvice` owns the HTTP mapping so the
  domain stays free of web concerns.
- **Custom shared hook (`useDucks`)** — one place in the frontend that talks to the API; the table,
  form and delete dialog are presentational and share it rather than duplicating fetch logic.

### The Strategy pattern in the store module

The exercise asks for packaging (§3b) and pricing (§3c) to be implemented "using one or more design
patterns". They got different answers, deliberately.

**Pricing is Strategy.** The twelve rules in §3c all apply, in order, and each contributes one line
to the itemized breakdown §3d requires. That is exactly Strategy's shape — one interface, many
interchangeable implementations, all of them applied:

```java
public interface PricingRule {
    Optional<BreakdownLine> apply(PricingContext context);   // empty = this rule does not apply
}
```

`PricingEngine` holds the ordered list and is the only thing that rounds:

| Rule class | §3c rules |
| --- | --- |
| `BaseCostRule` | 1 — quantity × unit price |
| `BulkDiscountRule` | 2 — more than 100 units, −20% |
| `PackagingAdjustmentRule` | 3–5 — wood +5%, plastic +10%, cardboard −1% |
| `DestinationSurchargeRule` | 6–9 — a lookup table with an open-ended default |
| `ShippingChargeRule` | 10–12 — sea flat, land and air per-unit, air taper above 1,000 |

Five classes, not twelve, because the twelve numbered items collapse into five *families* — and the
family is the real axis of change. A new country is a new map entry inside
`DestinationSurchargeRule`; a genuinely new *kind* of charge is a new class plus one line in the
engine. Splitting USA / Bolivia / India into three classes would be turning a lookup table into a
hierarchy for no gain.

Three properties fall out of the structure rather than being maintained by hand:

- **The breakdown is the design.** One rule contributes one line, so the itemisation cannot drift
  out of step with what was actually charged.
- **Percentages cannot accidentally compound.** `PricingContext` carries the base subtotal and no
  running total, so a rule is physically unable to price off another rule's output.
- **Money is rounded in one place.** Rules return unrounded amounts; `PricingEngine` rounds each
  line to cents and the total is their exact sum, so "the breakdown adds up to the total" holds by
  construction.

**Packaging is not Strategy, on purpose.** `PackagingPolicy` keeps its exhaustive `switch`
expressions. The mapping is total and closed — five sizes × three shipping modes, all fifteen cases
fixed by the spec — and an exhaustive switch already delivers the guarantee a class hierarchy would:
adding a `Size` is a *compile error* until every branch is handled. A class per case would be
indirection with nothing behind it. What was genuinely wrong was location, not shape, so the rules
moved out of the pricing service into their own collaborator.

**Decorator was considered and rejected.** "A package, then wrapped in protective materials" is the
textbook Decorator example, so it deserves an explicit answer: the §3d response is a *list of
material labels*, not an object whose behaviour composes. There is no polymorphic call for a
decorator to wrap, so a decorator chain would add indirection and change nothing about the output.

**Chain of Responsibility would have been the wrong name.** A chain stops at the first handler that
claims the request; here every applicable rule must contribute.

The refactor was verified rather than assumed: the cent-exact expectations in
`PricingCalculationTest` were hand-computed from the PDF *before* it, and the whole suite passes
with the three existing store test classes changed only where the constructor gained its two new
collaborators — not one expected value edited. `PricingEngineTest` is new, and covers only what the
refactor introduced: rule order, omitted rules, and rounding.

---

## Frontend

- Table of Id / Colour / Size / Price / Quantity with per-row edit and delete, an **Add duck**
  control above it, and the sort order the API guarantees.
- One shared form for add and edit; in edit mode the colour and size selects are disabled and the
  request body carries only price and quantity.
- Delete goes through a confirmation dialog; a failed delete shows the server's message in the
  dialog rather than failing silently.
- Loading, empty and error states are all handled; every mutation re-fetches so the table always
  reflects server truth.

---

## Assumptions

1. `price > 0` and `quantity >= 0`, enforced by Bean Validation and by database `CHECK`
   constraints. A quote requires `quantity >= 1`.
2. Prices carry at most 2 decimal places (`NUMERIC(12,2)`); `10.00` and `10.000` are the same price
   and merge.
3. Country matching is case-insensitive and trims surrounding whitespace.
4. There is no authentication — the exercise does not ask for it, and adding it would be scope
   beyond the ask.
5. Logical deletion is one-way through the API; there is no "undelete" endpoint.
