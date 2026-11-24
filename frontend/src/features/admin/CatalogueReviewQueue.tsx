import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import {
  catalogueReviewStatusLabel,
  formatCatalogueReviewDate,
} from './catalogueReview'
import type {
  CatalogueReviewFilter,
  CatalogueReviewItem,
  CatalogueReviewPage,
  CatalogueReviewStatus,
} from './catalogueReview'
import './catalogue-review.css'

interface CatalogueReviewQueueProps {
  token: string
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'Only an administrator can review catalogue products.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Could not load the catalogue review queue.'
}

export function CatalogueReviewQueue({ token }: CatalogueReviewQueueProps) {
  const [filter, setFilter] = useState<CatalogueReviewFilter>('NEEDS_REVIEW')
  const [searchDraft, setSearchDraft] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<CatalogueReviewPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [hideReasons, setHideReasons] = useState<Record<number, string>>({})

  const loadQueue = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    setError('')
    try {
      const query = new URLSearchParams({
        status: filter,
        page: String(page),
        size: '20',
      })
      if (searchTerm) query.set('q', searchTerm)
      const next = await apiRequest<CatalogueReviewPage>(
        '/api/admin/catalogue/review-queue?' + query.toString(),
        {},
        token,
      )

      if (page > 0 && next.content.length === 0 && page >= next.totalPages) {
        setPage(Math.max(0, next.totalPages - 1))
        return
      }
      setResult(next)
    } catch (loadError) {
      setError(messageFor(loadError))
    } finally {
      if (!quiet) setLoading(false)
    }
  }, [filter, page, searchTerm, token])

  useEffect(() => {
    void loadQueue()
  }, [loadQueue])

  async function updateStatus(
    item: CatalogueReviewItem,
    status: CatalogueReviewStatus,
    reason?: string,
  ) {
    const normalizedReason = reason?.trim() ?? ''
    if (status === 'HIDDEN' && !normalizedReason) {
      setError('Add a reason before hiding this product.')
      return
    }

    setBusyId(item.id)
    setError('')
    setNotice('')
    try {
      await apiRequest<CatalogueReviewItem>(
        '/api/admin/catalogue/products/' + item.id + '/review',
        {
          method: 'PATCH',
          body: JSON.stringify({
            status,
            reason: normalizedReason || null,
          }),
        },
        token,
      )
      setHideReasons((current) => {
        const next = { ...current }
        delete next[item.id]
        return next
      })
      setNotice(
        status === 'ACTIVE'
          ? item.name + ' is now visible in customer discovery.'
          : status === 'HIDDEN'
            ? item.name + ' has been hidden from customer discovery.'
            : item.name + ' was returned to the review queue.',
      )
      await loadQueue(true)
    } catch (updateError) {
      setError(messageFor(updateError))
    } finally {
      setBusyId(null)
    }
  }

  function searchProducts(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPage(0)
    setResult(null)
    setSearchTerm(searchDraft.trim())
  }

  function hideProduct(event: FormEvent<HTMLFormElement>, item: CatalogueReviewItem) {
    event.preventDefault()
    void updateStatus(item, 'HIDDEN', hideReasons[item.id] ?? '')
  }

  const totalElements = result?.totalElements ?? 0
  const totalPages = result?.totalPages ?? 0
  const currentPage = result?.number ?? page

  return (
    <section className="catalogue-review" aria-labelledby="catalogue-review-heading">
      <div className="operations-section-heading catalogue-review__heading">
        <div>
          <p className="operations-eyebrow">PRODUCT QUALITY</p>
          <h2 id="catalogue-review-heading">Catalogue review</h2>
          <p>Check imported product details before they appear in customer discovery.</p>
        </div>
        <button
          type="button"
          className="operations-button operations-button--secondary"
          onClick={() => void loadQueue()}
          disabled={loading}
        >
          {loading ? 'Refreshing…' : 'Refresh queue ↻'}
        </button>
      </div>

      <div className="catalogue-review__toolbar">
        <label>
          Review status
          <select
            value={filter}
            onChange={(event) => {
              setFilter(event.target.value as CatalogueReviewFilter)
              setPage(0)
              setResult(null)
            }}
          >
            <option value="NEEDS_REVIEW">Needs review</option>
            <option value="ACTIVE">Active</option>
            <option value="HIDDEN">Hidden</option>
            <option value="ALL">All products</option>
          </select>
        </label>
        <form className="catalogue-review-search" onSubmit={searchProducts}>
          <label>
            Search catalogue
            <input
              value={searchDraft}
              onChange={(event) => setSearchDraft(event.target.value)}
              maxLength={100}
              placeholder="Name, brand, barcode or category"
            />
          </label>
          <button type="submit" className="operations-button operations-button--secondary">
            Search
          </button>
          {searchTerm && (
            <button
              type="button"
              className="operations-text-button"
              onClick={() => {
                setSearchDraft('')
                setSearchTerm('')
                setPage(0)
                setResult(null)
              }}
            >
              Clear
            </button>
          )}
        </form>
        <div className="catalogue-review__count" aria-live="polite">
          <strong>{totalElements.toLocaleString('en-IN')}</strong>
          <span>{filter === 'NEEDS_REVIEW' ? 'products awaiting review' : 'matching products'}</span>
        </div>
      </div>

      {error && <div className="operations-alert operations-alert--error" role="alert">{error}</div>}
      {notice && <div className="operations-alert operations-alert--success" role="status">{notice}</div>}

      {loading ? (
        <div className="operations-state" role="status">Loading catalogue review items…</div>
      ) : result === null ? (
        <div className="operations-state">The review queue could not be loaded. Use refresh to try again.</div>
      ) : result.content.length > 0 ? (
        <>
          <div className="catalogue-review__list">
            {result.content.map((item) => (
              <article className="catalogue-review-card" key={item.id}>
                {(() => null)()}
                <div className="catalogue-review-card__top">
                  <div className="catalogue-review-card__identity">
                    <span className={'catalogue-review-status catalogue-review-status--' + (item.status?.toLowerCase() ?? 'legacy')}>
                      {catalogueReviewStatusLabel(item.status)}
                    </span>
                    <span className="catalogue-review-card__id">Product #{item.id}</span>
                  </div>
                  <strong className="catalogue-review-card__name">{item.name}</strong>
                  <p className="catalogue-review-card__meta">
                    {[item.brand, item.packSize, item.barcode ? 'GTIN ' + item.barcode : null]
                      .filter(Boolean)
                      .join(' · ') || 'Brand, pack size and barcode not fully recorded'}
                  </p>
                </div>

                <div className="catalogue-review-card__details">
                  <div><span>Category</span><strong>{[item.category, item.subcategory].filter(Boolean).join(' / ') || 'Unclassified'}</strong></div>
                  <div><span>Source</span><strong>{item.catalogueSource || 'Manually added / legacy'}</strong></div>
                  <div><span>Imported</span><strong>{formatCatalogueReviewDate(item.importedAt)}</strong></div>
                  {item.reviewedBy && <div><span>Last reviewed by</span><strong>{item.reviewedBy}</strong></div>}
                </div>

                {item.reviewReason && (
                  <div className="catalogue-review-card__reason">
                    <span>Why it was flagged</span>
                    <p>{item.reviewReason}</p>
                  </div>
                )}

                <div className="catalogue-review-card__actions">
                  {item.status !== 'ACTIVE' && !(
                    item.catalogueSource === 'Mendeley Indian Packaged Foods Dataset (2026)'
                    && (!item.barcode?.trim() || !item.packSize?.trim())
                  ) && (
                    <button
                      type="button"
                      className="operations-button"
                      disabled={busyId === item.id}
                      onClick={() => void updateStatus(item, 'ACTIVE')}
                    >
                      {busyId === item.id ? 'Saving…' : 'Approve & publish'}
                    </button>
                  )}
                  {item.catalogueSource === 'Mendeley Indian Packaged Foods Dataset (2026)'
                    && (!item.barcode?.trim() || !item.packSize?.trim()) && (
                    <div className="catalogue-review-card__reason" role="note">
                      <span>Publishing blocked</span>
                      <p>This dataset row has no verified retail barcode or pack size. It must remain a reference candidate until an approved source supplies the exact retail variant.</p>
                    </div>
                  )}
                  {item.status !== 'NEEDS_REVIEW' && (
                    <button
                      type="button"
                      className="operations-button operations-button--secondary"
                      disabled={busyId === item.id}
                      onClick={() => void updateStatus(item, 'NEEDS_REVIEW', 'Returned to the catalogue review queue')}
                    >
                      Send to review
                    </button>
                  )}
                  {item.status !== 'HIDDEN' && (
                    <details className="catalogue-review-hide">
                      <summary>Hide product</summary>
                      <form onSubmit={(event) => hideProduct(event, item)}>
                        <label>
                          Reason for hiding
                          <textarea
                            value={hideReasons[item.id] ?? ''}
                            onChange={(event) => setHideReasons((current) => ({
                              ...current,
                              [item.id]: event.target.value,
                            }))}
                            maxLength={500}
                            rows={2}
                            required
                            placeholder="Explain why this product should not appear to customers"
                          />
                        </label>
                        <button
                          type="submit"
                          className="operations-button operations-button--secondary"
                          disabled={busyId === item.id || !(hideReasons[item.id] ?? '').trim()}
                        >
                          {busyId === item.id ? 'Saving…' : 'Confirm hide'}
                        </button>
                      </form>
                    </details>
                  )}
                </div>
              </article>
            ))}
          </div>

          <div className="catalogue-review__pagination">
            <span>Page {currentPage + 1} of {Math.max(totalPages, 1)}</span>
            <div>
              <button
                type="button"
                className="operations-button operations-button--secondary"
                disabled={loading || currentPage <= 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Previous
              </button>
              <button
                type="button"
                className="operations-button operations-button--secondary"
                disabled={loading || currentPage + 1 >= totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Next
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="operations-state">
          {filter === 'NEEDS_REVIEW'
            ? 'The review queue is clear. No products are waiting for moderation.'
            : 'No products match this review status.'}
        </div>
      )}
    </section>
  )
}
