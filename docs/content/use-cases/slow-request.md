---
description: Record each request to a Java server as its own call tree, find the slowest requests with vbtm, and compare them with a typical one to see where the time went.
---

# Finding why a request is slow

Verbatime records each request as its own session, with every call it made. You can pick out the
slowest requests and compare them with a typical one, instead of reading an average over all
requests.

## Record

:::: steps

1. Start the server with the agent and a JMX port, and connect JDK Mission Control, as in
   [Quick start](../quick-start.md#with-jdk-mission-control).

2. Under Instrumentation roots, add the method that handles each request.

   | Server | Root |
   |---|---|
   | Spring MVC | `org.springframework.web.servlet.DispatcherServlet::doDispatch` |
   | Tomcat | `org.apache.catalina.connector.CoyoteAdapter::service` |

   The [examples](../examples.md) have roots for more frameworks.

3. Press Start recording, send the requests, then press Stop recording.

::::

JDK Mission Control saves the recording as a `.vbtm` file in the directory shown in its
[settings](../jmc.md#settings). The commands below read it as `rec.vbtm`.

## Find the slowest requests

```sh
vbtm sessions rec.vbtm --root DispatcherServlet::doDispatch --sort dur --limit 5
```

```text
file: rec.vbtm  status: complete  recorded: 2026-09-28T05:51:09.347+00:00
length: 25376.0537 ms  threads: 11  sessions: 5,818  calls: 20,230,812  methods: 30,331  gc: 31 pauses, 104.0000 ms
units: ms, 0.0001 ms = 1 tick of 100 ns
5,817 of 5,818 sessions match, sorted by dur, showing 5

id       start      dur    calls  depth  throws   gc_ms  thread                root
11  15206.8327  92.5098   21,929     44     139  0.0000  http-nio-8080-exec-7  DispatcherServlet.doDispatch
 5  15206.8326  92.4852   43,531     41     362  0.0000  http-nio-8080-exec-5  DispatcherServlet.doDispatch
12  15206.8840  92.4033   10,617     44      28  0.0000  http-nio-8080-exec-6  DispatcherServlet.doDispatch
 9  15207.1457  92.0460   18,653     43     100  0.0000  http-nio-8080-exec-4  DispatcherServlet.doDispatch
 6  15207.6754  91.5391  103,821     41     981  0.0000  http-nio-8080-exec-9  DispatcherServlet.doDispatch
# 5,812 more sessions. next: vbtm sessions rec.vbtm --root DispatcherServlet::doDispatch --sort dur --limit 15
```

This is a recording of the
[`spring-boot-mvc`](https://github.com/yagipass/verbatime/tree/main/examples/spring-boot-mvc)
example under load. It also recorded startup, so `--root` keeps only the requests.

- `dur` is how long the request took, and `calls` how many calls it made.
- `throws` counts the exceptions thrown during the request.
- `gc_ms` is the time the request spent stopped for garbage collection. When it is large, the
  request was slow because of GC, not because of its own code.

Here, the slowest requests all started at the same moment, when the load began. Nine in ten
requests took under 14 ms.

## See where the time went

Rank the methods of the slowest request by the time spent in their own code.

```sh
vbtm hot rec.vbtm 11 --limit 5
```

```text
scope: session 11, 92.5098 ms in root calls, 21,929 calls, 1,586 methods
sorted by self, showing 5. units: ms

self_ms  self%  total_ms  calls  method
27.1916  29.4%   29.2256     73  JarUrlClassLoader.loadClass
16.4770  17.8%   16.4770      4  Work.cpu
11.6996  12.6%   11.6996      2  Work.io
 6.7461   7.3%    7.3015      2  ServletRequestDataBinderFactory.createBinderInstance
 6.2964   6.8%    9.2932      1  BasicSerializerFactory.findSerializerByPrimaryType
# 1,581 more methods. next: vbtm hot rec.vbtm 11 --limit 15
```

Then do the same for a typical request.

```sh
vbtm hot rec.vbtm 3871 --limit 5
```

```text
scope: session 3871, 12.7478 ms in root calls, 1,900 calls, 776 methods
sorted by self, showing 5. units: ms

self_ms  self%  total_ms  calls  method
12.2574  96.2%   12.2574      2  Work.io
 0.0750   0.6%    0.0750      4  Work.cpu
 0.0277   0.2%   12.3675      1  OrderService.placeOrder
 0.0099   0.1%   12.3779      1  InvocableHandlerMethod.doInvoke
 0.0055   0.0%    8.8093      1  PaymentGateway.charge
# 771 more methods. next: vbtm hot rec.vbtm 3871 --limit 15
```

`Work.cpu` and `Work.io` are the example's own CPU work and simulated I/O. In the typical request,
almost all the time is the I/O. In the slowest request, the same `Work.cpu` calls took 16 ms
instead of 0.08 ms, and much of the time went to loading classes and to setting up request
binding and JSON output for the first time. The first requests after startup pay for this once.

Class loading looks longer in a recording than it is without the agent. See
[Limitations](../limitations.md#class-loading-looks-slower-than-it-is).

## Follow the calls

`hot` shows which methods took the time. To see the calls in the order they ran, print the
session's call tree, then one call and everything under it.

```sh
vbtm tree rec.vbtm 11
vbtm tree rec.vbtm --at 11.1340
```

In JDK Mission Control, select the session in the recording to show its flame chart, then click a
call. See [View](../jmc.md#view).

## Next

- [Finding why startup is slow](./slow-startup.md)
- [vbtm commands](../cli.md#commands)
