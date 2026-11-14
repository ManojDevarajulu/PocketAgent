# Contributing to Pocket Agent

Thanks for helping. Pocket Agent is a small, bring-your-own-key Android app with no server of its own (the optional Computer Link only listens on your local network while the user has it switched on), so changes should stay simple and safe.

## Ground rules

- **Safety first.** Changes that touch `safety/`, the blocked-app list, risky-action confirmation or key storage need extra care and tests. Do not weaken them to make a task easier.
- **No secrets.** Never commit API keys, keystores, `local.properties` or screenshots that show a key or personal data.
- **No new dependencies** without a clear reason in the pull request.
- **Core packages stay pure.** `agent`, `bridge`, `device`, `llm`, `safety`, `playbook` and `setup` must not import Android framework classes. `ArchitectureTest` enforces this.
- Follow the existing code style and keep changes small and focused.

## Build and test

Requires JDK 17 and the Android SDK.

```bash
./gradlew testDebugUnitTest   # unit tests (JVM, no device needed)
./gradlew assembleDebug       # debug APK
```

CI runs the same checks on every pull request. Live-provider tests are skipped unless you set the variables described in [docs/TESTING.md](docs/TESTING.md); never put a key in a file.

## Pull requests

1. Open an issue first for anything larger than a small fix.
2. Branch from `main`, make one logical change per pull request, and add or update tests.
3. Make sure `./gradlew testDebugUnitTest` passes.
4. Describe what changed and how you checked it. For UI changes on a device, say which Android version you used.

## Contributing an app playbook

A playbook teaches the agent how to navigate one app. It is trusted guidance given to the model, so each one is reviewed like code.

1. Create `app/src/main/assets/playbooks/<package_name>.md` (the file name must match the package).
2. Start with this frontmatter (all three keys are required):

   ```markdown
   ---
   app: YouTube
   package: com.google.android.youtube
   tested_version: "19.0"
   ---
   ```

3. Add short, concrete navigation tips after it.

Rules, enforced by `PlaybookStoreTest` (the build fails if broken):

- At most **2048 bytes**.
- No control characters, and no mention of `screen_data` or `app_tips`.
- Not for a blocked app (banking, payments, password managers, system settings).
- Tips must work on the stated `tested_version`. Playbooks describe navigation only; they cannot change safety rules.

## Reporting bugs and security issues

Use the issue templates for bugs and ideas. For security problems, follow [SECURITY.md](SECURITY.md) and do not open a public issue.

By contributing you agree your work is released under the [MIT Licence](LICENSE) and that you will follow the [Code of Conduct](CODE_OF_CONDUCT.md).
