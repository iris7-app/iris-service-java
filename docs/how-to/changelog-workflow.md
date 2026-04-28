# Changelog workflow — hand-rolled release automation

Iris's release automation is a pair of shell scripts under
[`bin/ship/`](../../bin/ship/) that run locally after a stable-v*
tag is cut. This doc walks the 5-step workflow.

**Why not release-please** : `googleapis/release-please` was
configured but removed on 2026-04-23 — the tool is GitHub-API-only
(401 Bad Credentials hitting `api.github.com/graphql` with a GitLab
PAT, even when you pass `--repo-url`). We replaced it with the
shell scripts below rather than migrating to `semantic-release` +
`@semantic-release/gitlab` because the shell workflow is :

- 2 scripts, ~200 LOC total (readable in one pass)
- zero `node_modules/` added to the Java project's supply chain
- zero CI burn (no tag-triggered pipeline, no shared-runner quota)
- identical output (CHANGELOG entry + GitLab Release object)

The trade-off is that releases are **manual, not automatic** —
`stable-v*` tags don't open release PRs on their own. That's fine
for a ~3-6 tag/day cadence with a single maintainer ; revisit when
the team crosses 2+ contributors.

## Prerequisites

- `glab` CLI authenticated (`glab auth status` returns green)
- Annotated `stable-v*` tags already in use (we're not changing the
  tag convention — see [CLAUDE.md → "Tag every green stability
  checkpoint, never tag on red"](../../CLAUDE.md))
- Conventional Commits since the last `stable-v*` tag (feat / fix /
  perf / refactor / docs / test / chore / ci / build / style) —
  enforced by [`lefthook.yml`](../../.config/lefthook.yml)
  `commit-msg` hook

## Workflow — 5 steps

### 1. Regenerate `CHANGELOG.md` from commits

```bash
bin/ship/changelog.sh                  # default : since last stable-v* tag
bin/ship/changelog.sh --since v1.0.30  # custom range
bin/ship/changelog.sh --include-chore  # keep chore/ci/build/style sections
bin/ship/changelog.sh --dry-run        # print to stdout without writing
```

The script classifies each commit subject by Conventional-Commit
type and emits a grouped Markdown section :

| Type | Section | Kept by default |
|---|---|---|
| `feat!` or `BREAKING CHANGE` | 💥 Breaking | ✅ |
| `feat:` | ✨ Features | ✅ |
| `fix:` | 🐛 Bug fixes | ✅ |
| `perf:` | ⚡ Performance | ✅ |
| `refactor:` | ♻️  Refactoring | ✅ |
| `docs:` | 📚 Documentation | ✅ |
| `test:` | 🧪 Tests | ✅ |
| `chore:` / `ci:` / `build:` / `style:` | (various) | `--include-chore` only |

Each entry links to the commit SHA — reviewers can click through
without leaving the CHANGELOG.

### 2. Review + commit the CHANGELOG bump

```bash
less CHANGELOG.md                                  # sanity-check the new entry
git add CHANGELOG.md
git commit -m "chore(changelog): bump for vX.Y.Z"
```

Pick `vX.Y.Z` by the usual semver rules :

- any `feat!` / `BREAKING CHANGE` → major bump
- any `feat:` → minor bump
- only `fix:` / `perf:` / lower → patch bump
- only `chore:` / `ci:` / `docs:` → no release (skip this workflow)

### 3. Tag the stable checkpoint

```bash
git tag -a stable-vX.Y.Z -m "Stability checkpoint — <one-line summary>"
git push origin stable-vX.Y.Z
```

**Never tag on red** — wait for the post-merge main pipeline green
([CLAUDE.md → "Tag every green stability checkpoint"](../../CLAUDE.md)
for the operational pattern : arm a Monitor on the post-merge main
pipeline, wait for the success event, THEN tag).

### 4. Promote the tag to a GitLab Release

```bash
bin/ship/gitlab-release.sh stable-vX.Y.Z
```

The script reads the annotated tag message (or `--notes "..."` if
you want a custom description) and creates a Release object at
<https://gitlab.com/iris-7/iris-service/-/releases> via
`glab release create`. Takes ~1 s.

### 5. Announce (optional)

If the release crosses a meaningful boundary (v1.1.0 feature
wave, v2.0.0 API break, …), drop a line in the project README's
"Recent releases" section or push an MR description for the next
batch of work referencing the new tag.

## How it interacts with tag conventions

Iris uses **one tag family** : `stable-vX.Y.Z`. There's no
parallel `vX.Y.Z` set anymore — release-please's automated `vX.Y.Z`
tags are gone. Every release = one tag + one CHANGELOG entry + one
Release object.

## Troubleshooting

**"changelog.sh : No stable-v* tag found"**

The script needs at least one prior `stable-v*` tag to anchor the
range. Pass `--since <ref>` explicitly for the first-ever run.

**"changelog.sh emits no sections"**

No Conventional Commits between `<since>` and `HEAD`. Check
`git log <since>..HEAD --oneline` — if everything is `Merge branch
'dev'...`, there are no feature commits to categorise (the script
correctly skips merge commits via `--no-merges`).

**"gitlab-release.sh : tag not found on origin"**

The script refuses to create a Release for a tag that isn't pushed.
Run `git push origin stable-vX.Y.Z` first.

**"CHANGELOG.md merge conflict"**

Someone (or you on a parallel branch) bumped the CHANGELOG on
`main` between the regen and the commit. Rebase, re-run
`changelog.sh`, and commit the fresh version.

## References

- [`bin/ship/changelog.sh`](../../bin/ship/changelog.sh) — the generator
- [`bin/ship/gitlab-release.sh`](../../bin/ship/gitlab-release.sh) — the promoter
- [`CHANGELOG.md`](../../CHANGELOG.md) — the hand-rolled output
- [Conventional Commits spec](https://www.conventionalcommits.org)
- [`~/.claude/CLAUDE.md`](../../.claude/CLAUDE.md) → "Tag every green
  stability checkpoint, never tag on red" — the tagging contract
