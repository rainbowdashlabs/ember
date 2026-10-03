# Contributing

## Branches

| Branch | Holds | Pull requests go into |
|---|---|---|
| `main` | What is released, plus fixes waiting for their release | |
| `release/vX.Y.Z` | The next feature release | `main`, once, to release it |
| `feature/<name>` | One feature | the open `release/vX.Y.Z`, squash-merged |
| `fix/<name>` | One fix | `main` when it is urgent, otherwise the open `release/vX.Y.Z`; squash-merged |

A feature never goes into `main` directly. A fix that cannot wait goes into `main` and is released on
its own as a patch; it reaches the release branch by the rebase described below. A fix that can wait
for the next feature release goes into the open release branch, branched from it, and carries its
version. A pull request into any other branch fails CI.

Dependency updates from Renovate go into `main` without a version bump of their own. They carry the
version `main` carries and ship with the next fix release.

## Versions

The version lives in `build.gradle.kts`. CI checks it on every push and pull request.

- A release branch `release/vX.Y.Z` carries the version `X.Y.Z` from its first commit on.
- A feature branch carries the version of the open release branch.
- `main` carries one patch above the newest release: after `v26.20.1`, it carries `26.20.2`, set by
  the bump pull request opened after the release. An urgent fix into `main` carries that version too,
  and several fixes merged before the next release share it. A fix never goes further than one patch
  above the newest release.
- A fix into the open release branch carries that branch's version, like a feature.
- A version that is already tagged is never used again.

## Migrations

Migrations live in `src/main/resources/database/postgresql/1/`, and
`src/main/resources/database/version` names the newest one.

- A branch adds at most one patch. Further schema changes on the same branch edit that patch.
- A patch that is on `main` never changes, because databases that already ran it never run it again.
- A release branch carries at most one patch of its own, and its number is above every patch on `main`.
  A feature that needs schema while the release branch already has a patch edits that patch.
- A fix that needs schema takes the next free number on `main`. When the release branch is rebased
  onto that fix, its own patch moves above it by itself.

`./toolchain.sh db-renumber-patch <from> <to>` renames a patch that is not on `main` yet, together with
the version file and every test that names it. It refuses a patch that is on `main`.

## Changelog

Every change a user or operator can notice gets a line in both `CHANGELOG.md` and `CHANGELOG.de.md`,
under the version it ships with. Each version block opens with a short paragraph on what the release
focuses on, followed by the sections it needs: New Features, Improvements, Security, Changes, Fixes.
The wording is factual and impersonal. Refactors, tests, build changes and dependency updates get no
line.

## Commands

Every build, test, lint and check runs through `./toolchain.sh <command>`. `./toolchain.sh help` lists
them. A recurring command without a subcommand gets one added to `toolchain.sh`.

## Commits and pull requests

Commit messages and pull request titles are one short imperative line, such as
`Show station logos on the discovery page`. A body of one or two sentences is added only when the
reason is not obvious. Each feature or fix lands as one squashed commit.

## Releasing

Releases are made by the `Release` workflow. It needs the repository secret `RELEASE_TOKEN` (see
below); tags it creates are not signed.

### Feature release

1. Open a pull request from `release/vX.Y.Z` into `main`.
2. Wait until CI is green on the release branch.
3. Add the label `release` to the pull request.

The workflow checks that the branch carries version `X.Y.Z`, that the version is not released yet,
that `CHANGELOG.md` has a block for it, that CI passed on the branch head and that `main` can be
fast-forwarded to it. It then moves `main` to the branch head without a merge commit, tags `vX.Y.Z`
on that commit, and creates the GitHub release `vX.Y.Z` from the English changelog block. GitHub then
shows the pull request as merged. When a check fails, the workflow removes the label and comments
why.

`./toolchain.sh release-check <pull request>` runs the same checks locally and changes nothing.

### Fix release

Fix pull requests are squash-merged into `main` as usual. To release the fixes on `main`, either add
the label `release` to the merged pull request (before or after merging), or run the `Release`
workflow by hand without a pull request number. The workflow waits for CI on that commit, tags the
version it carries and creates the GitHub release.

### After a release

The workflow opens the next versions. Nothing of it is merged automatically: both bump pull
requests are opened for you and merged by hand.

- After every release it opens the pull request `chore/bump-X.Y.Z` into `main`, with the one commit
  `Bump version to X.Y.Z` that moves `main` to the next patch: `26.21.0` is followed by `26.21.1`,
  `26.20.2` by `26.20.3`. When that pull request is open already, or `main` carries another version
  than the released one, nothing happens.
- After a feature release `X.Y.0` it also opens the next release branch `release/vX.(Y+1).0`, at the
  released commit and without a commit of its own, and the pull request `chore/bump-X.(Y+1).0` into
  it with the one commit that sets its version. Until that pull request is merged, the branch still
  carries the released version, and CI accepts it. When a release branch for a later version exists
  already, no new one is opened.

`./toolchain.sh release-next --dry-run` shows locally what would be opened.

### Rebase of the release branch

After every push to `main` (a merged fix, a dependency update, a merged bump) and after every release,
the `Release Sync` workflow rebases each open `release/v*` branch onto `main`, so the release branch
always contains `main`. A branch that contains `main` already, a released one, and one without a
commit of its own (a new release branch whose bump is not merged yet) are left alone. A push to a
release branch runs the rebase too, which is how a new release branch catches up with `main` once its
bump is merged. When `main`
gained a patch with the number the release branch uses for its own, the release branch's patch is
renumbered above it first. Three conflicts are resolved on the way: `build.gradle.kts` keeps the
release branch's version, the migration version file names the newest patch, and two version blocks
added at the top of a changelog are both kept, the release branch's above the fix's. Any other
conflict stops the rebase and opens an issue naming the branch and the files; the branch is then
rebased by hand.

The rebase rewrites the release branch's commits, so they lose their signatures, and open feature
pull requests into the release branch need a rebase afterwards. The workflow can also be run by hand
as `Release Sync`.

### Token

`RELEASE_TOKEN` is a fine-grained personal access token for this repository with read and write
access to contents, pull requests, issues and workflows, and read access to actions. Workflows are
only started by pushes, tags and releases made with such a token, which is how the release reaches
CI and the Docker build, how the bump pull requests get their checks, and how a rebased release
branch is verified. Only a token with workflow access may push commits that change files under
`.github/workflows`. Without the secret, the workflows fall back to the built-in token, and none of
that happens: the bump pull requests are still opened, but no check runs on them until a later push.
