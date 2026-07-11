# Design Token Platform Mapping

Source tokens live in `shared/design/tokens/design-tokens.json`. The design prose remains in `docs/08-design-system.md`; this file records the intended native mapping so Android and Windows use the same values without copying them by hand from the prose.

## Android Compose

| Token group | Compose target |
|---|---|
| `colors.neutral`, `colors.brand`, `colors.functional` | `Color` constants in a small design-token object, then app `ColorScheme`/semantic finance colors |
| `colors.category` | category/icon-chip defaults and seeded category presentation |
| `colors.banner` | inline banner and read-only banner colors |
| `typography.families` | app font-family setup; use `Geist` for interface and `IBM Plex Mono` for ledger figures |
| `typography.scale` | `Typography` text styles |
| `typography.features.figures` | tabular-number font feature for amount text |
| `spacingPx` | `Dp` spacing constants |
| `radius` | `RoundedCornerShape` constants |
| `elevation` | `shadow`/`tonalElevation` decisions; dark mode should prefer surfaces and borders |
| `sizing` | component dimensions as `Dp` constants |
| `icons.core` | Material Symbols names mapped to the chosen Android icon implementation |

Compose implementation should keep tokens in one package, for example `ui/theme/tokens`, and screens should consume semantic names (`incomePositive`, `debtDanger`, `buttonHeight`) rather than raw hex or magic numbers.

## Windows WinUI

| Token group | WinUI target |
|---|---|
| `colors.neutral`, `colors.brand`, `colors.functional` | `Color`/`SolidColorBrush` resources in a merged resource dictionary |
| `colors.category` | category/icon-chip brush resources and seeded category presentation |
| `colors.banner` | banner and read-only banner brushes |
| `typography.families` | `FontFamily` resources; use Geist for UI and IBM Plex Mono for ledger figures |
| `typography.scale` | `FontSize` and `FontWeight` resources/styles |
| `typography.features.figures` | tabular amount style where the selected text stack supports it |
| `spacingPx` | spacing thickness resources |
| `radius` | `CornerRadius` resources |
| `elevation` | theme shadow resources where useful; dark mode should prefer surfaces and borders |
| `sizing` | control dimension resources |
| `icons.core` | Symbol/icon font mapping for the selected Windows icon approach |

WinUI implementation should expose the same semantic resource names as Compose where practical, even if the platform types differ.

## Rules

- Do not introduce app-local token values unless they are generated from `design-tokens.json` or explicitly added to `docs/08-design-system.md` and this shared file.
- Use functional color for money meaning and category color for identity; never swap those roles.
- Expense remains neutral ink, not a separate decorative hue.
- Dark mode uses the `dark` token variants and surface/border contrast instead of simply reusing light-mode shadows.
- UI text remains Catalan in app resource files; token names stay English.
