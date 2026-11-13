import { useState } from 'react'
import { resolveProductImageUrl } from './marketplace'

interface ProductImageProps {
  imageUrl?: string | null
  productName: string
  category?: string | null
  className?: string
}

export function ProductImage({
  imageUrl,
  productName,
  category,
  className = '',
}: ProductImageProps) {
  const primaryImage = resolveProductImageUrl(imageUrl, category)
  const fallbackImage = resolveProductImageUrl(null, category)
  const [showingFallback, setShowingFallback] = useState(primaryImage === fallbackImage)
  const [imageUnavailable, setImageUnavailable] = useState(false)

  if (imageUnavailable) {
    return (
      <div className={`product-photo product-photo--placeholder ${className}`} aria-label={`${productName} image unavailable`}>
        <span aria-hidden="true">{productName.trim().charAt(0).toUpperCase() || 'P'}</span>
      </div>
    )
  }

  return (
    <img
      className={`product-photo ${className}`}
      src={showingFallback ? fallbackImage : primaryImage}
      alt={productName}
      loading="lazy"
      decoding="async"
      onError={() => {
        if (!showingFallback && primaryImage !== fallbackImage) {
          setShowingFallback(true)
        } else {
          setImageUnavailable(true)
        }
      }}
    />
  )
}
