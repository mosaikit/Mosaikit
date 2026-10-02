// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
/**
 * Public contract between the Mosaikit shell and plugin frontends.
 *
 * @packageDocumentation
 */
export { NO_LIVE, type LiveChannel } from './live.js';
export { contributionsTo, stringAttribute } from './contributions.js';
export type {
  Contribution,
  FrontendBridge,
  FrontendPlugin,
  OwnedContribution,
} from './contributions.js';
export { EventBus, isTopic, patternCovers, topicMatches } from './events.js';
export type { EventHandler, HandlerErrorListener } from './events.js';
export { DataError, dataCollections } from './data.js';
export type { DataCollection, DataDocument } from './data.js';
export { definePlugin, isMosaikitPlugin } from './plugin.js';
export type { CurrentUser, MosaikitPlugin, PluginContext } from './plugin.js';
