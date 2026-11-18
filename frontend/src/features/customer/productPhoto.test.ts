import { describe, expect, it } from 'vitest'
import {
  productPhotoAltText,
  productPhotoCredit,
  productPhotoLicenseUrl,
} from './productPhoto'

describe('approved product photo presentation', () => {
  it('uses the actual product name for accessible alternative text', () => {
    expect(productPhotoAltText('  Toned Milk  ')).toBe('Toned Milk packaging photo')
    expect(productPhotoAltText('')).toBe('Product packaging photo')
  })

  it('keeps contributor attribution and source identity together', () => {
    expect(productPhotoCredit('Open Food Facts contributors', 'Open Food Facts'))
      .toBe('Open Food Facts contributors · Open Food Facts')
    expect(productPhotoCredit(null, 'Brand catalogue')).toBe('Brand catalogue')
    expect(productPhotoCredit(null, null)).toBe('')
  })

  it('links Open Facts photo credits to the CC BY-SA 3.0 legal deed', () => {
    expect(productPhotoLicenseUrl('CC BY-SA'))
      .toBe('https://creativecommons.org/licenses/by-sa/3.0/')
    expect(productPhotoLicenseUrl('MANUFACTURER_AUTHORIZED')).toBeNull()
    expect(productPhotoLicenseUrl(null)).toBeNull()
  })
})
