package guessmarket.ui;

import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.MarketMethod;

import java.util.Locale;

/** How numbers and enumerations are written on screen. Nothing here knows about the engine. */
public final class Format {

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
}
