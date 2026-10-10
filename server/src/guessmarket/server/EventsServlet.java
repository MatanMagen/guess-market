package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /events} — every event in the system, in every state. Filtering is the client's. */
@WebServlet("/events")
public class EventsServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> engine().listEvents());
    }
}
