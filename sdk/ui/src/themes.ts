// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { createDarkTheme, createLightTheme, type BrandVariants } from '@fluentui/tokens';

/**
 * The themes of the shell. Each one gives the tokens of Fluent UI and the `--mk-*` tokens that
 * plugins read, for a light and a dark color scheme.
 */
export const THEMES = ['mosaikit', 'pa'] as const;
export type ThemeName = (typeof THEMES)[number];
export type ColorScheme = 'light' | 'dark';

/** Whether a value is the name of a theme. */
export function isThemeName(value: unknown): value is ThemeName {
  return typeof value === 'string' && (THEMES as readonly string[]).includes(value);
}

interface Palette {
  readonly brand: string;
  readonly background: string;
  readonly surface: string;
  readonly foreground: string;
  readonly muted: string;
  readonly line: string;
  readonly danger: string;
  readonly success: string;
  readonly warning: string;
  /** The ring around the element with the keyboard focus. */
  readonly focus: string;
}

interface ThemeDefinition {
  readonly title: string;
  readonly font: string;
  readonly monospace: string;
  /** Radius of controls, in pixels; larger surfaces use twice as much. */
  readonly radius: number;
  readonly light: Palette;
  readonly dark: Palette;
}

const MONOSPACE = "'Roboto Mono', ui-monospace, 'Cascadia Mono', Consolas, monospace";

const DEFINITIONS: Record<ThemeName, ThemeDefinition> = {
  // The default: a workspace in the manner of collaboration suites, on the fonts of the system.
  mosaikit: {
    title: 'Mosaikit',
    font: "'Segoe UI Variable', 'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', sans-serif",
    monospace: MONOSPACE,
    radius: 6,
    light: {
      brand: '#3654c9',
      background: '#f5f6fa',
      surface: '#ffffff',
      foreground: '#1c2333',
      muted: '#5a6478',
      line: '#d8dce6',
      danger: '#b3261e',
      success: '#0e7a3d',
      warning: '#8a5300',
      focus: '#1c2333',
    },
    dark: {
      brand: '#7d95ff',
      background: '#14171f',
      surface: '#1d2230',
      foreground: '#e6e9f2',
      muted: '#9aa3b8',
      line: '#2e3546',
      danger: '#f2867e',
      success: '#5fd08f',
      warning: '#f0b45a',
      focus: '#ffffff',
    },
  },
  // Public administrations: the colors, the font and the visible focus of Bootstrap Italia,
  // the user interface kit of Designers Italia (AgID design guidelines). Its red (#d9364f) and
  // its orange focus (#ff9900) are darkened where they would miss the contrast of WCAG 2.1 AA,
  // which the accessibility obligations of public administrations require.
  pa: {
    title: 'Public administration (Bootstrap Italia)',
    font: "'Titillium Web', Geneva, Tahoma, sans-serif",
    monospace: MONOSPACE,
    radius: 4,
    light: {
      brand: '#0066cc',
      background: '#f2f7fc',
      surface: '#ffffff',
      foreground: '#1a1a1a',
      muted: '#5d7083',
      line: '#c5c7c9',
      danger: '#c22a44',
      success: '#008055',
      warning: '#a66300',
      focus: '#b35c00',
    },
    dark: {
      brand: '#4da3ff',
      background: '#0f1720',
      surface: '#17212c',
      foreground: '#f2f2f2',
      muted: '#a7b4c2',
      line: '#33404d',
      danger: '#f2788a',
      success: '#4cc499',
      warning: '#f5b450',
      focus: '#ff9900',
    },
  },
};

/** The title of a theme, for the people who choose it. */
export function themeTitle(name: ThemeName): string {
  return DEFINITIONS[name].title;
}

function channels(hex: string): [number, number, number] {
  const value = /^#([0-9a-f]{6})$/i.exec(hex)?.[1];
  if (!value) {
    throw new Error(`Not a color: ${hex}`);
  }
  return [0, 2, 4].map((at) => parseInt(value.slice(at, at + 2), 16)) as [number, number, number];
}

function mix(hex: string, with_: string, amount: number): string {
  const from = channels(hex);
  const to = channels(with_);
  return `#${from
    .map((channel, index) => Math.round(channel + ((to[index] ?? 0) - channel) * amount))
    .map((channel) => channel.toString(16).padStart(2, '0'))
    .join('')}`;
}

/**
 * The 16 shades of a brand color that Fluent UI expects, from the darkest (10) to the lightest
 * (160), with the color itself at 80, where Fluent puts the background of primary buttons.
 */
export function brandVariants(brand: string): BrandVariants {
  const shades: Record<number, string> = {};
  for (let step = 10; step <= 160; step += 10) {
    shades[step] =
      step < 80
        ? mix(brand, '#000000', ((80 - step) / 80) * 0.8)
        : mix(brand, '#ffffff', ((step - 80) / 80) * 0.95);
  }
  return shades as unknown as BrandVariants;
}

export interface ThemeTokens {
  /** Tokens of Fluent UI, without the leading `--`. */
  readonly fluent: Record<string, string | number>;
  /** The `--mk-*` tokens of the shell and the plugins, with the leading `--`. */
  readonly mosaikit: Record<string, string>;
}

/** The tokens of a theme in a color scheme. */
export function themeTokens(name: ThemeName, scheme: ColorScheme): ThemeTokens {
  const theme = DEFINITIONS[name];
  const palette = theme[scheme];
  const brand = brandVariants(palette.brand);
  const base = scheme === 'light' ? createLightTheme(brand) : createDarkTheme(brand);
  const radius = `${String(theme.radius)}px`;
  const fluent: Record<string, string | number> = {
    ...(base as unknown as Record<string, string | number>),
    fontFamilyBase: theme.font,
    fontFamilyMonospace: theme.monospace,
    borderRadiusSmall: `${String(Math.max(2, theme.radius / 2))}px`,
    borderRadiusMedium: radius,
    borderRadiusLarge: `${String(theme.radius * 1.5)}px`,
    borderRadiusXLarge: `${String(theme.radius * 2)}px`,
    colorNeutralBackground1: palette.surface,
    colorNeutralBackground2: palette.background,
    colorNeutralForeground1: palette.foreground,
    colorNeutralForeground2: palette.foreground,
    colorNeutralForeground3: palette.muted,
    colorNeutralStroke1: palette.line,
    colorStrokeFocus2: palette.focus,
    colorPaletteRedForeground1: palette.danger,
    colorStatusDangerForeground1: palette.danger,
    colorStatusSuccessForeground1: palette.success,
    colorStatusWarningForeground1: palette.warning,
  };
  const accent = scheme === 'light' ? palette.brand : brand[100];
  return {
    fluent,
    mosaikit: {
      '--mk-bg': palette.background,
      '--mk-surface': palette.surface,
      '--mk-fg': palette.foreground,
      '--mk-muted': palette.muted,
      '--mk-line': palette.line,
      '--mk-accent': accent,
      // The brand color of the light scheme in both: white text stays readable on it.
      '--mk-brand': theme.light.brand,
      '--mk-accent-fg': scheme === 'light' ? '#ffffff' : brand[10],
      '--mk-accent-soft': scheme === 'light' ? brand[160] : brand[30],
      '--mk-danger': palette.danger,
      '--mk-success': palette.success,
      '--mk-warning': palette.warning,
      '--mk-focus': palette.focus,
      '--mk-radius': radius,
      '--mk-font': theme.font,
      '--mk-font-mono': theme.monospace,
    },
  };
}
