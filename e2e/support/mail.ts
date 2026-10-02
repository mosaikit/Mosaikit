// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
import { mails } from '../../tools/smtp.ts';
import { MAIL } from './env.js';

/** The mails received so far for an address, oldest first. */
export function mailsTo(address: string): ReturnType<typeof mails> {
  return mails(MAIL).filter((mail) => mail.to.includes(address));
}

/** The latest confirmation link sent to an address, waiting for it to arrive. */
export async function confirmationLink(address: string, after = 0): Promise<string> {
  for (let attempt = 0; attempt < 100; attempt++) {
    const received = mailsTo(address);
    const latest = received.length > after ? received.at(-1) : undefined;
    const link = latest ? /https?:\/\/\S+\?confirm=[\w-]+/.exec(latest.raw)?.[0] : undefined;
    if (link) {
      return link;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`No confirmation mail for ${address}`);
}
