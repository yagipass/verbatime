---
description: Check that a Verbatime jar you downloaded is the one Verbatime released, unchanged, with the GitHub CLI or GPG.
---

# Verifying downloads

To check that a jar from the [GitHub releases page](https://github.com/yagipass/verbatime/releases)
is the one Verbatime released, unchanged, verify it with the [GitHub CLI](https://cli.github.com/).

```sh
gh attestation verify verbatime-agent.jar --repo yagipass/verbatime
gh attestation verify verbatime-jmc-plugin.jar --repo yagipass/verbatime
gh attestation verify verbatime-cli.jar --repo yagipass/verbatime
```

The `vbtm` install script and Homebrew check the binary for you.

## With GPG

The agent is also signed with GPG. Its signature is on
[Maven Central](https://repo1.maven.org/maven2/io/github/yagipass/verbatime-agent/).

```sh
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys 75F1EAAE88F8E7BAB417515A2EB6ECF68A067DE9
gpg --verify verbatime-agent-<version>.jar.asc verbatime-agent.jar
```
