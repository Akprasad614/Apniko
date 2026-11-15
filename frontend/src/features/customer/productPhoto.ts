export function productPhotoAltText(productName: string): string {
  const name = productName.trim()
  return name ? name + ' packaging photo' : 'Product packaging photo'
}

export function productPhotoCredit(sourceName?: string | null, license?: string | null): string {
  return [sourceName?.trim(), license?.trim()].filter(Boolean).join(' · ')
}
