import { Link } from 'react-router'

interface BrandMarkProps {
  light?: boolean
  compact?: boolean
}

export function BrandMark({ light = false, compact = false }: BrandMarkProps) {
  const className = [
    'brand',
    light ? 'brand--light' : '',
    compact ? 'brand--compact' : '',
  ].filter(Boolean).join(' ')

  return (
    <Link className={className} to="/" aria-label="Apniko home">
      <span className="brand__symbol" aria-hidden="true">
        <svg viewBox="0 0 96 96" focusable="false">
          <path
            className="brand-mark__a"
            fillRule="evenodd"
            d="M48 5 5 86h19l24-44 24 44h19L48 5Zm0 33L27 86h42L48 38Z"
          />
          <path
            className="brand-mark__pin"
            d="M48 4c-9.4 0-16 6.8-16 15.7C32 32.5 48 44 48 44s16-11.5 16-24.3C64 10.8 57.4 4 48 4Z"
          />
          <circle className="brand-mark__pin-core" cx="48" cy="19" r="6" />
          <path className="brand-mark__shop" d="M37 61h22v17H37z" />
          <path className="brand-mark__shop-door" d="M45 68h7v10h-7z" />
          <path className="brand-mark__awning" d="m33 61 4-9h22l4 9H33Z" />
          <path className="brand-mark__awning-accent" d="M36 61v-7h7v7zm14 0v-7h7v7z" />
          <path className="brand-mark__sweep" d="M8 84c17 8 62 8 80-1" />
        </svg>
      </span>
      {!compact && (
        <span className="brand__word">
          <span>Apni</span><span className="brand__word-accent">ko</span><span className="brand__period">.</span>
        </span>
      )}
    </Link>
  )
}
