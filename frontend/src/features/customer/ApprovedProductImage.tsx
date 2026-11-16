import { useEffect, useState } from 'react'
import { productPhotoAltText, productPhotoCredit } from './productPhoto'
import './approved-product-image.css'

interface ApprovedProductImageProps {
  imageUrl?: string | null
  productName: string
  sourceName?: string | null
  sourceUrl?: string | null
  license?: string | null
  attribution?: string | null
  variant?: 'thumbnail' | 'card'
}

export function ApprovedProductImage({
  imageUrl,
  productName,
  sourceName,
  sourceUrl,
  license,
  attribution,
  variant = 'thumbnail',
}: ApprovedProductImageProps) {
  const [failed, setFailed] = useState(false)
  const credit = attribution?.trim() || productPhotoCredit(sourceName, license)

  useEffect(() => setFailed(false), [imageUrl])

  return (
    <div className={`approved-product-image approved-product-image--${variant}`}>
      {imageUrl && !failed ? (
        <img
          src={imageUrl}
          alt={productPhotoAltText(productName)}
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
        />
      ) : (
        <div className="approved-product-image__empty" role="img"
          aria-label={productName + ': verified product photo not yet available'}>
          <span className="approved-product-image__empty-mark" aria-hidden="true">▧</span>
          <strong>{failed ? 'Photo could not load' : 'Photo not yet available'}</strong>
          {variant === 'card' && (
            <small>Apniko displays verified product photographs only.</small>
          )}
        </div>
      )}
      {imageUrl && !failed && credit && (
        <small className="approved-product-image__credit" title={credit}>
          {sourceUrl ? <a href={sourceUrl} target="_blank" rel="noreferrer">{credit}</a> : credit}
        </small>
      )}
    </div>
  )
}
