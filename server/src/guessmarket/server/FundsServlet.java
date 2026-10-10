package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /funds  amount=...} — loads money into the caller's own account. */
@WebServlet("/funds")
public class FundsServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> engine().depositFunds(user, amount(request, "amount")));
    }
}
