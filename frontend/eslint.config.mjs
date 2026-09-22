import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['**/dist/**', '**/build/**', '**/release/**', 'node_modules/**', '**/*.config.js'],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: [
      'apps/web/src/**/*.{ts,tsx}',
      // 렌더러 코드와 웹·앱 공용 코드는 브라우저에서 돈다.
      'apps/desktop/src/**/*.{ts,tsx}',
      'packages/**/*.{ts,tsx}',
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: '19' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'simple-import-sort': simpleImportSort,
    },
    rules: {
      ...react.configs.flat.recommended.rules,
      ...react.configs.flat['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['apps/desktop/main/**/*.ts'],
    languageOptions: { globals: globals.node },
  },

  prettier,
);
