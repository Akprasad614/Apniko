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
