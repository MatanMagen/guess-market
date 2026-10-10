package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /quote?event=N&outcome=K&quantity=Q} — what an LMSR purchase would cost now. */
@WebServlet("/quote")
public class QuoteServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> engine().quoteLmsrPurchase(integer(request, "event"),
                integer(request, "outcome"), quantity(request, "quantity")));
    }
}
