// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { LitElement, css, html, nothing } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { Catalog, KernelClient, Offer } from './api.js';

/**
 * The marketplace, for platform administrators (MK-022): the offers of the catalogs, with an
 * install button, and the upload of a package for an installation without network. Installed
 * packages take effect at the next start.
 */
@customElement('mk-admin-plugins')
export class MkAdminPlugins extends LitElement {
  static override styles = css`
    :host {
      display: block;
      max-width: 860px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 8px;
      border-top: 1px solid var(--mk-line);
    }
    .muted {
      color: var(--mk-muted);
      font-size: 13px;
    }
    .error {
      color: var(--mk-danger);
    }
    button {
      font: inherit;
      padding: 4px 10px;
      border-radius: var(--mk-radius);
      border: 1px solid var(--mk-accent);
      background: var(--mk-accent);
      color: var(--mk-accent-fg);
      cursor: pointer;
    }
  `;

  @property({ attribute: false }) client: KernelClient | undefined;

  @state() private catalog: Catalog | undefined;
  @state() private message: string | undefined;
  @state() private failed = false;

  override connectedCallback(): void {
    super.connectedCallback();
    void this.refresh();
  }

  override render(): unknown {
    const catalog = this.catalog;
    return html`
      <h1>Plugins</h1>
      <p role="status" aria-live="polite" class=${this.failed ? 'error' : 'muted'}>
        ${this.message ?? nothing}
      </p>
      <h2>Catalogs</h2>
      ${
        catalog === undefined
          ? html`<p class="muted">Loading…</p>`
          : catalog.sources.length === 0
            ? html`<p class="muted">No catalog is configured (mosaikit.marketplace.sources).</p>`
            : html`<ul>
                ${catalog.sources.map(
                  (source) =>
                    html`<li>
                      ${source.uri}:
                      ${
                        source.status === 'verified'
                          ? html`verified, key ${source.keyId}`
                          : html`<span class="error">refused, ${source.error}</span>`
                      }
                    </li>`,
                )}
              </ul>`
      }
      ${
        catalog && catalog.plugins.length > 0
          ? html`<table>
              <thead>
                <tr>
                  <th>Plugin</th>
                  <th>Version</th>
                  <th>Installed</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                ${catalog.plugins.map(
                  (offer) =>
                    html`<tr>
                      <td>${offer.name}<br /><span class="muted">${offer.id}</span></td>
                      <td>${offer.version}</td>
                      <td>${offer.installedVersion ?? '—'}</td>
                      <td>
                        ${
                          offer.state === 'available' || offer.state === 'update'
                            ? html`<button type="button" @click=${() => void this.install(offer)}>
                                ${offer.state === 'update' ? 'Update' : 'Install'}
                              </button>`
                            : html`<span class="muted">${offer.state}</span>`
                        }
                      </td>
                    </tr>`,
                )}
              </tbody>
            </table>`
          : nothing
      }
      <h2>Install a package file</h2>
      <p class="muted">
        For installations without network: the package must be signed by a trusted publisher.
      </p>
      <input
        type="file"
        accept=".zip,application/zip"
        aria-label="Plugin package"
        @change=${this.upload}
      />
    `;
  }

  private async refresh(): Promise<void> {
    if (!this.client) {
      return;
    }
    try {
      this.catalog = await this.client.marketplace();
    } catch (error) {
      this.report(error);
    }
  }

  private async install(offer: Offer): Promise<void> {
    if (!this.client) {
      return;
    }
    try {
      const installation = await this.client.install(offer);
      this.done(`${installation.id} ${installation.version}`, installation.problems);
    } catch (error) {
      this.report(error);
    }
  }

  private readonly upload = async (event: Event): Promise<void> => {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.client) {
      return;
    }
    try {
      const installation = await this.client.upload(file);
      this.done(`${installation.id} ${installation.version}`, installation.problems);
    } catch (error) {
      this.report(error);
    }
    input.value = '';
  };

  private done(what: string, problems: string[] | undefined): void {
    this.failed = false;
    const notes = problems && problems.length > 0 ? ` Note: ${problems.join('; ')}` : '';
    this.message = `${what} is installed: restart Mosaikit to use it.${notes}`;
  }

  private report(error: unknown): void {
    this.failed = true;
    this.message = error instanceof Error ? error.message : 'The request failed.';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'mk-admin-plugins': MkAdminPlugins;
  }
}
