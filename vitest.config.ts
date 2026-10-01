// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      '@mosaikit/sdk': fileURLToPath(new URL('./sdk/js/src/index.ts', import.meta.url)),
    },
  },
  test: {
    include: [
      'sdk/js/test/**/*.test.ts',
      'sdk/create-plugin/test/**/*.test.ts',
      'sdk/java/src/test/ts/**/*.test.ts',
      'kernel/src/main/webui/test/**/*.test.ts',
      'docs/requirements/test/**/*.test.ts',
      'docs/test/**/*.test.ts',
      'plugins/*/test/**/*.test.ts',
    ],
    environment: 'node',
    coverage: {
      provider: 'v8',
      include: ['sdk/js/src/**', 'sdk/create-plugin/src/**', 'kernel/src/main/webui/src/**'],
      exclude: [
        'sdk/create-plugin/src/bin.ts',
        'kernel/src/main/webui/src/main.ts',
        'kernel/src/main/webui/src/mk-shell.ts',
        'kernel/src/main/webui/src/mk-admin-plugins.ts',
        'kernel/src/main/webui/src/mk-assistant.ts',
        'kernel/src/main/webui/src/mk-plugin-frame.ts',
      ],
      reporter: ['text', 'lcov', 'html'],
      thresholds: { lines: 85, branches: 80, functions: 85, statements: 85 },
    },
  },
});
