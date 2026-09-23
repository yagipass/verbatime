# Agent options

Options go after `=` in the `-javaagent` flag, as `key=value` pairs separated by `,`. All are
optional. Separate several values for one key with `+`.

```text
-javaagent:/path/to/verbatime-agent.jar=include=com.example.app,roots=com.example.app.Job::run
```

| Key | Default | Description |
|---|---|---|
| `include` | everything | Packages or classes to instrument, as in `include=com.example.app`. The JDK and the agent are never instrumented. Wildcards such as `com.example.*` are not accepted. |
| `exclude` | none | Packages or classes to leave out of `include`. |
| `roots` | none | Root methods, as in `roots=com.example.app.Job::run`. JDK Mission Control can still change them. |
| `out` | none | Write the recording to this file. The directory must exist. |
| `spool` | temp dir | Where to keep a recording until JDK Mission Control reads it, without `out=`. |
| `waitstart` | off | Pause at `main()` until recording starts, for up to the given time, as in `waitstart=60s`. |
| `record` | `ondemand` | `ondemand` waits for JDK Mission Control. `startup` records from startup to exit, and needs `roots=` and `out=`. |

An invalid option stops the JVM at startup with a message that says what to fix.
