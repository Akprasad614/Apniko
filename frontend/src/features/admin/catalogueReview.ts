export type CatalogueReviewStatus = 'ACTIVE' | 'NEEDS_REVIEW' | 'HIDDEN'
export type CatalogueReviewFilter = CatalogueReviewStatus | 'ALL'

export interface CatalogueReviewItem {
  id: number
  name: string
  brand: string | null
  barcode: string | null
  packSize: string | null
  category: string | null
  subcategory: string | null
  catalogueSource: string | null
  status: CatalogueReviewStatus | null
  reviewReason: string | null
  importedAt: string | null
  reviewedAt: string | null
  reviewedBy: string | null
}

export interface CatalogueReviewPage {
  content: CatalogueReviewItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function catalogueReviewStatusLabel(status: CatalogueReviewStatus | null): string {
  if (!status) return 'Legacy item'
  switch (status) {
    case 'ACTIVE':
      return 'Active'
    case 'NEEDS_REVIEW':
      return 'Needs review'
    case 'HIDDEN':
      return 'Hidden'
  }
}

export function formatCatalogueReviewDate(value: string | null): string {
  if (!value) return 'Not recorded'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Not recorded'
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date)
}


export interface CatalogueCategoryNode {
  id: number
  name: string
  subcategories: Array<{ id: number; name: string }>
}

export function categoryNameExists(
  categories: CatalogueCategoryNode[],
  name: string,
  parentCategoryId: number | null,
): boolean {
  const normalized = name.trim().replace(/\\s+/g, ' ').toLocaleLowerCase()
  if (!normalized) return false

  if (parentCategoryId === null) {
    return categories.some((category) => category.name.toLocaleLowerCase() === normalized)
  }

  const parent = categories.find((category) => category.id === parentCategoryId)
  return parent?.subcategories.some((child) => child.name.toLocaleLowerCase() === normalized) ?? false
}
