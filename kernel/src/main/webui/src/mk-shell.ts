// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import type { FrontendPlugin } from '@mosaikit/sdk';
import { LitElement, css, html, nothing, type PropertyValues } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import {
  KernelClient,
  KernelError,
  type Account,
  type ActionDraft,
  type SystemInfo,
} from './api.js';
import { FederatedSignIn, type Federation, type Tokens } from './federation.js';
import { entryForPath, launcherEntries, type LauncherEntry } from './navigation.js';
import './mk-admin-plugins.js';
import './mk-assistant.js';
import './mk-plugin-frame.js';
import { PluginLoader, type LoadResult } from './plugin-loader.js';

/** How often the shell looks for actions proposed by assistants. */
const DRAFT_REFRESH_MS = 20_000;

/** Route of the administration of plugins, for platform administrators (MK-022). */
const ADMIN_PLUGINS = '/admin/plugins';

/**
 * Root element of the shell: sign-in, launcher navigation and the area where plugin apps render.
 */
@customElement('mk-shell')
export class MkShell extends LitElement {
  static override readonly styles = css`
    :host {
      display: grid;
      grid-template-rows: 52px 1fr;
      min-height: 100vh;
    }
    header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 0 16px;
      background: var(--mk-surface);
      border-bottom: 1px solid var(--mk-line);
    }
    .brand {
      font-weight: 700;
      font-size: 17px;
    }
    .spacer {
      flex: 1;
    }
    .body {
      display: grid;
      grid-template-columns: 220px 1fr;
      min-height: 0;
    }
    nav {
      background: var(--mk-surface);
      border-right: 1px solid var(--mk-line);
      padding: 12px 8px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    nav a {
      color: var(--mk-fg);
      text-decoration: none;
      padding: 7px 10px;
      border-radius: var(--mk-radius);
    }
    nav a[aria-current='page'] {
      background: var(--mk-accent-soft);
      font-weight: 600;
    }
    main {
      padding: 20px;
      min-width: 0;
    }
    form {
      display: grid;
      gap: 12px;
      width: min(360px, 100% - 32px);
      margin: 12vh auto 0;
      padding: 24px;
      background: var(--mk-surface);
      border: 1px solid var(--mk-line);
      border-radius: var(--mk-radius);
    }
    label {
      display: grid;
      gap: 4px;
      font-size: 13px;
      color: var(--mk-muted);
    }
    input {
      font: inherit;
      padding: 8px 10px;
      border: 1px solid var(--mk-line);
      border-radius: var(--mk-radius);
      background: var(--mk-bg);
      color: var(--mk-fg);
    }
    button {
      font: inherit;
      font-weight: 600;
      padding: 8px 12px;
      border-radius: var(--mk-radius);
      border: 1px solid var(--mk-accent);
      background: var(--mk-accent);
      color: var(--mk-accent-fg);
      cursor: pointer;
    }
    button.secondary {
      background: transparent;
      color: var(--mk-fg);
      border-color: var(--mk-line);
    }
    :focus-visible {
      outline: 2px solid var(--mk-accent);
      outline-offset: 2px;
    }
    .error {
      color: var(--mk-danger);
      margin: 0;
    }
    .muted {
      color: var(--mk-muted);
    }
    .organization {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .pending {
      border: 1px solid var(--mk-line);
      border-radius: var(--mk-radius);
      padding: 12px 16px;
      margin-bottom: 16px;
    }
    .pending ul {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 12px;
    }
    .pending pre {
      margin: 4px 0;
      white-space: pre-wrap;
      font-size: 0.85em;
    }
    select {
      font: inherit;
      padding: 4px 8px;
      border: 1px solid var(--mk-line);
      border-radius: var(--mk-radius);
      background: var(--mk-bg);
      color: var(--mk-fg);
    }
  `;

  private readonly client = new KernelClient();
  private readonly federated = new FederatedSignIn();
  private session: { federation: Federation; tokens: Tokens } | undefined;
  private plugins: FrontendPlugin[] = [];
  private loader: PluginLoader | undefined;
  private refreshTimer: ReturnType<typeof setTimeout> | undefined;

  @state() private info: SystemInfo | undefined;
  @state() private account: Account | undefined;
  @state() private entries: LauncherEntry[] = [];
  @state() private loadResults: LoadResult[] = [];
  @state() private path = location.pathname;
  @state() private error: string | undefined;
  @state() private drafts: ActionDraft[] = [];
  @state() private assistantModel: string | undefined;
  private draftTimer: ReturnType<typeof setInterval> | undefined;
  @state() private busy = false;
  /** Email entered at the first step; the password step follows when there is no realm. */
  @state() private email: string | undefined;

  private readonly onPopState = (): void => {
    this.path = location.pathname;
  };

  override connectedCallback(): void {
    super.connectedCallback();
    window.addEventListener('popstate', this.onPopState);
    this.client.systemInfo().then(
      (info) => {
        this.info = info;
      },
      () => {
        this.error = 'The kernel is not reachable.';
      },
    );
    void this.completeFederatedSignIn();
  }

  override disconnectedCallback(): void {
    clearInterval(this.draftTimer);
    window.removeEventListener('popstate', this.onPopState);
    super.disconnectedCallback();
  }

  protected override updated(changed: PropertyValues): void {
    if (changed.has('path') || changed.has('entries')) {
      this.renderApp();
    }
  }

  override render(): unknown {
    return html`
      <header>
        <span class="brand">${this.info?.name ?? 'Mosaikit'}</span>
        <span class="muted">${this.info ? `v${this.info.version}` : nothing}</span>
        <span class="spacer"></span>
        ${
          this.account
            ? html`${this.renderOrganizationSelector()}<span>${this.account.displayName}</span>
                <button class="secondary" @click=${this.signOut}>Sign out</button>`
            : nothing
        }
      </header>
      ${this.account ? this.renderWorkspace() : this.renderSignIn()}
    `;
  }

  /**
   * Sign-in in two steps: the email address chooses the organization and its realm (MK-012); a
   * person without a realm, or who asks for it, then enters a local password.
   */
  private renderSignIn(): unknown {
    const error = this.error ? html`<p class="error" role="alert">${this.error}</p>` : nothing;
    if (this.email === undefined) {
      return html`
        <form @submit=${this.continueWithEmail} aria-labelledby="sign-in-title">
          <h1 id="sign-in-title">Sign in</h1>
          <label
            >Email or username
            <input name="username" inputmode="email" autocomplete="username" required
          /></label>
          ${error}
          <button type="submit" ?disabled=${this.busy}>Continue</button>
          <button type="button" class="secondary" @click=${this.usePassword}>
            Sign in with a password
          </button>
        </form>
      `;
    }
    return html`
      <form @submit=${this.signIn} aria-labelledby="sign-in-title">
        <h1 id="sign-in-title">Sign in</h1>
        <label>
          Email or username
          <input
            name="username"
            inputmode="email"
            autocomplete="username"
            .value=${this.email}
            required
          />
        </label>
        <label>
          Password
          <input name="password" type="password" autocomplete="current-password" required />
        </label>
        ${error}
        <button type="submit" ?disabled=${this.busy}>Sign in</button>
        <button type="button" class="secondary" @click=${this.restartSignIn}>Back</button>
      </form>
    `;
  }

  /**
   * Chooses the organization the requests act on, for a person who belongs to several (MK-017).
   * With a password, organizations that accept only their identity provider are not available.
   */
  private renderOrganizationSelector(): unknown {
    const memberships = this.account?.memberships ?? [];
    if (memberships.length < 2) {
      return nothing;
    }
    const federated = this.session !== undefined;
    return html`<label class="organization"
      >Organization
      <select @change=${this.chooseOrganization} ?disabled=${federated}>
        ${memberships.map(
          (membership) =>
            html`<option
              value=${membership.slug}
              ?selected=${membership.slug === this.account?.organization}
              ?disabled=${!federated && membership.signIn === 'realm'}
            >
              ${membership.name}${!federated && membership.signIn === 'realm' ? ' (sign in through its identity provider)' : ''}
            </option>`,
        )}
      </select></label
    >`;
  }

  /**
   * The actions that assistants proposed for the person (MK-015): nothing happens until the
   * person confirms one.
   */
  private renderPendingActions(): unknown {
    if (this.drafts.length === 0) {
      return nothing;
    }
    return html`<section class="pending" aria-label="Pending actions">
      <h2>Pending actions</h2>
      <p class="muted">An assistant proposed these actions. They run only if you confirm them.</p>
      <ul>
        ${this.drafts.map(
          (draft) =>
            html`<li>
              <strong>${draft.title}</strong>
              ${draft.description ? html`<span class="muted">${draft.description}</span>` : nothing}
              <pre>${JSON.stringify(draft.input, null, 2)}</pre>
              <span class="muted"
                >Expires at ${new Date(draft.expiresAt).toLocaleTimeString()}</span
              >
              <div>
                <button type="button" @click=${() => void this.decide(draft, true)}>Confirm</button>
                <button
                  type="button"
                  class="secondary"
                  @click=${() => void this.decide(draft, false)}
                >
                  Reject
                </button>
              </div>
            </li>`,
        )}
      </ul>
    </section>`;
  }

  private async decide(draft: ActionDraft, confirm: boolean): Promise<void> {
    try {
      if (confirm) {
        const invocation = await this.client.confirmDraft(draft.id);
        const status = invocation.result?.status ?? 0;
        this.error = status >= 400 ? `${draft.title} failed (HTTP ${String(status)}).` : undefined;
      } else {
        await this.client.rejectDraft(draft.id);
      }
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'The action cannot be decided.';
    }
    await this.loadDrafts();
  }

  /** The assistant panel, when the installation has one and the person is in an organization. */
  private renderAssistant(): unknown {
    if (this.assistantModel === undefined || !this.account?.organizationId) {
      return nothing;
    }
    return html`<mk-assistant
      .client=${this.client}
      model=${this.assistantModel}
      @mk-drafts-changed=${() => void this.loadDrafts()}
    ></mk-assistant>`;
  }

  private async loadAssistant(): Promise<void> {
    try {
      const assistant = await this.client.assistant();
      this.assistantModel = assistant.enabled ? (assistant.model ?? '') : undefined;
    } catch {
      this.assistantModel = undefined;
    }
  }

  private async loadDrafts(): Promise<void> {
    if (!this.account?.organizationId) {
      this.drafts = [];
      return;
    }
    try {
      this.drafts = await this.client.actionDrafts();
    } catch {
      this.drafts = [];
    }
  }

  private readonly chooseOrganization = async (event: Event): Promise<void> => {
    const slug = (event.target as HTMLSelectElement).value;
    try {
      await this.enter(await this.client.useOrganization(slug));
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'The organization cannot be used.';
    }
  };

  private renderAdminLink(): unknown {
    if (!this.isPlatformAdmin()) {
      return nothing;
    }
    return html`<a
      href=${ADMIN_PLUGINS}
      aria-current=${this.path === ADMIN_PLUGINS ? 'page' : 'false'}
      @click=${this.navigate}
      >Plugins</a
    >`;
  }

  private renderWorkspace(): unknown {
    const failed = this.loadResults.filter((result) => !result.loaded);
    const failures =
      failed.length > 0 ? `${String(failed.length)} plugins could not be loaded.` : nothing;
    return html`
      <div class="body">
        <nav aria-label="Apps">
          <a href="/" aria-current=${this.path === '/' ? 'page' : 'false'} @click=${this.navigate}
            >Home</a
          >
          ${this.entries.map(
            (entry) =>
              html`<a
                href=${entry.route}
                aria-current=${this.path === entry.route ? 'page' : 'false'}
                @click=${this.navigate}
                >${entry.title}</a
              >`,
          )}
          ${this.renderAdminLink()}
        </nav>
        <main id="app-area">
          ${
            entryForPath(this.entries, this.path) || this.showsAdmin()
              ? nothing
              : html`${this.renderAssistant()}${this.renderPendingActions()}
                  <h1>Welcome, ${this.account?.displayName}</h1>
                  <p class="muted">${this.entries.length} apps available. ${failures}</p>`
          }
        </main>
      </div>
    `;
  }

  /** First step: sends the person to the realm of the organization, or asks for a password. */
  private readonly continueWithEmail = async (event: SubmitEvent): Promise<void> => {
    event.preventDefault();
    const email = field(new FormData(event.target as HTMLFormElement), 'username');
    this.busy = true;
    this.error = undefined;
    try {
      const options = await this.client.signInOptions(email);
      if (options.federation) {
        location.assign(await this.federated.begin(options.federation, email, redirectUri()));
        return;
      }
      this.email = email;
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Sign-in failed. Try again.';
    } finally {
      this.busy = false;
    }
  };

  private readonly usePassword = (): void => {
    this.error = undefined;
    this.email = '';
  };

  private readonly restartSignIn = (): void => {
    this.error = undefined;
    this.email = undefined;
  };

  private readonly signIn = async (event: SubmitEvent): Promise<void> => {
    event.preventDefault();
    const data = new FormData(event.target as HTMLFormElement);
    this.busy = true;
    this.error = undefined;
    try {
      await this.enter(await this.client.signIn(field(data, 'username'), field(data, 'password')));
    } catch (error) {
      this.error = error instanceof KernelError ? error.message : 'Sign-in failed. Try again.';
    } finally {
      this.busy = false;
    }
  };

  /** Completes a sign-in when the page is the answer of the realm of an organization. */
  private async completeFederatedSignIn(): Promise<void> {
    const url = new URL(location.href);
    try {
      const result = await this.federated.complete(url);
      if (!result) {
        return;
      }
      history.replaceState(null, '', url.pathname);
      this.busy = true;
      this.session = result;
      await this.enter(await this.client.signInWithToken(result.tokens.accessToken));
      this.scheduleRefresh();
    } catch (error) {
      history.replaceState(null, '', url.pathname);
      this.session = undefined;
      this.error = error instanceof Error ? error.message : 'Sign-in failed. Try again.';
    } finally {
      this.busy = false;
    }
  }

  /** Refreshes the access token before it expires; signs out when the realm refuses. */
  private scheduleRefresh(): void {
    clearTimeout(this.refreshTimer);
    const session = this.session;
    if (!session?.tokens.refreshToken) {
      return;
    }
    const refreshToken = session.tokens.refreshToken;
    this.refreshTimer = setTimeout(
      () => {
        this.federated.refresh(session.federation, refreshToken).then(
          (tokens) => {
            this.session = { federation: session.federation, tokens };
            this.client.useToken(tokens.accessToken);
            this.scheduleRefresh();
          },
          () => {
            this.clearSession();
            this.error = 'The session ended. Sign in again.';
          },
        );
      },
      Math.max(10, session.tokens.expiresIn * 0.8) * 1000,
    );
  }

  private async enter(account: Account): Promise<void> {
    const plugins = await this.client.shellPlugins();
    const loader = new PluginLoader((path, init) => this.client.request(path, init));
    this.loadResults = await loader.loadAll(plugins, account);
    this.plugins = plugins;
    this.loader = loader;
    this.entries = launcherEntries(plugins);
    this.account = account;
    this.email = undefined;
    await this.loadDrafts();
    await this.loadAssistant();
    clearInterval(this.draftTimer);
    this.draftTimer = setInterval(() => void this.loadDrafts(), DRAFT_REFRESH_MS);
  }

  private readonly signOut = async (): Promise<void> => {
    const session = this.session;
    this.clearSession();
    if (session) {
      const url = await this.federated
        .signOutUrl(session.federation, session.tokens.idToken, redirectUri())
        .catch(() => undefined);
      if (url) {
        location.assign(url);
      }
    }
  };

  private clearSession(): void {
    clearTimeout(this.refreshTimer);
    clearInterval(this.draftTimer);
    this.drafts = [];
    this.session = undefined;
    this.client.signOut();
    this.account = undefined;
    this.entries = [];
    this.loadResults = [];
    this.plugins = [];
    this.loader = undefined;
  }

  private readonly navigate = (event: MouseEvent): void => {
    const link = event.currentTarget as HTMLAnchorElement;
    event.preventDefault();
    history.pushState(null, '', link.pathname);
    this.path = link.pathname;
  };

  /** Renders the custom element of the current app, created by its plugin. */
  private isPlatformAdmin(): boolean {
    return this.account?.roles.includes('platform-admin') === true;
  }

  private showsAdmin(): boolean {
    return this.path === ADMIN_PLUGINS && this.isPlatformAdmin();
  }

  private renderApp(): void {
    const area = this.renderRoot.querySelector('#app-area');
    if (area && this.showsAdmin()) {
      if (area.firstElementChild?.localName !== 'mk-admin-plugins') {
        const admin = document.createElement('mk-admin-plugins');
        admin.client = this.client;
        area.replaceChildren(admin);
      }
      return;
    }
    const entry = entryForPath(this.entries, this.path);
    if (!area || !entry) {
      return;
    }
    const plugin = this.plugins.find((candidate) => candidate.id === entry.pluginId);
    if (plugin?.isolation === 'iframe') {
      const current = area.firstElementChild;
      if (current instanceof HTMLElement && current.dataset.app === `${plugin.id}/${entry.id}`) {
        return;
      }
      const frame = document.createElement('mk-plugin-frame');
      frame.dataset.app = `${plugin.id}/${entry.id}`;
      frame.plugin = plugin;
      frame.element = entry.element;
      frame.user = this.account;
      frame.events = this.loader?.events;
      frame.request = (path, init) => this.client.request(path, init);
      area.replaceChildren(frame);
      return;
    }
    if (area.firstElementChild?.localName !== entry.element) {
      area.replaceChildren(document.createElement(entry.element));
    }
  }
}

/** Where the realm sends the person back: the root of the site, registered in its client. */
function redirectUri(): string {
  return `${location.origin}/`;
}

/** Reads a text field of a submitted form. */
function field(data: FormData, name: string): string {
  const value = data.get(name);
  return typeof value === 'string' ? value : '';
}

declare global {
  interface HTMLElementTagNameMap {
    'mk-shell': MkShell;
  }
}
