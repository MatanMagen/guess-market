package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** {@code GET /users} — everybody's name, balance and whether they are a market maker. */
@WebServlet("/users")
public class UsersServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, user -> engine().listUsers());
    }
}
