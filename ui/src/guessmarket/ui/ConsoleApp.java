package guessmarket.ui;

import guessmarket.engine.api.Engine;
import guessmarket.engine.impl.GuessMarketEngine;

/**
 * Entry point. Wires an engine to a console and starts it.
 * <p>
 * The line below is the only place in the whole console that names the concrete engine class;
 * everything after it works against the {@link Engine} interface. Anything thrown is caught here so
 * the user never ends up staring at a stack trace.
 */
public final class ConsoleApp {

    private ConsoleApp() {
    }

    public static void main(String[] args) {
        OutputFormatter out = new OutputFormatter(System.out);
        ConsoleReader in = new ConsoleReader(System.in, out);
        Engine engine = new GuessMarketEngine();

        try {
            new GuessMarketConsole(engine, in, out).run();
        } catch (InputClosedException e) {
            out.blank();
            out.info("Input has ended. Goodbye.");
        } catch (RuntimeException e) {
            out.blank();
            out.error("The application stopped because of an unexpected problem: " + describe(e));
            out.error("Please report this along with what you were doing at the time.");
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.trim();
    }
}
