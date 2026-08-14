package guessmarket.ui;

import java.io.InputStream;
import java.util.Scanner;

/**
 * The only place in the application that reads from the console. Every method keeps asking until it
 * gets something usable, so a typing mistake never ends a command.
 * <p>
 * Lines are read whole and parsed afterwards, never with {@code Scanner.nextInt}, which leaves the
 * rest of the line behind and throws the next prompt out of step.
 */
public class ConsoleReader {

    /** Value the user can enter at a selection prompt to go back to the menu. */
    public static final int CANCEL = 0;

    private final Scanner scanner;
    private final OutputFormatter out;

    public ConsoleReader(InputStream in, OutputFormatter out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    public String readLine(String prompt) {
        out.prompt(prompt);
        if (!scanner.hasNextLine()) {
            throw new InputClosedException();
        }
        return scanner.nextLine().trim();
    }

    public String readRequiredLine(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (!line.isEmpty()) {
                return line;
            }
            out.error("Nothing was entered. Please type a value.");
        }
    }

    public int readIntInRange(String prompt, int min, int max) {
        while (true) {
            String line = readLine(prompt);
            if (line.isEmpty()) {
                out.error("Nothing was entered. Please type a number between " + min + " and " + max + ".");
                continue;
            }
            try {
                int value = Integer.parseInt(line);
                if (value < min || value > max) {
                    out.error("'" + line + "' is out of range. Please type a number between "
                            + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                out.error("'" + line + "' is not a whole number. Please type a number between "
                        + min + " and " + max + ".");
            }
        }
    }

    /** @return the one based selection, or {@link #CANCEL} if the user backed out. */
    public int readSelection(String prompt, int itemCount) {
        return readIntInRange(prompt, CANCEL, itemCount);
    }

    /** @return a positive quantity, or {@link #CANCEL} if the user backed out. */
    public long readQuantity(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (line.isEmpty()) {
                out.error("Nothing was entered. Please type how many shares you want to buy.");
                continue;
            }
            try {
                long value = Long.parseLong(line);
                if (value == CANCEL) {
                    return CANCEL;
                }
                if (value < 0) {
                    out.error("A quantity cannot be negative. Please type a whole number of 1 or more.");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                out.error("'" + line + "' is not a whole number. Please type how many shares you want to buy, "
                        + "for example 100.");
            }
        }
    }
}
