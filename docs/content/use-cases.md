---
description: Guides to find why a request, startup, a test, or a batch job is slow in a Java application, each with how to record, which root to use, and real vbtm output.
---

# Use cases

Pick the case closest to yours. Each page shows how to record, which root to use, and how to read
the recording, with real output from the [examples](./examples.md).

| Case | Root | Record with |
|---|---|---|
| [A slow request](./use-cases/slow-request.md) | The request entry point, such as `DispatcherServlet::doDispatch` | JDK Mission Control |
| [Slow startup](./use-cases/slow-startup.md) | The startup method, such as `SpringApplication::run` | JDK Mission Control and `waitstart=` |
| [A slow test](./use-cases/slow-test.md) | `TestMethodTestDescriptor::execute` | The agent alone |
| [A slow batch job](./use-cases/batch-job.md) | The job's entry method | The agent alone |

## Let an AI agent find the cause

Record in any of the ways above, [add the vbtm skill](./cli.md#with-an-ai-agent) to your agent,
and ask why it was slow.
