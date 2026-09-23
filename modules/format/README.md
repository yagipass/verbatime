# verbatime-format

## Why

Verbatime records every call, so the agent writes an event every few dozen nanoseconds and a
recording can hold 100 million events. The file is shaped by what that takes.

- **Append only.** The agent only ever adds bytes at the end and never seeks back, so writing
  is cheap and a file that stops mid-record is readable up to the cut.
- **Small events.** Each event is a few bytes. It holds a varint time delta, plus a method id
  for ENTER or an exception id for an EXIT by exception. Names are written once, in THREAD, CLASS
  and EXCEPTION records.
- **One chunk per thread.** Each thread fills its own chunk without locking, and the file is
  locked only to append a finished chunk. The reader can follow one thread and skip the rest.
- **Exact time.** Ticks are 100 ns, and the ANCHOR record maps tick 0 to wall-clock time.

## Usage

The module has no dependencies and runs on Java 17 or later.

### Reading a recording

`TraceReader` walks the records and calls a `Visitor` for each one. Override only the records
you care about. Inside a chunk, `EventCursor` decodes the events one at a time, and any result
other than ENTER or EXIT means the chunk has ended or is incomplete or corrupt.

```java
final Map<Integer, String> methods = new HashMap<>();
final EventCursor cursor = new EventCursor();
TraceReader.read(Files.readAllBytes(Path.of("trace.vbtm")), new TraceReader.Visitor() {
    @Override
    public void clazz(final long baseId, final String className, final String[] sigs) {
        for (int k = 0; k < sigs.length; k++) {
            methods.put((int) baseId + k, className + "." + sigs[k]);
        }
    }

    @Override
    public void chunk(final long tid, final long baseTicks, final byte[] bytes, final int off,
            final int len, final boolean sessionEnd, final boolean truncated) {
        cursor.reset(bytes, off, len, baseTicks);
        while (true) {
            final EventCursor.Event e = cursor.next();
            if (e == EventCursor.Event.ENTER) {
                System.out.println(tid + " " + cursor.ticks() + " enter " + methods.get(cursor.methodId()));
            } else if (e == EventCursor.Event.EXIT) {
                System.out.println(tid + " " + cursor.ticks() + " exit");
            } else {
                break;
            }
        }
    }
});
```

`read` returns `CLEAN` for a file that ends with the END record, or `TRUNCATED` for one that stops
before it, and throws `CorruptTraceException` for a malformed one. `FrameStack` pairs ENTER and
EXIT events into calls with a start, a duration, and a self time.

### Writing a recording

`TraceBuilder` builds a file in memory, and `TraceBuilder.Payload` builds the events of one chunk
from absolute ticks. Every method id used in a chunk must be declared by a CLASS record. Both are
thin wrappers over `RecordEncoder` and `EventEncoder`. `RecordEncoder` returns each record as a new
array, while `RecordEncoder.chunkHeader` and `EventEncoder` encode into a caller-supplied array. The
agent writes straight to a file with those.

In the example below, `enter` and `exit` take ticks of 100 ns, and `enter` also takes a method id.
The CLASS record declares methods 0 and 1, and the chunk belongs to thread 1, starts at tick 0,
and is the last chunk of its session.

```java
final TraceBuilder.Payload events = new TraceBuilder.Payload(0)
        .enter(0, 0).enter(10, 1).exit(510).exit(520);
final byte[] trace = new TraceBuilder(System.currentTimeMillis(), 0)
        .thread(1, "main")
        .clazz(0, "Main", "run()V", "work()V")
        .chunk(1, 0, events.bytes(), true)
        .end()
        .bytes();
Files.write(Path.of("trace.vbtm"), trace);
```

Read back with the example above, the file prints:

```text
1 0 enter Main.run()V
1 10 enter Main.work()V
1 510 exit
1 520 exit
```

## The vbtm format

A fixed 18-byte header, then records appended in the order the agent produced them. The header is
the magic, the version byte and the ANCHOR record. A file that stops mid-record is truncated, not
corrupt, so a recording is readable while it is being written.

```text
┌────────┬─────────┬──────────┬────────┬────────┬─────┬─────┐
│ "vbtm" │ version │ ANCHOR   │ record │ record │ ... │ END │
│ 4 B    │ 1 B     │ 13 B     │        │        │     │ 1 B │
└────────┴─────────┴──────────┴────────┴────────┴─────┴─────┘
```

The version byte is 1. Each record is a type byte and a payload. `varint` is an unsigned LEB128
integer of at most 10 bytes, the same as a Protocol Buffers varint. `string` is `varint length`
followed by that many bytes of UTF-8.

| Type | Record | Payload |
|---|---|---|
| `0x06` | ANCHOR | `int64 startEpochMs`, `int32 utcOffsetSeconds`, big-endian. Wall-clock time at tick 0. |
| `0x01` | THREAD | `varint tid`, `string name` |
| `0x04` | CLASS | `varint baseId`, `varint count`, `string className`, `count × string sig`. Method `baseId + k` is `className.sig[k]`, as in `Foo.run()V`. A `sig` is a method name followed by its descriptor. |
| `0x07` | EXCEPTION | `varint id`, `string className`. Ids start at 1. |
| `0x08` | GC | `varint startTicks`, `varint durTicks`, `varint action`, `string collector`, `string cause`. `action` is 0 for unknown, 1 for minor, 2 for major. |
| `0x02` | CHUNK | `varint tid`, `varint baseTicks`, `varint len`, `len` bytes of events. The session continues in a later chunk. |
| `0x03` | CHUNK_END | Same as CHUNK, but the last chunk of the session. |
| `0x05` | END | Nothing. The recording was stopped cleanly. |

### Sessions and events

A session is one call tree under a root method on one thread, written as a series of chunks.
Chunks of different threads interleave.

```text
thread 7:   CHUNK ── CHUNK ── CHUNK_END        CHUNK_END
thread 9:        CHUNK ── CHUNK_END
```

Time is in 100 ns ticks since the file was opened. `baseTicks` is the time of the chunk's first
event, and each later event carries a delta from the one before it. The low bits of the first
varint give the event kind.

| Event | First varint | Second varint |
|---|---|---|
| ENTER | `delta << 1` | `methodId` |
| EXIT | `delta << 2 \| 1` | none |
| EXIT with exception | `delta << 2 \| 3` | `exceptionId` |

## Limits

| Field | Limit |
|---|---|
| method id, exception id | below 2^22 |
| ticks | at most `Long.MAX_VALUE / 100` |
| chunk payload | at most 2^28 bytes |
| GC label | at most 256 bytes |
| UTC offset | within ±18 hours |

## License

Verbatime is licensed under the [Apache License, Version 2.0](../../LICENSE).
Copyright 2026 yagipass. See [NOTICE](../../NOTICE) for attribution requirements when redistributing.
