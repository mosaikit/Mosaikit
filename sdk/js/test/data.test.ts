// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { describe, expect, it, vi } from 'vitest';
import { DataError, dataCollections } from '../src/data.js';

function answering(status: number, body?: unknown) {
  return vi.fn<(path: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'content-type': 'application/json' },
      }),
    ),
  );
}

describe('dataCollections (ADR-0031)', () => {
  it('calls the collection of the plugin', async () => {
    const fetch = answering(200, []);
    const items = dataCollections(fetch, 'dev.example.todo')('items');

    await items.list({ offset: 10, limit: 5 });
    await items.get('a/b');
    await items.create({ title: 'Paint' });
    await items.update('42', { title: 'Paint' }, 3);
    await items.update('42', { title: 'Paint' });

    const base = '/api/v1/data/dev.example.todo/items';
    expect(fetch.mock.calls.map(([path, init]) => [path, init?.method ?? 'GET'])).toEqual([
      [`${base}?offset=10&limit=5`, 'GET'],
      [`${base}/a%2Fb`, 'GET'],
      [base, 'POST'],
      [`${base}/42`, 'PUT'],
      [`${base}/42`, 'PUT'],
    ]);
    expect(fetch.mock.calls[3]?.[1]?.headers).toEqual({
      'Content-Type': 'application/json',
      'If-Match': '3',
    });
    expect(fetch.mock.calls[4]?.[1]?.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(fetch.mock.calls[2]?.[1]?.body).toBe('{"title":"Paint"}');
  });

  it('removes without reading a body', async () => {
    const fetch = answering(204);

    await expect(dataCollections(fetch, 'p')('items').remove('1')).resolves.toBeUndefined();
    expect(fetch).toHaveBeenCalledWith('/api/v1/data/p/items/1', { method: 'DELETE' });
  });

  it('turns refusals into errors with the detail of the kernel', async () => {
    const conflict = dataCollections(answering(409, { detail: 'changed since' }), 'p')('items');
    const plain = dataCollections(answering(500, 'oops'), 'p')('items');

    await expect(conflict.update('1', {}, 1)).rejects.toEqual(new DataError(409, 'changed since'));
    const error = await plain.get('1').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(DataError);
    expect(error).toMatchObject({ status: 500, message: 'HTTP 500', name: 'DataError' });
  });
});
