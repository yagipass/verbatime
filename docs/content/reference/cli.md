# vbtm commands

Each command takes a recording, and most take a session id or a call id printed by an earlier
command. See [Reading a recording with vbtm](../guide/reading-with-vbtm.md) for a walkthrough.

| Command | What it shows |
|---|---|
| `sessions` | Every session with its time, calls, exceptions, GC time, thread, and root method. Start here. |
| `hot` | Methods ranked by self time, total time, or calls |
| `throws` | Exceptions, where they were thrown and caught, and how often |
| `tree` | The call tree of a session, or of one call with `--at` |
| `find` | Every call of a method, slowest first |
| `callers` | The paths that lead to a method |

`vbtm --help` and `vbtm <command> --help` list all options.
