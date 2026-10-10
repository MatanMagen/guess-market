package guessmarket.client;

import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EntryKind;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.MarketMethod;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How numbers and enumerations are written on screen. Nothing here knows about the engine. */
public final class Format {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private Format() {
    }

    public static String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    public static String signedMoney(double amount) {
        return (amount >= 0 ? "+" : "") + money(amount);
    }

    public static String price(Double price) {
        return price == null ? "—" : String.format(Locale.US, "%.2f", price);
    }

    public static String percent(double fraction) {
        return String.format(Locale.US, "%.1f%%", fraction * 100.0d);
    }

    public static String quantity(long quantity) {
        return String.format(Locale.US, "%,d", quantity);
    }

    public static String status(EventLifecycle status) {
        return switch (status) {
            case NOT_STARTED -> "Not started";
            case ACTIVE -> "Active";
            case CLOSED -> "Closed";
        };
    }

    public static String method(MarketMethod method) {
        return method == MarketMethod.LMSR ? "LMSR" : "Order book";
    }

    public static String commission(CommissionPolicy policy) {
        return policy == CommissionPolicy.ON_PURCHASE ? "On purchase" : "On close";
    }

    public static String time(long millis) {
        return CLOCK.format(Instant.ofEpochMilli(millis));
    }

    public static String entry(EntryKind kind) {
        return switch (kind) {
            case DEPOSIT -> "Loaded funds";
            case SUBSIDY_PAID -> "Subsidy for starting an LMSR event";
            case INITIAL_STOCK -> "Initial stock for starting an order book";
            case SHARES_BOUGHT -> "Bought shares";
            case SHARES_SOLD -> "Sold shares";
            case COMMISSION_PAID -> "Commission paid";
            case COMMISSION_RECEIVED -> "Commission received";
            case WINNINGS -> "Winning shares paid out";
            case SUBSIDY_RETURNED -> "Unused subsidy returned";
        };
    }
}
