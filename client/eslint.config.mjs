import { defineConfig, globalIgnores } from "eslint/config"
import nextVitals from "eslint-config-next/core-web-vitals"
import nextTs from "eslint-config-next/typescript"

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,

  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts"]),

  {
    plugins: {
      boundaries,
      "simple-import-sort": simpleImportSort,
      "unused-imports": unusedImports,
    },

    settings: {
      "boundaries/elements": [
        {
          type: "app",
          pattern: "src/app/**",
        },
        {
          type: "views",
          pattern: "src/views/**",
        },
        {
          type: "widgets",
          pattern: "src/widgets/*",
          capture: ["slice"],
        },
        {
          type: "features",
          pattern: "src/features/*",
          capture: ["slice"],
        },
        {
          type: "entities",
          pattern: "src/entities/*",
          capture: ["slice"],
        },
        {
          type: "shared",
          pattern: "src/shared/**",
        },
      ],
    },

    rules: {
      "simple-import-sort/imports": "warn",
      "simple-import-sort/exports": "warn",

      "unused-imports/no-unused-imports": "warn",

      "@typescript-eslint/no-unused-vars": "off",

      "unused-imports/no-unused-vars": [
        "warn",
        {
          vars: "all",
          varsIgnorePattern: "^_",
          args: "after-used",
          argsIgnorePattern: "^_",
        },
      ],

      "@typescript-eslint/consistent-type-imports": [
        "error",
        {
          prefer: "type-imports",
          fixStyle: "separate-type-imports",
          disallowTypeAnnotations: true,
        },
      ],

      "boundaries/dependencies": [
        "error",
        {
          default: "disallow",
          rules: [
            {
              from: { type: "app" },
              allow: {
                to: {
                  type: ["views", "widgets", "features", "entities", "shared"],
                },
              },
            },
            {
              from: { type: "views" },
              allow: {
                to: { type: ["widgets", "features", "entities", "shared"] },
              },
            },
            {
              from: { type: "widgets" },
              allow: {
                to: { type: ["features", "entities", "shared"] },
              },
            },
            {
              from: { type: "features" },
              allow: {
                to: { type: ["entities", "shared"] },
              },
            },
            {
              from: { type: "entities" },
              allow: {
                to: { type: "shared" },
              },
            },
            {
              from: { type: "shared" },
              allow: {
                to: { type: "shared" },
              },
            },
          ],
        },
      ],

      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: [
                "@views/*/*",
                "@widgets/*/*",
                "@entities/*/*",
                "@features/*/*/*",
              ],
              message: "Use public API imports from index.ts.",
            },
          ],
        },
      ],
    },
  },

  eslintConfigPrettier,
])

export default eslintConfig
