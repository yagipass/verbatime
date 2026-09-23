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

Download `vbtm-linux-amd64`, `vbtm-linux-arm64`, or `vbtm-macos-arm64` from the
[GitHub releases page](https://github.com/yagipass/verbatime/releases), rename it to `vbtm`, and
make it executable. On other platforms, download `verbatime-cli.jar` and run it with
`java -jar verbatime-cli.jar` on Java 17 or later.

## Find a slow call

Start with the list of sessions, then narrow down.

```sh
vbtm sessions rec.vbtm --sort dur    # the slowest sessions, such as 7
vbtm hot rec.vbtm 7                  # the methods that took the most time
vbtm tree rec.vbtm 7                 # the call tree, longest calls first
vbtm tree rec.vbtm --at 7.57         # one call and everything under it
```

See [vbtm commands](../reference/cli.md) for every command.
