# Security Policy

Pocket Agent controls a phone through an accessibility service and handles a user's AI provider key, so security reports are taken seriously.

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private reporting instead: go to the repository's **Security** tab and choose **Report a vulnerability**.

Include what you found, the steps to reproduce it, the Android version and app version, and the impact you expect. Never include a real API key.

You can expect an acknowledgement within about a week. This is a volunteer project, so fix times vary; reporters are credited in the release notes unless they prefer not to be.

## Scope

In scope:

- Bypassing the safety gate (blocked apps, risky-action confirmation).
- Leaking or weakly storing the API key.
- Prompt injection through screen content that makes the agent act against the user's instructions.
- Sending data to a host other than the provider the user configured.

Out of scope: problems that need a rooted or already compromised device, and the behaviour of third-party AI providers.

## Supported versions

Only the latest release and `main` receive fixes.
