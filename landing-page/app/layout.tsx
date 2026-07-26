import type { Metadata, Viewport } from "next";
import "./globals.css";
import {
  APP_FULL_NAME,
  APP_NAME,
  PLAY_STORE_URL,
  SITE_DESCRIPTION
} from "@/lib/constants";

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: `${APP_FULL_NAME} - Shoot digital. Feel analog.`,
    template: `%s | ${APP_NAME}`
  },
  description: SITE_DESCRIPTION,
  applicationName: APP_FULL_NAME,
  alternates: {
    canonical: "/"
  },
  openGraph: {
    title: `${APP_FULL_NAME} - Shoot digital. Feel analog.`,
    description: SITE_DESCRIPTION,
    url: "/",
    siteName: APP_FULL_NAME,
    images: [
      {
        url: "/playstore/feature_graphic_1024x500.png",
        width: 1024,
        height: 500,
        alt: `${APP_FULL_NAME} feature graphic`
      }
    ],
    locale: "en_US",
    type: "website"
  },
  twitter: {
    card: "summary_large_image",
    title: `${APP_FULL_NAME} - Shoot digital. Feel analog.`,
    description: SITE_DESCRIPTION,
    images: ["/playstore/feature_graphic_1024x500.png"]
  },
  icons: {
    icon: "/playstore/app_icon_512.png",
    apple: "/playstore/app_icon_512.png"
  },
  other: {
    "google-play-url": PLAY_STORE_URL
  }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#0b0a09",
  colorScheme: "dark"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
