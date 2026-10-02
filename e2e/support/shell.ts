// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { expect, type Locator, type Page } from '@playwright/test';
import { ADMIN, BASE_URL, CONTROL_URL, ORGANIZATION, type Person } from './env.js';
import type { Settings } from './installation.js';

/** Signs in on the sign-in form of the shell with a local password. */
export async function signIn(page: Page, person: Person = ADMIN): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in with a password' }).click();
  await page.getByLabel('Email or username').fill(person.user);
  await page.getByLabel('Password', { exact: true }).fill(person.password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Welcome/ })).toBeVisible();
}

export const apps = (page: Page): Locator => page.getByRole('navigation', { name: 'Apps' });

export async function openApp(page: Page, name: string): Promise<void> {
  await apps(page).getByRole('link', { name, exact: true }).click();
}

/** Restarts the installation, with other settings when given; returns the log of the restart. */
export async function restart(settings?: Settings): Promise<string> {
  const response = await fetch(`${CONTROL_URL}/restart`, {
    method: 'POST',
    body: JSON.stringify(settings ? { settings } : {}),
  });
  const log = await response.text();
  if (!response.ok) {
    throw new Error(`Restart failed: ${log}`);
  }
  return log;
}

/** Calls the API of the kernel as a person, in the organization of the tests. */
export async function api(
  person: Person,
  path: string,
  init: RequestInit = {},
): Promise<{ status: number; body: unknown }> {
  const headers = new Headers(init.headers);
  headers.set(
    'authorization',
    `Basic ${Buffer.from(`${person.user}:${person.password}`).toString('base64')}`,
  );
  headers.set('X-Mosaikit-Organization', ORGANIZATION.slug);
  headers.set('content-type', headers.get('content-type') ?? 'application/json');
  const response = await fetch(BASE_URL + path, { ...init, headers });
  const text = await response.text();
  let body: unknown = text;
  try {
    body = JSON.parse(text) as unknown;
  } catch {
    // not JSON
  }
  return { status: response.status, body };
}

export interface Activity {
  readonly id: string;
  readonly title: string;
  readonly done: boolean;
}

export async function activities(person: Person): Promise<Activity[]> {
  return (await api(person, '/api/v1/p/sample-activities/activities')).body as Activity[];
}

export async function createActivity(person: Person, title: string): Promise<Activity> {
  const { status, body } = await api(person, '/api/v1/p/sample-activities/activities', {
    method: 'POST',
    body: JSON.stringify({ title }),
  });
  expect(status).toBe(201);
  return body as Activity;
}

export interface PluginStatus {
  readonly id: string;
  readonly version: string;
  readonly status: string;
  readonly problems: readonly string[];
}

export async function plugins(): Promise<PluginStatus[]> {
  const { body } = await api(ADMIN, '/api/v1/plugins');
  return Array.isArray(body)
    ? (body as PluginStatus[])
    : (body as { plugins: PluginStatus[] }).plugins;
}

/** A title that no other run of the tests used. */
export const unique = (prefix: string): string => `${prefix} ${Date.now().toString(36)}`;
