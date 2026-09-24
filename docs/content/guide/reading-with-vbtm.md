# Reading a recording with vbtm

`vbtm` is a command-line tool that reads a `.vbtm` recording and shows where the time went.

A recording holds every call, often millions of them. `vbtm` prints only what you ask for, a few
hundred lines at a time, so you or an AI agent can follow a slow request down to the method that
caused it.

- **Short output.** Every output is capped and ends with the command that shows more.
- **Chained.** The ids one command prints are what the next command takes.
- **For scripts too.** It never prompts, and `--json` prints JSON Lines.
- **Fast.** A command reads a recording of 100 million events in about a second.

## Install

Download `vbtm` from the [GitHub releases page](https://github.com/yagipass/verbatime/releases)
and put it on your `PATH`.

::: code-group

```sh [macOS (Apple silicon)]
curl -Lo vbtm https://github.com/yagipass/verbatime/releases/latest/download/vbtm-macos-arm64
chmod +x vbtm
```

```sh [Linux x86_64]
curl -Lo vbtm https://github.com/yagipass/verbatime/releases/latest/download/vbtm-linux-amd64
chmod +x vbtm
```

```sh [Linux aarch64]
curl -Lo vbtm https://github.com/yagipass/verbatime/releases/latest/download/vbtm-linux-arm64
chmod +x vbtm
```

```sh [Other (Java 17+)]
curl -LO https://github.com/yagipass/verbatime/releases/latest/download/verbatime-cli.jar
java -jar verbatime-cli.jar --help
```

```sh [Nix]
nix profile install github:yagipass/verbatime#vbtm
```

:::

## Find a slow call

Start with the list of sessions, then narrow down.

```sh
vbtm sessions rec.vbtm --sort dur    # the slowest sessions, such as 7
vbtm hot rec.vbtm 7                  # the methods that took the most time
vbtm tree rec.vbtm 7                 # the call tree, longest calls first
vbtm tree rec.vbtm --at 7.57         # one call and everything under it
```

See [vbtm commands](../reference/cli.md) for every command.
