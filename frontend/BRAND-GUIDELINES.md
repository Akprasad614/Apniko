# Apniko Brand Guidelines

These guidelines define the shared visual language for the Apniko customer, shopkeeper, delivery-partner and administrator experiences.

## Brand idea

**Apniko — Your neighbourhood, closer.**

Apniko connects customers with the independent shops around them. The identity should feel approachable, dependable and useful: local commerce made easier, without looking like a generic marketplace.

## Logo concept

The primary mark combines three ideas:

- **A-shaped structure:** the first letter of Apniko and a stable visual frame.
- **Location pin:** finding shops and products close to the customer.
- **Storefront and green sweep:** neighbourhood businesses and the connection between them.

The mark and wordmark are original vector artwork created for this project. They are a project identity concept, not a claim of trademark registration or guaranteed global uniqueness. Complete trademark, domain and social-handle clearance before commercial launch.

## Logo assets

| Asset | Intended use |
| --- | --- |
| `public/brand/apniko-logo.svg` | Primary horizontal logo on light surfaces |
| `public/brand/apniko-logo-light.svg` | Horizontal logo on dark surfaces |
| `public/brand/apniko-mark.svg` | Standalone symbol |
| `public/brand/apniko-app-icon.svg` | App-icon artwork |
| `public/favicon.svg` | Browser favicon |
| `src/components/BrandMark.tsx` | React brand component used in the application |

Use the complete logo when there is enough space. Use the mark alone in compact navigation, small avatars and icon contexts. Keep clear space around the mark and never stretch, recolour individual parts arbitrarily, add shadows to the SVG, or place the dark logo over a similarly dark background.

## Colour palette

| Token | Hex | Role |
| --- | --- | --- |
| Apniko Navy | `#132B45` | Primary identity, headings and navigation |
| Deep Navy | `#0D2034` | High-contrast dark surfaces |
| Apniko Green | `#168653` | Growth, positive actions and local commerce |
| Deep Green | `#0F6A40` | Green hover states and emphasis |
| Apniko Orange | `#F28A2B` | Pin, highlights and secondary accent |
| Canvas | `#F6F8F7` | Main application background |
| Surface | `#FFFFFF` | Cards, forms and elevated panels |
| Ink | `#202B36` | Primary text |
| Muted | `#657384` | Supporting text |
| Border | `#E1E8E4` | Dividers and control outlines |

Use navy for structure, green for positive and primary commerce actions, and orange sparingly for emphasis. Do not use colour as the only way to communicate status; pair it with a label or icon.

## Typography

- Body and interface: Inter, falling back to the platform sans-serif stack.
- Display: the same sans-serif family with stronger weight and tighter tracking for consistency across devices.
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

## Brand review before public launch

The artwork is an original project concept, but no automated generation or code change can establish legal exclusivity. Before public commercial use, search the relevant Indian trademark classes, review similar marks, and check the intended domain and social handles.
