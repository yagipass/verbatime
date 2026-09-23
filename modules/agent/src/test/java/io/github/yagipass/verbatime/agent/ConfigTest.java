package io.github.yagipass.verbatime.agent;

import java.util.List;

import io.github.yagipass.verbatime.agent.test.Check;

public final class ConfigTest {

    private ConfigTest() {
    }

    public static void run() {
        final Config c = Config.parse("include=com.example.app+org.acme,exclude=com.example.app.internal,out=/var/log/app/trace.vbtm");
        Check.eq(List.of("com/example/app", "org/acme"), c.includes(), "includes as internal names");
        Check.eq(List.of("com/example/app/internal"), c.excludes(), "excludes");
        Check.eq("/var/log/app/trace.vbtm", c.out(), "out is taken verbatim, extension included");
        Check.that(c.spoolDir() == null, "no spool dir with out=");

        final Config none = Config.parse(null);
        Check.that(none.includes().isEmpty() && none.excludes().isEmpty(), "null args -> no filters");
        Check.that(none.out() == null && none.spoolDir() == null, "null args -> spooled to a temp dir");
        Check.that(none.selects("com/anything/At/All"), "everything is included by default");

        Check.eq("/var/tmp/vbtm", Config.parse("spool=/var/tmp/vbtm").spoolDir(), "spool directory");

        final Config inc = Config.parse("include=com.example.app,exclude=com.example.app.internal");
        Check.that(inc.selects("com/example/app/foo/Bar"), "class inside include");
        Check.that(inc.selects("com/example/app/Top"), "class directly in the package");
        Check.that(!inc.selects("com/example/appx/Bar"), "sibling package with the prefix as a substring");
        Check.that(!inc.selects("com/example/Bar"), "parent package");
        Check.that(!inc.selects("com/example/app/internal/X"), "excluded subpackage");
        Check.that(inc.selects("com/example/app/internalx/X"), "exclude also matches on package boundary");
        final Config cls = Config.parse("include=com.example.Foo");
        Check.that(cls.selects("com/example/Foo"), "include a class");
        Check.that(cls.selects("com/example/Foo$Inner"), "include covers nested classes");
        Check.that(!cls.selects("com/example/FooBar"), "include does not cover FooBar");

        final Config exc = Config.parse("exclude=com.example");
        Check.that(!exc.selects("com/example/Foo"), "exclude narrows the default include-everything");
        Check.that(exc.selects("org/other/Foo"), "everything else stays included");

        final Config jdk = Config.parse("include=java.util+com.example");
        Check.eq(List.of("com/example"), jdk.includes(), "include=java.* is ignored with a warning");

        Check.eq(60_000L, Config.parse("waitstart=60s").waitStartMs(), "waitstart with the s suffix");
        Check.eq(5_000L, Config.parse("waitstart=5").waitStartMs(), "waitstart as bare seconds");
        Check.eq(0L, Config.parse(null).waitStartMs(), "waitstart is off by default");
        Check.that(Config.parse("waitstart=60s").describe().contains("waitstart=60s"), "waitstart appears in describe()");
        Check.that(!Config.parse(null).describe().contains("waitstart"), "no waitstart in describe() when off");

        expectFailure("waitstart=", "empty waitstart");
        expectFailure("waitstart=0", "zero waitstart");
        expectFailure("waitstart=-1s", "negative waitstart");
        expectFailure("waitstart=abc", "non-numeric waitstart");
        expectFailure("foo=1", "unknown key");
        expectFailure("out=", "empty out");
        expectFailure("spool=", "empty spool");
        expectFailure("out=/a/trace.vbtm,spool=/b", "out and spool together");
        expectFailure("garbage", "argument without =");
        expectFailure("root=a.B::m", "unknown key root, which is the singular of roots", "unknown argument: root");
        expectFailure("mode=manual", "unknown key mode", "unknown argument: mode");
        expectFailure("dump=/tmp/d", "unknown key dump", "unknown argument: dump");
        expectFailure("maxdepth=20", "unknown key maxdepth");
        expectFailure("buffer=5000", "unknown key buffer");
        expectFailure("report=text", "unknown key report");

        roots();

        expectFailure("include=com.example.*", "trailing wildcard", "write include=com.example");
        expectFailure("include=com.example.", "trailing dot", "write include=com.example");
        expectFailure("include=.com.example", "leading dot", "empty package segment");
        expectFailure("include=com.example..Foo", "double dot", "empty package segment");
        expectFailure("exclude=com.example/", "trailing slash in exclude", "write exclude=com.example");
        expectFailure("include=com.sun.*", "wildcard under a NEVER prefix is rejected, not ignored", "wildcards");
        expectFailure("include=com.example.app+org.acme.*", "one malformed prefix rejects the whole argument", "org.acme.*");
    }

    private static void roots() {
        final Config one = Config.parse("roots=com.example.Order::place");
        Check.eq(List.of(new RootSpec("com.example.Order", "place", null)), one.roots(), "roots= parses one spec");
        Check.eq(RecordStart.ONDEMAND, one.recordStart(), "record=ondemand is the default");

        final Config many = Config.parse("roots=com.example.Order::place+com.example.Cart::add(Ljava/lang/String;)V");
        Check.eq(2, many.roots().size(), "roots= takes several specs joined with +");
        Check.eq("(Ljava/lang/String;)V", many.roots().get(1).descriptor(), "roots= keeps the descriptor of a spec that has one");

        Check.eq(List.of(), Config.parse(null).roots(), "no roots by default");
        Check.that(one.describe().contains("roots=com.example.Order::place"), "roots appear in describe()");
        Check.that(Config.parse(null).describe().contains("record=ondemand"), "the record mode always appears in describe()");

        final Config startup = Config.parse("roots=com.example.Order::place,out=/tmp/t.vbtm,record=startup");
        Check.eq(RecordStart.STARTUP, startup.recordStart(), "record=startup is parsed");
        Check.that(startup.describe().contains("record=startup"), "record=startup appears in describe()");

        expectFailure("roots=", "empty roots", "roots= needs a method");
        expectFailure("roots=com.example.Order", "root spec without ::", "pkg.Cls::method");
        expectFailure("roots=com.example.Order::<init>", "constructor as a root", "constructors");
        expectFailure("roots=java.util.ArrayList::size", "root on a never-instrumented class", "not instrumentable");
        expectFailure("include=com.example,roots=org.acme.Job::run", "root outside include=", "not instrumentable");
        expectFailure("record=manual", "unknown record mode", "record= needs startup or ondemand");
        expectFailure("record=startup,out=/tmp/t.vbtm", "record=startup without roots", "needs roots=");
        expectFailure("record=startup,roots=com.example.Order::place", "record=startup without out", "needs out=");
        expectFailure("record=startup,roots=com.example.Order::place,spool=/tmp/s", "record=startup with spool", "mutually exclusive");
        expectFailure("record=startup,roots=com.example.Order::place,out=/tmp/t.vbtm,waitstart=60s", "record=startup with waitstart", "mutually exclusive");
    }

    private static void expectFailure(final String args, final String what) {
        Check.thrown(IllegalArgumentException.class, () -> Config.parse(args), "Config.parse rejects " + what + ": '" + args + "'");
    }

    private static void expectFailure(final String args, final String what, final String mustMention) {
        final IllegalArgumentException e = Check.thrown(IllegalArgumentException.class, () -> Config.parse(args), "Config.parse rejects " + what + ": '" + args + "'");
        if (e != null) {
            Check.that(e.getMessage().contains(mustMention), what + ": the message must show the corrected form '" + mustMention + "', got: " + e.getMessage());
        }
    }
}
