import { describe, expect, it } from 'vitest'
import {
  catalogueReviewStatusLabel,
  formatCatalogueReviewDate,
} from './catalogueReview'

describe('catalogue review display helpers', () => {
  it('uses clear labels for all moderation states and legacy products', () => {
    expect(catalogueReviewStatusLabel('NEEDS_REVIEW')).toBe('Needs review')
    expect(catalogueReviewStatusLabel('ACTIVE')).toBe('Active')
    expect(catalogueReviewStatusLabel('HIDDEN')).toBe('Hidden')
    expect(catalogueReviewStatusLabel(null)).toBe('Legacy item')
  })

  it('formats imported and reviewed dates for the Indian locale', () => {
    expect(formatCatalogueReviewDate('2026-11-15T12:00:00Z')).toMatch(/15 Nov 2026/)
  })

  it('handles missing and invalid date values without throwing', () => {
    expect(formatCatalogueReviewDate(null)).toBe('Not recorded')
    expect(formatCatalogueReviewDate('not-a-date')).toBe('Not recorded')
  })
})


describe('catalogue category name validation', () => {
  const categories = [
    { id: 1, name: 'Staples', subcategories: [{ id: 11, name: 'Rice' }] },
    { id: 2, name: 'Snacks', subcategories: [{ id: 22, name: 'Chips' }] },
  ]

  it('detects duplicate top-level category names after trimming and case folding', () => {
    expect(categoryNameExists(categories, ' staples ', null)).toBe(true)
    expect(categoryNameExists(categories, 'Beverages', null)).toBe(false)
  })

  it('detects a duplicate only under the selected parent category', () => {
    expect(categoryNameExists(categories, ' rice ', 1)).toBe(true)
    expect(categoryNameExists(categories, 'Rice', 2)).toBe(false)
    expect(categoryNameExists(categories, 'Chips', 1)).toBe(false)
  })

  it('does not treat an empty name as a duplicate', () => {
    expect(categoryNameExists(categories, '   ', null)).toBe(false)
  })
})
