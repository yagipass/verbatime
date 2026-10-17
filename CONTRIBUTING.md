# Contributing

## Repository settings

The repository settings, such as labels, rulesets, and Actions permissions, are kept in
[`.github/infra.yml`](.github/infra.yml) and applied with
[gh-infra](https://github.com/babarot/gh-infra). To change one, edit the file in a pull request.
Applying it needs admin access to the repository, so a maintainer runs it by hand after the merge.

```sh
gh infra plan .github/infra.yml
gh infra apply .github/infra.yml
```

`apply` only adds and updates. Removing a label or a ruleset from the file does not delete it on
GitHub. Environments and secrets are not managed by gh-infra. Change them in the repository
settings on GitHub.
