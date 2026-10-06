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

CI verifies a feature or fix branch through its pull request only; pushing the branch alone runs
nothing. Open a draft pull request to have a branch verified before it is ready for review.

Dependency updates from Renovate go into `main` without a version bump of their own. They carry the
version `main` carries and ship with the next fix release.

Pull requests are labelled automatically: by the branch they come from (`t:feature`, `t:fix`,
`t:deps`, `t:release`, `t:chore`, `t:other`) and by what they change (`a:backend`, `a:frontend`,
`a:database`, `a:api`, `a:i18n`, `a:helpcenter`, `a:federation`, `a:tests`, `a:e2e`, `a:docs`, `ci`). The rules are in
`.github/labeler.yml`.

## Versions

The version lives in `build.gradle.kts`. CI checks it on every pull request and every push it runs on.

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

A change people will notice needs an entry under the version it ships with, in both `CHANGELOG.md`
and `CHANGELOG.de.md`. A release needs a block for its version in `CHANGELOG.md`, because its release
notes are taken from there.

## Releasing

Releases are made by the `Release` workflow. It needs the repository secret `RELEASE_TOKEN` (see
below); tags it creates are not signed.

### Feature release

Every release branch has one release pull request from `release/vX.Y.Z` into `main`, titled
`Release vX.Y.Z`. It is opened as a draft once the branch has its first commit (see below) and stays
open until the release.

1. Wait until CI is green on the release branch and `Release branch contains main` passes.
2. Add the label `release` to the release pull request.

The workflow checks that the branch carries version `X.Y.Z`, that the version is not released yet,
that `CHANGELOG.md` has a block for it, that CI passed on the branch head and that `main` can be
fast-forwarded to it. It then marks the pull request ready for review, moves `main` to the branch
head without a merge commit, tags `vX.Y.Z` on that commit, and creates the GitHub release `vX.Y.Z`
from the English changelog block. GitHub then shows the pull request as merged. When a check fails, the workflow removes the label and comments
why.

`./toolchain.sh release-check <pull request>` runs the same checks locally and changes nothing.

The release pull request can also be merged with "Rebase and merge" instead of the label. `main` then
holds the branch's commits under new hashes, with the merging person as committer. CI on `main`
accepts the unreleased version `X.Y.0` one minor above the newest release when `CHANGELOG.md` has its
block, and the workflow waits for that CI run, then tags `vX.Y.0` on the head of `main` and creates
the release. A release pull request that carries the label was merged by the workflow and is not
released a second time.

### Fix release

Fix pull requests are squash-merged into `main` as usual. To release the fixes on `main`, either add
the label `release` to the merged pull request (before or after merging), or run the `Release`
workflow by hand without a pull request number. The workflow waits for CI on that commit, tags the
version it carries and creates the GitHub release.

### After a release

CI writes no commits, so the next versions are opened locally with `./toolchain.sh release-next`.
After a release, the workflow opens the issue `Next versions to open` listing what is due, and the
local run closes it. The bump commits carry the identity and the signature of whoever runs it, and
are made in a scratch worktree, so the checkout is left alone. Nothing of it is merged
automatically: both bump pull requests are merged by hand.

- After every release it opens the pull request `chore/bump-X.Y.Z` into `main`, with the one commit
  `Bump version to X.Y.Z` that moves `main` to the next patch: `26.21.0` is followed by `26.21.1`,
  `26.20.2` by `26.20.3`. When that pull request is open already, or `main` carries another version
  than the released one, nothing happens.
- After a feature release `X.Y.0` it also opens the next release branch `release/vX.(Y+1).0`, at the
  released commit and without a commit of its own, and the pull request `chore/bump-X.(Y+1).0` into
  it with the one commit that sets its version. Until that pull request is merged, the branch still
  carries the released version, and CI accepts it. When a release branch for a later version exists
  already, no new one is opened.
- Once the release branch has a commit of its own, it opens the draft release pull request. A new
  branch cannot have one before its bump is merged; the next `release-next` or `release-sync` run
  opens it then.

`./toolchain.sh release-next --dry-run` shows locally what would be opened. A signature that cannot
be made stops the run before anything is pushed; run it again once the key signs.

### Rebase of the release branch

The release branch always has to contain `main`. After every push to `main` (a merged fix, a
dependency update, a merged bump), after every release and after a push to a release branch, the
`Release Sync` workflow checks this and sets the commit status `Release branch contains main` on the
head of each open `release/v*` branch with a commit of its own: green when it contains `main`, red
when it is behind. The status shows on the release pull request. The workflow writes to no branch.

The rebase itself is run locally with `./toolchain.sh release-sync` when that status is red. It
rebases each open release branch onto `main` in a scratch worktree, signs every rewritten commit as
`commit.gpgsign` says, and pushes it with a lease; the push turns the status green. It also opens
the draft release pull request of a branch that has none. A branch that contains `main` already, a
released one, and one without a commit of its own (a new release branch whose bump is not merged
yet) are not rebased. When `main` gained a patch with the number the release branch uses for its own, the
release branch's patch is renumbered above it first. Three conflicts are resolved on the way:
`build.gradle.kts` keeps the release branch's version, the migration version file names the newest
patch, and two version blocks added at the top of a changelog are both kept, the release branch's
above the fix's. Any other conflict, or a signature that cannot be made, stops the rebase, leaves the
branch as it was and names the reason; a conflict is then resolved by hand.
`./toolchain.sh release-sync --dry-run` does the rebase locally without signing or pushing.

Open pull requests into the release branch catch up by merging the release branch into their branch
("Update branch" on the pull request), never by a rebase: their own commits keep their history and
signatures, and the squash merge leaves no merge commit on the release branch.

### Token

`RELEASE_TOKEN` is a fine-grained personal access token for this repository with read and write
access to contents, pull requests, issues and workflows, and read access to actions. Workflows are
only started by pushes, tags and releases made with such a token, which is how the release reaches
CI and the Docker build. Only a token with workflow access may push commits that change files under
`.github/workflows`. Without the secret, the workflows fall back to the built-in token, and the
release starts no other workflow.
