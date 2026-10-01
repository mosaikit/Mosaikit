// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { describe, expect, it, vi } from 'vitest';
import { KernelClient, KernelError, ORGANIZATION_HEADER } from '../src/api.js';

const json = (status: number, body: unknown): Response =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('KernelClient (MK-008)', () => {
  it('signs in with basic credentials and reuses them', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, { username: 'ada@example.org' })),
    );
    const client = new KernelClient(fetchMock);

    const account = await client.signIn('ada@example.org', 'secret');
    await client.shellPlugins();

    expect(account.username).toBe('ada@example.org');
    expect(client.signedIn).toBe(true);
    const headers = new Headers(fetchMock.mock.calls[1]?.[1]?.headers);
    expect(headers.get('Authorization')).toBe('Basic ' + btoa('ada@example.org:secret'));
  });

  it('forgets credentials when sign-in fails', async () => {
    const client = new KernelClient(() => Promise.resolve(new Response(null, { status: 401 })));

    await expect(client.signIn('ada@example.org', 'wrong')).rejects.toMatchObject({
      status: 401,
      message: 'The email or the password is not correct.',
    });
    expect(client.signedIn).toBe(false);
  });

  it('turns problem details into errors', async () => {
    const client = new KernelClient(() =>
      Promise.resolve(json(409, { title: 'Conflict', detail: 'Already exists.' })),
    );

    const error = await client.systemInfo().catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(KernelError);
    expect(error).toMatchObject({ status: 409, title: 'Conflict', message: 'Already exists.' });
  });

  it('copes with error bodies that are not JSON', async () => {
    const client = new KernelClient(() =>
      Promise.resolve(new Response('oops', { status: 500, statusText: 'Server Error' })),
    );

    await expect(client.systemInfo()).rejects.toMatchObject({ status: 500, title: 'Server Error' });
  });

  it('clears credentials at sign-out', async () => {
    const client = new KernelClient(() => Promise.resolve(json(200, {})));
    await client.signIn('a@b.c', 'x');

    client.signOut();

    expect(client.signedIn).toBe(false);
  });

  it('signs in with an access token of a realm (MK-012)', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, { username: 'ada@acme.com' })),
    );
    const client = new KernelClient(fetchMock);

    await client.signInWithToken('token-1');
    client.useToken('token-2');
    await client.shellPlugins();

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer token-1',
    );
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer token-2',
    );
  });

  it('asks the kernel how an email address signs in (MK-012)', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, { organization: 'acme', federation: null })),
    );

    const options = await new KernelClient(fetchMock).signInOptions('ada+x@acme.com');

    expect(options.organization).toBe('acme');
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/identity/sign-in-options?email=ada%2Bx%40acme.com',
    );
  });

  it('forgets a token that the kernel refuses (MK-012)', async () => {
    const client = new KernelClient(() => Promise.resolve(new Response(null, { status: 401 })));

    await expect(client.signInWithToken('expired')).rejects.toMatchObject({ status: 401 });
    expect(client.signedIn).toBe(false);
  });

  it('acts on the organization chosen by the person until sign-out (MK-017)', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, { username: 'ada@example.org', organization: 'globex' })),
    );
    const client = new KernelClient(fetchMock);
    await client.signIn('ada@example.org', 'secret');

    const account = await client.useOrganization('globex');
    await client.shellPlugins();
    client.signOut();
    await client.systemInfo();

    expect(account.organization).toBe('globex');
    const header = (call: number) =>
      new Headers(fetchMock.mock.calls[call]?.[1]?.headers).get(ORGANIZATION_HEADER);
    expect(header(0)).toBeNull();
    expect(header(1)).toBe('globex');
    expect(header(2)).toBe('globex');
    expect(header(3)).toBeNull();
  });

  it('lists, confirms and rejects the drafts of assistants (MK-015)', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, [])),
    );
    const client = new KernelClient(fetchMock);

    await client.actionDrafts();
    await client.confirmDraft('d 1');
    await client.rejectDraft('d2');

    expect(fetchMock.mock.calls.map(([path, init]) => `${init?.method ?? ''} ${path}`)).toEqual([
      'GET /api/v1/ai/drafts',
      'POST /api/v1/ai/drafts/d%201/confirmation',
      'DELETE /api/v1/ai/drafts/d2',
    ]);
  });

  it('reads the marketplace and installs packages (MK-022)', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, { sources: [], plugins: [] })),
    );
    const client = new KernelClient(fetchMock);

    await client.marketplace();
    await client.install({ source: 'file:/catalog/', id: 'dev.acme.maps', version: '1.0.0' });
    await client.upload(new Blob(['zip'], { type: 'application/zip' }));

    const calls = fetchMock.mock.calls;
    expect(calls.map(([path, init]) => `${init?.method ?? ''} ${path}`)).toEqual([
      'GET /api/v1/marketplace',
      'POST /api/v1/marketplace/installations',
      'POST /api/v1/plugins/packages',
    ]);
    expect(JSON.parse(calls[1]?.[1]?.body as string)).toEqual({
      source: 'file:/catalog/',
      id: 'dev.acme.maps',
      version: '1.0.0',
    });
    expect(new Headers(calls[2]?.[1]?.headers).get('Content-Type')).toBe('application/zip');
    expect(calls[2]?.[1]?.body).toBeInstanceOf(Blob);
  });

  it('talks with the assistant (MK-024)', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
      Promise.resolve(json(200, { enabled: true, reply: 'Ciao', tools: [], drafts: [] })),
    );
    const client = new KernelClient(fetchMock);

    expect((await client.assistant()).enabled).toBe(true);
    const reply = await client.ask([{ role: 'user', content: 'Ciao' }]);

    expect(reply.reply).toBe('Ciao');
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/ai/assistant/replies');
    expect(JSON.parse(fetchMock.mock.calls[1]?.[1]?.body as string)).toEqual({
      messages: [{ role: 'user', content: 'Ciao' }],
    });
  });
});
