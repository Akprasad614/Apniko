# Apniko design system

## Art direction

**Neighbourhood market, not generic SaaS dashboard.** The visual language takes cues from printed grocery labels, produce-market signage, warm paper, ink, painted shop fronts and simple editorial layouts. Avoid the default AI-generated look: no purple/blue gradients, glassmorphism, excessive rounded cards, random metric tiles or decorative charts without a job to do.

The customer experience should feel welcoming and tactile. Shopkeeper and delivery interfaces should feel calm, legible and operationally efficient.

## Core palette

| Token | Value | Use |
|---|---|---|
| Forest | `#173F35` | Brand surfaces, primary actions |
| Deep forest | `#102F28` | High-contrast brand surface |
| Moss | `#426955` | Secondary emphasis |
| Citrus | `#D8ED79` | Primary accent and selected state |
| Paper | `#F5F2E8` | Main canvas |
| Paper deep | `#EBE6D8` | Quiet surface |
| White | `#FFFDF7` | Form and content surface |
| Terracotta | `#D67851` | Warm highlight, alerts used sparingly |
| Ink | `#202A25` | Main text |
| Muted | `#68736C` | Supporting text |
| Line | `#D9D7CA` | Borders and dividers |

All values are centralised in `frontend/src/styles/tokens.css`. Components should use tokens rather than introducing one-off colour values.

## Typography

- Display: Georgia / Times New Roman serif stack for distinctive editorial headings.
- UI and body: system sans-serif stack for fast rendering and predictable legibility.
- Use strong hierarchy, short line lengths and restrained uppercase labels.
- Do not download remote fonts by default. A self-hosted font can be evaluated later if it materially improves the product.

## Shape, spacing and composition

- Prefer open layouts, section dividers and asymmetric editorial composition over a grid of identical cards.
- Use small radii for controls, medium radii for functional panels and larger radii only for major surfaces.
- Use whitespace to separate content. Keep status, price and next action visually obvious.
- Use motion sparingly; respect `prefers-reduced-motion`.
- Use CSS illustrations or product photography only when they add real information. Do not fill empty space with random decoration.

## Interaction and accessibility

- Every interactive element needs a visible keyboard focus state.
- Form controls must have explicit labels and understandable error text.
- Colour must not be the only way to communicate status.
- Keep touch targets comfortable and avoid hover-only interaction.
- Responsive layouts must work at 320px, tablet widths and desktop widths.
- Use semantic landmarks, heading order, skip links and descriptive names.

## Page-specific direction

- **Customer:** discovery-led, warm, editorial and product-focused; useful filters and clear stock/price information.
- **Shopkeeper:** dense enough for daily work but calm; inventory, incoming orders and low-stock actions should be scan-friendly.
- **Delivery:** action-first; show assignment, pickup and delivery states clearly with large primary actions.
- **Admin:** restrained operational interface; prioritise safe actions, audit context and clear confirmation.

## Design review references

Use these as critique and quality references, not as templates to copy:

- Anthropic Frontend Design: bespoke art direction and deliberate composition.
- Taste: visual taste and refinement of spacing, contrast and density.
- UI UX Pro Max: design-system and pattern research.
- Vercel Web Interface Guidelines: accessibility, responsive behaviour and interaction quality.
- Impeccable: critique and refinement passes.

The project owns its visual identity. Do not copy another product's screens or a starter template wholesale.
