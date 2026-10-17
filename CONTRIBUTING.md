# Contributing

## Development environment

Build inside the Nix dev shells listed under [Build](README.md#build). Entering one installs the
pre-commit hooks, which CI also runs through `nix flake check`.

## Pull requests

- Write the title as a [Conventional Commit](https://www.conventionalcommits.org/), such as
  `fix(cli): ...`. Pull requests are squash merged, so the title becomes the commit on `main`.
- `mvn verify` fails on unformatted Java. Run `mvn spotless:apply` to format it.
- If a change to a `pom.xml` or `flake.lock` makes `nix flake check` fail with a hash mismatch for
  `vbtm`, update the hash:

  ```sh
  nix run --inputs-from . nixpkgs#nix-update -- --flake vbtm --version skip
  ```

- After adding or updating an action in a workflow, run `gh actions-lock`.

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
