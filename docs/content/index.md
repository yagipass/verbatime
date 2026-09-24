---
layout: entry
title: Verbatime
description: Records every Java method call under the methods you choose, and shows where the time went.
hero:
  text: Every call, as it happened
  tagline: Records every Java method call under the methods you choose, and shows where the time went.
  image:
    src: verbatime-banner.png
    alt: Verbatime — JVM execution, frame by frame.
    width: 960
    height: 320
  actions:
    - theme: brand
      text: Get Started
      link: quick-start.md
    - theme: alt
      text: View on GitHub
      link: https://github.com/yagipass/verbatime
features:
  - title: Record with the agent alone
    details: Record everything from startup until the JVM exits, to a file.
    link: quick-start.md#agent-alone
  - title: Record with JDK Mission Control
    details: Start and stop recording at any time while the application runs.
    link: jmc.md
  - title: Read from the command line
    details: Find a slow call with vbtm, a few hundred lines at a time.
    link: cli.md
  - title: Ask an AI agent
    details: Let an agent follow a slow request down to its cause.
    link: cli.md#with-an-ai-agent
---

Verbatime is for development. Run it on your machine to check the performance of a feature you
built, not in production.

## Why

To see where one request, one server startup, or one batch job spent its time, you need every
call it made, in order. A sampling profiler only sees some of them, and most other profilers merge
all requests into one average.

Verbatime records every call and shows it as it happened.

- **Every call.** Each run of a method you choose becomes one call tree, with the order and time
  of every call in it. Nothing is sampled or dropped.
- **Drop in.** Add one `-javaagent` flag. No code changes.
- **Cheap.** A recorded call costs about 35 ns.
- **Large recordings.** 100 million calls open smoothly.

![A Spring Boot startup recorded with Verbatime, shown as a timeline flame chart in JDK Mission Control](/images/flame-chart.png)
