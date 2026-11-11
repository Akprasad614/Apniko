import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <main className="not-found wrap">
      <p className="eyebrow">404 / Wrong turn</p>
      <h1>This page isn't<br /><em>on the map.</em></h1>
      <Link className="button button--lime" to="/">Back to LocalCart <span aria-hidden="true">↗</span></Link>
    </main>
  )
}
