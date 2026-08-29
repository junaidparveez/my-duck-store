import { useState } from 'react'
import { Alert, Button, Modal, Spinner } from 'react-bootstrap'

/**
 * Confirmation dialog shown before a duck is logically deleted.
 *
 * Mounted only while a duck is queued for deletion, and keyed on that duck's id, so its state
 * starts clean for every duck without an effect to reset it.
 *
 * @param {{ duck: object, onConfirm: () => Promise<void>, onHide: () => void }} props
 */
export default function DeleteDialog({ duck, onConfirm, onHide }) {
  const [deleting, setDeleting] = useState(false)
  const [apiError, setApiError] = useState(null)

  /**
   * A failed delete has to be shown, not swallowed. `onConfirm` rejects with the message the
   * backend sent, and the dialog stays open carrying it — same treatment DuckForm gives its
   * own submit errors.
   */
  async function handleConfirm() {
    setDeleting(true)
    setApiError(null)
    try {
      await onConfirm()
    } catch (err) {
      setApiError(err.message)
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Modal show onHide={onHide} centered size="sm" backdrop="static">
      <Modal.Header closeButton>
        <Modal.Title style={{ fontSize: '1rem', fontWeight: 700 }}>
          🗑️ Delete Duck
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {apiError && (
          <Alert variant="danger" className="py-2 mb-3" style={{ fontSize: '0.825rem' }}>
            {apiError}
          </Alert>
        )}

        <p className="mb-0" style={{ fontSize: '0.9rem' }}>
          Are you sure you want to delete the{' '}
          <strong>{duck.color} / {duck.size}</strong> duck
          {' '}(qty&nbsp;{duck.quantity.toLocaleString()})?
          <br />
          <span className="text-muted" style={{ fontSize: '0.8rem' }}>
            The record will be hidden from the warehouse. This cannot be undone.
          </span>
        </p>
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
