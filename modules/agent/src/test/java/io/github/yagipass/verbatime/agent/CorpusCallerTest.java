package io.github.yagipass.verbatime.agent;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.TestMain;
import java.util.List;

final class CorpusCallerTest {

  private static final String P = CorpusFixtures.PACKAGE;

  private static final String I = P.replace('.', '/');

  private record CallerCheck(
      String fixture, String method, String expected, String libraryClass, String why) {}

  private static final List<CallerCheck> CHECKS =
      List.of(
          new CallerCheck(
              "LoggerNameFixture",
              "loggerName",
              P + "LoggerNameFixture",
              "org/apache/logging/log4j/util/StackLocator",
              "log4j2's no-arg LogManager.getLogger() must name the logger after the calling class when log4j-api is instrumented, as the default include= does, or such loggers are named after log4j's own classes and per-class levels and appenders stop applying"),
          new CallerCheck(
              "CallerClassFixture",
              "callerOfCallee",
              P + "CallerClassFixture",
              I + "CallerClassFixture$Callee",
              "StackWalker.getCallerClass() inside an instrumented method must return the class that called the method, not the method's own class, or caller-based access checks and caller-scoped lookups pick the wrong class"),
          new CallerCheck(
              "DepthWalkFixture",
              "callerFoundByDepth",
              P + "DepthWalkFixture",
              I + "DepthWalkFixture$Internals",
              "a library that skips a fixed number of its own frames to find its caller, as log4j's StackLocator does, must land on the real caller when the library is instrumented"),
          new CallerCheck(
              "MarshallingFieldFixture",
              "fromMethod",
              "42",
              "org/jboss/marshalling/FieldSetter",
              "JBoss Marshalling's FieldSetter.get must accept a call from the field's own class when jboss-marshalling is instrumented, or Infinispan fails with 'Cannot get field from someone else's class', which is why the WildFly example excludes org.jboss.marshalling"),
          new CallerCheck(
              "MarshallingFieldFixture",
              "fromStaticInitializer",
              "7",
              "org/jboss/marshalling/FieldSetter",
              "FieldSetter.get called from a static initializer, the way serializable classes hold their setters, must succeed when jboss-marshalling is instrumented: static initializers are never instrumented, so the extra frame it rejects is FieldSetter's own"),
          new CallerCheck(
              "StackTraceFixture",
              "topTwoFrames",
              P + "StackTraceFixture$Inner.frames <- " + P + "StackTraceFixture.topTwoFrames",
              I + "StackTraceFixture$Inner",
              "a stack trace taken inside an instrumented method must show the method itself with its real caller below it, or exception traces and log location info (%M) name synthetic methods and extra frames"));

  private CorpusCallerTest() {}

  public static void main(String[] args) {
    TestMain.run("CorpusCallerTest", CorpusCallerTest::run);
    TestMain.report();
  }

  static void run() throws Exception {
    System.setProperty(
        "log4j2.loggerContextFactory",
        "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
    try (CorpusFixtures fx = CorpusFixtures.open()) {
      for (CallerCheck c : CHECKS) {
        String name = c.fixture() + "." + c.method();
        String original = CorpusFixtures.call(fx.original, c.fixture(), c.method());
        String transformed = CorpusFixtures.call(fx.transformed, c.fixture(), c.method());
        Check.eq(
            c.expected(),
            original,
            name + " gives the right answer without instrumentation, so the check itself is sound");
        Check.that(
            fx.instrumented.contains(I + c.fixture()) && fx.instrumented.contains(c.libraryClass()),
            name
                + " ran through instrumented "
                + I
                + c.fixture()
                + " and "
                + c.libraryClass()
                + ", or it proves nothing about instrumentation");
        Check.eq(c.expected(), transformed, name + ": " + c.why());
      }
    }
  }
}
