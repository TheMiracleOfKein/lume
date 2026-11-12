# Accessibility

What was checked for font size up to 200 % and for colour contrast, what was fixed, and what still needs a real device. TalkBack labels, roles and state descriptions were done in v0.7.15 and are not repeated here.

The checks were made by reading the code and by computing contrast from the theme values. Nothing here was run on a device at 200 %.

## What was checked, screen by screen

For each screen: fixed-height containers that could clip text, single-line text that loses meaning when cut, rows and buttons that do not wrap, dialogs that do not scroll, long titles in top bars, text and icon colours, and touch targets.

| Screen | Result |
|---|---|
| Home | Progress bar colour fixed (contrast). Rows and titles already wrap or ellipsize. |
| Library | Top bar had a fixed 56 dp height: now a minimum, so a large selection title grows it. Selection bar already overlays the grid. Search field and chips fine. |
| Title | Already uses minimum heights and scrolling lists. Nothing to change. |
| Chapters | Selection bar no longer resizes the list (see below). Download progress colour fixed. Team chips scroll sideways. |
| Reader menus and sheets | Top bars had a fixed 56 dp height: now a minimum, so a title and chapter name at 200 % are not clipped. The bottom bar holds only icons. Settings sheet already scrolls. |
| Settings and sub-screens | Radio buttons and tabs used the amber accent that fails on a light background: now the text accent. The sync passphrase and tracker sign-in dialogs scroll. |
| Sources | Focus line and radio colours fixed. Dialogs already scroll. |
| Downloads | Progress colour fixed. |
| Updates | Progress bars use the text accent. Rows already have a minimum height. |
| History | Dialogs hold short text only. |
| Nearby | Progress bar colour fixed. |
| Stats | Bars are decoration next to numbers; nothing to change. |
| Shell | Bottom bar labels stay on one line and end with an ellipsis instead of breaking mid-word. The search field is at least 48 dp high. |

Single-line text without an ellipsis: only the badge on the cover (a few characters) and the team chips (in a sideways scroller) remain; both are fine.

## O17: the chapter list jumping

The selection bar was the Scaffold `bottomBar`, so the content padding changed when it appeared and the list moved. It now overlays the list in a `Box` aligned to the bottom and slides in with the shared bar motion. The list keeps a fixed bottom padding the height of the bar, so the last rows can always be scrolled above it. The bar still handles the navigation-bar inset itself. The Library selection bar already overlays the grid and is unchanged; Downloads has no selection bar.

## Contrast

WCAG 2.x: 4.5:1 for text, 3:1 for icons and borders. `ThemeContrastTest` computes the ratio for every pair below in the dark, light and AMOLED themes and fails the build if one drops under its limit.

Pairs checked: primary, secondary and tertiary text on the page, surface, card, search and selected-row colours (tertiary is not used on the raised card or selected rows); accent text, error colour and borders on the page, surface and card; text on the accent fill; text on the selected chip.

### Pairs that failed and the fix

| Pair | Theme | Before | Fix |
|---|---|---|---|
| Secondary text on raised card | dark | 4.0 | `#9A9A9A` to `#AAAAAA` |
| Tertiary text on card, search | dark | 3.8 | `#858585` to `#9A9A9A` |
| Error colour on raised card | dark | 3.8 | `#E57373` to `#EF8A8A` |
| Text on the selected chip | dark, AMOLED | 3.8 | chip colour `#B87321` to `#9C5F12` |
| Field borders | dark, AMOLED | 1.7 to 2.7 | `#5A5A5E` / `#4A4A4E` to `#7C7C80` |
| Tertiary text | light | 2.5 to 3.5 | `#8A8A8A` to `#666666` |
| Accent text | light | 3.4 to 4.2 | `#A8650A` to `#8F5200` |
| Error colour on raised card | light | 4.1 | `#C62828` to `#B71C1C` |
| Field borders | light | 2.0 to 2.8 | `#9A9AA0` to `#757579` |

The amber accent `#F5A524` itself is 1.9:1 on the light page. It stays a fill (switch tracks, buttons with dark text on them, selection borders). Where it was used as a foreground (radio buttons, tab labels, progress bars, text fields' focus line, "show details") it is now the accent text colour.

### Ratios after the fixes

Computed by the test; the lowest value of each group across the three themes.

| Pair | Lowest ratio | Limit |
|---|---|---|
| Primary text on any surface | above 11 | 4.5 |
| Secondary text on any surface | 4.5 | 4.5 |
| Tertiary text on page, surface, card, search | 4.5 | 4.5 |
| Accent text on page, surface, card, raised card | 4.5 | 4.5 |
| Error colour on page, surface, card, raised card | 4.5 | 4.5 |
| Borders on page, surface, card | 3.0 | 3.0 |
| Text on accent fill | above 4.5 | 4.5 |
| Text on selected chip | 4.5 | 4.5 |

Not covered: the dynamic (wallpaper) colour scheme, which follows the system's own palette, and text or badges drawn over cover images, whose contrast depends on the picture.

## Needs a real device at 200 %

- Home, Library and Title screens with the system font at its largest, in portrait and landscape.
- The Reader top and bottom bars and the settings sheet.
- The bottom navigation bar with long Russian labels.
- Dialogs with only text (delete, clear history): confirm the buttons stay on screen with a long message.
- Touch targets under 48 dp were not audited one by one; check small icons in rows and in the Title header.
- Display size at its largest together with font size.
