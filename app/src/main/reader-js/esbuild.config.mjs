import * as esbuild from "esbuild";
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";

const outDir = "../assets/reader";

const common = {
  bundle: true,
  minify: false,
  target: ["chrome60"],
  format: "iife",
  platform: "browser",
};

const bridgeOut = `${outDir}/bridge.js`;
mkdirSync(dirname(bridgeOut), { recursive: true });
await esbuild.build({
  ...common,
  entryPoints: ["src/index.js"],
  outfile: bridgeOut,
});

copyFileSync("theme.css", `${outDir}/theme.css`);
