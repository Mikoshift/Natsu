import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["reader/src/**/*.test.ts"],
  },
});
