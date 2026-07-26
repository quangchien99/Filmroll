# Filmroll Landing Page

Standalone promotional landing page for Filmroll: Vintage Camera. It follows the landing page shape from the referenced Awesome Battery Charging project and uses the generated Filmroll Play Store assets.

## Local Development

```bash
cd landing-page
npm install
npm run dev
```

Open `http://localhost:3000`.

## Routes

- `/` - promotional landing page with Play Store CTA, QR code, feature sections, and screenshot gallery.
- `/policy-privacy` - privacy policy page for the Android app.

## Deployment

Use `landing-page` as the project root directory for Vercel or another Next.js host.

Recommended settings:

- Framework Preset: Next.js
- Root Directory: `landing-page`
- Install Command: `npm ci`
- Build Command: `npm run build`

Optional environment variable:

```bash
NEXT_PUBLIC_SITE_URL=https://your-production-domain.example
```

## Assets

The page serves copied Play Store assets from `public/playstore`. Refresh them from the repo root with:

```bash
cp play-store-assets/en-US/app-icon-512.png landing-page/public/playstore/app_icon_512.png
cp play-store-assets/en-US/feature-graphic-1024x500.png landing-page/public/playstore/feature_graphic_1024x500.png
cp play-store-assets/en-US/screenshots/*.png landing-page/public/playstore/phone/
```
