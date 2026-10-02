// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { applyTheme, isThemeName } from '@mosaikit/ui';
import type { FrontendPlugin } from '@mosaikit/sdk';
import { LitElement, css, html, nothing, type PropertyValues } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import {
  KernelClient,
  KernelError,
  type Account,
  type ActionDraft,
  type Registration,
  type RegistrationOptions,
  type SystemInfo,
} from './api.js';
import { FederatedSignIn, type Federation, type Tokens } from './federation.js';
import { entryForPath, launcherEntries, type LauncherEntry } from './navigation.js';
import './mk-admin-plugins.js';
import './mk-admin-settings.js';
import './mk-assistant.js';
import './mk-plugin-frame.js';
import './mk-sign-in.js';
import type { PasswordSignIn } from './mk-sign-in.js';
import { PluginLoader, type LoadResult } from './plugin-loader.js';

/** How often the shell looks for actions proposed by assistants. */
const DRAFT_REFRESH_MS = 20_000;
/** How often the shell asks whether the watched plugins changed (development mode). */
const PLUGIN_WATCH_MS = 1_000;

/** The pages of platform administrators: plugins (MK-022) and settings (MK-048). */
const ADMIN_PAGES = [
  { path: '/admin/plugins', title: 'Plugins', element: 'mk-admin-plugins' },
  { path: '/admin/settings', title: 'Settings', element: 'mk-admin-settings' },
] as const;

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
    main > .error,
    .notice {
      margin: 0 0 16px;
      padding: 10px 14px;
      border: 1px solid var(--mk-line);
      border-radius: var(--mk-radius);
      background: var(--mk-surface);
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
  private watchTimer: ReturnType<typeof setInterval> | undefined;
  @state() private busy = false;
  /** Email entered at the first step; the password step follows when there is no realm. */
  @state() private email: string | undefined;
  /** The page of the sign-in: signing in, creating an account, or waiting for the confirmation. */
  @state() private signInMode: 'sign-in' | 'register' | 'sent' = 'sign-in';
  @state() private registration: RegistrationOptions | undefined;
  @state() private sentTo: string | undefined;
  @state() private notice: string | undefined;

  private readonly onPopState = (): void => {
    this.path = location.pathname;
  };

  override connectedCallback(): void {
    super.connectedCallback();
    this.addEventListener('mk-plugins-changed', () => void this.reloadPlugins());
    window.addEventListener('popstate', this.onPopState);
    this.client.systemInfo().then(
      (info) => {
        this.info = info;
        applyTheme(isThemeName(info.theme) ? info.theme : 'mosaikit');
      },
      () => {
        this.error = 'The kernel is not reachable.';
      },
    );
    this.client.registrationOptions().then(
      (options) => {
        this.registration = options;
      },
      () => undefined,
    );
    void this.confirmFromLink()
      .then(() => this.completeFederatedSignIn())
      .then(() => this.resumeSession());
  }

  /** Confirms the address when the page is the link of a confirmation mail (MK-048). */
  private async confirmFromLink(): Promise<void> {
    const url = new URL(location.href);
    const token = url.searchParams.get('confirm');
    if (!token) {
      return;
    }
    url.searchParams.delete('confirm');
    history.replaceState(null, '', url.pathname + url.search);
    this.email = '';
    try {
      await this.client.confirmEmail(token);
      this.notice = 'Your email address is confirmed. Sign in to start.';
    } catch (error) {
      this.error =
        error instanceof KernelError ? error.message : 'The address could not be confirmed.';
    }
  }

  /** After a reload, enters again with the session of a local account, when there is one. */
  private async resumeSession(): Promise<void> {
    if (this.account || this.session) {
      return;
    }
    try {
      const account = await this.client.resumeSession();
      if (account) {
        await this.enter(account);
      }
    } catch {
      // No session: the sign-in form stays.
    }
  }

  override disconnectedCallback(): void {
    clearInterval(this.draftTimer);
    clearInterval(this.watchTimer);
    window.removeEventListener('popstate', this.onPopState);
    super.disconnectedCallback();
  }

  protected override updated(changed: PropertyValues): void {
    if (changed.has('path') || changed.has('entries')) {
      this.renderApp();
    }
  }

  override render(): unknown {
    if (!this.account) {
      return this.renderSignIn();
    }
    return html`
      <header>
        <span class="brand">${this.info?.name ?? 'Mosaikit'}</span>
        <span class="muted">${this.info ? `v${this.info.version}` : nothing}</span>
        <span class="spacer"></span>
        ${this.renderOrganizationSelector()}<span>${this.account.displayName}</span>
        <button class="secondary" @click=${this.signOut}>Sign out</button>
      </header>
      ${this.renderWorkspace()}
    `;
  }

  /** The sign-in page (mk-sign-in), which tells the shell what the person asked for. */
  private renderSignIn(): unknown {
    return html`<mk-sign-in
      .product=${this.info?.name ?? 'Mosaikit'}
      .version=${this.info?.version}
      .email=${this.email}
      .busy=${this.busy}
      .error=${this.error}
      .rememberDays=${this.info?.rememberDays ?? 30}
      @mk-continue=${this.continueWithEmail}
      @mk-use-password=${this.usePassword}
      @mk-back=${this.restartSignIn}
      @mk-sign-in=${this.signIn}
      .mode=${this.signInMode}
      .registration=${this.registration}
      .sentTo=${this.sentTo}
      .notice=${this.notice}
      @mk-show-register=${this.showRegister}
      @mk-register=${this.register}
      @mk-resend=${this.resend}
    ></mk-sign-in>`;
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
        this.error = failureOf(draft.title, invocation.result);
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
    return ADMIN_PAGES.map(
      (page) =>
        html`<a
          href=${page.path}
          aria-current=${this.path === page.path ? 'page' : 'false'}
          @click=${this.navigate}
          >${page.title}</a
        >`,
    );
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
          ${this.renderOrganizationNotice()}
          ${
            entryForPath(this.entries, this.path) || this.showsAdmin()
              ? nothing
              : html`${this.renderAssistant()}${this.renderPendingActions()}
                  <h1>Welcome, ${this.account?.displayName}</h1>
                  <p class="muted">${this.entries.length} apps available. ${failures}</p>`
          }
          <div id="app-host"></div>
        </main>
      </div>
    `;
  }

  /** First step: sends the person to the realm of the organization, or asks for a password. */
  private readonly continueWithEmail = async (
    event: CustomEvent<{ email: string }>,
  ): Promise<void> => {
    const email = event.detail.email;
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
    this.notice = undefined;
    this.email = undefined;
    this.signInMode = 'sign-in';
  };

  private readonly showRegister = (): void => {
    this.error = undefined;
    this.notice = undefined;
    this.signInMode = 'register';
  };

  /** Creates an account; with a confirmation, waits for the person to open the link. */
  private readonly register = async (event: CustomEvent<Registration>): Promise<void> => {
    const registration = event.detail;
    this.busy = true;
    this.error = undefined;
    try {
      const sent = await this.client.register(registration);
      if (sent) {
        this.sentTo = registration.email;
        this.signInMode = 'sent';
      } else {
        const account = await this.client.signIn(registration.email, registration.password);
        this.signInMode = 'sign-in';
        await this.enter(account);
      }
    } catch (error) {
      this.error = error instanceof KernelError ? error.message : 'The account was not created.';
    } finally {
      this.busy = false;
    }
  };

  private readonly resend = async (): Promise<void> => {
    if (!this.sentTo) {
      return;
    }
    this.busy = true;
    this.error = undefined;
    try {
      await this.client.resendConfirmation(this.sentTo);
      this.notice = 'We sent a new link. The previous ones do not work any more.';
    } catch (error) {
      this.error = error instanceof KernelError ? error.message : 'The link was not sent.';
    } finally {
      this.busy = false;
    }
  };

  private readonly signIn = async (event: CustomEvent<PasswordSignIn>): Promise<void> => {
    const { username, password, remember } = event.detail;
    this.busy = true;
    this.error = undefined;
    try {
      const account = await this.client.signIn(username, password);
      if (remember) {
        // Not being remembered is no reason to stay out: the session of the browser goes on.
        await this.client.remember().catch(() => undefined);
      }
      await this.enter(account);
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
    this.notice = undefined;
    await this.loadDrafts();
    await this.loadAssistant();
    clearInterval(this.draftTimer);
    this.draftTimer = setInterval(() => void this.loadDrafts(), DRAFT_REFRESH_MS);
    await this.watchPlugins();
  }

  /**
   * When the kernel watches the plugins directory, follows its revision: a changed module cannot
   * replace the custom elements it defined, so the shell reloads the page, which keeps the session
   * of a local account. With the tokens of a realm, held in memory only, it loads the new plugins.
   */
  private async watchPlugins(): Promise<void> {
    clearInterval(this.watchTimer);
    let revision = await this.client.pluginRevision().catch(() => undefined);
    if (revision === undefined) {
      return;
    }
    this.watchTimer = setInterval(() => {
      void this.client.pluginRevision().then(
        (current) => {
          if (current === undefined || current === revision) {
            return;
          }
          revision = current;
          if (this.session) {
            void this.reloadPlugins();
          } else {
            location.reload();
          }
        },
        () => undefined,
      );
    }, PLUGIN_WATCH_MS);
  }

  /**
   * Loads the plugins that became active without a restart (ADR-0031), such as one just installed
   * from the Plugins page; those already loaded stay as they are.
   */
  private async reloadPlugins(): Promise<void> {
    if (!this.account || !this.loader) {
      return;
    }
    const plugins = await this.client.shellPlugins();
    const known = new Set(this.plugins.map((plugin) => plugin.id));
    const added = plugins.filter((plugin) => !known.has(plugin.id));
    const results = await this.loader.loadAll(added, this.account, plugins);
    this.loadResults = [...this.loadResults, ...results];
    this.plugins = plugins;
    this.entries = launcherEntries(plugins);
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
    clearInterval(this.watchTimer);
    this.drafts = [];
    this.session = undefined;
    void this.client.signOut();
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
    this.error = undefined;
  };

  /**
   * What went wrong in the workspace, such as an action that failed once confirmed, and a notice for
   * people who are in no organization: apps act on the data of one, so they would show nothing.
   */
  private renderOrganizationNotice(): unknown {
    const error = this.error ? html`<p class="error" role="alert">${this.error}</p>` : nothing;
    // The Plugins page does not act on an organization.
    if (!this.account || this.account.organizationId || this.showsAdmin()) {
      return error;
    }
    return html`${error}
      <p class="notice">
        You are not working in an organization: the apps act on the data of an organization, so they
        have nothing to show.
        ${
          this.isPlatformAdmin()
            ? 'Add yourself to an organization to use them.'
            : 'Ask an administrator to add you to one.'
        }
      </p>`;
  }

  private isPlatformAdmin(): boolean {
    return this.account?.roles.includes('platform-admin') === true;
  }

  private showsAdmin(): boolean {
    return this.adminPage() !== undefined;
  }

  private adminPage(): (typeof ADMIN_PAGES)[number] | undefined {
    return this.isPlatformAdmin() ? ADMIN_PAGES.find((page) => page.path === this.path) : undefined;
  }

  /**
   * Mounts the current app, or the Plugins page, in an element of its own: Lit renders the rest of
   * the work area, and replacing children that Lit placed breaks its next render (back to Home).
   */
  private renderApp(): void {
    const area = this.renderRoot.querySelector('#app-host');
    if (area && !this.showsAdmin() && !entryForPath(this.entries, this.path)) {
      area.replaceChildren();
      return;
    }
    const page = this.adminPage();
    if (area && page) {
      if (area.firstElementChild?.localName !== page.element) {
        const admin = document.createElement(page.element);
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

/**
 * The message for a confirmed action that the plugin refused, with the detail of its problem
 * (RFC 9457) when there is one, or `undefined` when it succeeded.
 */
export function failureOf(
  title: string,
  result: { status: number; body?: unknown } | undefined,
): string | undefined {
  const status = result?.status ?? 0;
  if (status < 400) {
    return undefined;
  }
  const body = result?.body;
  const detail =
    typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string'
      ? body.detail
      : `HTTP ${String(status)}`;
  return `${title} failed: ${detail}. Nothing was changed.`;
}

/** Where the realm sends the person back: the root of the site, registered in its client. */
function redirectUri(): string {
  return `${location.origin}/`;
}

declare global {
  interface HTMLElementTagNameMap {
    'mk-shell': MkShell;
  }
}
