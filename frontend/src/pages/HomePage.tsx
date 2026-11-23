import { Link } from 'react-router'
import { Wordmark } from '../components/Wordmark'

export function HomePage() {
  return (
    <div className="site-shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="site-header wrap">
        <Wordmark />
        <nav className="site-nav" aria-label="Main navigation">
          <a className="site-nav__link" href="#how-it-works">How it works</a>
          <a className="site-nav__link" href="#local-stores">For local stores</a>
          <Link className="site-nav__login" to="/login">Sign in <span aria-hidden="true">↗</span></Link>
        </nav>
      </header>

      <main id="main">
        <section className="hero wrap">
          <div className="hero__copy">
            <p className="eyebrow"><span className="eyebrow__dot" /> A little closer to home</p>
            <h1>Good groceries.<br /><em>Closer to home.</em></h1>
            <p className="hero__intro">
              Your neighbourhood shops, brought together in one simple place.
              Find the everyday things you need and keep local commerce moving.
            </p>
            <div className="hero__actions">
              <Link className="button button--lime" to="/register">Create a customer account <span aria-hidden="true">↗</span></Link>
              <Link className="text-link" to="/login">Already with Apniko? Sign in</Link>
            </div>
            <div className="hero__note">
              <span className="hero__note-mark" aria-hidden="true">✳</span>
              <span>Made for the shops that already know your name.</span>
            </div>
          </div>

          <div className="hero-art" aria-label="Illustration of a local grocery bag with fresh produce" role="img">
            <div className="hero-art__sun" />
            <div className="hero-art__orbit hero-art__orbit--one" />
            <div className="hero-art__orbit hero-art__orbit--two" />
            <div className="hero-art__label hero-art__label--top"><span>01</span> NEARBY, NOT FAR AWAY</div>
            <div className="produce produce--leaf produce--leaf-one" />
            <div className="produce produce--leaf produce--leaf-two" />
            <div className="produce produce--tomato" />
            <div className="produce produce--citrus" />
            <div className="grocery-bag">
              <div className="grocery-bag__handle" />
              <div className="grocery-bag__front">
                <span className="grocery-bag__word">LOCAL<br />FINDS</span>
              </div>
            </div>
            <div className="hero-art__label hero-art__label--bottom">THE NEIGHBOURHOOD EDITION <span>✳</span></div>
          </div>
        </section>

        <section className="principles wrap" id="how-it-works" aria-labelledby="principles-title">
          <div className="section-heading">
            <p className="eyebrow">Small shops. One shared shelf.</p>
            <h2 id="principles-title">A simpler way to shop <em>local.</em></h2>
          </div>
          <div className="principles__list">
            <article className="principle">
              <span className="principle__number">01 / FIND</span>
              <h3>Start close by.</h3>
              <p>Discover shops and everyday essentials from your neighbourhood.</p>
            </article>
            <article className="principle">
              <span className="principle__number">02 / CHOOSE</span>
              <h3>Pick your usuals.</h3>
              <p>Bring the things on your list together without the extra runaround.</p>
            </article>
            <article className="principle">
              <span className="principle__number">03 / SUPPORT</span>
              <h3>Keep it local.</h3>
              <p>Make room for the people and small businesses behind your daily needs.</p>
            </article>
          </div>
        </section>

        <section className="local-stores" id="local-stores">
          <div className="local-stores__inner wrap">
            <div>
              <p className="eyebrow eyebrow--light">For the people behind the counter</p>
              <h2>Your shop has a place<br />in the <em>neighbourhood.</em></h2>
            </div>
            <div className="local-stores__aside">
              <p>Apniko is being built to help independent stores manage orders and reach the customers already around them.</p>
              <Link className="button button--outline" to="/login">Shopkeeper sign in <span aria-hidden="true">↗</span></Link>
              <span className="local-stores__fine">Shopkeeper accounts are created by an Apniko administrator.</span>
            </div>
            <span className="local-stores__asterisk" aria-hidden="true">✳</span>
          </div>
        </section>
      </main>

      <footer className="site-footer wrap">
        <Wordmark />
        <span>Good things happen close to home.</span>
        <span className="site-footer__copy">© {new Date().getFullYear()} Apniko</span>
      </footer>
    </div>
  )
}
