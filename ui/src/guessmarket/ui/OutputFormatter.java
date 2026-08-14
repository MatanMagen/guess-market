package guessmarket.ui;

import guessmarket.dto.CloseResult;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.FileProblem;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.OutcomeState;
import guessmarket.dto.PurchaseResult;
import guessmarket.dto.TradeRecord;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The only class in the application that prints. Plain text throughout: no colours, no screen
 * clearing, and nothing wider than 78 columns so it does not wrap in a default command prompt.
 * <p>
 * Amounts use {@link Locale#US} explicitly, otherwise a machine set to a comma-decimal locale
 * would print 65,11.
 */
public class OutputFormatter {

    private static final int WIDTH = 78;
    /** Width the labels of a detail block are padded to, so their values line up. */
    private static final int DETAIL_LABEL_WIDTH = 25;
    private static final String MONEY_FORMAT = "%.2f";
    private static final String PRICE_FORMAT = "%.2f";

    private final PrintStream out;

    public OutputFormatter(PrintStream out) {
        this.out = out;
    }

    public void banner() {
        out.println(rule('='));
        out.println(centered("G U E S S   M A R K E T"));
        out.println(centered("Console Edition  -  Exercise 1"));
        out.println(rule('='));
    }

    public void menu(String loadedDescription, int eventCount) {
        out.println();
        out.println(rule('-'));
        out.println(" MAIN MENU");
        out.println(rule('-'));
        for (MenuCommand command : MenuCommand.values()) {
            out.printf("  %d. %s%n", command.menuNumber(), command.label());
        }
        out.println(rule('-'));
        if (loadedDescription == null) {
            out.println(" Loaded file: (none yet)");
        } else {
            out.println(" Loaded file: " + loadedDescription);
            out.println(" Events     : " + eventCount);
        }
        out.println(rule('-'));
    }

    public void prompt(String text) {
        out.print(text);
        out.flush();
    }

    public void section(String title) {
        out.println();
        out.println(rule('='));
        out.println(" " + title);
        out.println(rule('='));
    }

    public void info(String message) {
        out.println(message);
    }

    public void blank() {
        out.println();
    }

    public void error(String message) {
        out.println("  [!] " + message);
    }

    public void loadSucceeded(LoadSummary summary) {
        section("FILE LOADED");
        out.println(" The file was checked and loaded in full.");
        out.println();
        out.println("   File   : " + summary.sourceDescription());
        out.println("   Events : " + summary.eventsLoaded());
        out.println();
        out.println(" Every event is open for trading, and each one has been funded with its");
        out.println(" LMSR opening subsidy. Anything loaded before this file has been replaced.");
    }

    /** @param stillLoaded what remains loaded, or {@code null} if nothing is. */
    public void loadFailed(List<FileProblem> problems, String stillLoaded) {
        section("FILE REJECTED");
        out.println(" The file was not loaded because of the following:");
        out.println();
        for (int i = 0; i < problems.size(); i++) {
            printWrapped(String.format("   %d) ", i + 1), Messages.describe(problems.get(i)));
        }
        out.println();
        if (stillLoaded == null) {
            out.println(" Nothing is loaded, so the trading commands are still unavailable.");
        } else {
            out.println(" The previously loaded file is untouched and still in use:");
            out.println("   " + stillLoaded);
        }
    }

    public void eventList(String title, List<EventSummary> events) {
        section(title + "  (" + events.size() + ")");
        for (EventSummary event : events) {
            out.println();
            out.printf(" [%d] %s%n", event.displayNumber(), event.name());
            out.printf("     Event number : %d%n", event.id());
            out.printf("     Status       : %s%n", Messages.label(event.status()));
            out.printf("     Commission   : %d%% (%s)%n",
                    event.commissionPercent(), Messages.label(event.commissionType()));
            List<String> outcomeNames = event.outcomeNames();
            for (int i = 0; i < outcomeNames.size(); i++) {
                String label = i == 0 ? "     Answers      : " : "                    ";
                out.printf("%s(%d) %s%n", label, i + 1, outcomeNames.get(i));
            }
            printWrapped("     Description  : ", event.description());
        }
    }

    public void eventState(EventState state) {
        EventSummary summary = state.summary();
        section("TRADING STATE  -  " + summary.name() + "  (event number " + summary.id() + ")");
        out.printf(" Status         : %s%n", Messages.label(summary.status()));
        out.printf(" Trading method : LMSR   (liquidity b = %d)%n", state.liquidity());
        out.printf(" Commission     : %d%% (%s)%n",
                summary.commissionPercent(), Messages.label(summary.commissionType()));

        out.println();
        out.println(" CURRENT MARKET");
        out.println("   No.  Answer                              Value   Shares bought");
        out.println("   ---  ----------------------------------  ------  -------------");
        for (OutcomeState outcome : state.outcomes()) {
            out.printf("   %3d  %-34s  %6s  %13d%n",
                    outcome.displayNumber(),
                    clip(outcome.name(), 34),
                    price(outcome.price()),
                    outcome.sharesBought());
        }

        out.println();
        out.println(" EVENT ACCOUNT");
        out.printf("   Balance (opening subsidy included) : %14s%n", money(state.accountBalance()));
        out.printf("   Opening subsidy paid by the MM     : %14s%n", money(state.openingSubsidy()));
        out.printf("   Market maker net result            : %14s%n", money(state.marketMakerNetResult()));
        out.printf("   Commission collected so far        : %14s%n", money(state.commissionCollected()));

        out.println();
        tradeHistory(state.tradeHistory());

        if (state.closed()) {
            out.println();
            finalResult(state);
        }
    }

    public void purchase(PurchaseResult result) {
        section("PURCHASE COMPLETED");
        out.printf(" Bought %d share(s) of '%s'.%n", result.quantity(), result.outcomeName());
        out.println();
        amountLine("Paid for the shares", money(result.sharesCost()));
        if (result.commissionCharged()) {
            amountLine(String.format("Commission (%d%%)",
                    result.stateAfterPurchase().summary().commissionPercent()), money(result.commissionPaid()));
        } else {
            noteLine("Commission", "none now, it is collected when the event closes");
        }
        amountLine("Total paid", money(result.totalPaid()));
    }

    public void close(CloseResult result) {
        section("EVENT CLOSED");
        out.printf(" The event was resolved on '%s'.%n", result.winningOutcomeName());
        out.println();
        amountLine("Winning shares bought", String.valueOf(result.winningShares()));
        amountLine("Payout before commission", money(result.grossPayout()));
        if (result.commissionAppliedOnClose()) {
            amountLine(String.format("Commission (%d%% on close)",
                    result.stateAfterClose().summary().commissionPercent()), money(result.commissionCharged()));
        } else {
            noteLine("Commission", "already collected on each purchase");
        }
        amountLine("Paid out to the winners", money(result.netPaidToWinners()));
    }

    private void amountLine(String label, String value) {
        out.printf("   %-" + DETAIL_LABEL_WIDTH + "s : %14s%n", label, value);
    }

    private void noteLine(String label, String note) {
        out.printf("   %-" + DETAIL_LABEL_WIDTH + "s : %s%n", label, note);
    }

    private void tradeHistory(List<TradeRecord> history) {
        out.println(" TRADE HISTORY  (newest first)");
        if (history.isEmpty()) {
            out.println("   No shares have been bought in this event yet.");
            return;
        }
        out.println("   No.  Answer                  Shares  Shares cost  Commission   Total paid");
        out.println("   ---  ----------------------  ------  -----------  ----------  -----------");
        for (int i = 0; i < history.size(); i++) {
            TradeRecord trade = history.get(i);
            out.printf("   %3d  %-22s  %6d  %11s  %10s  %11s%n",
                    i + 1,
                    clip(trade.outcomeName(), 22),
                    trade.quantity(),
                    money(trade.sharesCost()),
                    money(trade.commissionPaid()),
                    money(trade.totalPaid()));
        }
    }

    private void finalResult(EventState state) {
        out.println(" FINAL RESULT");
        out.println("   Winning answer : " + state.winningOutcomeName());
        out.println("   Total shares bought per answer:");
        for (OutcomeState outcome : state.outcomes()) {
            boolean winner = outcome.name().equals(state.winningOutcomeName());
            out.printf("     (%d) %-34s %13d%s%n",
                    outcome.displayNumber(),
                    clip(outcome.name(), 34),
                    outcome.sharesBought(),
                    winner ? "   <-- WINNER" : "");
        }
    }

    /** Wraps text to the console width, lining continuation lines up under the first. */
    private void printWrapped(String label, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        int available = Math.max(20, WIDTH - label.length());
        List<String> lines = wrap(text.trim(), available);
        String continuation = " ".repeat(label.length());
        for (int i = 0; i < lines.size(); i++) {
            out.println((i == 0 ? label : continuation) + lines.get(i));
        }
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.isEmpty()) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String money(double amount) {
        return String.format(Locale.US, MONEY_FORMAT, amount == 0.0d ? 0.0d : amount);
    }

    private String price(double value) {
        return String.format(Locale.US, PRICE_FORMAT, value == 0.0d ? 0.0d : value);
    }

    private String clip(String text, int width) {
        if (text.length() <= width) {
            return text;
        }
        return text.substring(0, width - 3) + "...";
    }

    private String rule(char character) {
        return String.valueOf(character).repeat(WIDTH);
    }

    private String centered(String text) {
        if (text.length() >= WIDTH) {
            return text;
        }
        int padding = (WIDTH - text.length()) / 2;
        return " ".repeat(padding) + text;
    }
}
