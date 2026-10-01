// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { describe, expect, it } from 'vitest';
import { contributionsTo, stringAttribute, type FrontendPlugin } from '../src/contributions.js';
import { definePlugin, isMosaikitPlugin } from '../src/plugin.js';

const plugins: FrontendPlugin[] = [
  {
    id: 'dev.example.zeta',
    version: '1.0.0',
    entry: '/z.js',
    isolation: 'module',
    contributions: [{ point: 'launcher.app', id: 'zeta', attributes: { title: 'Zeta' } }],
  },
  {
    id: 'dev.example.alpha',
    version: '1.0.0',
    entry: '/a.js',
    isolation: 'module',
    contributions: [
      { point: 'launcher.app', id: 'second', attributes: {} },
      { point: 'launcher.app', id: 'first', attributes: { title: 42 } },
      { point: 'shell.command', id: 'cmd', attributes: {} },
    ],
  },
];

describe('contributionsTo', () => {
  it('collects the contributions to a point, ordered by plugin and id', () => {
    const result = contributionsTo(plugins, 'launcher.app');

    expect(result.map((c) => `${c.pluginId}/${c.id}`)).toEqual([
      'dev.example.alpha/first',
      'dev.example.alpha/second',
      'dev.example.zeta/zeta',
    ]);
  });

  it('returns nothing for a point without contributions', () => {
    expect(contributionsTo(plugins, 'maps.toolbar')).toEqual([]);
  });
});

describe('stringAttribute', () => {
  it('returns string attributes only', () => {
    const [first, , zeta] = contributionsTo(plugins, 'launcher.app');

    expect(zeta && stringAttribute(zeta, 'title')).toBe('Zeta');
    expect(first && stringAttribute(first, 'title')).toBeUndefined();
  });
});

describe('plugin modules', () => {
  it('recognizes objects with an activate function', () => {
    expect(isMosaikitPlugin(definePlugin({ activate: () => undefined }))).toBe(true);
    expect(isMosaikitPlugin({ activate: 'no' })).toBe(false);
    expect(isMosaikitPlugin(null)).toBe(false);
    expect(isMosaikitPlugin('module')).toBe(false);
  });
});
