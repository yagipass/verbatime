# verbatime-agent

A Java agent that records every call under the methods you choose, and writes them to a `.vbtm`
recording.

## Why

To see where one unit of work spent its time, such as a single request, a server startup, or a
batch job, you need every call it made, in order, not a sample or an average.

- **Every call.** Each run of a root method becomes one call tree, called a *session*, with the
  order, nesting, and time of every call in it. Nothing is sampled or dropped.
- **Drop in.** Add one `-javaagent` flag. No code changes, and no settings are required.
- **Cheap.** A recorded call costs about 35 ns, and time is measured in steps of 100 ns.
- **Readable at once.** A recording can be opened while it is still being written.

## Usage

The agent needs Java 25 or later. Download `verbatime-agent.jar` from the
[GitHub releases page](https://github.com/yagipass/verbatime/releases).

### Recording from JDK Mission Control

Start the application with the agent and a JMX port.

```sh
java -javaagent:/path/to/verbatime-agent.jar \
     -Dcom.sun.management.jmxremote.port=7091 -Dcom.sun.management.jmxremote.rmi.port=7091 \
     -Dcom.sun.management.jmxremote.host=127.0.0.1 \
     -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
     -Djava.rmi.server.hostname=localhost \
     -cp ... your.Main
```

Then, in the Verbatime Control view of JDK Mission Control, connect to `localhost:7091`, set the
root methods, and press Start recording and Stop recording. The
[root README](../../README.md#quick-start) shows how to install the plugin.

### Recording with the agent alone

A test run or a batch job may finish before you can press Start recording. With `record=startup`,
the agent alone records from startup until the JVM exits and writes the recording to `out=`. No JMX
port is needed.

```text
-javaagent:/path/to/verbatime-agent.jar=record=startup,roots=com.example.app.Job::run,out=trace.vbtm
```

### Options

Options are `key=value` pairs separated by `,` after `=`. All are optional. Where a key takes
several values, separate them with `+`.

| Key | Default | Description |
|---|---|---|
| `include` | everything | Packages or classes to instrument, as in `include=com.example.app`. The JDK and the agent are never instrumented. Wildcards such as `com.example.*` are not accepted. |
| `exclude` | none | Packages or classes to leave out of `include`. |
| `roots` | none | Root methods, as in `roots=com.example.app.Job::run`. JDK Mission Control can still change them. |
| `out` | none | Write the recording to this file. The directory must exist. |
| `spool` | temp dir | Where to keep a recording until JDK Mission Control reads it, when `out=` is not given. |
| `waitstart` | off | Pause at `main()` until recording starts, for up to the given time, as in `waitstart=60s`. |
| `record` | `ondemand` | `ondemand` waits for JDK Mission Control. `startup` records from startup to exit, and needs `roots=` and `out=`. |

A mistake in the options stops the JVM at startup with a message that says what to fix.

## Build

From the repository root, as described in [Build](../../README.md#build):

```sh
nix develop .#agent --command mvn -B -pl modules/agent -am verify
```

This writes `target/verbatime-agent.jar` and runs the unit tests and end-to-end runs of the
packaged jar. [`../../bench/`](../../bench/) measures the agent's overhead and is run by hand.

## License

Verbatime is licensed under the [Apache License, Version 2.0](../../LICENSE).
Copyright 2026 yagipass. See [NOTICE](../../NOTICE) for attribution requirements when redistributing.
