// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

/** Where things are, and the settings of the test installation; each can be changed by env. */
export const ROOT = fileURLToPath(new URL('../../', import.meta.url));
export const DIST = join(ROOT, 'target', 'dist');
export const WORK = join(ROOT, 'e2e', '.work');
export const INSTALLATION = join(WORK, 'mosaikit');
export const CATALOG = join(WORK, 'catalog');
export const KEYS = join(WORK, 'keys');

const env = (name: string, fallback: string): string => process.env[name] ?? fallback;

export const KERNEL_PORT = Number(env('E2E_KERNEL_PORT', '8181'));
export const MODEL_PORT = Number(env('E2E_MODEL_PORT', '18091'));
export const CONTROL_PORT = Number(env('E2E_CONTROL_PORT', '18090'));
export const BASE_URL = `http://localhost:${String(KERNEL_PORT)}`;
export const CONTROL_URL = `http://localhost:${String(CONTROL_PORT)}`;
export const MODEL_URL = `http://localhost:${String(MODEL_PORT)}/v1`;

export const DATABASE = {
  url: env('E2E_DB_URL', 'jdbc:postgresql://localhost:55432/mosaikit'),
  username: env('E2E_DB_USERNAME', 'mosaikit'),
  password: env('E2E_DB_PASSWORD', 'mosaikit'),
};

/** Signs the packages and the index that the tests add; trusted by the test installation. */
export const KEY_NAME = 'e2e';

export const ORGANIZATION = { slug: 'comune-prova', name: 'Comune di Prova' };

export interface Person {
  readonly user: string;
  readonly password: string;
  readonly name: string;
}
export const ADMIN: Person = {
  user: 'admin',
  password: 'E2e-admin-2026',
  name: 'Platform administrator',
};
export const MARIO: Person = {
  user: 'mario.rossi@comune.test',
  password: 'Prova-2026-sicura',
  name: 'Mario Rossi',
};
export const ANNA: Person = {
  user: 'anna.bianchi@comune.test',
  password: 'Prova-2026-sicura',
  name: 'Anna Bianchi',
};

export const WINDOWS = process.platform === 'win32';
