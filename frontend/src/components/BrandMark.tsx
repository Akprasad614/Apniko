import { Link } from 'react-router'

interface BrandMarkProps {
  light?: boolean
  compact?: boolean
}

export function BrandMark({ light = false, compact = false }: BrandMarkProps) {
  return (
    <Link className={`brand${light ? ' brand--light' : ''}`} to="/" aria-label="Apniko home">
      <span className="brand__symbol" aria-hidden="true">
        <svg viewBox="0 0 36 36" role="presentation">
          <path d="M18 4.8c-7.1 0-12.2 5.5-12.2 12.1 0 7.8 6 13.4 12.2 13.4s12.2-5.6 12.2-13.4C30.2 10.3 25.1 4.8 18 4.8Z" fill="currentColor" opacity=".16" />
          <path d="M9.3 21.8c7.1.2 12.2-4.2 15-11.7 1.3 8.8-1.6 15.1-8.1 17.2-3 .9-5.7-.7-6.9-5.5Z" fill="currentColor" />
          <path d="M13.2 24.4c1.3-4.6 4.5-8 9.3-10.3" fill="none" stroke="#f5f2e8" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </span>
      {!compact && <span className="brand__word">apniko<span className="brand__period">.</span></span>}
    </Link>
  )
}
