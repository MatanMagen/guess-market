package guessmarket.dto;

import java.util.List;

/**
 * The chat lines a client has not seen yet. {@code version} is how many lines exist in total, and
 * is what the client sends back next time so it is only ever handed the new ones.
 */
public record ChatLines(int version, List<ChatLine> lines) {
    public ChatLines {
        lines = List.copyOf(lines);
    }
}
