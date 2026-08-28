import { Table } from 'react-bootstrap'

/** Colour dot + label, styled to match the actual duck colour. */
const COLOR_STYLES = {
  Red:    { background: '#fee2e2', color: '#991b1b', dot: '#ef4444' },
  Green:  { background: '#dcfce7', color: '#166534', dot: '#22c55e' },
  Yellow: { background: '#fef3c7', color: '#92400e', dot: '#f59e0b' },
  Black:  { background: '#f1f5f9', color: '#0f172a', dot: '#334155' },
}

function ColorBadge({ color }) {
  const s = COLOR_STYLES[color] ?? { background: '#e2e8f0', color: '#334155', dot: '#94a3b8' }
  return (
    <span className="color-badge" style={{ background: s.background, color: s.color }}>
      <span className="color-dot" style={{ background: s.dot }} />
      {color}
    </span>
  )
}

function SizeBadge({ size }) {
  return <span className="size-badge">{size}</span>
}

function formatUSD(value) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value)
}

/**
 * Warehouse table.
 *
 * The list is already sorted by quantity ascending (the API guarantees this).
 * Edit and delete actions are delegated back to the parent via callbacks.
 *
 * @param {{ ducks: object[], onEdit: (duck: object) => void, onDelete: (duck: object) => void }} props
 */
export default function DuckTable({ ducks, onEdit, onDelete }) {
  if (ducks.length === 0) {
    return (
      <div className="wh-empty">
        <span className="wh-empty-icon">🦆</span>
        <p className="wh-empty-title">No ducks in stock</p>
        <p className="wh-empty-sub">Add your first duck using the button above.</p>
      </div>
    )
  }

  return (
    <Table className="wh-table" responsive>
      <thead>
        <tr>
          <th>Color</th>
          <th>Size</th>
          <th>Price (USD)</th>
          <th>Quantity</th>
          <th style={{ width: 90 }}>Actions</th>
        </tr>
      </thead>
      <tbody>
        {ducks.map(duck => (
          <tr key={duck.id}>
            <td><ColorBadge color={duck.color} /></td>
            <td><SizeBadge size={duck.size} /></td>
            <td className="price-cell">{formatUSD(duck.price)}</td>
            <td className="qty-cell">{duck.quantity.toLocaleString()}</td>
            <td>
              <div className="wh-actions">
                <button
                  className="btn-icon btn-icon-edit"
                  title={`Edit ${duck.color} ${duck.size}`}
                  aria-label={`Edit ${duck.color} ${duck.size}`}
                  onClick={() => onEdit(duck)}
                  id={`edit-duck-${duck.id}`}
                >
                  ✏️
                </button>
                <button
                  className="btn-icon btn-icon-delete"
                  title={`Delete ${duck.color} ${duck.size}`}
                  aria-label={`Delete ${duck.color} ${duck.size}`}
                  onClick={() => onDelete(duck)}
                  id={`delete-duck-${duck.id}`}
                >
                  🗑️
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </Table>
  )
}
