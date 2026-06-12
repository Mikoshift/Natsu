import * as esbuild from "esbuild";
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const readerJsDir = dirname(fileURLToPath(import.meta.url));
const outDir = join(readerJsDir, "../app/src/main/assets/reader");

const common = {
  bundle: true,
  minify: false,
  target: ["chrome60"],
  format: "iife",
  platform: "browser",
};

const bridgeOut = join(outDir, "bridge.js");
mkdirSync(dirname(bridgeOut), { recursive: true });
await esbuild.build({
  ...common,
  entryPoints: [join(readerJsDir, "src/index.ts")],
  outfile: bridgeOut,
});

copyFileSync(join(readerJsDir, "theme.css"), join(outDir, "theme.css"));
