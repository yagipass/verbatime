---
description: Install the Verbatime plugin for JDK Mission Control, start and stop recordings, and read them as a timeline flame chart.
---

# JMC plugin

The JDK Mission Control plugin starts and stops recordings, and shows them as a timeline flame
chart.

## Install

Download `verbatime-jmc-plugin.jar` from the
[GitHub releases page](https://github.com/yagipass/verbatime/releases). To check that it was built
by this repository's GitHub Actions, verify it with the [GitHub CLI](https://cli.github.com/).

```sh
gh attestation verify verbatime-jmc-plugin.jar --repo yagipass/verbatime
```

Copy it into the `dropins` directory of JDK Mission Control, and restart it.

::: code-group

```sh [macOS]
cp verbatime-jmc-plugin.jar "/Applications/JDK Mission Control.app/Contents/Eclipse/dropins/"
```

```sh [Linux and Windows]
cp verbatime-jmc-plugin.jar <jmc>/dropins/
```

:::

To uninstall, delete the file and restart.

## Record

Start the application with the agent and the [JMX flags](./agent/setup.md#jmx-flags). Then choose
`Window > Verbatime`, and in the Verbatime Control view:

1. Enter `localhost:7091` and press Connect.
2. Under Instrumentation roots, type part of a method name and pick a match, or type
   `pkg.Class::method`. A green dot means the root is instrumented. A yellow dot means its class
   has not loaded yet.
3. Press Start recording, use the application, then press Stop recording.

<img src="/images/control-view.png" width="423" alt="The Verbatime Control view, connected to localhost:7091 with two roots">

The recording is saved on your machine and opens right away. Roots cannot be changed while
recording.

## View

<img src="/images/recordings-view.png" width="421" alt="The Verbatime Recordings view, listing the recordings saved on this machine">

Open a recording from the Verbatime Recordings view, or with `File > Open`. Select a session to
show its flame chart, then click a call.

| View | Shows |
|---|---|
| Verbatime Control | Connects to an application, sets the roots, and starts and stops recording. |
| Verbatime Recordings | The recordings saved on your machine. |
| Verbatime Call Details | The selected call and its callers, root first. |
| Verbatime Top-down | The call tree under the selected call, heaviest child first. |
| Verbatime Bottom-up | Who called each method under the selected call. |

![The Verbatime Call Details view, showing the selected call and its ancestors](/images/call-views.png)

To find a method, type part of its name in Search methods (`⌘F` on macOS). Calls that match stay
in color, and the rest are dimmed.

![Searching for methods named has, with the matching calls in color](/images/search.png)

## Settings

Under `Window > Preferences > Mission Control > Verbatime`, you can change the directory where
recordings are saved.
