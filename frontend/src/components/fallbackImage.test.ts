import { describe, expect, it } from 'vitest'
import { fallbackImageMessage } from './FallbackImage'

describe('product image fallback labels', () => {
  it('shows the unavailable label when no image URL is supplied', () => {
    expect(fallbackImageMessage(false, 'Photo not yet available'))
      .toBe('Photo not yet available')
  })

  it('shows a distinct message after an image URL fails to load', () => {
    expect(fallbackImageMessage(true, 'Image unavailable'))
      .toBe('Photo could not load')
  })
})
