---
description: Record every JUnit test method as its own call tree in Maven or Gradle, find the slowest tests by name with vbtm, and see where their time went.
---

# Finding why a test is slow

Verbatime records each JUnit test method as its own session, including the extensions,
`@BeforeEach`, and `@AfterEach` around it. You can find the slowest tests, then see what each one
spent its time on.

## Record

A test JVM exits as soon as the tests are done, so record with the
[agent alone](../quick-start.md#agent-alone), from startup until the JVM exits.

:::: steps

1. Add the agent to the test JVM, with `TestMethodTestDescriptor::execute` as the root.

   ::: code-group

   ```xml [Maven (pom.xml)]
   <plugin>
     <artifactId>maven-surefire-plugin</artifactId>
     <configuration>
       <forkCount>1</forkCount>
       <reuseForks>true</reuseForks>
       <argLine>-javaagent:/path/to/verbatime-agent.jar=record=startup,roots=org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute,out=/path/to/tests.vbtm</argLine>
     </configuration>
   </plugin>
   ```

   ```kotlin [Gradle (build.gradle.kts)]
   tasks.test {
       maxParallelForks = 1
       forkEvery = 0
       jvmArgs(
           "-javaagent:/path/to/verbatime-agent.jar=record=startup," +
               "roots=org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute," +
               "exclude=org.gradle+worker.org.gradle," +
               "out=/path/to/tests.vbtm",
       )
   }
   ```

   :::

   - Run the tests in one forked JVM. Several JVMs would write the same file over each other.
   - Give `out=` an absolute path, because the test JVM may run in another directory.
   - With Gradle, `exclude=` leaves out Gradle's own classes, which reject being instrumented.

2. Run the tests. When the test JVM exits, `tests.vbtm` is complete.

::::

[Adding the agent](../agent/setup.md) shows where the agent goes for other runners.

## Find the slowest tests

```sh
vbtm sessions tests.vbtm --sort dur
```

```text
file: tests.vbtm  status: complete  recorded: 2026-09-28T05:53:28.000+00:00
length: 385.8782 ms  threads: 1  sessions: 2  calls: 1,601  methods: 4,495  gc: 9 pauses, 11.0000 ms
units: ms, 0.0001 ms = 1 tick of 100 ns
2 sessions, sorted by dur, showing 2

id     start      dur  calls  depth  throws   gc_ms  thread  root
 1  341.9746  22.4053    854     39       1  2.0000  main    TestMethodTestDescriptor.execute
 2  369.1580  16.7202    747     29       0  0.0000  main    TestMethodTestDescriptor.execute
```

This is a recording of the two tests in the
[`junit-maven`](https://github.com/yagipass/verbatime/tree/main/examples/junit-maven) example.
Every session has the same root, so find the test methods by name. Give `find` part of the test
class or package name, and `--all` to list every method that matches.

```sh
vbtm find tests.vbtm OrderServiceTest --all
```

```text
pattern: OrderServiceTest -> io.github.yagipass.verbatime.examples.junit.maven.OrderServiceTest.setUp()V, io.github.yagipass.verbatime.examples.junit.maven.OrderServiceTest.placesOrder()V, io.github.yagipass.verbatime.examples.junit.maven.OrderServiceTest.rejectsOutOfStock()V, io.github.yagipass.verbatime.examples.junit.maven.OrderServiceTest.lambda$rejectsOutOfStock$0()V  scope: all 2 sessions
matches: 5 calls, 25.0927 ms in total, where a call inside another call of the same method is not added again
sorted by dur, showing 5. start is ms from the session start

   id    start      dur    self  depth  method                                       caller                        flags
2.643   0.3902  16.2276  0.0180     21  OrderServiceTest.placesOrder                 ReflectionUtils.invokeMethod
1.768  13.8426   8.1208  3.1366     21  OrderServiceTest.rejectsOutOfStock           ReflectionUtils.invokeMethod
1.772  17.1697   4.7902  0.0050     25  OrderServiceTest.lambda$rejectsOutOfStock$0  AssertThrows.assertThrows#4   !OutOfStockException
1.533   8.4614   0.7412  0.7412     25  OrderServiceTest.setUp                       ReflectionUtils.invokeMethod
2.440   0.2573   0.0031  0.0031     25  OrderServiceTest.setUp                       ReflectionUtils.invokeMethod

names printed alike:
  AssertThrows.assertThrows#4 = org.junit.jupiter.api.AssertThrows.assertThrows(Ljava/lang/Class;Lorg/junit/jupiter/api/function/Executable;Ljava/lang/Object;)Ljava/lang/Throwable;
```

The number before the dot in an id is the session. Session 1 is `rejectsOutOfStock`, and session 2
is `placesOrder`.

The session is longer than the test method, because it also holds JUnit's work around the test.
Session 1 took 22.4 ms, but `rejectsOutOfStock` itself took 8.1 ms. Here the difference is 14 ms
in the first test and under 1 ms in the second, so a test that runs first can look slower than it
is.

`!OutOfStockException` marks a call that ended by throwing. `rejectsOutOfStock` expects that
exception with `assertThrows`.

## See where the time went

```sh
vbtm hot tests.vbtm 2 --limit 5
```

```text
scope: session 2, 16.7202 ms in root calls, 747 calls, 178 methods
sorted by self, showing 5. units: ms

self_ms  self%  total_ms  calls  method
11.6129  69.5%   11.6129      2  Work.io
 1.1721   7.0%    1.1721      4  Work.cpu
 1.0122   6.1%   14.7718      1  OrderService.placeOrder
 0.9607   5.7%    9.6178      1  PaymentGateway.charge
 0.7730   4.6%    1.2142      1  Assertions.assertEquals#91

names printed alike:
  Assertions.assertEquals#91 = org.junit.jupiter.api.Assertions.assertEquals(Ljava/lang/Object;Ljava/lang/Object;)V
# 173 more methods. next: vbtm hot tests.vbtm 2 --limit 15
```

`placesOrder` spends most of its time in `Work.io`, the example's simulated I/O, called from the
code under test. To see which call made it, print the test method and everything under it.

```sh
vbtm tree tests.vbtm --at 2.643
```

## Next

- [Finding why a batch job is slow](./batch-job.md)
- [`junit-maven`](https://github.com/yagipass/verbatime/tree/main/examples/junit-maven) and
  [`junit-gradle`](https://github.com/yagipass/verbatime/tree/main/examples/junit-gradle), with
  more roots to try
