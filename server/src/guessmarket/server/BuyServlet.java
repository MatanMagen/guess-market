package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /buy  event=N outcome=K quantity=Q} — buys LMSR shares for the caller. */
@WebServlet("/buy")
public class BuyServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> engine().buyLmsrShares(integer(request, "event"), user,
                integer(request, "outcome"), quantity(request, "quantity")));
    }
}
