# My Duck Store

An online shop for rubber ducks built as two modules:

| Module        | Scope                                          | Interface           |
| ------------- | ---------------------------------------------- | ------------------- |
| **Warehouse** | Create / list / edit / logically delete ducks  | REST API + React UI |
| **Store**     | Resolve price, decide packaging, compute total | REST API only       |

---

## Tech Stack

| Layer    | Technology                                                           |
| -------- | -------------------------------------------------------------------- |
| Backend  | Java 21, Spring Boot 4.1.1, Spring Data JPA, Bean Validation, Flyway |
| Database | PostgreSQL 16 (via Docker)                                           |
| Frontend | React 19, Vite, react-bootstrap 2                                    |

---

## Prerequisites

- **Java 21+**
- **Docker & Docker Compose**
- **Node.js 20+**

---

## Quickstart

```bash
# 1. Start the database
docker compose up -d

# 2. Start the backend  (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# 3. Start the frontend (http://localhost:5173)
cd frontend
npm install        # first time only
npm run dev
```

The Vite dev server proxies every `/api` request to `:8080`, so no CORS
configuration is required on the backend during development.

---

## Warehouse API

| Method   | Endpoint          | Description                                                      |
| -------- | ----------------- | ---------------------------------------------------------------- |
| `GET`    | `/api/ducks`      | All active ducks, sorted by quantity ascending                   |
| `POST`   | `/api/ducks`      | Add duck — merges quantities if same color + size + price exists |
| `PUT`    | `/api/ducks/{id}` | Update price and quantity only                                   |
| `DELETE` | `/api/ducks/{id}` | Logical delete (row stays in DB with `deleted = true`)           |

### List all ducks

```bash
curl http://localhost:8080/api/ducks
```

### Add a duck

```bash
curl -X POST http://localhost:8080/api/ducks \
  -H 'Content-Type: application/json' \
  -d '{"color":"Red","size":"Medium","price":10.00,"quantity":50}'
```

Adding the same `color + size + price` a second time merges the quantities
instead of creating a duplicate — returns `200 OK` on merge, `201 Created`
on a new record.

### Edit a duck (price + quantity only)

```bash
curl -X PUT http://localhost:8080/api/ducks/1 \
  -H 'Content-Type: application/json' \
  -d '{"price":12.50,"quantity":75}'
```

Color and size are read-only after creation — enforced by the shape of the
`PUT` request body, not by a runtime check.

### Delete a duck

```bash
curl -X DELETE http://localhost:8080/api/ducks/1
```

The row is never physically removed. It disappears from the listing
(`deleted = true`) but stays in the database so historical data is preserved.

---

## Store API

| Method | Endpoint            | Description                                    |
| ------ | ------------------- | ---------------------------------------------- |
| `POST` | `/api/orders/quote` | Price a potential order — no stock is consumed |

### Get a price quote

```bash
curl -X POST http://localhost:8080/api/orders/quote \
  -H 'Content-Type: application/json' \
  -d '{
    "color":        "Red",
    "size":         "Medium",
    "quantity":     5,
    "country":      "USA",
    "shippingMode": "Air"
  }'
```

**Example response:**

```json
{
  "packageType": "Cardboard",
  "protectionMaterials": ["Polystyrene balls"],
  "total": 208.5,
  "breakdown": [
    { "description": "Base cost (5 × $10.00)", "amount": 50.0 },
    { "description": "Cardboard packaging (−1%)", "amount": -0.5 },
    { "description": "Destination USA (+18%)", "amount": 9.0 },
    { "description": "Air shipping ($30 × 5 units)", "amount": 150.0 }
  ]
}
```

### Valid values

| Field          | Accepted values                                          |
| -------------- | -------------------------------------------------------- |
| `color`        | `Red`, `Green`, `Yellow`, `Black`                        |
| `size`         | `XLarge`, `Large`, `Medium`, `Small`, `XSmall`           |
| `shippingMode` | `Air`, `Land`, `Sea`                                     |
| `country`      | Any string — unknown countries attract the 15% surcharge |

### Pricing rules (all % applied to base subtotal)

| Rule                               | Effect                                               |
| ---------------------------------- | ---------------------------------------------------- |
| Base                               | `quantity × lowest active price`                     |
| Quantity > 100                     | −20%                                                 |
| Wood packaging (XLarge / Large)    | +5%                                                  |
| Plastic packaging (Small / XSmall) | +10%                                                 |
| Cardboard packaging (Medium)       | −1%                                                  |
| Destination USA                    | +18%                                                 |
| Destination Bolivia                | +13%                                                 |
| Destination India                  | +19%                                                 |
| Any other destination              | +15%                                                 |
| Sea shipping                       | +$400 flat                                           |
| Land shipping                      | +$10 per unit                                        |
| Air shipping                       | +$30 per unit (−15% on air charge above 1,000 units) |

Each breakdown line is rounded to 2 decimal places individually.
The `total` is the exact sum of those rounded lines.

### Packaging & protection matrix

| Size          | Package   | Air               | Land              | Sea                                         |
| ------------- | --------- | ----------------- | ----------------- | ------------------------------------------- |
| XLarge, Large | Wood      | Polystyrene balls | Polystyrene balls | Moisture-absorbing beads + Bubble wrap bags |
| Medium        | Cardboard | Polystyrene balls | Polystyrene balls | Moisture-absorbing beads + Bubble wrap bags |
| Small, XSmall | Plastic   | Bubble wrap bags  | Polystyrene balls | Moisture-absorbing beads + Bubble wrap bags |

---

## Key Design Decisions

| #   | Decision                                                            | Reason                                                                                                        |
| --- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| 1   | Merge rule enforced by a **partial unique index** + atomic `UPDATE` | A service-level find-then-insert is not atomic; two concurrent requests can both find nothing and both insert |
| 2   | Editing price can collide → **fold** edited duck into matching one  | Keeps the invariant true on every write path                                                                  |
| 3   | Multiple prices for same color + size → use **lowest active price** | Deterministic and easy to reason about                                                                        |
| 4   | Quoting an order **does not consume stock**                         | The requirement describes a pricing calculation only; no order entity exists                                  |
| 5   | All % rules apply to the **base subtotal**, not compounding         | Spec names a different basis when it means one                                                                |
| 6   | Each breakdown line rounded to 2dp; total = their sum               | Breakdown always adds up exactly to the total shown                                                           |
| 7   | Listing sorted **quantity ascending**, `id` as tie-break            | Surfaces low stock first — the useful view for a warehouse                                                    |
| 8   | Country is a **String**, not an enum                                | "Any other destination → 15%" requires an open set                                                            |
| 9   | `201 Created` on insert, `200 OK` on merge                          | Lets the client distinguish which path was taken                                                              |
