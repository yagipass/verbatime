import { defineConfig, type Plugin } from "vite";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { oxContent, defineTheme, defaultTheme } from "@ox-content/vite-plugin";

const base = process.env.DOCS_BASE ?? "/";
const siteUrl = "https://verbatime-docs.yagipass.com";

function docsImages(): Plugin {
  const dir = join(import.meta.dirname, "content/images");
  return {
    name: "verbatime-docs-images",
    configureServer(server) {
      server.middlewares.use("/images", (req, res, next) => {
        try {
          res.setHeader("Content-Type", "image/png");
          res.end(readFileSync(join(dir, decodeURIComponent(req.url ?? "").replace(/^\/+/, ""))));
        } catch {
          next();
        }
      });
    },
    generateBundle() {
      for (const name of readdirSync(dir).filter((n) => n.endsWith(".png"))) {
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
            logo: "verbatime-icon.png",
            logoWidth: 32,
            logoHeight: 32,
          },
          embed: {
            head: `<link rel="icon" href="${base}verbatime-icon.png" type="image/png">`,
          },
          css: ".header-nav { margin-left: 2rem; } .content img { max-width: 100%; height: auto; }",
          socialLinks:{ github: "https://github.com/yagipass/verbatime" },
          sidebar: [
            {
              text: "Get started",
              items: [
                { text: "What is Verbatime?", link: "/index.md" },
                { text: "Quick start", link: "/quick-start.md" },
                { text: "Use cases", link: "/use-cases.md" },
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
                { text: "vbtm CLI", link: "/cli.md" },
              ],
            },
            {
              text: "More",
              items: [
                { text: "Troubleshooting", link: "/troubleshooting.md" },
                { text: "Examples", link: "/examples.md" },
                { text: "Limitations", link: "/limitations.md" },
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
  ],
  build: { outDir: "dist" },
});
