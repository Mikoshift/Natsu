import js from "@eslint/js";

export default [
  {
    ignores: ["node_modules/**", "app/src/main/assets/**"],
  },
  js.configs.recommended,
  {
    files: ["reader-js/src/**/*.js"],
    languageOptions: {
      ecmaVersion: 2020,
      sourceType: "module",
      globals: {
        window: "readonly",
        document: "readonly",
        requestAnimationFrame: "readonly",
        console: "readonly",
        Node: "readonly",
        NodeFilter: "readonly",
      },
    },
    rules: {
      "no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
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
];
