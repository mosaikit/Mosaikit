// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import type { LiveChannel } from './live.js';
import type { Contribution, OwnedContribution } from './contributions.js';
import type { DataCollection } from './data.js';
import type { EventBus } from './events.js';

/** Information about the signed-in person, as far as a plugin needs it. */
export interface CurrentUser {
  readonly username: string;
  readonly displayName: string;
  readonly roles: readonly string[];
}

/**
 * What the shell gives to a plugin when it activates it.
 *
 * The context is a facade: plugins never reach the internals of the shell, so the shell can
 * change without breaking them.
 */
export interface PluginContext {
  /** The plugin being activated. */
  readonly plugin: { readonly id: string; readonly version: string };
  /** Contributions of this plugin, as declared in its manifest. */
  readonly contributions: readonly Contribution[];
  /**
   * The contributions of every active plugin to one of the extension points that this plugin
   * declares in `frontend.points` (MK-020), in a stable order; empty for other points, and in an
   * isolated frame, where the elements of other plugins are not loaded.
   */
  readonly contributionsTo: (point: string) => readonly OwnedContribution[];
  /** Event channel shared by all plugins. */
  readonly events: EventBus;
  /** Current language, as a BCP 47 tag. */
  readonly locale: string;
  /** Signed-in person. */
  readonly user: CurrentUser;
  /** Calls the kernel API with the credentials of the signed-in person. */
  readonly fetch: (path: string, init?: RequestInit) => Promise<Response>;
  /**
   * A collection of documents that the plugin declares in `data.collections` of its manifest,
   * kept by the kernel for the organization of the person (ADR-0031).
   */
  readonly data: <T extends object = Record<string, unknown>>(
    collection: string,
  ) => DataCollection<T>;
  /** Events of the kernel in real time (MK-031); nothing arrives in an isolated frame. */
  readonly live: LiveChannel;
}

/** The module exported by a plugin frontend. */
export interface MosaikitPlugin {
  /** Called once, after the module is loaded. Registers custom elements and subscriptions. */
  activate(context: PluginContext): void | Promise<void>;
  /** Called when the shell unloads the plugin, for example at sign-out. */
  deactivate?(): void | Promise<void>;
}

/** Declares a plugin frontend with type checking. */
export function definePlugin(plugin: MosaikitPlugin): MosaikitPlugin {
  return plugin;
}

/** Returns `true` if a loaded module looks like a plugin frontend. */
export function isMosaikitPlugin(candidate: unknown): candidate is MosaikitPlugin {
  return (
    typeof candidate === 'object' &&
    candidate !== null &&
    'activate' in candidate &&
    typeof candidate.activate === 'function'
  );
}
