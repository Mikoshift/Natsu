import js from "@eslint/js";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["node_modules/**", "app/src/main/assets/**"],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["reader-js/src/**/*.ts"],
    rules: {
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-non-null-assertion": "off",
      "no-console": "warn",
      eqeqeq: ["error", "always", { null: "ignore" }],
      curly: ["error", "all"],
    },
  },
  {
    files: [
      "reader-js/**/*.config.{js,mjs}",
      "reader-js/vitest.config.mjs",
      "eslint.config.js",
    ],
    languageOptions: {
      ecmaVersion: 2020,
      sourceType: "module",
      globals: {
        process: "readonly",
      },
    },
  },
);
