## Summary
<!-- What does this change and why? -->

## Platforms
- [ ] Shared logic (`shared/`)
- [ ] Android (`androidApp/`)
- [ ] iPhone (`iosApp/`, `shared/src/iosMain`)
- [ ] Website / docs

## Checklist
- [ ] App logic lives in `shared/src/commonMain`; platform folders contain only platform code and UI
- [ ] User-facing changes applied to both the Compose and SwiftUI UIs
- [ ] `AGENTS.md` updated if behavior, architecture, or build setup changed
- [ ] Version bumped with `scripts/set-version.sh` + changelog text (AGENTS.md §9) if this is a release change
- [ ] Tests added or updated; CI green

## Testing
<!-- How was this verified? -->
