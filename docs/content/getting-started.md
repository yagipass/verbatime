# Getting Started

You need Java 25 or later for the application, and
[JDK Mission Control](https://github.com/openjdk/jmc) to view the results.

:::: steps

1. Download `verbatime-agent.jar` and `verbatime-jmc-plugin.jar` from the
   [GitHub releases page](https://github.com/yagipass/verbatime/releases).

2. Copy the plugin into the `dropins` directory of JDK Mission Control, and restart it.

   ::: code-group

   ```sh [macOS]
   cp verbatime-jmc-plugin.jar "/Applications/JDK Mission Control.app/Contents/Eclipse/dropins/"
   ```

   ```sh [Linux and Windows]
   cp verbatime-jmc-plugin.jar <jmc>/dropins/
   ```

   :::

3. Start your application with the agent and a JMX port.

   ```sh
   java -javaagent:/path/to/verbatime-agent.jar \
        -Dcom.sun.management.jmxremote.port=7091 -Dcom.sun.management.jmxremote.rmi.port=7091 \
        -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
        -Djava.rmi.server.hostname=localhost \
        -cp ... your.Main
   ```

4. In JDK Mission Control, choose `Window > Verbatime`. In the Verbatime Control view, connect
   to `localhost:7091`.

5. Under Instrumentation roots, add the methods to measure.

   ![The Verbatime Control view, connected to localhost:7091 with two roots](/images/control-view.png)

6. Press Start recording, use the application, then press Stop recording.

::::

The recording opens as a timeline flame chart. Click a call to see its details, the calls under
it, and who called it.

![The timeline flame chart of a recording](/images/viewer-overview.png)

::: tip
To try it without your own application, the [examples](./examples.md) have ready-to-run setups
for Spring Boot, Tomcat, Quarkus, a batch job, JUnit runs, and more.
:::

## Next steps

- [Recording without JDK Mission Control](./guide/recording-without-jmc.md), for test runs and
  batch jobs
- [Reading a recording with vbtm](./guide/reading-with-vbtm.md), from the command line
- [Agent options](./reference/agent-options.md)
