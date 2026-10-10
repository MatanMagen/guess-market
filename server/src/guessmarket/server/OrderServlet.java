package guessmarket.server;

import guessmarket.dto.OrderSide;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code POST /order  event=N outcome=K side=BUY|SELL quantity=Q price=P} — an order book order. */
@WebServlet("/order")
public class OrderServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> engine().submitOrder(integer(request, "event"), user,
                integer(request, "outcome"), side(request), quantity(request, "quantity"),
                amount(request, "price")));
    }

    private static OrderSide side(HttpServletRequest request) {
        try {
            return OrderSide.valueOf(text(request, "side").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("'" + request.getParameter("side") + "' is not BUY or SELL.");
        }
    }
}
