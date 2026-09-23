# junit-gradle

The same two JUnit 6 tests as [`junit-maven`](../junit-maven/), run by Gradle 9.7 with the agent
attached to the test worker JVM. The unit of work is one test method, or one whole test run. The
worker exits as soon as the tests are done, too fast for anyone to start a recording by hand, so the
agent records the run by itself with `record=startup` and writes it to a file on the host, which is
then opened in JMC from disk. No JMX and no JMC are involved while the tests run.

## Run

```sh
docker compose run --rm --build test
```

The test worker starts recording in the agent's `premain`, the two tests run in well under a second,
and the agent's shutdown hook closes the recording as the worker exits, leaving it in the file
below, as described in
[Examples whose JVM exits on its own](../README.md#examples-whose-jvm-exits-on-its-own):

```text
recordings/junit-gradle.vbtm
```

A second run in the same container would find the `test` task up to date and start no worker,
which is why `docker compose run --rm` starts from a fresh container each time. `docker compose down -v` also
removes the `gradle-home` volume that caches Gradle's home directory.

## Agent settings

Set in [`build.gradle.kts`](build.gradle.kts) as `tasks.test.jvmArgs`:

```text
-javaagent:/work/verbatime-agent.jar=record=startup,roots=org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute,exclude=org.gradle+worker.org.gradle,out=/work/recordings/junit-gradle.vbtm
```

| Setting | Value |
|---|---|
| Instrumented classes | Everything except the JDK and Gradle's own packages, which `exclude=org.gradle+worker.org.gradle` leaves out. That covers the JUnit Platform and Jupiter engine, the workload, and the test class. Gradle's worker validates its internal service methods by annotation and rejects the `name$trace` method bodies the agent creates, so Gradle itself is left uninstrumented |
| `record=startup` | Records from the agent's `premain` until the worker exits, with no JMX control registered. `roots=` and `out=` are required in this mode |
| `roots=…TestMethodTestDescriptor::execute` | One tree per test method. The root is resolved when the Jupiter engine class is loaded, which is well after `premain` |
| `out=/work/recordings/junit-gradle.vbtm` | Writes the recording to `./recordings/` on the host through a bind mount. The default spool would be deleted when the worker exits |
| `maxParallelForks = 1`, `forkEvery = 0` | One worker JVM for the whole run. More workers would truncate the same file |

`tasks.test.jvmArgs` is the only knob that reaches the worker: `GRADLE_OPTS`, `JAVA_TOOL_OPTIONS`,
and `org.gradle.jvmargs` reach the Gradle client and daemon JVMs instead, which would then record
their own run over the same file. On the host, `gradle test` runs the tests without the agent,
because the `jvmArgs` are added only where `/work/verbatime-agent.jar` exists, and the workload is
compiled in from `../workload/src/main/java`, so no Maven install is needed.

## Roots to try

Edit `roots=` in [`build.gradle.kts`](build.gradle.kts) and run the command again.

| Root | What one tree covers |
|---|---|
| `org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute` | One test method on the `Test worker` thread: extensions, `@BeforeEach`, the test itself with the workload, and `@AfterEach`. The `rejectsOutOfStock` tree carries the `OutOfStockException` thrown in `InventoryRepository.reserve` under `assertThrows`. The default here, and it records two trees per run. |
| `org.junit.platform.launcher.core.EngineExecutionOrchestrator::execute` | The whole test run in one tree: engine and test discovery results, the test class, both test methods, and the listeners. |

Several roots at once are written with `+`, as in `roots=a.B::m+c.D::n`.

## Driving it from JMC instead

To pick roots interactively, drop `record=startup` and `roots=` and use the default
`record=ondemand` with `waitstart=60s` and the JMX flags, as the server examples do. The worker
then pauses at `worker.org.gradle.process.internal.worker.GradleWorkerMain.main` until JMC starts a
recording, and port 7091 has to be published with `docker compose run --service-ports`.

## Tests

The two tests are the ones listed in [`junit-maven`](../junit-maven/README.md#tests).
