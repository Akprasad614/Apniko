import './category-mark.css'

interface CategoryMarkProps {
  category?: string | null
  size?: 'product' | 'thumbnail' | 'tile'
}

function categorySymbol(category: string): string {
  const value = category.toLocaleLowerCase()
  if (value.includes('fruit') || value.includes('vegetable') || value.includes('produce')) return '🥬'
  if (value.includes('dairy') || value.includes('milk') || value.includes('egg')) return '🥛'
  if (value.includes('snack') || value.includes('biscuit') || value.includes('confection')) return '🍪'
  if (value.includes('grain') || value.includes('staple') || value.includes('rice') || value.includes('atta') || value.includes('oil') || value.includes('masala')) return '🌾'
  if (value.includes('home') || value.includes('household') || value.includes('clean')) return '🧽'
  if (value.includes('bakery') || value.includes('bread')) return '🥐'
  if (value.includes('beverage') || value.includes('drink')) return '🥤'
  if (value.includes('beauty') || value.includes('hygiene')) return '🧴'
  if (value.includes('baby')) return '🍼'
  if (value.includes('pet')) return '🐾'
  return '🛒'
}

function categoryTone(category: string): string {
  const value = category.toLocaleLowerCase()
  if (value.includes('fruit') || value.includes('vegetable') || value.includes('produce')) return 'mint'
  if (value.includes('dairy') || value.includes('milk') || value.includes('egg')) return 'sky'
  if (value.includes('snack') || value.includes('biscuit') || value.includes('confection')) return 'peach'
  if (value.includes('grain') || value.includes('staple') || value.includes('rice') || value.includes('atta') || value.includes('oil') || value.includes('masala')) return 'sun'
  if (value.includes('home') || value.includes('household') || value.includes('clean')) return 'lavender'
  if (value.includes('bakery') || value.includes('bread')) return 'rose'
  if (value.includes('beverage') || value.includes('drink')) return 'sky'
  if (value.includes('beauty') || value.includes('hygiene')) return 'lavender'
  return 'mint'
}

export function CategoryMark({ category, size = 'product' }: CategoryMarkProps) {
  const value = category?.trim() || 'grocery'
  return (
    <span
      className={`apniko-category-mark apniko-category-mark--${size} apniko-category-mark--${categoryTone(value)}`}
      aria-hidden="true"
    >
      <span>{categorySymbol(value)}</span>
    </span>
  )
}
