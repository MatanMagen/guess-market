package guessmarket.tests;

import guessmarket.dto.FileProblem;
import guessmarket.dto.ProblemKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A very small test harness. Pulling in JUnit would add a dependency the submission does not
 * otherwise need, and all the tests want is named checks, comparison with a tolerance, and an exit
 * code a build script can act on.
 */
public final class TestSupport {

    /** How close two amounts of money must be to count as equal. */
    public static final double MONEY_TOLERANCE = 1e-6;

    private final List<String> failures = new ArrayList<>();
    private int checksRun;
    private String currentGroup = "";

    public void group(String name) {
        currentGroup = name;
        System.out.println();
        System.out.println("-- " + name);
    }

    public void check(String name, ThrowingRunnable body) {
        checksRun++;
        try {
            body.run();
            System.out.printf("   ok   %s%n", name);
        } catch (AssertionError e) {
            failures.add(currentGroup + " / " + name + ": " + e.getMessage());
            System.out.printf("   FAIL %s%n        %s%n", name, e.getMessage());
        } catch (Throwable t) {
            failures.add(currentGroup + " / " + name + ": unexpected " + t);
            System.out.printf("   FAIL %s%n        unexpected %s: %s%n",
                    name, t.getClass().getSimpleName(), t.getMessage());
        }
    }

    public int report() {
        System.out.println();
        System.out.println("=".repeat(78));
        if (failures.isEmpty()) {
            System.out.printf("ALL %d CHECKS PASSED%n", checksRun);
            System.out.println("=".repeat(78));
            return 0;
        }
        System.out.printf("%d of %d CHECKS FAILED%n", failures.size(), checksRun);
        for (String failure : failures) {
            System.out.println("  - " + failure);
        }
        System.out.println("=".repeat(78));
        return 1;
    }

    public static void assertTrue(String what, boolean condition) {
        if (!condition) {
            throw new AssertionError(what + " should have been true");
        }
    }

    public static void assertFalse(String what, boolean condition) {
        if (condition) {
            throw new AssertionError(what + " should have been false");
        }
    }

    public static void assertEquals(String what, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    public static void assertEquals(String what, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected '" + expected + "' but was '" + actual + "'");
        }
    }

    public static void assertClose(String what, double expected, double actual) {
        assertClose(what, expected, actual, MONEY_TOLERANCE);
    }

    public static void assertClose(String what, double expected, double actual, double tolerance) {
        if (Double.isNaN(actual) || Double.isInfinite(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(String.format(Locale.US,
                    "%s: expected %.10f but was %.10f", what, expected, actual));
        }
    }

    public static void assertContains(String what, String needle, String haystack) {
        if (haystack == null || !haystack.toLowerCase().contains(needle.toLowerCase())) {
            throw new AssertionError(what + ": expected to find '" + needle + "' in '" + haystack + "'");
        }
    }

    // Pinning the kinds rather than any wording: the engine reports faults as data and the
    // phrasing lives in the front end, so matching on sentences would test the wrong layer.
    public static void assertKinds(String what, List<FileProblem> problems, ProblemKind... expected) {
        List<ProblemKind> actual = new ArrayList<>();
        for (FileProblem problem : problems) {
            actual.add(problem.kind());
        }
        List<ProblemKind> wanted = List.of(expected);
        if (!actual.equals(wanted)) {
            throw new AssertionError(what + ": expected problems " + wanted + " but got " + actual);
        }
    }

    public static void assertValue(String what, FileProblem problem, int index, String expected) {
        if (!expected.equals(problem.value(index))) {
            throw new AssertionError(what + ": expected value " + index + " to be '" + expected
                    + "' but was '" + problem.value(index) + "'");
        }
    }

    public static <T extends Throwable> T assertThrows(String what, Class<T> expected, ThrowingRunnable body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return expected.cast(thrown);
            }
            throw new AssertionError(what + ": expected " + expected.getSimpleName()
                    + " but got " + thrown.getClass().getSimpleName() + " (" + thrown.getMessage() + ")");
        }
        throw new AssertionError(what + ": expected " + expected.getSimpleName() + " but nothing was thrown");
    }

    /** A body of test code that is allowed to throw anything. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
