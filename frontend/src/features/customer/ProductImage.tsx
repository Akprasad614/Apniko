import { useState } from 'react'
import {
  getProductCategoryIcon,
  getProductCategoryPhoto,
} from './marketplace'
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
  const [failedUrls, setFailedUrls] = useState<string[]>([])
  const productPhoto = isSafeImageUrl(imageUrl) ? imageUrl : null
  const categoryPhoto = getProductCategoryPhoto(category)
  const source = [productPhoto, categoryPhoto].find(
    (candidate) => candidate && !failedUrls.includes(candidate),
  ) ?? null

  if (source) {
    return (
      <div className={`product-image${compact ? ' product-image--compact' : ''}`}>
        <img
          src={source}
          alt={productPhoto === source ? name : `${category || 'Grocery'} category photo for ${name}`}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={() => setFailedUrls((current) => [...current, source])}
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
