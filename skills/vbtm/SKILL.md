---
name: vbtm
description: Read a Verbatime `.vbtm` recording with the `vbtm` command to find where the time went. Use whenever the user points at a `.vbtm` file or asks why a request, server startup, batch job or test run was slow, which methods are hot, which code path calls a method, whether there is an N+1 query or a costly loop, or which exceptions are thrown and where they are caught.
---

# Investigating a `.vbtm` recording with `vbtm`

`vbtm` prints a few hundred lines at a time. Every output is capped and ends with the command that shows
more, and the ids one command prints are what the next command takes. `vbtm --help` and
`vbtm <command> --help` are the reference. If `vbtm` is not found, ask the user where it is.

## Terms

- A **session** is one call tree under a root method on one thread, such as one request. `vbtm sessions`
  numbers them, such as `7`.
- A **call** is `SESSION.N`, the Nth call entered in that session. `7.0` is the root. Ids stay the same for
  the same file, so they can be quoted in a report.
- **total** is entry to exit including callees. **self** is the time in the method's own code.
- Durations are ms with 4 decimals. Options take `500us`, `1ms`, `2s`.
- Nothing is sampled or dropped. Counts are exact.

## Workflow

Stop as soon as the question is answered.

1. `vbtm sessions rec.vbtm --sort dur`
   Pick the slow session. Narrow with `--root PATTERN` or `--thread TEXT`. Compare `gc_ms` with `dur` before
   blaming code.
2. `vbtm hot rec.vbtm 7`
   Methods by self time. `--by total` shows the expensive subsystem, `--by calls` the chatty methods. Small
   self time with a huge call count is a loop or an N+1.
3. `vbtm throws rec.vbtm 7`
   Exceptions with thrower, catcher, number of throws and the time of the calls that threw. Many throws of
   one class under one catcher are retries or lookups that throw when nothing is found. `slowest` is a call
   id for `tree --at`.
4. `vbtm tree rec.vbtm 7`
   Where the time goes, longest calls only. Lines starting with `-` fold the hidden calls under their parent.
   `--merge` sums repeated calls under the same path, which is shorter for loops and N+1 patterns.
5. `vbtm tree rec.vbtm --at 7.57`
   One call's subtree, after its path from the root. Repeat on the child that holds the time until the self
   time is in one method or in a fold of many small calls.
6. `vbtm find rec.vbtm 'OrderDao::query' --session 7`
   Every call of a method, slowest first, with its caller. `--min 5ms` keeps slow calls, `--thrown` the calls
   that threw, `--sort start` shows them in order. Compare `tree --at` of the slowest with a typical one.
7. `vbtm callers rec.vbtm 'OrderDao::query'`
   The code paths that lead to a method, each with its calls, time and `slowest` call id.

## Rules

- **Follow the printed commands.** `# N more` and `# largest fold` give the command that shows the rest. Run it
  only when the rest matters, and prefer `--at`, `--session`, `--floor` or `--depth` over a larger `--limit`.
- **Never guess a call id.** Copy it from an earlier output.
- **A pattern names one method**: `Class::method`, `pkg.Class::method`, the printed name such as `Class.method`
  or `Class.method#2`, or any part of `pkg.Class.method`. When it matches several methods, exit status 1
  lists them. Pick one and rerun. Pass `--all` only when they should be counted together. A
  `names printed alike:` block maps names like `Class.method#2` to their full signatures.
- **Markers.** `~` means still open when the recording ended, so the duration is a lower bound. `!eN` in
  `tree` and `!Exception` in `find` mean the call ended by throwing. An exception counts once, not once per
  call it unwinds through.
- **GC.** `tree` lists the stop-the-world pauses inside the printed tree and includes them in durations.
  Subtract them before attributing a slow call to its code.
- **Locks.** A `synchronized` method's time starts after its lock is acquired, so waiting for the lock
  appears as self time of the caller.
- **JSON.** Every command takes `--json` and prints one JSON object per line with a `type` field. Use it
  with `jq` to compare many rows.
- **Exit status.** 0 is success, including a truncated recording, which is read up to the cut. 1 is a bad
  argument, with a `hint:` on stderr. 2 means the file cannot be read or is not a recording. 3 means it is
  corrupt, and what was printed before `# status:` is still valid.

## Reporting

Back every claim with:

- the session id and its duration, and GC time when it matters
- the call ids with their total, self and calls
- the path from the root, as `tree --at` prints it
- the `vbtm` command that shows each result
- why the time is spent: one slow call, many cheap calls from one code path, or a wait inside a method

A number the recording does not contain is a guess and is labeled as one.
