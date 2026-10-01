// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import type { FrontendPlugin } from '@mosaikit/sdk';
import { describe, expect, it } from 'vitest';
import { entryForPath, launcherEntries } from '../src/navigation.js';

const plugin = (attributes: Record<string, unknown>, id = 'app'): FrontendPlugin => ({
  id: 'dev.example.plugin',
  version: '1.0.0',
  entry: '/index.js',
  isolation: 'module',
  contributions: [{ point: 'launcher.app', id, attributes }],
});

describe('launcherEntries (MK-008)', () => {
  it('builds entries from launcher contributions', () => {
    const entries = launcherEntries([
      plugin({ route: '/app/hello', element: 'mk-hello-app', title: 'Hello' }),
    ]);

    expect(entries).toEqual([
      {
        pluginId: 'dev.example.plugin',
        id: 'app',
        title: 'Hello',
        route: '/app/hello',
        element: 'mk-hello-app',
      },
    ]);
  });

  it('uses the contribution id when the title is missing', () => {
    const [entry] = launcherEntries([plugin({ route: '/app/x', element: 'x-app' }, 'fallback')]);

    expect(entry?.title).toBe('fallback');
  });

  it.each([
    { route: '/elsewhere', element: 'mk-hello-app' },
    { route: '/app/hello', element: 'noHyphen' },
    { route: '/app/hello' },
    { element: 'mk-hello-app' },
    { route: 42, element: 'mk-hello-app' },
  ])('ignores invalid contributions %j', (attributes) => {
    expect(launcherEntries([plugin(attributes)])).toEqual([]);
  });
});

describe('entryForPath', () => {
  const entries = launcherEntries([plugin({ route: '/app/hello', element: 'mk-hello-app' })]);

  it('matches the route, ignoring a trailing slash', () => {
    expect(entryForPath(entries, '/app/hello/')?.element).toBe('mk-hello-app');
    expect(entryForPath(entries, '/app/hello')?.element).toBe('mk-hello-app');
  });

  it('returns nothing for other paths', () => {
    expect(entryForPath(entries, '/')).toBeUndefined();
    expect(entryForPath(entries, '/app/other')).toBeUndefined();
  });
});
