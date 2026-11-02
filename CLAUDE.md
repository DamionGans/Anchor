# Claude Code Instructions (`CLAUDE.md`)

All project architecture rules, audio/sync invariants, UI guidelines, release checklists, and standing user instructions are maintained in **[`AGENTS.md`](./AGENTS.md)**.

## Mandatory Rules for Claude
1. **Read [`AGENTS.md`](./AGENTS.md) first** before planning or making any changes to this repository.
2. **Self-Updating Requirement**: Whenever you change project behavior, architecture, build configuration, or fulfill new standing instructions from the user, you **must** update [`AGENTS.md`](./AGENTS.md) in the same turn/commit so the documentation stays 100% synchronized with the codebase.
3. **Version & Changelog Sync**: Every build/version update must synchronize `app/build.gradle.kts`, `CHANGELOG.md` (neutral tone), `ChangelogDialog.kt`, `docs/version.json`, `docs/index.html`, `AppUpdateManagerTest.kt`, and `iosApp/project.yml` as documented in Section 8 of [`AGENTS.md`](./AGENTS.md).
