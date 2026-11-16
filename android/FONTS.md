# Bundled fonts (M7-2 Stage A)

The design mandates two families plus a Devanagari sibling (DESIGN-HANDOFF-M7-2.md
§1.3), and none is reliably a system face on Android, so all three ship as
`res/font` resources. All are licensed under the SIL Open Font License 1.1; the
license texts ship in the APK under `assets/fonts/`.

| Family | Version | Source (official release) | Files |
|---|---|---|---|
| IBM Plex Sans | `@ibm/plex-sans@1.1.0` | github.com/IBM/plex releases → `ibm-plex-sans.zip` | `ibm_plex_sans_{regular,semibold,bold}.ttf` |
| IBM Plex Sans Devanagari | `@ibm/plex-sans-devanagari@1.1.0` | github.com/IBM/plex releases → `ibm-plex-sans-devanagari.zip` | `ibm_plex_sans_devanagari_regular.ttf` |
| JetBrains Mono | `v2.304` | github.com/JetBrains/JetBrainsMono releases → `JetBrainsMono-2.304.zip` | `jetbrains_mono_{regular,medium,semibold,bold}.ttf` |

Weights follow the type scale's actual use: Plex Sans 400/600/700 (body / fn·⌫·chips /
display), JetBrains Mono 400/500/600/700 (captions / entry·keys / result·chips / =·badges),
Devanagari 400 (unit names, numeral output — consumed from Stage B on, bundled now).
Static faces, not the variable fonts: minSdk 24 predates variable-font support (API 26).
