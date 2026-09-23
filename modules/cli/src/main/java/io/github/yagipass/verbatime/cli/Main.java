package io.github.yagipass.verbatime.cli;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class Main {

    static final String HELP = """
            vbtm - read .vbtm recordings of Verbatime, written for AI agents and scripts

            Usage: vbtm <command> <file> [arguments] [options]

            Commands:
              sessions <file>              summary of the recording and its sessions, where to start
              hot <file> [SESSION]         methods ranked by self time, total time or calls
              throws <file> [SESSION]      exceptions thrown, with the method that threw and the one that caught each
              tree <file> SESSION          call tree of a session, or of one call with --at CALL
              find <file> PATTERN          calls of a method, slowest first, with their ids
              callers <file> PATTERN       the call paths that lead to a method

            Run vbtm <command> --help for the options of each command. Every command takes --json, which prints one
            JSON object per line instead of text.

            Ids: a session is a number such as 7, from vbtm sessions. A call is SESSION.N such as 7.57, the Nth call
            entered in session 7, where 7.0 is its root. Ids stay the same for the same file.

            Patterns name a method: Class::method or pkg.Class::method, the name as printed such as Class.method,
            or Class.method#2 when two methods print the same, or any part of pkg.Class.method. A pattern that
            matches several different methods is an error that lists them, unless --all is given.

            Units: durations are ms with 4 decimals, where 0.0001 ms is one tick of 100 ns. Options that take a
            duration accept 500us, 1ms, 2s and so on. Output is capped, and a line starting with "# N more" gives
            the command that shows the rest.

            A typical investigation:
              1. vbtm sessions rec.vbtm --sort dur              pick the slow session, say 7
              2. vbtm hot rec.vbtm 7                            which methods spend the time themselves
              3. vbtm throws rec.vbtm 7                         which exceptions are thrown, by whom, and who catches them
              4. vbtm tree rec.vbtm 7                           where the time goes, longest calls only
              5. vbtm tree rec.vbtm --at 7.57                   drill into one call, with its path from the root
              6. vbtm find rec.vbtm 'Dao::query' --session 7    every call of a suspect method, slowest first
              7. vbtm callers rec.vbtm 'Dao::query'             which code paths call it, and how often
            For many repeated calls such as loops and N+1 queries, vbtm tree --merge and vbtm callers are shorter
            than a plain tree.

            Exit status: 0 success, 1 bad arguments, 2 the file cannot be read or is not a .vbtm recording,
            3 the file is corrupt. What could be read is still printed, followed by a "# status:" line.
            """;

    private interface Runner {

        int run(List<String> argv, PrintStream stdout);
    }

    private record Command(String help, Runner runner) {
    }

    private Main() {
    }

    public static void main(final String[] args) {
        final PrintStream stdout = new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out),
                1 << 16), false, StandardCharsets.UTF_8);
        final PrintStream stderr = new PrintStream(new FileOutputStream(FileDescriptor.err), true,
                StandardCharsets.UTF_8);
        final int code = run(Arrays.asList(args), stdout, stderr);
        stdout.flush();
        System.exit(code);
    }

    static int run(final List<String> args, final PrintStream stdout, final PrintStream stderr) {
        try {
            if (args.isEmpty() || args.get(0).equals("--help") || args.get(0).equals("-h")
                    || args.get(0).equals("help")) {
                stdout.print(HELP);
                return 0;
            }
            final Command command = command(args.get(0));
            final List<String> rest = args.subList(1, args.size());
            if (rest.contains("--help") || rest.contains("-h")) {
                stdout.print(command.help());
                return 0;
            }
            return command.runner().run(rest, stdout);
        } catch (final CliException e) {
            stdout.flush();
            stderr.println("vbtm: " + e.getMessage());
            if (e.hint() != null) {
                stderr.println("hint: " + e.hint());
            }
            return e.exitCode();
        }
    }

    private static Command command(final String name) {
        return switch (name) {
            case "sessions" -> new Command(SessionsCommand.HELP, SessionsCommand::run);
            case "hot" -> new Command(HotCommand.HELP, HotCommand::run);
            case "throws" -> new Command(ThrowsCommand.HELP, ThrowsCommand::run);
            case "tree" -> new Command(TreeCommand.HELP, TreeCommand::run);
            case "find" -> new Command(FindCommand.HELP, FindCommand::run);
            case "callers" -> new Command(CallersCommand.HELP, CallersCommand::run);
            default -> throw CliException.usage("unknown command '" + name + "'",
                    "the commands are sessions, hot, throws, tree, find and callers, see vbtm --help");
        };
    }
}
