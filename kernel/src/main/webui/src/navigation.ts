// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { contributionsTo, stringAttribute, type FrontendPlugin } from '@mosaikit/sdk';

/** Extension point through which a plugin adds an app to the launcher. */
export const LAUNCHER_POINT = 'launcher.app';

/** An app offered in the launcher. */
export interface LauncherEntry {
  readonly pluginId: string;
  readonly id: string;
  readonly title: string;
  readonly route: string;
  /** Custom element that renders the app. */
  readonly element: string;
}

const CUSTOM_ELEMENT = /^[a-z][a-z0-9]*(-[a-z0-9]+)+$/;
const ROUTE = /^\/app\/[a-z0-9-]+$/;

/**
 * Builds the launcher from the contributions of active plugins. Contributions without a valid
 * route or custom element name are ignored, so that a broken plugin cannot break the shell.
 */
export function launcherEntries(plugins: readonly FrontendPlugin[]): LauncherEntry[] {
  return contributionsTo(plugins, LAUNCHER_POINT).flatMap((contribution) => {
    const route = stringAttribute(contribution, 'route');
    const element = stringAttribute(contribution, 'element');
    if (!route || !ROUTE.test(route) || !element || !CUSTOM_ELEMENT.test(element)) {
      return [];
    }
    return [
      {
        pluginId: contribution.pluginId,
        id: contribution.id,
        title: stringAttribute(contribution, 'title') ?? contribution.id,
        route,
        element,
      },
    ];
  });
}

/** Finds the entry for a path, ignoring a trailing slash. */
export function entryForPath(
  entries: readonly LauncherEntry[],
  path: string,
): LauncherEntry | undefined {
  const normalized = path.length > 1 ? withoutTrailingSlashes(path) : path;
  return entries.find((entry) => entry.route === normalized);
}

function withoutTrailingSlashes(path: string): string {
  let end = path.length;
  while (end > 0 && path[end - 1] === '/') {
    end--;
  }
  return path.slice(0, end);
}
