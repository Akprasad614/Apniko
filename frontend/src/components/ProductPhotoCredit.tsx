import {
  productPhotoCredit,
  productPhotoLicenseUrl,
} from '../features/customer/productPhoto'

interface ProductPhotoCreditProps {
  sourceName?: string | null
  sourceUrl?: string | null
  license?: string | null
  attribution?: string | null
  className?: string
}

export function ProductPhotoCredit({
  sourceName,
  sourceUrl,
  license,
  attribution,
  className = 'product-photo-credit',
}: ProductPhotoCreditProps) {
  const credit = productPhotoCredit(attribution, sourceName)
  const licenseText = license?.trim()
  const licenseUrl = productPhotoLicenseUrl(license)

  if (!credit && !licenseText) return null

  return (
    <small className={className}>
      {credit && (sourceUrl
        ? <a href={sourceUrl} target="_blank" rel="noopener noreferrer">{credit}</a>
        : <span>{credit}</span>)}
      {licenseText && (
        <>
          {credit ? ' · ' : ''}
          {licenseUrl
            ? <a href={licenseUrl} target="_blank" rel="noopener noreferrer">{licenseText}</a>
            : <span>{licenseText}</span>}
        </>
      )}
    </small>
  )
}
