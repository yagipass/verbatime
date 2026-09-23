# Recording without JDK Mission Control

A test run or a batch job may finish before you can press Start recording. With `record=startup`,
the agent records from startup until the JVM exits and writes the recording to a file. No JMX port
is needed.

```text
-javaagent:/path/to/verbatime-agent.jar=record=startup,roots=com.example.app.Job::run,out=trace.vbtm
```

- `record=startup` records from before `main()` until the JVM exits.
- `roots=` names the methods to record.
- `out=` is the file to write. Each run overwrites it.

Open the file in JDK Mission Control with `File > Open`, or read it with
[vbtm](./reading-with-vbtm.md).

## Where to put -javaagent

| Runner | Where |
|---|---|
| Fat jar, or plain `java` | `JAVA_TOOL_OPTIONS` |
| Maven Surefire | `argLine` |
| Gradle | `tasks.test.jvmArgs` |
| Tomcat | `CATALINA_OPTS` |
| Jetty | `JAVA_OPTIONS` |
| WildFly | `JAVA_OPTS` |
| Open Liberty | `jvm.options` |

The same places work for the JMX flags in
[Recording with JDK Mission Control](./recording-with-jmc.md).
