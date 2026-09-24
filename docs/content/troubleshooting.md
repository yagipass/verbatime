# Troubleshooting

The agent writes its messages to standard error, starting with `[verbatime]`.

## The JVM stops at startup

An agent option is invalid. The line starting with `[verbatime] WARN invalid agent arguments:`
says what to fix. See [Agent options](./agent/options.md#all-options).

## The Verbatime views do not appear

Check that `verbatime-jmc-plugin.jar` is in the `dropins` directory, then start JDK Mission Control
once with `-clean`. Do the same when a new version of the plugin does not take effect.

## JDK Mission Control cannot connect

The Control view shows `Connection failed` or `Connection timed out`.

- Check that the application runs with both the agent and the
  [JMX flags](./agent/setup.md#jmx-flags).
- For a container or another host, see
  [From a container or another host](./agent/setup.md#from-a-container-or-another-host).

## A recording has no sessions

- **The root did not run while recording.** Send the request or run the job after Start recording.
  A call already running when recording starts is not recorded.
- **The root name does not match.** A root whose dot stays yellow after its code has run matches
  no method. With `roots=`, the agent logs `[verbatime] WARN root ... never matched a loaded method`
  when the JVM exits. Check the class and method name, and write nested classes as `Outer$Inner`.

## No recording file after the JVM exits

- The directory in `out=` must exist. If not, the agent logs a warning at startup and records
  nothing.
- Without `out=`, the recording is deleted when the JVM exits. See
  [Limitations](./limitations.md).
- With Maven, Surefire must fork the tests. `forkCount=0` ignores `argLine`.
