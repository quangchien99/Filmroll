import type { Metadata } from "next";
import {
  APP_FULL_NAME,
  CONTACT_EMAIL,
  PACKAGE_NAME
} from "@/lib/constants";
import { ExternalLink, Mail, ShieldCheck } from "lucide-react";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: `Privacy policy for ${APP_FULL_NAME}.`
};

const thirdPartyServices = [
  {
    name: "Google Play services",
    href: "https://policies.google.com/privacy"
  }
];

export default function PrivacyPolicyPage() {
  return (
    <main className="policyPage">
      <article className="policyArticle">
        <div className="policyEyebrow">
          <ShieldCheck aria-hidden="true" size={20} />
          App privacy
        </div>
        <h1>Privacy Policy</h1>
        <p className="policyLead">
          This Privacy Policy explains how {APP_FULL_NAME} handles information
          when you use the Android app and related web pages.
        </p>
        <dl className="policyMeta">
          <div>
            <dt>App</dt>
            <dd>{APP_FULL_NAME}</dd>
          </div>
          <div>
            <dt>Package name</dt>
            <dd>{PACKAGE_NAME}</dd>
          </div>
          <div>
            <dt>Last updated</dt>
            <dd>April 20, 2024</dd>
          </div>
        </dl>

        <section>
          <h2>Information We Process</h2>
          <p>
            Filmroll is designed as an open-source analog film camera and photo
            editor. The app does not require registration and does not collect
            personal information for its own account system.
          </p>
          <ul>
            <li>
              Photos or media you choose are used to provide camera, editing,
              preview, and export features.
            </li>
            <li>
              App preferences such as language, theme, export format, favorite
              film stocks, reminders, and cached LUT availability may be stored
              locally on your device.
            </li>
            <li>
              Basic network requests may be made when the app downloads film LUT
              resources or opens external links.
            </li>
          </ul>
        </section>

        <section>
          <h2>Permissions</h2>
          <p>
            Android permissions are requested only when needed for app features.
          </p>
          <ul>
            <li>Internet access supports downloading film LUT resources.</li>
            <li>Camera access is used when you choose to shoot through Filmroll.</li>
            <li>
              Media or file access is used when you choose an existing photo or
              save an edited image.
            </li>
            <li>
              Notifications are used only for optional daily reminders when
              enabled.
            </li>
          </ul>
        </section>

        <section>
          <h2>Third-Party Services</h2>
          <p>
            The app may rely on platform services that process information under
            their own privacy terms. Review their policies for details:
          </p>
          <ul className="externalList">
            {thirdPartyServices.map((service) => (
              <li key={service.href}>
                <a href={service.href} rel="noreferrer" target="_blank">
                  {service.name}
                  <ExternalLink aria-hidden="true" size={15} />
                </a>
              </li>
            ))}
          </ul>
        </section>

        <section>
          <h2>Sharing</h2>
          <p>
            We do not sell personal information. Information may be shared only
            when required to operate app features, comply with law, protect
            users, or respond to support requests.
          </p>
        </section>

        <section>
          <h2>Retention And Deletion</h2>
          <p>
            Local preferences, cached data, and chosen media remain on your
            device unless you remove them, clear app data, or uninstall the app.
            To request deletion of information associated with a support
            request, contact us.
          </p>
        </section>

        <section>
          <h2>Children</h2>
          <p>
            Filmroll is not directed to children under 13. If you believe a
            child provided personal information, contact us so we can review and
            remove it where applicable.
          </p>
        </section>

        <section>
          <h2>Changes</h2>
          <p>
            This policy may be updated when the app changes. The latest version
            will be posted on this page with an updated date.
          </p>
        </section>

        <section>
          <h2>Contact</h2>
          <p>
            Questions about this policy can be sent to{" "}
            <a href={`mailto:${CONTACT_EMAIL}`}>
              <Mail aria-hidden="true" size={16} />
              {CONTACT_EMAIL}
            </a>
            .
          </p>
        </section>
      </article>
    </main>
  );
}
