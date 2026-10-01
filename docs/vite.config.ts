import { defineConfig, type Plugin } from "vite";
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { extname, join } from "node:path";
import { oxContent, defineTheme, defaultTheme } from "@ox-content/vite-plugin";

const base = process.env.DOCS_BASE ?? "/";
const siteUrl = "https://verbatime-docs.yagipass.com";
const imageTypes: Record<string, string> = { ".png": "image/png", ".webp": "image/webp" };
const brandColors = {
  primary: "#fb8f24",
  primaryHover: "#fdac5c",
  background: "#222220",
  backgroundAlt: "#2a2926",
  text: "#f2ebe1",
  textMuted: "#aca29a",
  border: "#3a3833",
  codeBackground: "#1a1918",
  codeBackgroundTop: "#1a1918",
  codeText: "#f2ebe1",
};
const flameBars = "linear-gradient(90deg, #fb8f24 0 22%, #e0701c 22% 38%, #c4561a 38% 61%, #f2a54a 61% 74%, #a8481a 74% 100%)";

function docsImages(): Plugin {
  const dir = join(import.meta.dirname, "content/images");
  return {
    name: "verbatime-docs-images",
    configureServer(server) {
      server.middlewares.use(`${base}images`, (req, res, next) => {
        try {
          const name = decodeURIComponent(req.url ?? "").replace(/^\/+/, "");
          const source = readFileSync(join(dir, name));
          res.setHeader("Content-Type", imageTypes[extname(name)] ?? "application/octet-stream");
          res.end(source);
        } catch {
          next();
        }
      });
    },
    generateBundle() {
      for (const name of readdirSync(dir).filter((n) => extname(n) in imageTypes)) {
        this.emitFile({ type: "asset", fileName: `images/${name}`, source: readFileSync(join(dir, name)) });
      }
    },
  };
}

function hostHeaders(): Plugin {
  return {
    name: "verbatime-docs-headers",
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "_headers", source: readFileSync(join(import.meta.dirname, "_headers")) });
    },
  };
}

function sitemapHome(): Plugin {
  return {
    name: "verbatime-docs-sitemap-home",
    closeBundle: {
      order: "post",
      sequential: true,
      handler() {
        const file = join(import.meta.dirname, "dist/sitemap.xml");
        const loc = `<loc>${siteUrl}${base}</loc>`;
        const sitemap = readFileSync(file, "utf8");
        if (sitemap.includes(loc)) return;
        writeFileSync(file, sitemap.replace(/<urlset[^>]*>\n/, (urlset) => `${urlset}  <url>\n    ${loc}\n  </url>\n`));
      },
    },
  };
}

export default defineConfig({
  base,
  publicDir: "../assets",
  plugins: [
    docsImages(),
    hostHeaders(),
    oxContent({
      srcDir: "content",
      outDir: "dist",
      base,
      highlight: true,
      headingPermalinks: true,
      containers: true,
      steps: true,
      codeGroups: true,
      docs: false,
      siteMaps: true,
      ssg: {
        siteName: "Verbatime",
        siteUrl,
        ogImage: `${siteUrl}/verbatime-og.png`,
        notFound: true,
        jsonLd: true,
        theme: defineTheme({
          extends: defaultTheme,
          aside: true,
          nav: [
            { text: "Quick start", link: `${base}quick-start/` },
            { text: "Use cases", link: `${base}use-cases/` },
          ],
          header: {
            logo: "images/verbatime-icon.png",
            logoWidth: 32,
            logoHeight: 32,
          },
          colors: brandColors,
          darkColors: brandColors,
          fonts: {
            named: {
              display: { family: "Lexend", weights: [600, 700], selfHost: true, fallbacks: ["sans-serif"] },
            },
          },
          embed: {
            head: `<link rel="icon" href="${base}images/verbatime-icon.png" type="image/png">
<script>try { localStorage.setItem("theme", "dark") } catch {} document.documentElement.setAttribute("data-theme", "dark")</script>`,
          },
          css: `
            .header-nav { margin-left: 2rem; }
            .content img { max-width: 100%; height: auto; }
            .theme-toggle, [data-mobile-theme] { display: none; }
            .header::after { content: ""; position: absolute; left: 0; right: 0; bottom: -4px; height: 4px; background: ${flameBars}; }
            .nav-link.active, .nav-link.active:hover { background: var(--octc-color-primary); color: var(--octc-color-on-primary); }
            .nav-title { color: var(--octc-color-primary); }
            .content h1, .content h2, .header-title { font-family: var(--octc-font-display); letter-spacing: -0.01em; }
            .content h2 { position: relative; border-bottom: 0; padding-bottom: 0.6rem; }
            .content h2::after { content: ""; position: absolute; left: 0; bottom: 0; width: 4.5rem; height: 4px; background: ${flameBars}; }
            .content .ox-steps__item::before { border-radius: 2px; font-weight: 700; }
          `,
          socialLinks:{ github: "https://github.com/yagipass/verbatime" },
          sidebar: [
            {
              text: "Get started",
              items: [
                { text: "What is Verbatime?", link: "/overview.md" },
                { text: "Quick start", link: "/quick-start.md" },
              ],
            },
            {
              text: "Use cases",
              items: [
                { text: "Overview", link: "/use-cases.md" },
                { text: "A slow request", link: "/use-cases/slow-request.md" },
                { text: "Slow startup", link: "/use-cases/slow-startup.md" },
                { text: "A slow test", link: "/use-cases/slow-test.md" },
                { text: "A slow batch job", link: "/use-cases/batch-job.md" },
              ],
            },
            {
              text: "Agent",
              items: [
                { text: "Adding the agent", link: "/agent/setup.md" },
                { text: "Agent options", link: "/agent/options.md" },
              ],
            },
            {
              text: "Tools",
              items: [
                { text: "JMC plugin", link: "/jmc.md" },
                { text: "CLI", link: "/cli.md" },
              ],
            },
            {
              text: "More",
              items: [
                { text: "Troubleshooting", link: "/troubleshooting.md" },
                { text: "Examples", link: "/examples.md" },
                { text: "Limitations", link: "/limitations.md" },
                { text: "Verifying downloads", link: "/verify.md" },
              ],
            },
          ],
          footer: {
            message:
              'Released under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>.',
            copyright: "Copyright 2026 yagipass",
          },
        }),
      },
    }),
    sitemapHome(),
  ],
  build: { outDir: "dist" },
});
