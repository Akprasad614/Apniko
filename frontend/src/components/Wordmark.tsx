import { Link } from 'react-router'

interface WordmarkProps {
  light?: boolean
  className?: string
}

export function Wordmark({ light = false, className = '' }: WordmarkProps) {
  const classes = ['wordmark', light ? 'wordmark--light' : '', className]
    .filter(Boolean)
    .join(' ')

  return (
    <Link className={classes} to="/" aria-label="Apniko home">
      apniko<span>.</span>
    </Link>
  )
}
