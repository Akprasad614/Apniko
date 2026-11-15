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
}

export function ApprovedProductImage({
  imageUrl,
  productName,
  sourceName,
  sourceUrl,
  license,
  attribution,
}: ApprovedProductImageProps) {
  const [failed, setFailed] = useState(false)
  const credit = attribution?.trim() || productPhotoCredit(sourceName, license)

  useEffect(() => setFailed(false), [imageUrl])

  return (
    <div className="approved-product-image">
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
          aria-label={productName + ': photo not available'}>
          <span aria-hidden="true">◇</span>
          <small>{failed ? 'Photo unavailable' : 'Photo not available'}</small>
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
