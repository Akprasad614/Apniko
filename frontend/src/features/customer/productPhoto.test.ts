import { describe, expect, it } from 'vitest'
import { productPhotoAltText, productPhotoCredit } from './productPhoto'

describe('approved product photo presentation', () => {
  it('uses the actual product name for accessible alternative text', () => {
    expect(productPhotoAltText('  Toned Milk  ')).toBe('Toned Milk packaging photo')
    expect(productPhotoAltText('')).toBe('Product packaging photo')
  })

  it('keeps the source and licence together for attribution', () => {
    expect(productPhotoCredit('Open Food Facts', 'CC BY-SA')).toBe('Open Food Facts · CC BY-SA')
    expect(productPhotoCredit('Open Food Facts', null)).toBe('Open Food Facts')
    expect(productPhotoCredit(null, null)).toBe('')
  })
})
