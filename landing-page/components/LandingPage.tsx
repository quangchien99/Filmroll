"use client";

import Image from "next/image";
import Link from "next/link";
import { motion, useReducedMotion } from "framer-motion";
import QRCode from "react-qr-code";
import type { LucideIcon } from "lucide-react";
import {
  Camera,
  CheckCircle2,
  ChevronRight,
  Download,
  ExternalLink,
  Film,
  Focus,
  Heart,
  Play,
  ScanLine,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Star
} from "lucide-react";
import {
  APP_FULL_NAME,
  APP_NAME,
  PLAY_STORE_URL
} from "@/lib/constants";

const screenshots = [
  {
    src: "/playstore/phone/01-shoot-digital-feel-analog.png",
    title: "Shoot digital. Feel analog.",
    caption: "Frame modern photos with a warm analog camera mood."
  },
  {
    src: "/playstore/phone/02-live-film-viewfinder.png",
    title: "Live film viewfinder",
    caption: "Preview the film look before you press the shutter."
  },
  {
    src: "/playstore/phone/03-real-3d-lut-film-looks.png",
    title: "Real 3D LUT looks",
    caption: "Use classic color and black-and-white film-inspired LUTs."
  },
  {
    src: "/playstore/phone/04-classic-film-filters.png",
    title: "Classic film filters",
    caption: "Move between cinematic film tones in one focused editor."
  },
  {
    src: "/playstore/phone/05-fine-tune-every-frame.png",
    title: "Fine-tune every frame",
    caption: "Adjust exposure, contrast, warmth, saturation, and grain."
  },
  {
    src: "/playstore/phone/06-capture-or-import.png",
    title: "Capture or import",
    caption: "Shoot through Filmroll or bring in a photo from your gallery."
  },
  {
    src: "/playstore/phone/07-save-in-full-quality.png",
    title: "Save in full quality",
    caption: "Export finished images at full resolution."
  },
  {
    src: "/playstore/phone/08-build-your-film-shelf.png",
    title: "Build your film shelf",
    caption: "Browse, search, and favorite the film stocks you return to."
  }
];

const features: Array<{
  icon: LucideIcon;
  title: string;
  body: string;
  metric: string;
  visual: "strip" | "swatches" | "slider" | "grain" | "grid" | "export";
}> = [
  {
    icon: Camera,
    title: "Live analog viewfinder",
    body: "Shoot through a film stock so the frame already has its final mood before capture.",
    metric: "Live preview",
    visual: "strip"
  },
  {
    icon: Film,
    title: "3D LUT film library",
    body: "Browse classic color, slide, and black-and-white inspired looks built around real LUTs.",
    metric: "Film stocks",
    visual: "swatches"
  },
  {
    icon: SlidersHorizontal,
    title: "Precise editing controls",
    body: "Tune exposure, contrast, warmth, saturation, shadows, highlights, grain, and fringing.",
    metric: "Fine control",
    visual: "slider"
  },
  {
    icon: Focus,
    title: "Before and after compare",
    body: "Hold to compare the original frame against the film treatment without leaving the editor.",
    metric: "A/B view",
    visual: "grain"
  },
  {
    icon: Heart,
    title: "Favorite film shelf",
    body: "Pin the looks you keep coming back to and find them quickly in the film browser.",
    metric: "Favorites",
    visual: "grid"
  },
  {
    icon: Download,
    title: "Full-quality export",
    body: "Save straight to your gallery at full resolution and keep metadata when you want it.",
    metric: "Clean output",
    visual: "export"
  }
];

const heroStats = [
  "Live camera",
  "Photo import",
  "3D LUTs",
  "Film grain",
  "Compare original",
  "Favorites",
  "Offline LUTs",
  "Full export"
];

const workflowSteps = [
  {
    eyebrow: "01",
    title: "Frame the look",
    body: "Open the camera and preview a film stock live while composing the shot.",
    src: "/playstore/phone/02-live-film-viewfinder.png"
  },
  {
    eyebrow: "02",
    title: "Shape the frame",
    body: "Move from film selection into precise adjustments without losing the image.",
    src: "/playstore/phone/05-fine-tune-every-frame.png"
  },
  {
    eyebrow: "03",
    title: "Keep the final",
    body: "Save a full-resolution image that keeps the analog mood and modern detail.",
    src: "/playstore/phone/07-save-in-full-quality.png"
  }
];

const promoHighlights = [
  "Live analog film camera",
  "Film LUT photo editor",
  "Manual grain and color controls",
  "Full-resolution saves"
];

const reveal = {
  hidden: { opacity: 0, y: 26 },
  visible: { opacity: 1, y: 0 }
};

function PlayStoreButton({ className = "" }: { className?: string }) {
  return (
    <a
      aria-label={`Open ${APP_FULL_NAME} on Google Play`}
      className={`playStoreButton ${className}`}
      href={PLAY_STORE_URL}
      rel="noreferrer"
      target="_blank"
    >
      <Play aria-hidden="true" fill="currentColor" size={20} />
      <span>
        <small>Get it on</small>
        Google Play
      </span>
      <ExternalLink aria-hidden="true" size={18} />
    </a>
  );
}

function HeroInstallQr() {
  return (
    <div
      aria-label={`QR code to open ${APP_FULL_NAME} on Google Play`}
      className="heroInstallQr"
      role="img"
    >
      <div aria-hidden="true" className="heroInstallQrCode">
        <QRCode
          bgColor="transparent"
          fgColor="#1d1a16"
          size={256}
          value={PLAY_STORE_URL}
          viewBox="0 0 256 256"
        />
      </div>
      <span>Scan for Play Store</span>
    </div>
  );
}

function FeatureVisual({ type }: { type: (typeof features)[number]["visual"] }) {
  return (
    <div aria-hidden="true" className={`featureVisual featureVisual-${type}`}>
      <span />
      <span />
      <span />
      <span />
      <span />
    </div>
  );
}

function SectionIntro({
  kicker,
  title,
  body
}: {
  kicker: string;
  title: string;
  body: string;
}) {
  return (
    <motion.div
      className="sectionIntro"
      initial="hidden"
      transition={{ duration: 0.55, ease: "easeOut" }}
      variants={reveal}
      viewport={{ once: true, margin: "-120px" }}
      whileInView="visible"
    >
      <p className="kicker">{kicker}</p>
      <h2>{title}</h2>
      <p>{body}</p>
    </motion.div>
  );
}

export function LandingPage() {
  const reduceMotion = useReducedMotion();

  return (
    <main className="landingPage">
      <section className="heroSection" id="top">
        <Image
          alt=""
          className="heroBackdrop"
          fill
          priority
          sizes="100vw"
          src="/playstore/feature_graphic_1024x500.png"
        />
        <div aria-hidden="true" className="heroFilmGrain" />
        <div className="heroShade" />
        <motion.div
          animate={reduceMotion ? undefined : { x: ["-18%", "118%"] }}
          aria-hidden="true"
          className="heroLightLeak"
          transition={{ duration: 8, ease: "linear", repeat: Infinity }}
        />

        <header className="siteHeader">
          <Link aria-label={`${APP_FULL_NAME} home`} className="brandMark" href="/">
            <Image
              alt=""
              height={42}
              priority
              src="/playstore/app_icon_512.png"
              width={42}
            />
            <span>{APP_NAME}</span>
          </Link>
          <nav aria-label="Primary navigation">
            <a href="#features">Features</a>
            <a href="#screens">Screens</a>
            <a href="#install">Install</a>
            <Link href="/policy-privacy">Privacy</Link>
          </nav>
        </header>

        <div className="heroContent">
          <motion.div
            className="heroCopy"
            animate={{ opacity: 1, y: 0 }}
            initial={{ opacity: 0, y: 32 }}
            transition={{ duration: 0.75, ease: "easeOut" }}
          >
            <p className="heroBadge">
              <Sparkles aria-hidden="true" size={18} />
              Shoot digital. Feel analog.
            </p>
            <h1>{APP_FULL_NAME}</h1>
            <p className="heroLead">
              A focused vintage camera and photo editor for live film LUTs,
              manual grain and color controls, before/after compare, and
              full-quality gallery export.
            </p>
            <div className="heroActions">
              <PlayStoreButton className="heroPlayStoreButton" />
              <HeroInstallQr />
            </div>
            <div aria-label="App highlights" className="heroStats">
              {heroStats.map((stat) => (
                <span key={stat}>
                  <CheckCircle2 aria-hidden="true" size={16} />
                  {stat}
                </span>
              ))}
            </div>
          </motion.div>

          <motion.div
            aria-label={`${APP_FULL_NAME} screenshot preview`}
            className="heroDeviceStage"
            animate={{ opacity: 1, scale: 1, y: 0 }}
            initial={{ opacity: 0, scale: 0.96, y: 28 }}
            transition={{ delay: 0.18, duration: 0.8, ease: "easeOut" }}
          >
            <div aria-hidden="true" className="filmHalo filmHaloOne" />
            <div aria-hidden="true" className="filmHalo filmHaloTwo" />
            <motion.figure
              animate={reduceMotion ? undefined : { y: [0, -14, 0] }}
              className="heroPhone heroPhonePrimary"
              transition={{ duration: 5.4, ease: "easeInOut", repeat: Infinity }}
            >
              <Image
                alt="Filmroll 3D LUT film looks preview"
                className="heroPhoneImage"
                height={1920}
                priority
                sizes="(max-width: 900px) 60vw, 340px"
                src="/playstore/phone/03-real-3d-lut-film-looks.png"
                width={1080}
              />
            </motion.figure>
            <motion.div
              animate={reduceMotion ? { rotate: 5 } : { y: [0, 12, 0], rotate: 5 }}
              className="heroPhone heroPhoneSecondary"
              transition={{ duration: 6.4, ease: "easeInOut", repeat: Infinity }}
            >
              <Image
                alt="Filmroll classic film filters preview"
                className="heroPhoneImage"
                height={1920}
                sizes="(max-width: 900px) 34vw, 210px"
                src="/playstore/phone/04-classic-film-filters.png"
                width={1080}
              />
            </motion.div>
            <motion.div
              animate={reduceMotion ? undefined : { opacity: [0.42, 1, 0.42] }}
              aria-hidden="true"
              className="safelightRail"
              transition={{ duration: 2.8, ease: "easeInOut", repeat: Infinity }}
            />
          </motion.div>
        </div>
      </section>

      <section className="featureSection" id="features">
        <SectionIntro
          body="Move from capture to film selection, focused edits, compare, favorites, and export without leaving a calm darkroom-style workflow."
          kicker="Darkroom workflow"
          title="Everything around one frame"
        />
        <div className="featureGrid">
          {features.map((feature, index) => {
            const Icon = feature.icon;
            return (
              <motion.article
                className={`featureCard featureCard-${index + 1}`}
                initial="hidden"
                key={feature.title}
                transition={{ delay: index * 0.04, duration: 0.45 }}
                variants={reveal}
                viewport={{ once: true, margin: "-80px" }}
                whileHover={{ y: -8 }}
                whileInView="visible"
              >
                <div className="featureCardHeader">
                  <span className="featureIcon">
                    <Icon aria-hidden="true" size={22} />
                  </span>
                  <span className="featureMetric">{feature.metric}</span>
                </div>
                <FeatureVisual type={feature.visual} />
                <div>
                  <h3>{feature.title}</h3>
                  <p>{feature.body}</p>
                </div>
              </motion.article>
            );
          })}
        </div>
      </section>

      <section className="screensSection" id="screens">
        <SectionIntro
          body="The page uses the same Play Store artwork style as the reference set, rebuilt with Filmroll's analog camera, film LUT, editor, and export story."
          kicker="Store-ready visuals"
          title="A complete screenshot set for the app story"
        />
        <div className="galleryShell">
          <motion.div
            aria-label="Filmroll screenshot gallery"
            animate={reduceMotion ? undefined : { x: ["0%", "-50%"] }}
            className="marqueeTrack"
            transition={{ duration: 52, ease: "linear", repeat: Infinity }}
          >
            {[...screenshots, ...screenshots].map((screen, index) => (
              <figure
                aria-hidden={index >= screenshots.length}
                className="screenshotFrame"
                key={`${screen.src}-${index}`}
              >
                <Image
                  alt={screen.title}
                  className="screenshotImage"
                  height={960}
                  sizes="(max-width: 760px) 62vw, 280px"
                  src={screen.src}
                  width={540}
                />
                <figcaption>
                  <strong>{screen.title}</strong>
                  <span>{screen.caption}</span>
                </figcaption>
              </figure>
            ))}
          </motion.div>
        </div>
      </section>

      <section className="styleSection">
        <motion.div
          className="storyCopy"
          initial="hidden"
          transition={{ duration: 0.55, ease: "easeOut" }}
          variants={reveal}
          viewport={{ once: true, margin: "-100px" }}
          whileInView="visible"
        >
          <p className="kicker">From camera to gallery</p>
          <h2>Build a repeatable film process on your phone</h2>
          <p>
            Filmroll keeps the screen centered on the image. Capture a frame,
            choose a film stock, make tactile adjustments, compare the original,
            and save the final in full quality.
          </p>
          <ul className="checkList">
            <li>
              <CheckCircle2 aria-hidden="true" size={19} />
              Live previews make film choice part of shooting, not only editing.
            </li>
            <li>
              <CheckCircle2 aria-hidden="true" size={19} />
              Focused sliders handle tone, color, grain, and film strength.
            </li>
            <li>
              <CheckCircle2 aria-hidden="true" size={19} />
              Favorites and offline LUTs keep your usual looks close.
            </li>
          </ul>
        </motion.div>
        <div aria-label="Filmroll workflow" className="styleDeck">
          {workflowSteps.map((step, index) => (
            <motion.article
              className="styleCard"
              initial={{
                opacity: 0,
                rotate: index === 1 ? 0 : index === 0 ? -3 : 3,
                y: 34
              }}
              key={step.title}
              transition={{ delay: index * 0.08, duration: 0.55, ease: "easeOut" }}
              viewport={{ once: true, margin: "-80px" }}
              whileHover={{ y: -10, rotate: 0 }}
              whileInView={{ opacity: 1, y: 0 }}
            >
              <Image
                alt={step.title}
                className="styleCardImage"
                height={1920}
                sizes="(max-width: 900px) 68vw, 230px"
                src={step.src}
                width={1080}
              />
              <div className="styleCardCopy">
                <span>{step.eyebrow}</span>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </div>
            </motion.article>
          ))}
        </div>
      </section>

      <section className="installSection" id="install">
        <div className="installCopy">
          <p className="kicker">Get the app</p>
          <h2>Open {APP_NAME} on Google Play</h2>
          <p>
            Scan from desktop or use the Play Store button on mobile. The link
            is based on the Android package configured in this repo.
          </p>
          <div className="installActions">
            <PlayStoreButton />
            <Link className="policyButton" href="/policy-privacy">
              <ShieldCheck aria-hidden="true" size={19} />
              Privacy policy
            </Link>
          </div>
        </div>

        <motion.aside
          className="qrPanel"
          initial={{ opacity: 0, rotate: -2, y: 22 }}
          transition={{ duration: 0.55, ease: "easeOut" }}
          viewport={{ once: true, margin: "-100px" }}
          whileInView={{ opacity: 1, rotate: 0, y: 0 }}
        >
          <div className="qrHeader">
            <ScanLine aria-hidden="true" size={20} />
            Scan for Play Store
          </div>
          <div aria-label="QR code for Google Play listing" className="qrCode">
            <QRCode
              bgColor="transparent"
              fgColor="#1d1a16"
              size={184}
              value={PLAY_STORE_URL}
              viewBox="0 0 256 256"
            />
          </div>
          <p>{PLAY_STORE_URL}</p>
        </motion.aside>
      </section>

      <section className="launchSection">
        <div>
          <p className="kicker">Ready for the store</p>
          <h2>Give every digital frame an analog finish</h2>
        </div>
        <ul className="launchList">
          {promoHighlights.map((highlight) => (
            <li key={highlight}>
              <Star aria-hidden="true" size={18} />
              {highlight}
            </li>
          ))}
        </ul>
        <a
          className="deployLink"
          href={PLAY_STORE_URL}
          rel="noreferrer"
          target="_blank"
        >
          View on Play
          <ChevronRight aria-hidden="true" size={18} />
        </a>
      </section>

      <footer className="footer">
        <div className="footerBrand">
          <Image alt="" height={34} src="/playstore/app_icon_512.png" width={34} />
          <span>{APP_FULL_NAME}</span>
        </div>
        <div className="footerLinks">
          <a href={PLAY_STORE_URL} rel="noreferrer" target="_blank">
            Play Store
          </a>
          <Link href="/policy-privacy">Privacy Policy</Link>
        </div>
      </footer>
    </main>
  );
}
