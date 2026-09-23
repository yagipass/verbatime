import { defineConfig, type Plugin } from "vite";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { oxContent, defineTheme, defaultTheme } from "@ox-content/vite-plugin";

const base = process.env.DOCS_BASE ?? "/";

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

export default defineConfig({
  base,
  publicDir: "../assets",
  plugins: [
    docsImages(),
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
      ssg: {
        siteName: "Verbatime",
        theme: defineTheme({
          extends: defaultTheme,
          aside: true,
          nav: [
            { text: "Guide", link: `${base}getting-started/` },
            { text: "Reference", link: `${base}reference/agent-options/` },
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
              text: "Introduction",
              items: [
                { text: "What is Verbatime?", link: "/index.md" },
                { text: "Getting Started", link: "/getting-started.md" },
              ],
            },
            {
              text: "Guide",
              items: [
                { text: "Recording with JDK Mission Control", link: "/guide/recording-with-jmc.md" },
                { text: "Recording without JDK Mission Control", link: "/guide/recording-without-jmc.md" },
                { text: "Reading a recording with vbtm", link: "/guide/reading-with-vbtm.md" },
                { text: "Using an AI agent", link: "/guide/ai-agents.md" },
              ],
            },
            {
              text: "Reference",
              items: [
                { text: "Agent options", link: "/reference/agent-options.md" },
                { text: "JMC plugin", link: "/reference/jmc-plugin.md" },
                { text: "vbtm commands", link: "/reference/cli.md" },
              ],
            },
            {
              text: "More",
              items: [
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
