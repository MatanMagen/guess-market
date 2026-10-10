package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Bonus 1, the shared chat. {@code GET /chat?version=N} hands back the lines after the first N;
 * {@code POST /chat  text=...} adds one under the caller's name.
 */
@WebServlet("/chat")
public class ChatServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_LINE = 500;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> chat().since(integer(request, "version")));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> {
            String text = text(request, "text").strip();
            if (text.isEmpty()) {
                throw new BadRequestException("An empty message was not sent.");
            }
            chat().say(user, text.length() > MAX_LINE ? text.substring(0, MAX_LINE) : text);
            return null;
        });
    }
}
