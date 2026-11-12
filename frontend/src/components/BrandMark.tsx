import { Link } from 'react-router'

interface BrandMarkProps {
  light?: boolean
  compact?: boolean
}

export function BrandMark({ light = false, compact = false }: BrandMarkProps) {
  return (
    <Link
      className={`brand${light ? ' brand--light' : ''}`}
      to="/"
      aria-label="Apniko home"
    >
      <span className="brand__word">Apniko{compact ? '' : ''}</span>
    </Link>
  )
}
