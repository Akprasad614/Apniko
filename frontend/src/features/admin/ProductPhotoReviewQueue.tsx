import { useCallback, useEffect, useState } from 'react'
import { FallbackImage } from '../../components/FallbackImage'
import { ApiError, apiRequest } from '../../lib/api'
import './product-photo-review.css'

interface ProductPhotoReviewItem {
  productId: number
  productName: string
  brand: string | null
  barcode: string | null
  packSize: string | null
  sourceProductName: string | null
  sourceProductBrand: string | null
  sourcePackSize: string | null
  candidateAuthorizationReference: string | null
  authorizationReference: string | null
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

interface PhotoRolloutReport {
  totalCatalogueProducts: number
  productsWithBarcodeField: number
  productsWithoutApprovedPhoto: number
  productsWithApprovedPhoto: number
  pendingCandidates: number
  rejectedCandidates: number
  approvedOpenFactsPhotos: number
  approvedManufacturerAuthorizedPhotos: number
  productsEligibleForFreshLookup: number
  retiredCandidates: number
}
interface RetirementResult {
  retiredCandidates: number
  preservedPreviouslyApprovedPhotos: number
  message: string
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
  const [report, setReport] = useState<PhotoRolloutReport | null>(null)
  const [retiring, setRetiring] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [reasons, setReasons] = useState<Record<number, string>>({})
  const [verification, setVerification] = useState<Record<number, boolean>>({})

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
    }
    try {
      const summary = await apiRequest<PhotoRolloutReport>(
        '/api/admin/catalogue/photo-rollout-report',
        {}, token,
      )
      setReport(summary)
    } catch {
      setReport(null)
    } finally {
      if (!quiet) setLoading(false)
    }
  }, [page, token])

  useEffect(() => { void loadQueue() }, [loadQueue])


  async function retireLegacyCandidates() {
    const pending = report?.pendingCandidates ?? 0
    if (!pending || retiring) return
    const confirmed = window.confirm(
      'Retire all ' + pending + ' pending photo candidate(s) from the active queue? ' +
      'Their image URLs, source details and review reason will be preserved in the photo review history. ' +
      'Products without an approved photo will become eligible for a fresh strict-match lookup. ' +
      'Previously approved photos will remain published.'
    )
    if (!confirmed) return

    setRetiring(true)
    setError('')
    setNotice('')
    try {
      const result = await apiRequest<RetirementResult>(
        '/api/admin/catalogue/photo-review-queue/retire-unverified',
        {
          method: 'POST',
          body: JSON.stringify({
            reason: 'Pending candidate retired from the active queue for Phase 3B re-review. The original image and source metadata are retained in the audit history; no approval is implied.',
            confirmRetirement: true,
          }),
        },
        token,
      )
      setNotice(
        result.retiredCandidates + ' legacy candidate(s) retired. ' +
        result.preservedPreviouslyApprovedPhotos + ' previously approved photo(s) preserved.'
      )
      await loadQueue(true)
    } catch (retireError) {
      setError(messageFor(retireError))
    } finally {
      setRetiring(false)
    }
  }

  async function review(item: ProductPhotoReviewItem, status: 'APPROVED' | 'REJECTED') {
    const reason = (reasons[item.productId] ?? '').trim()
    if (status === 'REJECTED' && !reason) {
      setError('Add a reason before rejecting this photograph.')
      return
    }
    if (status === 'APPROVED' && !verification[item.productId]) {
      setError('Confirm the product match and image-use permission before approving this photograph.')
      return
    }
    setBusyId(item.productId)
    setError('')
    setNotice('')
    try {
      await apiRequest<ProductPhotoReviewItem>(
        '/api/admin/catalogue/products/' + item.productId + '/photo-review',
        {
          method: 'PATCH',
          body: JSON.stringify({
            status,
            reason: reason || null,
            verificationConfirmed: status === 'APPROVED' && verification[item.productId] === true,
          }),
        },
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
      setVerification((current) => {
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
        <div className="product-photo-review__toolbar">
          <button type="button" className="operations-button operations-button--secondary"
            disabled={loading} onClick={() => void loadQueue()}>
            {loading ? 'Refreshing…' : 'Refresh photos ↻'}
          </button>
          <button type="button" className="operations-button operations-button--secondary"
            disabled={loading || retiring || !report?.pendingCandidates}
            onClick={() => void retireLegacyCandidates()}>
            {retiring ? 'Retiring…' : 'Retire old candidates for fresh lookup'}
          </button>
        </div>
      </div>
      {report && (
        <div className="product-photo-rollout">
          <div><span>Total catalogue</span><strong>{report.totalCatalogueProducts.toLocaleString('en-IN')}</strong></div>
          <div><span>Barcode field present</span><strong>{report.productsWithBarcodeField.toLocaleString('en-IN')}</strong></div>
          <div><span>Approved photographs</span><strong>{report.productsWithApprovedPhoto.toLocaleString('en-IN')}</strong></div>
          <div><span>Awaiting review</span><strong>{report.pendingCandidates.toLocaleString('en-IN')}</strong></div>
          <div><span>Eligible for fresh lookup</span><strong>{report.productsEligibleForFreshLookup.toLocaleString('en-IN')}</strong></div>
          <div><span>Retired candidates (audited)</span><strong>{report.retiredCandidates.toLocaleString('en-IN')}</strong></div>
          <p>
            Approved sources: {report.approvedOpenFactsPhotos} Open Facts · {report.approvedManufacturerAuthorizedPhotos} manufacturer/supplier-authorised.
            {report.productsWithoutApprovedPhoto} catalogue product(s) still have no approved image.
          </p>
        </div>
      )}
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
                <div className="product-photo-review-card__image-wrap">
                  <FallbackImage
                    src={item.candidateImageUrl}
                    alt={item.sourceProductName || item.productName}
                    unavailableLabel="Image unavailable"
                    className="product-photo-review-card__image"
                    imageClassName="product-photo-review-card__image-content"
                    fallbackClassName="product-photo-review-card__image-fallback"
                  />
                  {item.candidateImageUrl && (
                    <a href={item.candidateImageUrl} target="_blank" rel="noreferrer">
                      Open full-size photograph ↗
                    </a>
                  )}
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
                    <div><dt>Source brand</dt><dd>{item.sourceProductBrand || 'Not supplied'}</dd></div>
                    <div><dt>Source pack size</dt><dd>{item.sourcePackSize || 'Not supplied'}</dd></div>
                    <div><dt>Source</dt><dd>{item.candidateSourceName || 'Not recorded'}</dd></div>
                    <div><dt>Licence</dt><dd>{item.candidateLicense || 'Not recorded'}</dd></div>
                    <div><dt>Attribution</dt><dd>{item.candidateAttribution || 'Not recorded'}</dd></div>
                    {(item.candidateAuthorizationReference || item.authorizationReference) && (
                      <div><dt>Permission evidence</dt><dd>{item.candidateAuthorizationReference || item.authorizationReference}</dd></div>
                    )}
                  </dl>
                  <div className="product-photo-review-card__source-note">
                    {item.candidateSourceName === 'Brand / supplier authorised catalogue'
                      ? 'Supplier-authorised source: verify the recorded permission reference covers Apniko’s intended commercial display.'
                      : 'Community-contributed source: inspect the actual packaging and verify that the image licence and visible artwork rights are suitable for Apniko.'}
                  </div>
                  {item.candidateSourceUrl && (
                    <a href={item.candidateSourceUrl} target="_blank" rel="noreferrer">Open source product record ↗</a>
                  )}
                  <div className="product-photo-review-card__submitted">
                    Submitted: {item.submittedAt
                      ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(item.submittedAt))
                      : 'Not recorded'}
                  </div>
                  <label className="product-photo-review-card__reason">
                    Rejection reason (required when rejecting)
                    <textarea value={reasons[item.productId] ?? ''}
                      onChange={(event) => setReasons((current) => ({
                        ...current, [item.productId]: event.target.value,
                      }))}
                      maxLength={500} rows={2} placeholder="e.g. Different pack size or label" />
                  </label>
                  <label className="product-photo-review-card__verification">
                    <input
                      type="checkbox"
                      checked={verification[item.productId] === true}
                      onChange={(event) => setVerification((current) => ({
                        ...current, [item.productId]: event.target.checked,
                      }))}
                    />
                    <span>
                      I checked the photograph against the exact product, brand, variant and pack size,
                      and verified the recorded licence or permission for Apniko’s intended use.
                    </span>
                  </label>
                  <div className="product-photo-review-card__actions">
                    <button type="button" className="operations-button"
                      disabled={busyId === item.productId || verification[item.productId] !== true}
                      onClick={() => void review(item, 'APPROVED')}>
                      {busyId === item.productId ? 'Saving…' : 'Approve verified photo'}
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
