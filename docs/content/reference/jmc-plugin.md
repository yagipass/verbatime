# JMC plugin

The JDK Mission Control plugin starts and stops recordings, and shows them as a timeline flame
chart. For how to record, see [Recording with JDK Mission Control](../guide/recording-with-jmc.md).

## Install

Download `verbatime-jmc-plugin.jar` from the
[GitHub releases page](https://github.com/yagipass/verbatime/releases), copy it into the `dropins`
directory of JDK Mission Control, and restart it.

::: code-group

```sh [macOS]
cp verbatime-jmc-plugin.jar "/Applications/JDK Mission Control.app/Contents/Eclipse/dropins/"
```

```sh [Linux and Windows]
cp verbatime-jmc-plugin.jar <jmc>/dropins/
```

:::

To uninstall, delete the file and restart. If a new jar does not take effect, start JDK Mission
Control once with `-clean`.

## Views

Choose `Window > Verbatime` to show the views. When a recording opens, JDK Mission Control
switches to the Verbatime perspective.

![The timeline flame chart, zoomed in to a few hundred microseconds](/images/viewer-overview.png)

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
