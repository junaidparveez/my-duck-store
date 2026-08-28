-- Warehouse schema.
--
-- Flyway owns the schema; Hibernate runs with ddl-auto=validate and never alters it.
-- Money is NUMERIC, never a binary float, because order totals are compared to the cent.

CREATE TABLE duck (
    id       BIGSERIAL      PRIMARY KEY,
    color    VARCHAR(25)    NOT NULL,
    size     VARCHAR(16)    NOT NULL,
    price    NUMERIC(12, 2) NOT NULL,
    quantity INTEGER        NOT NULL,
    deleted  BOOLEAN        NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_duck_price_positive        CHECK (price > 0),
    CONSTRAINT chk_duck_quantity_not_negative CHECK (quantity >= 0)
);

-- The merge invariant, enforced by the database rather than by service code:
-- at most ONE active duck may exist for a given (color, size, price).
--
-- Two concurrent "add duck" requests cannot both check-then-insert successfully,
-- because the check and the insert are not atomic at application level. This index
-- makes the duplicate physically impossible; the service handles losing that race
-- by falling back to an atomic quantity increment.
--
-- It must be PARTIAL: deletion is logical, so deleted rows remain in the table.
-- A plain unique index would permanently block re-adding a combination that was
-- once deleted.
CREATE UNIQUE INDEX uq_duck_active_color_size_price
    ON duck (color, size, price)
    WHERE deleted = FALSE;

-- The listing query is "active ducks ordered by quantity"; this index serves it
-- directly, with id as the tie-break to keep the order stable.
CREATE INDEX idx_duck_active_quantity
    ON duck (quantity, id)
    WHERE deleted = FALSE;
