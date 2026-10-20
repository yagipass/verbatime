# Contributing

## Development environment

Build inside the Nix dev shells listed under [Build](README.md#build). Entering one installs the
pre-commit hooks, which CI also runs through `nix flake check`.

## Pull requests

- Write the title as a [Conventional Commit](https://www.conventionalcommits.org/), such as
  `fix(cli): ...`. Pull requests are squash merged, so the title becomes the commit on `main`.
- Start the branch name with the same type, such as `fix/print-gc-pauses`. The type and the
  changed files label the pull request, and the labels sort it into the sections of the
  [changelog](CHANGELOG.md).
- `mvn verify` fails on unformatted Java. Run `mvn spotless:apply` to format it.
- If a change to a `pom.xml` or `flake.lock` makes `nix flake check` fail with a hash mismatch for
  `vbtm`, update the hash:

  ```sh
  nix run --inputs-from . nixpkgs#nix-update -- --flake vbtm --version skip
  ```

- After adding or updating an action in a workflow, run `gh actions-lock`.

## Fuzzing

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

## Repository settings

Labels, rulesets, and other repository settings are kept in [`.github/infra.yml`](.github/infra.yml).
Change them in a pull request. After the merge, a maintainer applies them with
[gh-infra](https://github.com/babarot/gh-infra):

```sh
gh infra apply .github/infra.yml
```

`apply` does not delete labels or rulesets removed from the file. Renaming a job in `ci.yml` also
needs the same change under `required_status_checks`, or pull requests cannot merge. Environments
and secrets are changed on GitHub.
