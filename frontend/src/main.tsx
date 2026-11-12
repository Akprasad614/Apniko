import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import './styles/tokens.css'
import './styles/global.css'
import './styles/app.css'
import './styles/brand-system.css'

const rootElement = document.getElementById('root')
if (!rootElement) throw new Error('Could not find the Apniko app root element.')

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
