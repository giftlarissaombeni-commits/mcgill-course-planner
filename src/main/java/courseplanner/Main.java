package courseplanner;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Command-line front end. See {@link #USAGE}. */
public final class Main {

    private static final String USAGE = """
            McGill Course Planner

            Usage: java -jar course-planner.jar <command> [options]

            Commands:
              list                     Show every course in the catalog
              check                    Validate the catalog (prerequisite cycles, unknown courses)
              prereqs <COURSE>         Show the prerequisite tree of one course
              plan --goal A,B          Build a term-by-term plan
                   [--completed X,Y]   courses you have already finished
                   [--start fall-2026] first term to plan (default: the next upcoming term)
                   [--max-credits 15]  credit cap per term
              dot                      Print the prerequisite graph in Graphviz DOT format

            Global option:
              --catalog <file.csv>     Use your own catalog instead of the bundled sample

            Exit codes: 0 success, 1 error, 2 unknown command, 3 plan could not be completed
            """;

    private record Args(String command, Map<String, String> options, List<String> positional) {
        static Args parse(String[] argv) {
            Map<String, String> options = new LinkedHashMap<>();
            List<String> positional = new ArrayList<>();
            for (int i = 1; i < argv.length; i++) {
                String arg = argv[i];
                if (arg.startsWith("--")) {
                    if (i + 1 >= argv.length) {
                        throw new IllegalArgumentException("Option " + arg + " needs a value");
                    }
                    options.put(arg.substring(2), argv[++i]);
                } else {
                    positional.add(arg);
                }
            }
            return new Args(argv[0], options, positional);
        }
    }

    public static void main(String[] argv) {
        System.exit(run(argv, System.out, System.err));
    }

    static int run(String[] argv, PrintStream out, PrintStream err) {
        if (argv.length == 0 || argv[0].equals("help") || argv[0].equals("--help")) {
            out.print(USAGE);
            return 0;
        }
        try {
            Args args = Args.parse(argv);
            Catalog catalog = loadCatalog(args.options().get("catalog"));
            switch (args.command()) {
                case "list":
                    return list(catalog, out);
                case "check":
                    return check(catalog, out);
                case "prereqs":
                    return prereqs(catalog, args, out);
                case "plan":
                    return plan(catalog, args, out);
                case "dot":
                    return dot(catalog, out);
                default:
                    err.println("Unknown command: " + args.command());
                    err.print(USAGE);
                    return 2;
            }
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static Catalog loadCatalog(String path) throws IOException {
        return path == null ? Catalog.fromResource("/courses.csv") : Catalog.fromFile(Path.of(path));
    }

    // ------------------------------------------------------------------ commands

    private static int list(Catalog catalog, PrintStream out) {
        out.printf("%-9s %-42s %3s  %-3s  %s%n", "CODE", "TITLE", "CR", "OFF", "PREREQUISITES");
        for (Course c : catalog.all()) {
            out.printf("%-9s %-42s %3d  %-3s  %s%n", c.code(), c.title(), c.credits(), c.offeredLabel(), c.prereq());
        }
        out.printf("%n%d courses%n", catalog.size());
        return 0;
    }

    private static int check(Catalog catalog, PrintStream out) {
        out.printf("%d courses loaded.%n", catalog.size());
        Map<String, Set<String>> unknown = catalog.unknownReferences();
        for (Map.Entry<String, Set<String>> entry : unknown.entrySet()) {
            out.printf("Warning: %s requires %s, which is not in the catalog.%n",
                    entry.getKey(), String.join(", ", entry.getValue()));
        }
        Optional<List<String>> cycle = catalog.findCycle();
        if (cycle.isPresent()) {
            out.println("Prerequisite cycle (each course requires the next): " + String.join(" -> ", cycle.get()));
            return 1;
        }
        out.println("No prerequisite cycles found.");
        return 0;
    }

    private static int prereqs(Catalog catalog, Args args, PrintStream out) {
        if (args.positional().isEmpty()) {
            throw new IllegalArgumentException("prereqs needs a course, e.g. prereqs \"COMP 551\"");
        }
        String code = RequirementParser.normalizeCode(String.join(" ", args.positional()));
        if (catalog.get(code).isEmpty()) {
            throw new IllegalArgumentException("Unknown course: " + code);
        }
        printTree(catalog, code, 0, new LinkedHashSet<>(), out);
        return 0;
    }

    private static void printTree(Catalog catalog, String code, int depth, Set<String> path, PrintStream out) {
        String indent = "  ".repeat(depth);
        Optional<Course> course = catalog.get(code);
        if (course.isEmpty()) {
            out.printf("%s%s  (not in catalog)%n", indent, code);
            return;
        }
        Course c = course.get();
        String needs = c.prereq() instanceof Requirement.None ? "" : "   needs: " + c.prereq();
        if (!path.add(code)) {
            out.printf("%s%s  (cycle)%n", indent, code);
            return;
        }
        out.printf("%s%s  %s%s%n", indent, c.code(), c.title(), needs);
        for (String dep : c.prereq().mentionedCourses()) {
            printTree(catalog, dep, depth + 1, path, out);
        }
        path.remove(code);
    }

    private static int plan(Catalog catalog, Args args, PrintStream out) {
        String goalText = args.options().get("goal");
        if (goalText == null || goalText.isBlank()) {
            throw new IllegalArgumentException("plan needs --goal, e.g. --goal \"COMP 551\"");
        }
        Set<String> goals = splitCodes(goalText);
        Set<String> completed = splitCodes(args.options().getOrDefault("completed", ""));
        int maxCredits = parseInt("--max-credits", args.options().getOrDefault("max-credits", "15"));
        Term start = args.options().containsKey("start")
                ? Term.parse(args.options().get("start"))
                : Term.upcoming(LocalDate.now());

        Planner.Plan plan = new Planner(catalog).plan(completed, goals, new Planner.Options(maxCredits, start, 12));

        out.printf("Plan from %s, at most %d credits per term%n%n", start, maxCredits);
        int total = 0;
        for (Planner.TermPlan termPlan : plan.terms()) {
            out.printf("%s  (%d credits)%n", termPlan.term(), termPlan.credits());
            for (Course c : termPlan.courses()) {
                out.printf("  %-9s %-42s %d cr%n", c.code(), c.title(), c.credits());
                total += c.credits();
            }
            out.println();
        }
        if (!plan.alreadyDone().isEmpty()) {
            out.println("Already completed: " + String.join(", ", plan.alreadyDone()));
        }
        if (!plan.terms().isEmpty()) {
            Planner.TermPlan last = plan.terms().get(plan.terms().size() - 1);
            out.printf("%d credits in %d terms, finishing in %s.%n", total, plan.terms().size(), last.term());
        }
        if (!plan.external().isEmpty()) {
            out.println("Not in the catalog (mark as completed or add to your catalog): "
                    + String.join(", ", plan.external()));
        }
        if (!plan.unscheduled().isEmpty()) {
            out.println("Could not be scheduled within the limits: " + String.join(", ", plan.unscheduled()));
        }
        return plan.isComplete() ? 0 : 3;
    }

    private static int dot(Catalog catalog, PrintStream out) {
        out.println("digraph prerequisites {");
        out.println("  rankdir=LR;");
        out.println("  node [shape=box, style=rounded];");
        for (Course c : catalog.all()) {
            out.printf("  \"%s\" [label=\"%s\\n%s\"];%n", c.code(), c.code(), c.title().replace("\"", "'"));
        }
        for (Course c : catalog.all()) {
            for (String dep : c.prereq().mentionedCourses()) {
                out.printf("  \"%s\" -> \"%s\";%n", dep, c.code());
            }
        }
        out.println("}");
        return 0;
    }

    // ------------------------------------------------------------------ helpers

    private static Set<String> splitCodes(String text) {
        Set<String> codes = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                codes.add(RequirementParser.normalizeCode(part));
            }
        }
        return codes;
    }

    private static int parseInt(String name, String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number, got '" + text + "'");
        }
    }

    private Main() {
    }
}
