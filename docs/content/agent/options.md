# Agent options

Options go after `=` in the `-javaagent` flag, as `key=value` pairs separated by `,`. Separate
several values for one key with `+`.

```text
-javaagent:/path/to/verbatime-agent.jar=include=com.example.app,roots=com.example.app.Job::run
```

## Recording modes

| Mode | Options | Records |
|---|---|---|
| On demand | none | From Start recording to Stop recording in JDK Mission Control, at any time |
| On demand, from startup | `waitstart=60s` | The same, but the JVM waits at `main()` for up to 60 seconds until you press Start recording |
| Startup | `record=startup`, `roots=`, `out=` | Everything from before `main()` until the JVM exits. No JMX needed |

## Choosing roots

A root is a method whose calls you want to see. Each run of a root on one thread becomes one
session: a call tree of everything it called on that thread.

- Work handed to another thread is not in the session. Make the method that runs there a root too.
- A root called inside another session is part of that session, not a new one.
- A call already running when recording starts is not recorded. To record startup, use
  `waitstart=` or `record=startup`.

Write a root as `pkg.Class::method`, which matches every overload. For one overload, add its JVM
descriptor, as in `pkg.Class::method(Ljava/lang/String;)V`. Write nested classes as
`Outer$Inner`. Constructors cannot be roots.

### Common roots

| To record | Root |
|---|---|
| Each Spring MVC request | `org.springframework.web.servlet.DispatcherServlet::doDispatch` |
| Each request on Tomcat | `org.apache.catalina.connector.CoyoteAdapter::service` |
| Spring Boot startup | `org.springframework.boot.SpringApplication::run` |
| Each JUnit 5 test method | `org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute` |

The [examples](../examples.md) have roots for more frameworks.

## Choosing what to instrument

By default, every class except the JDK and the agent is instrumented, frameworks included. If the
application runs too slowly, narrow it.

- `include=com.example.app` instruments only that package or class.
- `exclude=com.example.app.generated` leaves a part of it out.

Calls into classes that are not instrumented are not recorded. Their time counts as self time of
the nearest recorded caller. Roots must be in instrumented classes.

## All options

All are optional.

| Key | Default | Description |
|---|---|---|
| `include` | everything | Packages or classes to instrument, as in `include=com.example.app`. Wildcards such as `com.example.*` are not accepted. |
| `exclude` | none | Packages or classes to leave out of `include`. |
| `roots` | none | Root methods, as in `roots=com.example.app.Job::run`. JDK Mission Control can still change them. |
| `out` | none | Write the recording to this file. The directory must exist. |
| `spool` | temp dir | Where to keep a recording until JDK Mission Control reads it, without `out=`. |
| `waitstart` | off | Pause at `main()` until recording starts, for up to the given time, as in `waitstart=60s`. |
| `record` | `ondemand` | `ondemand` waits for JDK Mission Control. `startup` records from startup to exit, and needs `roots=` and `out=`. |

An invalid option stops the JVM at startup with a message that says what to fix.
