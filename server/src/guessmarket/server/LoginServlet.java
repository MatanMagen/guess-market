package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * {@code POST /login  name=...} — joins the system under a name nobody else holds, with an empty
 * account. A taken name answers 409 so the client can ask for another one.
 */
@WebServlet("/login")
public class LoginServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, false, ignored -> {
            String name = text(request, "name").trim();
            engine().registerUser(name);
            request.getSession(true).setAttribute(USER_NAME, name);
            return engine().userDetails(name);
        });
    }
}
