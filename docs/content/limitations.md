# Limitations

## Classes under `com.sun.*` are never instrumented

The agent never instruments classes whose names start with `java.`, `jdk.`, `sun.`, or `com.sun.`.
This includes libraries under `com.sun.*`, such as Mojarra (`com.sun.faces`) and Jersey 1.x
(`com.sun.jersey`).

- Their methods are not recorded. Their time counts as self time of the nearest recorded caller.
- They cannot be roots.
- `include=` values under them are ignored with a warning. If all are ignored, every class is
  instrumented.

To measure work in these libraries, choose a root in your own code that calls into them, such as
a servlet filter or a controller.

## Recordings without `out=` do not survive the JVM

Without `out=`, a recording is kept in a spool file only until JDK Mission Control reads it. The
file is deleted when JDK Mission Control has read it, when a new recording starts, or when the JVM
exits, whether it was read or not.

For a JVM that exits on its own, such as a batch job or a test run, use `out=`. Each new recording
overwrites the file.

## Redeployed classes pile up until the JVM restarts

The agent remembers every class it has instrumented until the JVM exits, including old copies left
by redeploys. After many redeploys:

- Recordings get larger and slower to start.
- The agent reaches its limit of 4,194,304 methods. Classes loaded after that are not recorded,
  and the agent logs `[verbatime] WARN method id limit of 4194304 reached at ...` once.

Restart the JVM to start over, or narrow `include=` to the packages you want to measure.

## Deep recursion can cut a session short

Instrumented methods use more stack, so deeply recursive code throws `StackOverflowError` sooner.
When that happens during a recording, the session on that thread usually ends there. Open frames
show as unclosed, and the agent logs `[verbatime] WARN session #... ended early`. Rarely, the
session stays open until the recording stops, or the whole recording stops.

Give the thread a larger stack with `-Xss`, or leave the recursive code out with `exclude=`.
