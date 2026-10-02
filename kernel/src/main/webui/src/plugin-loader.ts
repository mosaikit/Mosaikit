// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import {
  dataCollections,
  EventBus,
  contributionsTo,
  isMosaikitPlugin,
  type CurrentUser,
  type FrontendPlugin,
} from '@mosaikit/sdk';

/** Outcome of loading one plugin frontend. */
export interface LoadResult {
  readonly pluginId: string;
  readonly loaded: boolean;
  readonly reason?: string;
}

type ImportModule = (url: string) => Promise<unknown>;

/** Loads plugin modules and activates them with their context. */
export class PluginLoader {
  readonly events: EventBus;

  constructor(
    private readonly request: (path: string, init?: RequestInit) => Promise<Response>,
    private readonly importModule: ImportModule = (url) => import(/* @vite-ignore */ url),
    reportError: (message: string, error: unknown) => void = (message, error) => {
      console.error(message, error);
    },
  ) {
    this.events = new EventBus((error, topic) => {
      reportError(`A handler of '${topic}' failed`, error);
    });
  }

  /** Loads every plugin; one failure never prevents the others from loading. */
  /**
   * @param all every active plugin, whose contributions the loaded ones can read; by default the
   *     loaded ones
   */
  async loadAll(
    plugins: readonly FrontendPlugin[],
    user: CurrentUser,
    all: readonly FrontendPlugin[] = plugins,
  ): Promise<LoadResult[]> {
    return Promise.all(plugins.map((plugin) => this.load(plugin, user, all)));
  }

  private async load(
    plugin: FrontendPlugin,
    user: CurrentUser,
    all: readonly FrontendPlugin[],
  ): Promise<LoadResult> {
    if (plugin.isolation === 'iframe') {
      // Isolated frontends are loaded in their own frame when one of their apps is opened (MK-014).
      return { pluginId: plugin.id, loaded: true };
    }
    try {
      const module = await this.importModule(plugin.entry);
      const candidate = (module as { default?: unknown }).default;
      if (!isMosaikitPlugin(candidate)) {
        return {
          pluginId: plugin.id,
          loaded: false,
          reason: 'the module has no default plugin export',
        };
      }
      await candidate.activate({
        plugin: { id: plugin.id, version: plugin.version },
        contributions: plugin.contributions,
        // Only the plugin that declares a point reads what others contribute to it (MK-020).
        contributionsTo: (point: string) =>
          plugin.points?.includes(point) ? contributionsTo(all, point) : [],
        events: this.events,
        locale: navigator.language,
        user,
        fetch: this.request,
        data: dataCollections(this.request, plugin.id),
      });
      return { pluginId: plugin.id, loaded: true };
    } catch (error) {
      return { pluginId: plugin.id, loaded: false, reason: String(error) };
    }
  }
}
