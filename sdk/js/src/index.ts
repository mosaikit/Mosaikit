// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
/**
 * Public contract between the Mosaikit shell and plugin frontends.
 *
 * @packageDocumentation
 */
export { contributionsTo, stringAttribute } from './contributions.js';
export type {
  Contribution,
  FrontendBridge,
  FrontendPlugin,
  OwnedContribution,
} from './contributions.js';
export { EventBus, isTopic, patternCovers, topicMatches } from './events.js';
export type { EventHandler, HandlerErrorListener } from './events.js';
export { definePlugin, isMosaikitPlugin } from './plugin.js';
export type { CurrentUser, MosaikitPlugin, PluginContext } from './plugin.js';
