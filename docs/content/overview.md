---
description: Why Verbatime records every Java method call in order instead of sampling, and what that costs, from one -javaagent flag to about 35 ns per recorded call.
---

# What is Verbatime?

Verbatime is for development. Run it on your machine to check the performance of a feature you
built, not in production.

## Why

To see where one request, one server startup, or one batch job spent its time, you need every
call it made, in order. A sampling profiler only sees some of them, and most other profilers merge
all requests into one call tree or flame graph.

Verbatime records every call and shows it as it happened.

- **Every call.** Each run of a method you choose becomes one call tree, with the order and time
  of every call in it. Nothing is sampled or dropped.
- **Drop in.** Add one `-javaagent` flag. No code changes.
- **Cheap.** A recorded call costs about 35 ns.
- **Large recordings.** 100 million calls open smoothly.

![A Spring Boot startup recorded with Verbatime, shown as a timeline flame chart in JDK Mission Control](/images/flame-chart.webp)
