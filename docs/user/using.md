# Using Mosaikit

Mosaikit is a web application made of *apps* provided by plugins. What you see depends on the
organization you belong to and on the plugins your administrator installed.

## Signing in

1. Open the address of your organization, for example `https://acme.mosaikit.example.org`, or
   the address of the installation.
2. Enter your email address. Mosaikit finds your organization:
   - if it signs in through its own identity provider (Keycloak), you are sent to its sign-in
     page, and come back signed in; the first time, you may be asked to change a temporary
     password;
   - otherwise you enter your Mosaikit password.
3. To sign out, use *Sign out* in the top bar; with an identity provider you are signed out there
   too.

If your organization allows self-registration, an account is created with an email address, a
display name and a password of at least 12 characters (`POST /api/v1/accounts/registrations`; the
sign-in page does not offer it yet).

## Several organizations

If you belong to several organizations, the top bar shows the organization you are working in;
choose another one there. Organizations that accept only their identity provider are shown but
cannot be chosen after signing in with a password: sign in to them through their identity
provider instead.

## The launcher and the apps

After signing in you see the launcher: one tile per app you can use. Choosing an app opens it in
the main area; its address (for example `/app/notes`) can be bookmarked and reloaded. Apps can
talk to each other: an action in one app can update another one.

If an app cannot be loaded, the home page says how many plugins failed; tell your administrator,
who sees the reason in the plugin list.

## The assistant

When the installation has an assistant, the home page shows it: ask in your language, for example
"which activities are still open?" or "close road A1 for works". It uses the apps with your
permissions in the current organization. It can read at once, but every change it proposes waits
under **Pending actions** until you confirm it. The kernel does not keep the conversation.

## Actions proposed by assistants

An assistant or an MCP client connected with your account can read data through the apps, but it
cannot change anything by itself. When it proposes a change, the home page shows it under
**Pending actions**, with its arguments: **Confirm** runs it, **Reject** discards it. A proposal
expires after 15 minutes. Only you see your proposals, and only in the organization where they
were made. Every proposal and decision is recorded in the audit log.

## Your data

Each app keeps its data in the organization you work for. Data of other organizations are never
visible.
