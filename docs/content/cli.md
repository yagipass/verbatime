---
description: Install vbtm and read a .vbtm recording from the command line, from the slowest sessions to hot methods and call trees, as text or JSON.
---

# vbtm CLI

`vbtm` reads a `.vbtm` recording and shows where the time went, a few hundred lines at a time.

## Install

On macOS (Apple silicon) and Linux (x86_64, aarch64), the install script downloads `vbtm` from
the [GitHub releases page](https://github.com/yagipass/verbatime/releases) and puts it in
`~/.local/bin`.

::: code-group

```sh [macOS / Linux]
curl -fsSL https://github.com/yagipass/verbatime/releases/latest/download/install-cli.sh | sh
```

```sh [Other (Java 17+)]
curl -LO https://github.com/yagipass/verbatime/releases/latest/download/verbatime-cli.jar
java -jar verbatime-cli.jar --help
```

```sh [Nix]
nix profile install github:yagipass/verbatime#vbtm
```

:::

Optionally, check that GitHub Actions built it:

```sh
gh attestation verify ~/.local/bin/vbtm --repo yagipass/verbatime
gh attestation verify verbatime-cli.jar --repo yagipass/verbatime    # for the jar
```

## Find a slow call

Start with the sessions, then narrow down. The ids one command prints are what the next one
takes.

```sh
vbtm sessions rec.vbtm --sort dur    # the slowest sessions, such as 7
vbtm hot rec.vbtm 7                  # the methods that took the most time
vbtm tree rec.vbtm 7                 # the call tree, longest calls first
vbtm tree rec.vbtm --at 7.57         # one call and everything under it
```

Every output is capped and ends with the command that shows more.

## Commands

| Command | What it shows |
|---|---|
| `sessions` | Every session with its time, calls, exceptions, GC time, thread, and root method. Start here. |
| `hot` | Methods ranked by self time, total time, or calls |
| `throws` | Exceptions, where they were thrown and caught, and how often |
| `tree` | The call tree of a session, or of one call with `--at` |
| `find` | Every call of a method, slowest first |
| `callers` | The paths that lead to a method |

`vbtm --help` and `vbtm <command> --help` list all options.

## JSON output

Every command takes `--json`, which prints one JSON object per line. `vbtm` never prompts.

| Exit status | Meaning |
|---|---|
| 0 | Success |
| 1 | Bad arguments |
| 2 | The file cannot be read, or is not a recording |
| 3 | The file is corrupt. What could be read is still printed. |

## With an AI agent

[`skills/vbtm/`](https://github.com/yagipass/verbatime/blob/main/skills/vbtm/SKILL.md) is an agent
skill that investigates a recording with `vbtm`. Add it to your agent, and ask why a request was
slow.

A recording holds class, method, thread, and exception names, GC pauses, and timings. It holds no
argument values, return values, or exception messages.
