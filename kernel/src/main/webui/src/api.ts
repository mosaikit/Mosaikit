// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import type { FrontendPlugin } from '@mosaikit/sdk';
import type { SignInOptions } from './federation.js';

/** Public information returned before sign-in. */
export interface SystemInfo {
  readonly name: string;
  readonly version: string;
  readonly activePlugins: number;
}

/** An organization of the signed-in person (MK-017). */
export interface Membership {
  readonly organizationId: string;
  readonly slug: string;
  readonly name: string;
  readonly roles: readonly string[];
  /** `realm`: the organization accepts only its identity provider, not passwords. */
  readonly signIn: 'password' | 'realm' | 'password-or-realm';
}

/** The signed-in account, as seen by the current request. */
export interface Account {
  readonly id: string;
  readonly username: string;
  readonly displayName: string;
  /** Roles of the current request: those of the platform and those in its organization. */
  readonly roles: readonly string[];
  /** The organization the requests act on, or `null`. */
  readonly organizationId: string | null;
  readonly organization?: string | null;
  readonly memberships?: readonly Membership[];
}

/** Header with which the shell names the organization chosen by the person. */
/** A tool proposed by an assistant, waiting for the person to confirm it (MK-015). */
export interface ActionDraft {
  id: string;
  tool: string;
  title: string;
  description?: string;
  risk?: string;
  input: unknown;
  status: string;
  createdAt: string;
  expiresAt: string;
}

/** The outcome of confirming a draft. */
export interface Invocation {
  outcome: 'executed' | 'drafted';
  result?: { status: number; body?: unknown };
  draft?: ActionDraft;
}

/** A package offered by a catalog of the marketplace (MK-022). */
export interface Offer {
  source: string;
  id: string;
  version: string;
  name: string;
  size: number;
  publisherKey: string;
  installedVersion?: string;
  /** `restart`: the package of this version is in place and waits for a restart. */
  state: 'available' | 'installed' | 'update' | 'older' | 'restart';
}

/** The catalogs of the marketplace and what they offer. */
export interface Catalog {
  sources: { uri: string; status: 'verified' | 'refused'; keyId?: string; error?: string }[];
  plugins: Offer[];
}

/** A package placed in the plugins directory, used at the next start. */
export interface Installation {
  id: string;
  version: string;
  file: string;
  replaced?: string;
  problems?: string[];
  restartRequired: boolean;
}

/** A message of a conversation with the assistant (MK-024). */
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

/** What the assistant did for a question. */
export interface AssistantReply {
  reply: string;
  tools: { name: string; outcome: 'executed' | 'drafted' | 'failed' }[];
  drafts: ActionDraft[];
}

export const ORGANIZATION_HEADER = 'X-Mosaikit-Organization';

/** An error returned by the kernel as an RFC 9457 problem detail. */
export class KernelError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    detail: string,
  ) {
    super(detail);
    this.name = 'KernelError';
  }
}

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

/** Where the shell remembers the organization chosen in the tab, which is not a secret. */
const ORGANIZATION_KEY = 'mosaikit.organization';

/** Header with which the kernel recognises the shell, which shows its own sign-in. */
const CLIENT_HEADER = 'X-Mosaikit-Client';

type KeyValueStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

function tabStorage(): KeyValueStore | undefined {
  try {
    return globalThis.sessionStorage;
  } catch {
    return undefined;
  }
}

/**
 * Minimal client of the kernel API. A local account signs in once: the kernel keeps the session
 * in an HttpOnly cookie that scripts cannot read, so no password stays in the page and a reload
 * keeps the session. Tokens of a realm are kept in memory only.
 */
export class KernelClient {
  private authorization: string | undefined;
  private organization: string | undefined;
  private cookieSession = false;

  constructor(
    private readonly fetchImpl: Fetch = (input, init) => fetch(input, init),
    private readonly storage: KeyValueStore | undefined = tabStorage(),
  ) {}

  /** Opens a session of the shell for a local account, and returns the account. */
  async signIn(username: string, password: string): Promise<Account> {
    const response = await this.fetchImpl('/api/v1/accounts/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', [CLIENT_HEADER]: 'shell' },
      body: new URLSearchParams({ username, password }).toString(),
    });
    if (!response.ok) {
      throw new KernelError(
        response.status,
        'Unauthorized',
        response.status === 401
          ? 'The email or the password is not correct.'
          : 'Sign-in failed. Try again.',
      );
    }
    this.cookieSession = true;
    this.organization = undefined;
    this.storage?.removeItem(ORGANIZATION_KEY);
    return this.getJson<Account>('/api/v1/accounts/me');
  }

  /**
   * Finds the session of a local account again after a reload, in the organization chosen before;
   * `undefined` when there is none.
   */
  async resumeSession(): Promise<Account | undefined> {
    this.organization = this.storage?.getItem(ORGANIZATION_KEY) ?? undefined;
    const response = await this.request('/api/v1/accounts/session');
    if (response.status !== 200) {
      this.organization = undefined;
      return undefined;
    }
    this.cookieSession = true;
    return (await response.json()) as Account;
  }

  /** Uses an access token of the realm of an organization for the following calls, and checks it. */
  async signInWithToken(accessToken: string): Promise<Account> {
    this.authorization = `Bearer ${accessToken}`;
    try {
      return await this.getJson<Account>('/api/v1/accounts/me');
    } catch (error) {
      this.authorization = undefined;
      throw error;
    }
  }

  /** Replaces the access token after a refresh. */
  useToken(accessToken: string): void {
    this.authorization = `Bearer ${accessToken}`;
  }

  /** How the person with this email address signs in on this site. */
  signInOptions(email: string): Promise<SignInOptions> {
    return this.getJson<SignInOptions>(
      `/api/v1/identity/sign-in-options?email=${encodeURIComponent(email)}`,
    );
  }

  /**
   * Makes the following calls act on an organization of the person (MK-017), and returns the
   * account as seen in it.
   */
  async useOrganization(slug: string | undefined): Promise<Account> {
    this.organization = slug;
    if (slug) {
      this.storage?.setItem(ORGANIZATION_KEY, slug);
    } else {
      this.storage?.removeItem(ORGANIZATION_KEY);
    }
    return this.getJson<Account>('/api/v1/accounts/me');
  }

  /** Forgets the credentials, and ends the session of a local account on the kernel. */
  signOut(): Promise<void> {
    const ended = this.cookieSession
      ? this.fetchImpl('/api/v1/accounts/session', {
          method: 'DELETE',
          headers: { [CLIENT_HEADER]: 'shell' },
        }).then(
          () => undefined,
          () => undefined,
        )
      : Promise.resolve();
    this.authorization = undefined;
    this.organization = undefined;
    this.cookieSession = false;
    this.storage?.removeItem(ORGANIZATION_KEY);
    return ended;
  }

  get signedIn(): boolean {
    return this.authorization !== undefined || this.cookieSession;
  }

  systemInfo(): Promise<SystemInfo> {
    return this.getJson<SystemInfo>('/api/v1/system/info');
  }

  shellPlugins(): Promise<FrontendPlugin[]> {
    return this.getJson<FrontendPlugin[]>('/api/v1/shell/plugins');
  }

  /** The drafts of tools that the person can still confirm, oldest first (MK-015). */
  actionDrafts(): Promise<ActionDraft[]> {
    return this.getJson<ActionDraft[]>('/api/v1/ai/drafts');
  }

  /** Runs a draft. */
  confirmDraft(id: string): Promise<Invocation> {
    return this.sendJson<Invocation>(
      `/api/v1/ai/drafts/${encodeURIComponent(id)}/confirmation`,
      'POST',
    );
  }

  /** Discards a draft. */
  rejectDraft(id: string): Promise<ActionDraft> {
    return this.sendJson<ActionDraft>(`/api/v1/ai/drafts/${encodeURIComponent(id)}`, 'DELETE');
  }

  /** The catalogs of the marketplace, for platform administrators (MK-022). */
  marketplace(): Promise<Catalog> {
    return this.getJson<Catalog>('/api/v1/marketplace');
  }

  /** Installs a package of a catalog; it takes effect at the next start. */
  install(offer: Pick<Offer, 'source' | 'id' | 'version'>): Promise<Installation> {
    return this.sendJson<Installation>('/api/v1/marketplace/installations', 'POST', {
      source: offer.source,
      id: offer.id,
      version: offer.version,
    });
  }

  /** Installs a package file, for an installation without network. */
  upload(file: Blob): Promise<Installation> {
    return this.sendJson<Installation>('/api/v1/plugins/packages', 'POST', file, 'application/zip');
  }

  /** Whether the installation has an assistant, and its model (MK-024). */
  assistant(): Promise<{ enabled: boolean; model?: string }> {
    return this.getJson('/api/v1/ai/assistant');
  }

  /** Asks the assistant about the last message of a conversation. */
  ask(messages: readonly ChatMessage[]): Promise<AssistantReply> {
    return this.sendJson<AssistantReply>('/api/v1/ai/assistant/replies', 'POST', { messages });
  }

  /** Calls the API with the stored credentials. Used by plugins through their context. */
  request(path: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers);
    if (this.authorization) {
      headers.set('Authorization', this.authorization);
    }
    if (this.organization) {
      headers.set(ORGANIZATION_HEADER, this.organization);
    }
    headers.set('Accept', headers.get('Accept') ?? 'application/json');
    headers.set(CLIENT_HEADER, 'shell');
    return this.fetchImpl(path, { ...init, headers });
  }

  private getJson<T>(path: string): Promise<T> {
    return this.sendJson<T>(path, 'GET');
  }

  private async sendJson<T>(
    path: string,
    method: string,
    body?: unknown,
    type = 'application/json',
  ): Promise<T> {
    const init: RequestInit = { method };
    if (body !== undefined) {
      init.headers = { 'Content-Type': type };
      init.body = body instanceof Blob ? body : JSON.stringify(body);
    }
    const response = await this.request(path, init);
    if (!response.ok) {
      throw await toError(response);
    }
    return (await response.json()) as T;
  }
}

async function toError(response: Response): Promise<KernelError> {
  if (response.status === 401) {
    return new KernelError(401, 'Unauthorized', 'The email or the password is not correct.');
  }
  try {
    const problem = (await response.json()) as { title?: string; detail?: string };
    return new KernelError(
      response.status,
      problem.title ?? response.statusText,
      problem.detail ?? 'The request failed.',
    );
  } catch {
    return new KernelError(response.status, response.statusText, 'The request failed.');
  }
}
