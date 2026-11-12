# Apniko design system

## Art direction

**Neighbourhood commerce, designed with clarity.** Apniko should feel like a dependable local marketplace: welcoming for customers, practical for shopkeepers and clear for delivery and administration teams. The visual identity uses a confident navy structure, a fresh green commerce accent and a small amount of warm orange.

Avoid the default generic-SaaS look: no purple/blue gradients, glassmorphism, excessive rounded cards, random metric tiles or decorative charts without a job to do. Do not copy another product's screen layouts or visual assets.

The customer experience should be discovery-led and product-focused. Shopkeeper and delivery interfaces should feel calm, legible and operationally efficient. Admin screens should prioritize safe actions and traceability.

## Brand identity and logo status

**Apniko — Your neighbourhood, closer.**

The final logo is pending. The project owner will supply approved artwork. Until then, the application uses a plain text wordmark only; do not add a generated or substitute symbol. The shared component is `frontend/src/components/BrandMark.tsx`.

## Core palette

| Token | Value | Use |
|---|---|---|
| Apniko Navy | `#132B45` | Primary identity, headings and navigation |
| Deep Navy | `#0D2034` | High-contrast dark surfaces |
| Apniko Green | `#168653` | Positive actions and local-commerce emphasis |
| Deep Green | `#0F6A40` | Green hover states and stronger emphasis |
| Apniko Orange | `#F28A2B` | Secondary accent |
| Green Soft | `#E8F5ED` | Quiet positive surfaces |
| Orange Soft | `#FFF1E3` | Quiet warning/highlight surfaces |
| Canvas | `#F6F8F7` | Main application background |
| Surface | `#FFFFFF` | Cards, forms and elevated panels |
| Ink | `#202B36` | Primary text |
| Muted | `#657384` | Supporting text |
| Border | `#E1E8E4` | Borders and dividers |

Use navy for structure, green for primary commerce actions and positive states, and orange sparingly for emphasis. Do not use colour as the only way to communicate status; pair it with a label or icon.

All canonical values are centralized in `frontend/src/styles/tokens.css`. New components should use `--apniko-*` variables. Existing `--lc-*` variables remain as compatibility aliases while older feature components are incrementally refreshed.

## Typography

- Body and interface: Inter, falling back to the platform sans-serif stack.
- Display: the same sans-serif family with stronger weight and tighter tracking for a consistent cross-device identity.
- Use strong hierarchy, short line lengths and restrained uppercase labels.
- Use tabular numerals for prices, order totals, balances, counts and transaction lists.
- Do not download remote fonts by default. The current system renders using locally available fonts and does not depend on a font CDN.

## Shape, spacing and composition

- Prefer open layouts, clear hierarchy and useful section dividers over a grid of identical cards.
- Use small radii for controls, medium radii for functional panels and larger radii for major surfaces.
- Use whitespace to separate content. Keep status, price and next action visually obvious.
- Use shared radius, spacing, page-width, shadow and motion tokens rather than one-off values.
- Use motion sparingly; respect `prefers-reduced-motion`.
- Use product photography only when it adds information. Avoid random decoration and placeholder imagery presented as real inventory.

## Interaction and accessibility

- Every interactive element needs a visible keyboard focus state.
- Form controls must have explicit labels and understandable error text.
- Colour must not be the only way to communicate status.
- Keep practical touch targets at least 44px wherever possible and avoid hover-only interaction.
- Responsive layouts must work at 320px, tablet widths and desktop widths.
- Respect mobile safe-area insets and avoid horizontal page overflow.
- Use semantic landmarks, heading order, skip links and descriptive accessible names.
- Honour `prefers-reduced-motion`.

## Page-specific direction

- **Customer:** product discovery first, helpful categories, visible price/stock/shop information, and a simple path to checkout.
- **Shopkeeper:** efficient daily-work interface; inventory, incoming orders and low-stock actions should be scan-friendly.
- **Delivery:** action-first; show assignment, pickup and delivery states clearly with large primary actions.
- **Admin:** restrained operational interface; prioritize safe actions, audit context, clear confirmation and permissions.

## Reusable UI foundation

The shared primitives in `frontend/src/styles/brand-system.css` include page containers, surfaces, status pills, icon buttons, controls, dividers and numeric display utilities. Use these when introducing new feature screens so the portals retain one consistent language.
