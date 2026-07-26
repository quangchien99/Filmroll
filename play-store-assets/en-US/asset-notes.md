# Filmroll Play Store Asset Notes

## Output Inventory

- `app-icon-512.png`: 512x512 PNG with alpha.
- `feature-graphic-1024x500.png`: 1024x500 RGB PNG.
- `screenshots/01-shoot-digital-feel-analog.png`: 1080x1920 RGB PNG.
- `screenshots/02-live-film-viewfinder.png`: 1080x1920 RGB PNG.
- `screenshots/03-real-3d-lut-film-looks.png`: 1080x1920 RGB PNG.
- `screenshots/04-classic-film-filters.png`: 1080x1920 RGB PNG.
- `screenshots/05-fine-tune-every-frame.png`: 1080x1920 RGB PNG.
- `screenshots/06-capture-or-import.png`: 1080x1920 RGB PNG.
- `screenshots/07-save-in-full-quality.png`: 1080x1920 RGB PNG.
- `screenshots/08-build-your-film-shelf.png`: 1080x1920 RGB PNG.
- `screenshots-contact-v2.png`: review contact sheet for the regenerated screenshot set.

## Source References

- `references/screenshot1.webp` through `references/screenshot8.webp`: used as composition references for dark textured Play Store screenshots, amber corner brackets, and phone-forward layouts.
- `shared/src/commonMain/composeResources/values/strings.xml`: used for product wording and supported feature claims.
- `shared/src/commonMain/kotlin/com/filmroll/camera/theme/Color.kt`: used for the warm darkroom palette.
- `_sources/icon-source.png`: generated source image for the final icon.
- `_sources/feature-base-source.png`: generated source image for the final feature graphic background.
- `_sources/screenshots-v2/*.png`: generated source images for the regenerated screenshot backgrounds.

## Icon Prompt

Use case: logo-brand
Asset type: Google Play app icon, final should work at 512x512
Primary request: Create a new app icon for "Filmroll: Vintage Camera", a mobile camera app that lets users shoot or import photos, preview analog film 3D LUT looks live, tune exposure/contrast/saturation/grain, and export full-quality images.
Input images: Image 1 is the current simple launcher concept; Image 2 and Image 3 are visual style/concept references only. Do not copy them exactly.
Subject: A clean, premium icon combining a compact vintage camera lens and a curling 35mm film strip, with a subtle LUT color spectrum/glass highlight motif.
Style/medium: polished vector-friendly app icon rendered as a crisp bitmap, not photorealistic, not overly detailed.
Composition/framing: centered icon mark, strong silhouette, readable at small sizes, square canvas, generous padding for Android adaptive icon masking.
Lighting/mood: warm darkroom mood with soft amber safelight highlights and muted teal/film color accents.
Color palette: near-black warm canvas, amber/yellow primary accent, small teal/red spectral details; avoid a one-color palette.
Materials/textures: minimal matte surface, subtle film grain only in the background, clean hard edges on the mark.
Text: no text.
Constraints: no brand names, no watermark, no UI screenshot, no human subject, no camera company logos, no rounded-square border baked into the artwork; icon should still look good when masked by Android.
Avoid: clutter, tiny unreadable film perforations, heavy neon glow, stock camera logo look.

## Feature Graphic Base Prompt

Use case: ads-marketing
Asset type: Google Play feature graphic base image, 1024x500 final crop
Primary request: Create a text-free hero background for Filmroll: Vintage Camera, a mobile analog film camera and 3D LUT photo editor.
Input images: Images 1-3 are Play Store style references for dark textured vintage camera marketing composition; Image 4 is Filmroll concept art reference. Use them only for mood and composition, not copying layout, text, people, or branding.
Scene/backdrop: darkroom-inspired black textured background with subtle film grain, warm amber safelight glow, a curling 35mm film strip, and hints of a modern Android phone camera/editor interface as abstract shapes.
Subject: premium vintage camera/film strip atmosphere with usable negative space on the left for text and a clean visual focus on the right.
Style/medium: polished app-store marketing artwork, semi-realistic product illustration, crisp and modern.
Composition/framing: wide landscape, left third mostly open dark negative space, right side has film strip/camera lens/phone silhouette, no important details at extreme edges.
Lighting/mood: cinematic but readable, warm amber with small teal/red film color accents.
Color palette: near-black, amber, muted teal, small red; balanced, not a single-hue palette.
Text: no text.
Constraints: no readable words, no logos, no watermark, no people, no competitor app branding, no Apple/iPhone hardware, no fake app store badges.
Avoid: busy center, text-like gibberish, large blank white areas, heavy neon cyberpunk styling.

## Screenshot Rendering Spec

The regenerated screenshots use built-in image generation for rich, text-free portrait bases and local compositing for exact title text, sizing, and PNG output. The layout follows the reference set more closely with dark textured backgrounds, amber corner brackets, large mono display titles, Android phone mockups, camera props, film rolls, photo stacks, LUT/color effects, dust, scratches, and warm light leaks.

Shared screenshot prompt pattern:

Use case: ads-marketing
Asset type: Google Play portrait screenshot base, 9:16, final crop 1080x1920
Primary request: Create a rich text-free Filmroll marketing screenshot base for one product feature.
Input images: `references/screenshot1.webp` through `references/screenshot8.webp` are style references only for dark textured Play Store screenshot composition, amber bracket accents, central phone/object layouts, film dust, light leaks, and vintage camera props.
Scene/backdrop: black grungy darkroom poster/tabletop background with dust, scratches, amber safelight glow, muted teal shadows, and analog film texture.
Subject: generic Android phone with a Filmroll-related camera/editor/library/export screen, surrounded by feature-specific objects such as 35mm film strips, film canisters, instant photos, LUT swatches, lenses, camera bodies, photo cards, and color/light effects.
Composition/framing: portrait 9:16, top 18% mostly dark and empty for local headline text, central Android phone large, supporting objects around sides and bottom, safe margins.
Text: no text in generated bases; exact titles are overlaid locally.
Constraints: no Apple/iPhone details, no brand logos, no readable UI labels, no watermark.
