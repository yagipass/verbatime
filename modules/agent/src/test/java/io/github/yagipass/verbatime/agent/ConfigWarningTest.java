package io.github.yagipass.verbatime.agent;

import java.util.ArrayList;
import java.util.List;

import io.github.yagipass.verbatime.agent.test.Check;

public final class ConfigWarningTest {

    private static final String EVERY_CLASS = "every class is instrumented";

    private ConfigWarningTest() {
    }

    public static void run() {
        final List<String> onlyNever = new ArrayList<>();
        final Config faces = Config.parse("include=com.sun.faces", onlyNever::add);
        Check.that(faces.selects("org/other/Lib"), "an include list emptied by ignored prefixes instruments everything, which is why it must be announced");
        Check.eq(2, onlyNever.size(), "one warning for the ignored prefix and one for the switch to every class: " + onlyNever);
        Check.that(onlyNever.stream().anyMatch(w -> w.contains("include=com.sun.faces") && w.contains("com.sun.*")), "the warning names the never-instrumented prefix the include fell under: " + onlyNever);
        Check.that(onlyNever.stream().noneMatch(w -> w.contains("JDK")), "com.sun.faces is an application library, so the warning must not call it a JDK class: " + onlyNever);
        Check.that(onlyNever.stream().anyMatch(w -> w.contains(EVERY_CLASS)), "the user learns that narrowing was lost: " + onlyNever);

        final List<String> mixed = new ArrayList<>();
        Config.parse("include=com.example+com.sun.faces", mixed::add);
        Check.eq(1, mixed.size(), "a surviving include still narrows, so only the ignored prefix is reported: " + mixed);
        Check.that(mixed.stream().noneMatch(w -> w.contains(EVERY_CLASS)), "no every-class warning while an include survives: " + mixed);

        final List<String> exclude = new ArrayList<>();
        Config.parse("exclude=com.sun.faces", exclude::add);
        Check.eq(1, exclude.size(), "an ignored exclude changes nothing, so there is no every-class warning: " + exclude);
        Check.that(exclude.stream().anyMatch(w -> w.contains("exclude=com.sun.faces") && w.contains("com.sun.*")), "the exclude warning names the prefix too: " + exclude);

        final List<String> agent = new ArrayList<>();
        Config.parse("include=io.github.yagipass.verbatime.agent.probe", agent::add);
        Check.that(agent.stream().anyMatch(w -> w.contains("io.github.yagipass.verbatime.agent.*")), "the agent's own packages are named as the prefix they fall under: " + agent);

        final List<String> quiet = new ArrayList<>();
        Config.parse(null, quiet::add);
        Config.parse("include=com.example,exclude=com.example.internal", quiet::add);
        Check.that(quiet.isEmpty(), "no warnings when nothing is ignored, including the default include-everything: " + quiet);
    }
}
