import { useEffect, useState } from 'react'

export function fallbackImageMessage(failed: boolean, unavailableLabel: string): string {
  return failed ? 'Photo could not load' : unavailableLabel
}

interface FallbackImageProps {
  src?: string | null
  alt: string
  unavailableLabel: string
  className: string
  imageClassName: string
  fallbackClassName: string
}

export function FallbackImage({
  src,
  alt,
  unavailableLabel,
  className,
  imageClassName,
  fallbackClassName,
}: FallbackImageProps) {
  const [failed, setFailed] = useState(false)

  useEffect(() => setFailed(false), [src])

  return (
    <span className={className}>
      {src && !failed ? (
        <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} className={imageClassName} />
      ) : (
        <span className={fallbackClassName} role="img"
          aria-label={alt + ': ' + fallbackImageMessage(failed, unavailableLabel)}>
          <span aria-hidden="true">▧</span>
          <small>{fallbackImageMessage(failed, unavailableLabel)}</small>
        </span>
      )}
    </span>
  )
}
