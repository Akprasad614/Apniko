import { useEffect, useState } from 'react'
import { ProductPhotoCredit } from '../../components/ProductPhotoCredit'
import { productPhotoAltText } from './productPhoto'
import './approved-product-image.css'

interface ApprovedProductImageProps {
  imageUrl?: string | null
  productName: string
  sourceName?: string | null
  sourceUrl?: string | null
  license?: string | null
  attribution?: string | null
  variant?: 'thumbnail' | 'card'
  showCredit?: boolean
}

export function ApprovedProductImage({
  imageUrl,
  productName,
  sourceName,
  sourceUrl,
  license,
  attribution,
  variant = 'thumbnail',
  showCredit = true,
}: ApprovedProductImageProps) {
  const [failed, setFailed] = useState(false)

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
      {showCredit && imageUrl && !failed && (
        <ProductPhotoCredit
          sourceName={sourceName}
          sourceUrl={sourceUrl}
          license={license}
          attribution={attribution}
          className="approved-product-image__credit"
        />
      )}
    </div>
  )
}
