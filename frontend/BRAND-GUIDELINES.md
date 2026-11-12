# Apniko Brand Guidelines

These guidelines define the shared visual language for the Apniko customer, shopkeeper, delivery-partner and administrator experiences.

## Brand idea

**Apniko — Your neighbourhood, closer.**

Apniko connects customers with the independent shops around them. The identity should feel approachable, dependable and useful: local commerce made easier, without looking like a generic marketplace.

## Logo status

The final Apniko logo is intentionally **not included yet**. The project owner will provide the approved artwork later.

Until then:
- Use the plain text wordmark rendered by `src/components/BrandMark.tsx`.
- Do not create, regenerate or add an icon, mascot, pin, storefront mark or favicon.
- Do not add temporary stock or generated brand symbols.
- When the approved logo is supplied, place the master assets under `public/brand/` and update the shared `BrandMark` component. Avoid reproducing the artwork separately in feature pages.

## Colour palette

| Token | Hex | Role |
| --- | --- | --- |
| Apniko Navy | `#132B45` | Primary identity, headings and navigation |
| Deep Navy | `#0D2034` | High-contrast dark surfaces |
| Apniko Green | `#168653` | Growth, positive actions and local commerce |
| Deep Green | `#0F6A40` | Green hover states and emphasis |
| Apniko Orange | `#F28A2B` | Secondary accent |
| Canvas | `#F6F8F7` | Main application background |
| Surface | `#FFFFFF` | Cards, forms and elevated panels |
| Ink | `#202B36` | Primary text |
| Muted | `#657384` | Supporting text |
| Border | `#E1E8E4` | Dividers and control outlines |

Use navy for structure, green for positive and primary commerce actions, and orange sparingly for emphasis. Do not use colour as the only way to communicate status; pair it with a label or icon.

## Typography

- Body and interface: Inter, falling back to the platform sans-serif stack.
- Display: the same sans-serif family with stronger weight and tighter tracking for consistency.
- Numeric values: use tabular numerals for prices, order totals, credit balances and transaction lists.
- Keep headings concise and use readable line lengths. Avoid decorative type for financial figures or operational statuses.

The app uses local/system font fallbacks; it does not require a remote font request to render correctly.

## Layout and responsiveness

- Design mobile-first, with 320–430 px as the primary narrow-screen range.
- Keep interactive targets at least 44 px high wherever practical.
- Use the shared page width and gutter tokens instead of hard-coded page widths.
- On desktop, expand content without making product, form or data rows excessively wide.
- Respect safe-area insets on mobile devices.
- Prevent horizontal page overflow and preserve visible keyboard focus.
- Honour `prefers-reduced-motion`.

## Shape, spacing and elevation

Use the `--apniko-radius-*` tokens for rounded controls and cards. Prefer subtle borders and restrained shadows over heavy outlines. Keep card surfaces white over the soft neutral canvas. Use the spacing/layout tokens and reusable classes in `src/styles/brand-system.css`.

## Shared CSS tokens

The canonical tokens live in `src/styles/tokens.css`. Existing feature code still uses some `--lc-*` variables; these remain as compatibility aliases to the Apniko palette so that the identity refresh does not require risky changes to feature logic. New components should prefer `--apniko-*` tokens.

## Accessibility

- Maintain readable contrast for text and interactive controls.
- Use visible focus styles for keyboard users.
- Add accessible names to icon-only controls.
- Do not convey availability, payment or order state by colour alone.
- Preserve reduced-motion preferences and support 320 px wide viewports.
