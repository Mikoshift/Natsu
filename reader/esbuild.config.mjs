import * as esbuild from "esbuild";
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const readerDir = dirname(fileURLToPath(import.meta.url));
const outDir = join(readerDir, "../app/src/main/assets/reader");

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
  entryPoints: [join(readerDir, "src/index.ts")],
  outfile: bridgeOut,
});

copyFileSync(join(readerDir, "theme.css"), join(outDir, "theme.css"));
