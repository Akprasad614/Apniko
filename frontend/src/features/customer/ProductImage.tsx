import { useState } from 'react'
import { getProductCategoryIcon } from './marketplace'
import './product-image.css'

interface ProductImageProps {
  name: string
  category: string | null
  imageUrl?: string | null
  compact?: boolean
}

function isSafeImageUrl(value: string | null | undefined): value is string {
  if (!value) return false
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:'
  } catch {
    return false
  }
}

export function ProductImage({ name, category, imageUrl, compact = false }: ProductImageProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const source = isSafeImageUrl(imageUrl) && failedUrl !== imageUrl ? imageUrl : null

  if (source) {
    return (
      <div className={`product-image${compact ? ' product-image--compact' : ''}`}>
        <img
          src={source}
          alt={name}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={() => setFailedUrl(source)}
        />
      </div>
    )
  }

  return (
    <div
      className={`product-image product-image--fallback${compact ? ' product-image--compact' : ''}`}
      role="img"
      aria-label={`${name} image placeholder`}
    >
      <span aria-hidden="true">{getProductCategoryIcon(category)}</span>
    </div>
  )
}
