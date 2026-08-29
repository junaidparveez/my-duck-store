import { useState } from 'react'
import { Alert, Button, Spinner } from 'react-bootstrap'
import './App.css'
import DeleteDialog from './components/DeleteDialog.jsx'
import DuckForm from './components/DuckForm.jsx'
import DuckTable from './components/DuckTable.jsx'
import { useDucks } from './hooks/useDucks.js'

export default function App() {
  const { ducks, loading, error, addDuck, updateDuck, deleteDuck } = useDucks()

  // ── Modal visibility state ─────────────────────────────────────────────────
  // formDuck === null  → add mode    (form closed when formOpen is false)
  // formDuck !== null  → edit mode
  const [formOpen,    setFormOpen]    = useState(false)
  const [formDuck,    setFormDuck]    = useState(null)   // duck being edited
  const [deletingDuck, setDeletingDuck] = useState(null) // duck queued for deletion

  // ── Handlers ───────────────────────────────────────────────────────────────
  function openAdd() {
    setFormDuck(null)
    setFormOpen(true)
  }

  function openEdit(duck) {
    setFormDuck(duck)
    setFormOpen(true)
  }

  function closeForm() {
    setFormOpen(false)
    setFormDuck(null)
  }

  /**
   * Called by DuckForm with the validated payload.
   *
   * Deliberately does not close the modal and does not catch: the dialog owns its own lifecycle,
   * closing itself on success and showing the message on failure. Closing in both places would
   * mean two owners for one piece of state, and swallowing the rejection here would hide a
   * failed save from the user.
   */
  async function handleFormSubmit(data) {
    if (formDuck) {
      await updateDuck(formDuck.id, data)
    } else {
      await addDuck(data)
    }
  }

  /** Called by DeleteDialog after the user confirms. Rejections surface in the dialog. */
  async function handleDeleteConfirm() {
    await deleteDuck(deletingDuck.id)
    setDeletingDuck(null)
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
      {/* ── Header ──────────────────────────────────────────────────────────── */}
      <header className="wh-header">
        <div className="wh-brand">
          <span className="wh-brand-icon" aria-hidden="true">🦆</span>
          <div className="wh-brand-text">
            <span className="wh-brand-title">Duck Warehouse</span>
            <span className="wh-brand-subtitle">Stock Management</span>
          </div>
        </div>

        <Button
          variant="primary"
          size="sm"
          id="add-duck-btn"
          onClick={openAdd}
        >
          + Add Duck
        </Button>
      </header>

      {/* ── Main content ────────────────────────────────────────────────────── */}
      <main className="wh-main">

        {/* Global fetch error */}
        {error && (
          <Alert variant="danger" className="mb-3" style={{ fontSize: '0.875rem' }}>
            {error}
          </Alert>
        )}

        {/* Section title bar */}
        <div className="wh-section-bar">
          <h1 className="wh-section-title">
            All Ducks
            {!loading && (
              <span className="wh-duck-count" aria-label={`${ducks.length} ducks`}>
                {ducks.length}
              </span>
            )}
          </h1>
          {loading && <Spinner size="sm" style={{ color: 'var(--duck-amber)' }} />}
        </div>

        {/* Table card */}
        <div className="wh-card">
          {loading && ducks.length === 0
            ? (
              <div className="wh-empty">
                <Spinner style={{ color: 'var(--duck-amber)', width: '2rem', height: '2rem' }} />
                <p className="mt-3 mb-0" style={{ color: 'var(--text-muted)' }}>Loading…</p>
              </div>
            )
            : (
              <DuckTable
                ducks={ducks}
                onEdit={openEdit}
                onDelete={setDeletingDuck}
              />
            )
          }
        </div>

        <p className="mt-2 mb-0" style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          Sorted by quantity · lowest stock first
        </p>
      </main>

      {/* ── Footer ────────────────────────────────────────────────────────── */}
      <footer className="wh-footer">
        Duck Warehouse — My Duck Store
      </footer>

      {/* ── Modals ──────────────────────────────────────────────────────────
          Each dialog is mounted only while it is open and keyed on what it is
          editing, so its internal state (fields, errors, in-flight flag) starts
          fresh every time it opens without an effect to reset it. */}
      {formOpen && (
        <DuckForm
          key={formDuck ? `edit-${formDuck.id}` : 'add'}
          onHide={closeForm}
          onSubmit={handleFormSubmit}
          duck={formDuck}
        />
      )}

      {deletingDuck && (
        <DeleteDialog
          key={deletingDuck.id}
          duck={deletingDuck}
          onConfirm={handleDeleteConfirm}
          onHide={() => setDeletingDuck(null)}
        />
      )}
    </>
  )
}
