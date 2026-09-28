---
description: Record a Java or Spring Batch job until the JVM exits, see how its time splits between reading, processing, and writing, and find the slow repetitions with vbtm.
---

# Finding why a batch job is slow

A batch job repeats the same work for many items. Verbatime records every repetition, so you can
see how the time splits between reading, processing, and writing, and which repetitions are slower
than the rest.

## Record

A batch job exits when it is done, so record with the [agent alone](../quick-start.md#agent-alone),
from startup until the JVM exits.

:::: steps

1. Run the job with the agent, and use the job's entry method as the root.

   ```sh
   java -javaagent:/path/to/verbatime-agent.jar=record=startup,roots=com.example.app.Job::run,out=job.vbtm \
        -jar job.jar
   ```

   When you cannot change the `java` command, set the same flag in `JAVA_TOOL_OPTIONS` for the job.
   For Spring Batch, these roots work:

   | Root | One session is |
   |---|---|
   | `org.springframework.batch.core.step.AbstractStep::execute` | One step |
   | `org.springframework.boot.SpringApplication::run` | The whole run, startup included |

2. When the JVM exits, `job.vbtm` is complete.

::::

## See how the time splits

```sh
vbtm sessions job.vbtm
```

```text
file: job.vbtm  status: complete  recorded: 2026-09-28T05:54:27.194+00:00
length: 7338.5872 ms  threads: 1  sessions: 1  calls: 128,829  methods: 17,604  gc: 14 pauses, 166.0000 ms
units: ms, 0.0001 ms = 1 tick of 100 ns
1 session, sorted by start, showing 1

id      start       dur    calls  depth  throws   gc_ms  thread  root
 1  7087.9063  250.6809  128,829     41   1,551  0.0000  main    AbstractStep.execute
```

This is a recording of the step in the
[`spring-batch`](https://github.com/yagipass/verbatime/tree/main/examples/spring-batch) example,
which processes five order lines in chunks of two. `--merge` puts the calls of the same method
under the same path on one line, so a loop becomes a few lines.

```sh
vbtm tree job.vbtm 1 --merge --depth 9 --floor 30ms
```

The call lines of the output are below. The header and the lines that sum up shorter calls are
left out.

```text
250.6809 1 1.0674 0 AbstractStep.execute [1.0]
236.1430 1 0.4202 1 ChunkOrientedStep.doExecute [1.8132]
235.6914 3 0.2109 2 TransactionOperations.executeWithoutResult [slowest 1.8141 185.3599]
235.4805 3 0.0751 3 TransactionTemplate.execute [slowest 1.8142 185.1614]
196.0145 3 0.0122 4 TransactionOperations.lambda$executeWithoutResult$0 [slowest 1.29214 150.9107]
196.0023 3 0.7005 5 ChunkOrientedStep.lambda$doExecute$0 [slowest 1.29215 150.9018]
193.6579 3 0.0059 6 ChunkOrientedStep.processNextChunk [slowest 1.32141 148.7058]
193.6483 3 0.1991 7 ChunkOrientedStep.processChunkSequentially [slowest 1.32143 148.6986]
103.4018 3 0.8831 8 ChunkOrientedStep.writeChunk [slowest 1.39566 99.9473]
93.4448 3 0.0170 9 ChunkOrientedStep.doWrite [slowest 1.41391 90.1045]
79.5870 3 0.0456 8 ChunkOrientedStep.processChunk [slowest 1.35928 38.9042]
74.1501 5 1.4543 9 ChunkOrientedStep.processItem [slowest 1.39510 17.8932]
```

Each line is the total time in ms, the number of calls, the self time, the depth, and the method,
with the id and time of the slowest call. The step ran three chunks, one transaction each. Writing
them took 103 ms, and processing the five items took 80 ms.

## Find the slow repetitions

The slowest `writeChunk` took 99.9 of the 103 ms. List every call to compare them.

```sh
vbtm find job.vbtm ChunkOrientedStep::writeChunk
```

```text
pattern: ChunkOrientedStep::writeChunk -> org.springframework.batch.core.step.item.ChunkOrientedStep.writeChunk(Lorg/springframework/batch/infrastructure/item/Chunk;Lorg/springframework/batch/core/step/StepContribution;)V  scope: the only session
matches: 3 calls, 103.4018 ms in total, where a call inside another call of the same method is not added again
sorted by dur, showing 3. start is ms from the session start

      id     start      dur    self  depth  method                        caller                                      flags
 1.39566   92.0260  99.9473  0.8394      8  ChunkOrientedStep.writeChunk  ChunkOrientedStep.processChunkSequentially
1.123010  224.7650   1.9703  0.0250      8  ChunkOrientedStep.writeChunk  ChunkOrientedStep.processChunkSequentially
1.124850  241.2989   1.4842  0.0187      8  ChunkOrientedStep.writeChunk  ChunkOrientedStep.processChunkSequentially
```

The first chunk's write took 100 ms, and the other two took 2 ms or less. Print the first one and
everything under it.

```sh
vbtm tree job.vbtm --at 1.39566 --floor 8ms
```

It loads classes, parses the SQL, and prepares the statement, work the later chunks do not repeat.
Class loading looks longer in a recording than it is without the agent. See
[Limitations](../limitations.md#class-loading-looks-slower-than-it-is).

A real job has many more items. Rank the methods by how often they were called to find work done
once per item, such as a query for each item.

```sh
vbtm hot job.vbtm 1 --by calls
```

## Next

- [Finding why a test is slow](./slow-test.md)
- [vbtm commands](../cli.md#commands)
