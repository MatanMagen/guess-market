package guessmarket.server;

import guessmarket.dto.ChatLine;
import guessmarket.dto.ChatLines;

import java.util.ArrayList;
import java.util.List;

/**
 * Bonus 1: one chat everybody logged in shares. Lines are only ever appended, so a client keeps
 * the count it has seen and is handed only what came after it, as in the course's chat example.
 */
public class ChatRoom {

    private final List<ChatLine> lines = new ArrayList<>();

    public synchronized void say(String userName, String text) {
        lines.add(new ChatLine(userName, System.currentTimeMillis(), text));
    }

    /** @param seen how many lines the caller already has; anything out of range means all of them. */
    public synchronized ChatLines since(int seen) {
        int from = seen < 0 || seen > lines.size() ? 0 : seen;
        return new ChatLines(lines.size(), new ArrayList<>(lines.subList(from, lines.size())));
    }
}
