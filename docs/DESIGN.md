# REPLATE design rules

Keep new screens consistent by using these tokens instead of raw numbers.

## Typography
Font: **Poppins** (Regular 400, Medium 500, SemiBold 600, Bold 700), bundled in `res/font/`
(licence: `docs/POPPINS_OFL.txt`). `@font/poppins` is the whole family, so `textStyle="bold"` picks the real bold face.

| Token (`@dimen/…`) | Size | Use |
| --- | --- | --- |
| `text_caption` | 11sp | timestamps, overlines |
| `text_xs` | 12sp | captions, chips, badges |
| `text_sm_compact` | 13sp | dense secondary text |
| `text_sm` | 14sp | body |
| `text_md` | 16sp | buttons, inputs, emphasised body |
| `text_lg` | 18sp | section headings |
| `text_title` | 20sp | screen sub-titles |
| `text_xl` | 22sp | headlines, totals |
| `text_xxl` | 26sp | confirmation titles, OTP |
| `text_display` | 28sp | hero numbers |

Prefer `textAppearance="@style/TextAppearance.REPLATE.*"` (Display, Headline, Subheading, Body, Label,
Caption, Overline). Do not type a literal `sp` value in a layout.

## Spacing
4dp grid: `space_0_5` 4, `space_1` 8, `space_1_5` 12, `space_2` 16 (screen gutter), `space_2_5` 20,
`space_3` 24, `space_4` 32, `space_5` 40, `space_6` 48. In-between values (2, 6, 10, 14, 18) are allowed for
fine optical tweaks only. Use `start`/`end`, never `left`/`right`, so layouts mirror correctly.

## Touch targets
Anything tappable is at least 48dp (`@dimen/touch_target_min`) or has padding that makes it so. Icon-only
buttons need a `contentDescription`.

## Text that can be long
Names and addresses get `ellipsize="end"` and `maxLines`. Descriptions and messages wrap.

## Forms
Every input has an `inputType`, an `imeOptions` (`actionNext` until the last field, then `actionDone`) and
`autofillHints`.

## Strings and prices
All user-visible text lives in `res/values/strings.xml` (no literals in layouts or `setText("…")`), so
Sinhala and Tamil can be added as `values-si` / `values-ta`. Show money with `Money.format(amount)`
("LKR 1,250.00"); never build a price with `String.format` in a screen.

## Navigation
- Bottom tabs are kept alive (add/hide/show); tapping the selected tab scrolls it to the top; Back from
  another tab returns to Home before leaving the app.
- Opening an order or the inbox from a notification builds a back stack (`parentActivityName` +
  `TaskStackBuilder`), so Back lands on Home, not the launcher.
- `MainActivity` is `singleTop`; login, logout and onboarding clear the task.

## Screens (app)
- **Green header** (`Widget.REPLATE.Card.Header`): floating rounded card with a 48dp back arrow (negative start
  margin keeps the icon aligned), a centred title and an optional muted subtitle. It stays pinned above the
  scrolling content on every screen (cart, favorites, notifications, checkout, order detail, settings, search).
- **List rows** (`Widget.REPLATE.Card.Row`): food, order, cart, notification and conversation items share radius,
  elevation, outline and a 16dp side gutter. Carousels use `FoodItemAdapter.Style.CAROUSEL` (78% width, peeks the
  next card); the two-column search grid uses `Style.GRID` (`item_food_grid.xml`).
- **Pinned actions**: the primary action of a long screen (Place order, Add to cart) lives in a bar under the
  scroll area, never inside it.
- **Empty states**: 128dp pale circle, 56dp decorative icon, Headline title, Body text, optional primary button.
- **Placeholders**: sample text in layouts is `tools:text` so it can never show to a user.
- **Splash**: the custom splash starts as an exact continuation of the system splash (same `@color/splash_bg`,
  176dp logo at the screen centre) and only then fades the wordmark in. Returning users skip it entirely.
- **Motion**: matched slide in/out (`fast_out_slow_in`, 320ms); every custom animation is skipped when the user
  turned animations off (`ValueAnimator.areAnimatorsEnabled()`).

## Website (`web-panels/`)
- Public pages (`index.html`, `privacy.html`, `terms.html`, `404.html`) use `css/site.css`; the signed-in panels use
  `css/style.css`. Both self-host Poppins from `assets/fonts/`.
- Use the helpers in `js/ui-shell.js`, never raw browser dialogs or unescaped HTML:
  `ReplateUI.esc()` / `safeUrl()` for anything that comes from the database, `toast()` instead of `alert()`,
  `confirmAction()` / `askText()` instead of `confirm()` / `prompt()`, `requireRole('admin'|'hotel')` at the top of
  every panel page, `skeleton()` / `emptyState()` / `errorState()` for lists and tables, `flash()` for a message
  that should appear after a redirect.
- Hotel pages read their own data with `ReplateSchema.hotelOrders()` / `hotelFoodItems()` (queries by `hotelId`);
  the database rules do not allow reading the whole list.
- `node scripts/check-web.js` checks that every script parses, local links exist and no page calls `alert()`.
