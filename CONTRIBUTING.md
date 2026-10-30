# Contributing

## Setup

Run `nix develop` (or `direnv allow`). Each module builds in its own dev shell, and entering any of
them installs the git hooks:

| Shell | Provides | For |
|---|---|---|
| `nix develop .#agent`, the default | JDK 25, Maven and mvnd | `modules/agent` |
| `nix develop .#jmc` | JDK 21, Maven and mvnd | `modules/jmc` |
| `nix develop .#cli` | GraalVM CE, Maven and mvnd | `modules/cli` |
| `nix develop .#docs` | Node.js and pnpm | `docs/` |

`modules/format` is built with each of the other modules. `direnv allow` in `docs/` enters the
docs shell.

## Build and test

| Command | Purpose |
|---|---|
| `nix develop .#agent --command mvn -B -pl modules/agent -am verify` | build `modules/agent/target/verbatime-agent.jar`, and run the unit tests and end-to-end runs of the packaged jar |
| `nix develop .#jmc --command mvn -B -pl modules/jmc -am verify` | build `modules/jmc/target/verbatime-jmc-plugin.jar` and run the unit tests |
| `nix develop .#cli --command mvn -B -pl modules/cli -am verify` | build `modules/cli/target/verbatime-cli.jar` and run the tests |
| `nix develop .#cli --command mvn -B -pl modules/cli -am -Pnative -DskipTests package` | build the native `modules/cli/target/vbtm` |
| `nix develop .#docs --command sh -c 'cd docs && pnpm install --frozen-lockfile && pnpm run build'` | build the docs site |
| `pnpm install && pnpm dev` in `docs/` | serve the docs site and reload on changes |
| `mvn spotless:apply` | format Java |
| `nix fmt` | format Nix |
| `nix flake check` | build the `vbtm` package and the dev shells, and run the git hooks over every tracked file |

CI also runs the agent build with `-Pcorpus-full`, `nix flake check` on Linux x86_64, Linux arm64
and macOS, `gitleaks git` over every commit, and `pnpm exec wrangler deploy --dry-run` for the docs
site.

### Agent corpus

The agent build instruments about 50 real-world jars in `modules/agent/target/corpus` and fails if
a class links with its original bytes but not with the instrumented ones. It also checks that code
that finds its caller on the stack, such as log4j2's `LogManager.getLogger()`, sees the same caller
with the agent. `-Pcorpus-full` checks about 120,000 classes and downloads another 180 MB.

[`bench/`](bench/) measures the agent's overhead and is run by hand.

### JMC plugin in the Eclipse IDE

To work on the plugin in the Eclipse IDE, use a package that includes PDE, such as Eclipse IDE for
RCP and RAP Developers, and import `modules/jmc` with
`File > Import > General > Existing Projects into Workspace`.

### Fuzzing

The `*FuzzTest` classes use [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer). Those of
the readers of recordings compare each reader with `ReferenceDecoder` in the format module's tests,
starting from the recordings in `Seeds`, which `mvn verify` replays too.

After changing a reader, `EventCursor`, or `ReferenceDecoder`, fuzz the tests of the readers it
affects for 5 minutes each before opening a pull request:

```sh
JAZZER_FUZZ=1 mvn -B -pl modules/format -am verify -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='TraceReaderFuzzTest#readsAnyBytesLikeTheReference'
```

- A failing input is saved as `crash-*` in `src/test/resources/<package>/<Class>Inputs/<method>/`,
  which git ignores, and `mvn verify` replays it while it is there. Turn it into a case in
  `SpecCases`, or into a test of that reader, then delete it.
- A difference between the readers that is meant to stay becomes a mode of `ReferenceDecoder` that
  the test of that reader uses, not a looser comparison.
- When adding a record type, add a recording that holds it to `Seeds`.

The `fuzz` workflow fuzzes each of these tests for 20 minutes every night, starting from what the
nights before found. When it finds an input, the run fails and the input is attached to it.

## Rules

- `mvn verify` fails on unformatted Java. Run `mvn spotless:apply` to format it.
- If a change to a `pom.xml` or `flake.lock` makes `nix flake check` fail with a hash mismatch for
  `vbtm`, update the hash:

  ```sh
  nix run --inputs-from . nixpkgs#nix-update -- --flake vbtm --version skip
  ```

- After adding or updating an action in a workflow, run `gh actions-lock`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/), checked by
  the `commit-msg` hook.
- Start the branch name with the Conventional Commits type, such as `fix/print-gc-pauses`. The type
  and the changed files label the pull request, and the labels sort it into the sections of the
  [changelog](CHANGELOG.md).

## Proposing a change

Open an issue before you start on a new feature or a change in behavior, with the
[issue form](https://github.com/yagipass/verbatime/issues/new/choose) that fits it. A small fix,
such as a typo, can go straight to a pull request. Report a vulnerability as
[SECURITY.md](SECURITY.md) describes, not in an issue.

A pull request:

- references its issue (`Closes #N`), if it has one.
- adds tests when it changes behavior. A bug fix adds a test that reproduces the bug.
- passes the builds of the modules and the docs site it changes, and `nix flake check`.
- has a title written as a Conventional Commit, such as `fix(cli): ...`. Pull requests are squash
  merged, so the title becomes the commit on `main`.

## Release

Merge the release pull request that [tagpr](https://github.com/Songmu/tagpr) keeps open. Label it
`tagpr:minor` or `tagpr:major` for more than a patch release. tagpr writes `CHANGELOG.md` and the
versions in the `pom.xml` files, `modules/jmc/META-INF/MANIFEST.MF` and `nix/vbtm.nix`, so do not
edit them by hand.

Merging it tags `vX.Y.Z`, and `release.yml` then:

- builds and tests the JMC plugin and the CLI again, and attaches `verbatime-jmc-plugin.jar`,
  `verbatime-cli.jar` and `install-cli.sh` to the draft GitHub release.
- uploads the signed agent to Maven Central and attaches `verbatime-agent.jar`.
- builds `vbtm` for Linux x86_64, Linux arm64 and macOS arm64, and attaches each binary with its
  SHA-256 checksum.
- publishes the GitHub release.
- updates the `vbtm` formula in [yagipass/homebrew-tap](https://github.com/yagipass/homebrew-tap).
- pushes the example images, tagged with the version, to the GitHub Container Registry.
- deploys the docs site to Cloudflare Workers.

The jars, `install-cli.sh` and the `vbtm` binaries get build provenance attestations. Then publish
the agent's deployment by hand in the
[Central Portal](https://central.sonatype.com/publishing/deployments).

This needs the "Allow GitHub Actions to create and approve pull requests" setting, which
`.github/infra.yml` turns on, and these secrets:

- in the environment `maven-central`: `MAVEN_CENTRAL_TOKEN_USERNAME` and
  `MAVEN_CENTRAL_TOKEN_PASSWORD` (a Central Portal user token), and `MAVEN_GPG_PRIVATE_KEY` and
  `MAVEN_GPG_PASSPHRASE` (the ASCII-armored signing key and its passphrase).
- in the environment `Cloudflare`: `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.
- in the repository: `HOMEBREW_TAP_TOKEN`, a token that can push to the tap.

## Repository settings

The repository settings, labels, rulesets, and Actions permissions are managed with
[gh-infra](https://github.com/babarot/gh-infra) in [`.github/infra.yml`](.github/infra.yml). To
change them, edit the file in a pull request, review the difference with
`gh infra plan .github/infra.yml`, and apply it with `gh infra apply .github/infra.yml` after the
merge. Applying needs admin access to the repository.

`apply` does not delete labels or rulesets removed from the file. Renaming a job in `ci.yml` also
needs the same change under `required_status_checks`, or pull requests cannot merge.

Environments and secrets are not managed by gh-infra. Change them in the repository settings on
GitHub.
