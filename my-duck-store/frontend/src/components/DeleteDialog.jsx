import { useState } from 'react'
import { Button, Modal, Spinner } from 'react-bootstrap'

/**
 * Confirmation dialog shown before a duck is logically deleted.
 *
 * @param {{ duck: object|null, onConfirm: () => Promise<void>, onHide: () => void }} props
 */
export default function DeleteDialog({ duck, onConfirm, onHide }) {
  const [deleting, setDeleting] = useState(false)

  async function handleConfirm() {
    setDeleting(true)
    try {
      await onConfirm()
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Modal show={duck !== null} onHide={onHide} centered size="sm" backdrop="static">
      <Modal.Header closeButton>
        <Modal.Title style={{ fontSize: '1rem', fontWeight: 700 }}>
          🗑️ Delete Duck
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {duck && (
          <p className="mb-0" style={{ fontSize: '0.9rem' }}>
            Are you sure you want to delete the{' '}
            <strong>{duck.color} / {duck.size}</strong> duck
            {' '}(qty&nbsp;{duck.quantity.toLocaleString()})?
            <br />
            <span className="text-muted" style={{ fontSize: '0.8rem' }}>
              The record will be hidden from the warehouse. This cannot be undone.
            </span>
          </p>
        )}
      </Modal.Body>

      <Modal.Footer>
        <Button variant="outline-secondary" size="sm" onClick={onHide} disabled={deleting}>
          Cancel
        </Button>
        <Button
          variant="danger"
          size="sm"
          onClick={handleConfirm}
          disabled={deleting}
          id="delete-confirm-btn"
        >
          {deleting
            ? <><Spinner size="sm" className="me-1" />Deleting…</>
            : 'Yes, delete'}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
