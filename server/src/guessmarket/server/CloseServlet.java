package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /close  event=N outcome=K} — the market maker resolves his event on answer K. */
@WebServlet("/close")
public class CloseServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user ->
                engine().closeEvent(integer(request, "event"), user, integer(request, "outcome")));
    }
}
