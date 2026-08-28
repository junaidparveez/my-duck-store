import { useCallback, useEffect, useState } from 'react'

const BASE_URL = '/api/ducks'

/**
 * Central data layer for the warehouse UI.
 *
 * Holds the duck list in state and exposes add / update / delete operations.
 * Every mutation re-fetches the full list so the table always reflects server truth.
 */
export function useDucks() {
  const [ducks, setDucks]     = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState(null)

  // ── Fetch ──────────────────────────────────────────────────────────────────
  const fetchDucks = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(BASE_URL)
      if (!res.ok) throw new Error(`Failed to load ducks (HTTP ${res.status})`)
      setDucks(await res.json())
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchDucks() }, [fetchDucks])

  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Parse an error response body, falling back to a generic message. */
  async function parseError(res) {
    try {
      const body = await res.json()
      return body.message || `Error ${res.status}`
    } catch {
      return `Error ${res.status}`
    }
  }

  // ── Mutations ──────────────────────────────────────────────────────────────

  /**
   * Add a duck. If the same color+size+price already exists the backend merges
   * the quantities (returns 200) rather than creating a duplicate (returns 201).
   * Either way the list is refreshed on success.
   *
   * @param {{ color: string, size: string, price: number, quantity: number }} data
   * @throws {Error} with a human-readable message on failure
   */
  async function addDuck(data) {
    const res = await fetch(BASE_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    })
    if (!res.ok) throw new Error(await parseError(res))
    await fetchDucks()
  }

  /**
   * Update the price and quantity of an existing duck.
   * Color and size are read-only after creation (enforced by the backend DTO shape).
   *
   * @param {number} id
   * @param {{ price: number, quantity: number }} data
   * @throws {Error} with a human-readable message on failure
   */
  async function updateDuck(id, data) {
    const res = await fetch(`${BASE_URL}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    })
    if (!res.ok) throw new Error(await parseError(res))
    await fetchDucks()
  }

  /**
   * Logically delete a duck. The row stays in the database with deleted=true
   * and will no longer appear in the listing.
   *
   * @param {number} id
   * @throws {Error} with a human-readable message on failure
   */
  async function deleteDuck(id) {
    const res = await fetch(`${BASE_URL}/${id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error(await parseError(res))
    await fetchDucks()
  }

  return { ducks, loading, error, fetchDucks, addDuck, updateDuck, deleteDuck }
}
