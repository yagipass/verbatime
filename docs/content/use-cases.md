# Use cases

Pick the case closest to yours. Each says how to record, which root to use, and where to look.

## A slow request on a server

1. Record [with JDK Mission Control](./quick-start.md#with-jdk-mission-control), and send the
   request while recording.
2. Use the request entry point as the root, such as
   `org.springframework.web.servlet.DispatcherServlet::doDispatch`. See
   [Common roots](./agent/options.md#common-roots).
3. Find the slowest request with `vbtm sessions rec.vbtm --sort dur`, or in the flame chart.

## Slow startup

1. Add `waitstart=60s` to the agent, and use the startup method as the root, such as
   `org.springframework.boot.SpringApplication::run`.
2. Start the application. It waits at `main()` until you press Start recording in
   [JDK Mission Control](./jmc.md#record).
3. Follow the widest calls in the flame chart, or run `vbtm hot rec.vbtm`.

## A slow test

1. Record with the [agent alone](./quick-start.md#agent-alone), set in Surefire's `argLine` or
   Gradle's `jvmArgs`. See [Adding the agent](./agent/setup.md).
2. Use `org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute` as the root. Each
   test method becomes one session.
3. Find the slowest tests with `vbtm sessions tests.vbtm --sort dur`.

## A batch job

1. Record with the [agent alone](./quick-start.md#agent-alone), set in `JAVA_TOOL_OPTIONS`.
2. Use the job's entry method as the root.
3. Find where the time went with `vbtm hot job.vbtm`.

## Let an AI agent find the cause

Record in any of the ways above, [add the vbtm skill](./cli.md#with-an-ai-agent) to your agent,
and ask why it was slow.
