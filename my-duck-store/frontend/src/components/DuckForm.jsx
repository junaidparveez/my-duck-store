import { useEffect, useState } from 'react'
import { Alert, Button, Form, Modal, Spinner } from 'react-bootstrap'

const COLORS = ['Red', 'Green', 'Yellow', 'Black']
const SIZES  = ['XLarge', 'Large', 'Medium', 'Small', 'XSmall']

const EMPTY_ADD = { color: 'Red', size: 'XLarge', price: '', quantity: '' }

/**
 * Shared form used for both adding and editing a duck.
 *
 * When `duck` is non-null the form is in edit mode:
 *   • colour and size selects are disabled (read-only by design)
 *   • the PUT /api/ducks/{id} payload contains only price + quantity
 *
 * When `duck` is null the form is in add mode and all four fields are active.
 */
export default function DuckForm({ show, onHide, onSubmit, duck }) {
  const isEdit = duck !== null

  const [fields, setFields]   = useState(EMPTY_ADD)
  const [saving, setSaving]   = useState(false)
  const [apiError, setApiError] = useState(null)
  const [validated, setValidated] = useState(false)

  // Sync fields whenever the target duck changes (opening edit vs add).
  useEffect(() => {
    if (duck) {
      setFields({
        color:    duck.color,
        size:     duck.size,
        price:    String(duck.price),
        quantity: String(duck.quantity),
      })
    } else {
      setFields(EMPTY_ADD)
    }
    setApiError(null)
    setValidated(false)
    setSaving(false)
  }, [duck, show])

  function set(key, value) {
    setFields(prev => ({ ...prev, [key]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const form = e.currentTarget

    if (!form.checkValidity()) {
      setValidated(true)
      return
    }

    const price    = parseFloat(fields.price)
    const quantity = parseInt(fields.quantity, 10)

    setSaving(true)
    setApiError(null)
    try {
      if (isEdit) {
        await onSubmit({ price, quantity })
      } else {
        await onSubmit({ color: fields.color, size: fields.size, price, quantity })
      }
      onHide()
    } catch (err) {
      setApiError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal show={show} onHide={onHide} centered backdrop="static" size="sm">
      <Modal.Header closeButton>
        <Modal.Title style={{ fontSize: '1rem', fontWeight: 700 }}>
          {isEdit ? '✏️ Edit Duck' : '🦆 Add Duck'}
        </Modal.Title>
      </Modal.Header>

      <Form noValidate validated={validated} onSubmit={handleSubmit}>
        <Modal.Body>
          {apiError && (
            <Alert variant="danger" className="py-2 mb-3" style={{ fontSize: '0.825rem' }}>
              {apiError}
            </Alert>
          )}

          {/* Color */}
          <Form.Group className="mb-3">
            <Form.Label>Color</Form.Label>
            <Form.Select
              id="duck-color"
              value={fields.color}
              disabled={isEdit}
              onChange={e => set('color', e.target.value)}
              required
            >
              {COLORS.map(c => <option key={c} value={c}>{c}</option>)}
            </Form.Select>
          </Form.Group>

          {/* Size */}
          <Form.Group className="mb-3">
            <Form.Label>Size</Form.Label>
            <Form.Select
              id="duck-size"
              value={fields.size}
              disabled={isEdit}
              onChange={e => set('size', e.target.value)}
              required
            >
              {SIZES.map(s => <option key={s} value={s}>{s}</option>)}
            </Form.Select>
            {isEdit && (
              <Form.Text className="text-muted" style={{ fontSize: '0.75rem' }}>
                Color and size cannot be changed after creation.
              </Form.Text>
            )}
          </Form.Group>

          {/* Price */}
          <Form.Group className="mb-3">
            <Form.Label>Price (USD)</Form.Label>
            <Form.Control
              id="duck-price"
              type="number"
              min="0.01"
              step="0.01"
              placeholder="0.00"
              value={fields.price}
              onChange={e => set('price', e.target.value)}
              required
            />
            <Form.Control.Feedback type="invalid">
              Enter a price ≥ $0.01.
            </Form.Control.Feedback>
          </Form.Group>

          {/* Quantity */}
          <Form.Group className="mb-0">
            <Form.Label>Quantity</Form.Label>
            <Form.Control
              id="duck-quantity"
              type="number"
              min="0"
              step="1"
              placeholder="0"
              value={fields.quantity}
              onChange={e => set('quantity', e.target.value)}
              required
            />
            <Form.Control.Feedback type="invalid">
              Quantity must be 0 or more.
            </Form.Control.Feedback>
          </Form.Group>
        </Modal.Body>

        <Modal.Footer>
          <Button variant="outline-secondary" size="sm" onClick={onHide} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" size="sm" disabled={saving} id="duck-form-save">
            {saving
              ? <><Spinner size="sm" className="me-1" />Saving…</>
              : isEdit ? 'Save changes' : 'Add duck'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  )
}
