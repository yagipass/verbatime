# Recording with JDK Mission Control

Install the [JMC plugin](../reference/jmc-plugin.md), then start the application with the agent
and a JMX port.

```sh
java -javaagent:/path/to/verbatime-agent.jar \
     -Dcom.sun.management.jmxremote.port=7091 -Dcom.sun.management.jmxremote.rmi.port=7091 \
     -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
     -Djava.rmi.server.hostname=localhost \
     -cp ... your.Main
```

## Record

Choose `Window > Verbatime`, then in the Verbatime Control view:

1. Enter `localhost:7091` and press Connect.
2. Under Instrumentation roots, add the methods to measure.
3. Press Start recording, use the application, then press Stop recording.

![The Verbatime Control view, connected to localhost:7091 with two roots](/images/control-view.png)

The recording is saved on your machine and opens right away.

::: tip
To record startup too, add `waitstart=60s` to the agent. The application waits at `main()` until
you press Start recording, for up to 60 seconds.
:::

## View

![The Verbatime Recordings view, listing the recordings saved on this machine](/images/recordings-view.png)

Open a recording from the Verbatime Recordings view, or with `File > Open`. Select a session to
show its flame chart, then click a call to see its details, the calls under it, and who called it.
See [Views](../reference/jmc-plugin.md#views).
