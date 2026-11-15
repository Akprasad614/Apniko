import { useCallback, useEffect, useState } from 'react'
import { ApiError, apiRequest } from '../../lib/api'
import './product-photo-review.css'

interface ProductPhotoReviewItem {
  productId: number
  productName: string
  brand: string | null
  barcode: string | null
  packSize: string | null
  sourceProductName: string | null
  candidateImageUrl: string | null
  candidateSourceName: string | null
  candidateSourceUrl: string | null
  candidateLicense: string | null
  candidateAttribution: string | null
  approvedImageUrl: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string | null
  reviewedAt: string | null
  reviewedBy: string | null
  reviewReason: string | null
}
interface ReviewPage {
  content: ProductPhotoReviewItem[]
  number: number
  totalElements: number
  totalPages: number
}
function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has expired. Sign in again.'
    if (error.status === 403) return 'Only an administrator can review product photographs.'
    return error.message
  }
  return error instanceof Error ? error.message : 'Could not load photo candidates.'
}

export function ProductPhotoReviewQueue({ token }: { token: string }) {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<ReviewPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [reasons, setReasons] = useState<Record<number, string>>({})

  const loadQueue = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    setError('')
    try {
      const next = await apiRequest<ReviewPage>(
        '/api/admin/catalogue/photo-review-queue?page=' + page + '&size=12',
        {}, token,
      )
      setResult(next)
    } catch (loadError) {
      setError(messageFor(loadError))
      setResult(null)
    } finally {
      if (!quiet) setLoading(false)
    }
  }, [page, token])

  useEffect(() => { void loadQueue() }, [loadQueue])

  async function review(item: ProductPhotoReviewItem, status: 'APPROVED' | 'REJECTED') {
    const reason = (reasons[item.productId] ?? '').trim()
    if (status === 'REJECTED' && !reason) {
      setError('Add a reason before rejecting this photograph.')
      return
    }
    setBusyId(item.productId)
    setError('')
    setNotice('')
    try {
      await apiRequest<ProductPhotoReviewItem>(
        '/api/admin/catalogue/products/' + item.productId + '/photo-review',
        { method: 'PATCH', body: JSON.stringify({ status, reason: reason || null }) },
        token,
      )
      setNotice(status === 'APPROVED'
        ? 'Approved photo for ' + item.productName + '.'
        : 'Rejected photo for ' + item.productName + '.')
      setReasons((current) => {
        const next = { ...current }
        delete next[item.productId]
        return next
      })
      if (result?.content.length === 1 && page > 0) setPage((current) => current - 1)
      else await loadQueue(true)
    } catch (reviewError) {
      setError(messageFor(reviewError))
    } finally {
      setBusyId(null)
    }
  }

  const items = result?.content ?? []
  return (
    <section className="product-photo-review" aria-labelledby="product-photo-review-heading">
      <div className="product-photo-review__heading">
        <div>
          <p className="operations-eyebrow">AUTHENTIC PRODUCT PHOTOGRAPHY</p>
          <h3 id="product-photo-review-heading">Product photo review</h3>
          <p>Check the exact barcode, product name and pack before approving a photograph.</p>
        </div>
        <button type="button" className="operations-button operations-button--secondary"
          disabled={loading} onClick={() => void loadQueue()}>
          {loading ? 'Refreshing…' : 'Refresh photos ↻'}
        </button>
      </div>
      {error && <div className="operations-alert operations-alert--error" role="alert">{error}</div>}
      {notice && <div className="operations-alert operations-alert--success" role="status">{notice}</div>}
      {loading ? (
        <div className="operations-state" role="status">Loading photo candidates…</div>
      ) : items.length === 0 ? (
        <div className="operations-state">
          {result?.totalElements === 0
            ? 'No product photographs are waiting for review.'
            : 'The photo review queue could not be loaded. Refresh to try again.'}
        </div>
      ) : (
        <>
          <div className="product-photo-review__list">
            {items.map((item) => (
              <article className="product-photo-review-card" key={item.productId}>
                <div className="product-photo-review-card__image">
                  {item.candidateImageUrl
                    ? <img src={item.candidateImageUrl} alt={item.sourceProductName || item.productName} loading="lazy" />
                    : <span>Image unavailable</span>}
                </div>
                <div className="product-photo-review-card__details">
                  <p className="product-photo-review-card__eyebrow">PRODUCT #{item.productId}</p>
                  <h4>{item.productName}</h4>
                  <p className="product-photo-review-card__meta">
                    {[item.brand, item.packSize, item.barcode ? 'GTIN ' + item.barcode : null]
                      .filter(Boolean).join(' · ')}
                  </p>
                  <dl>
                    <div><dt>Source product name</dt><dd>{item.sourceProductName || 'Not supplied'}</dd></div>
                    <div><dt>Source</dt><dd>{item.candidateSourceName || 'Not recorded'}</dd></div>
                    <div><dt>Licence</dt><dd>{item.candidateLicense || 'Not recorded'}</dd></div>
                    <div><dt>Attribution</dt><dd>{item.candidateAttribution || 'Not recorded'}</dd></div>
                  </dl>
                  {item.candidateSourceUrl && (
                    <a href={item.candidateSourceUrl} target="_blank" rel="noreferrer">Open source product record ↗</a>
                  )}
                  <label className="product-photo-review-card__reason">
                    Rejection reason (required when rejecting)
                    <textarea value={reasons[item.productId] ?? ''}
                      onChange={(event) => setReasons((current) => ({
                        ...current, [item.productId]: event.target.value,
                      }))}
                      maxLength={500} rows={2} placeholder="e.g. Different pack size or label" />
                  </label>
                  <div className="product-photo-review-card__actions">
                    <button type="button" className="operations-button"
                      disabled={busyId === item.productId}
                      onClick={() => void review(item, 'APPROVED')}>
                      {busyId === item.productId ? 'Saving…' : 'Approve photo'}
                    </button>
                    <button type="button" className="operations-button operations-button--secondary"
                      disabled={busyId === item.productId || !(reasons[item.productId] ?? '').trim()}
                      onClick={() => void review(item, 'REJECTED')}>
                      Reject photo
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
          <div className="product-photo-review__pagination">
            <span>{result?.totalElements ?? 0} waiting · Page {(result?.number ?? page) + 1} of {Math.max(result?.totalPages ?? 1, 1)}</span>
            <div>
              <button type="button" className="operations-button operations-button--secondary"
                disabled={loading || page <= 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
              <button type="button" className="operations-button operations-button--secondary"
                disabled={loading || !result || page + 1 >= result.totalPages}
                onClick={() => setPage((current) => current + 1)}>Next</button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
