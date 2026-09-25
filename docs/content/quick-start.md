---
description: Download the Verbatime agent and record a Java run, with the agent alone until the JVM exits or from JDK Mission Control at any time.
---

# Quick start

Record a run on your development machine and see where the time went. Download
`verbatime-agent.jar` from the [GitHub releases page](https://github.com/yagipass/verbatime/releases).
To check that it was built by this repository's GitHub Actions, verify it with the
[GitHub CLI](https://cli.github.com/).

```sh
gh attestation verify verbatime-agent.jar --repo yagipass/verbatime
```

Then choose to record with the [agent alone](#agent-alone) or with
[JDK Mission Control](#with-jdk-mission-control).

## Requirements

| Part | Needs |
|---|---|
| [Agent](./agent/setup.md) | Java 25 or later for the application |
| [JMC plugin](./jmc.md) | [JDK Mission Control](https://github.com/openjdk/jmc) |
| [vbtm CLI](./cli.md) | macOS on Apple silicon or Linux, or Java 17 or later for the jar |

## Agent alone

Records everything from startup until the JVM exits.

:::: steps

1. Run the application with the agent. `roots=` names the method to record, and `out=` the file
   to write.

   ```sh
   java -javaagent:/path/to/verbatime-agent.jar=record=startup,roots=com.example.app.OrderService::placeOrder,out=trace.vbtm \
        -jar app.jar
   ```

2. When the JVM exits, read `trace.vbtm` with [vbtm](./cli.md), or open it in
   [JDK Mission Control](./jmc.md) with `File > Open`.

   ```sh
   vbtm sessions trace.vbtm --sort dur
   ```

::::

## With JDK Mission Control

Starts and stops recording at any time while the application runs.

:::: steps

1. Copy `verbatime-jmc-plugin.jar` into the `dropins` directory of JDK Mission Control, and
   restart it. See [Install](./jmc.md#install).

2. Start the application with the agent and a JMX port.

   ```sh
   java -javaagent:/path/to/verbatime-agent.jar \
        -Dcom.sun.management.jmxremote.port=7091 -Dcom.sun.management.jmxremote.rmi.port=7091 \
        -Dcom.sun.management.jmxremote.host=127.0.0.1 \
        -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
        -Djava.rmi.server.hostname=localhost \
        -jar app.jar
   ```

   ::: warning
   These flags turn off JMX authentication.
   :::

3. In JDK Mission Control, choose `Window > Verbatime`. Connect to `localhost:7091`, and add the
   methods to record under Instrumentation roots.

4. Press Start recording, use the application, then press Stop recording.

::::

The recording opens as a timeline flame chart.

![The timeline flame chart of a recording](/images/viewer-overview.webp)

## Next

- [Adding the agent](./agent/setup.md) to Maven, Gradle, Tomcat, and more
- [Agent options](./agent/options.md), including which roots to choose
- [Use cases](./use-cases.md)
