export function productPhotoAltText(productName: string): string {
  const name = productName.trim()
  return name ? name + ' packaging photo' : 'Product packaging photo'
}

export function productPhotoCredit(
  attribution?: string | null,
  sourceName?: string | null,
): string {
  return [attribution?.trim(), sourceName?.trim()].filter(Boolean).join(' · ')
}

export function productPhotoLicenseUrl(license?: string | null): string | null {
  const normalized = (license ?? '')
    .trim()
    .replace(/[-_]/g, ' ')
    .replace(/\s+/g, ' ')
    .toUpperCase()

  return normalized === 'CC BY SA'
    ? 'https://creativecommons.org/licenses/by-sa/3.0/'
    : null
}
