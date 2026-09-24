# vbtm

A command-line tool that reads a `.vbtm` recording and shows where the time went.

## Why

A recording holds every call, often millions of them. `vbtm` prints only what you ask for, a few
hundred lines at a time, so you or an AI agent can follow a slow request down to the method that
caused it.

- **Short output.** Every output is capped and ends with the command that shows more.
- **Chained.** The ids one command prints are what the next command takes.
- **For scripts too.** It never prompts, and `--json` prints JSON Lines.
- **Fast.** A command reads a recording of 100 million events in about a second.

## Usage

Install `vbtm` from the [GitHub releases page](https://github.com/yagipass/verbatime/releases)
or with Nix.

### Finding a slow call

Start with the list of sessions, then narrow down.

```sh
vbtm sessions rec.vbtm --sort dur    # the slowest sessions, such as 7
vbtm hot rec.vbtm 7                  # the methods that took the most time
vbtm tree rec.vbtm 7                 # the call tree, longest calls first
vbtm tree rec.vbtm --at 7.57         # one call and everything under it
```

### Commands

| Command | What it shows |
|---|---|
| `sessions` | Every session with its time, calls, exceptions, GC time, thread, and root method. Start here. |
| `hot` | Methods ranked by self time, total time, or calls |
| `throws` | Exceptions, where they were thrown and caught, and how often |
| `tree` | The call tree of a session, or of one call with `--at` |
| `find` | Every call of a method, slowest first |
| `callers` | The paths that lead to a method |

`vbtm --help` and `vbtm <command> --help` list all options.

### With an AI agent

[`skills/vbtm/`](../../skills/vbtm/SKILL.md) is an agent skill that investigates a recording with
these commands. Add it to your agent, and ask why a request was slow.

## Build

From the repository root, as described in [Build](../../README.md#build):

```sh
nix develop .#cli --command mvn -B -pl modules/cli -am verify
nix develop .#cli --command mvn -B -pl modules/cli -am -Pnative -DskipTests package
```

## License

Verbatime is licensed under the [Apache License, Version 2.0](../../LICENSE).
Copyright 2026 yagipass. See [NOTICE](../../NOTICE) for attribution requirements when redistributing.
