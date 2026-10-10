package guessmarket.server;

import com.google.gson.Gson;
import guessmarket.engine.api.Engine;
import guessmarket.engine.exception.GuessMarketException;
import guessmarket.engine.exception.UserAlreadyExistsException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/**
 * What every endpoint does the same way: find out who is calling, run one engine call, and answer
 * in JSON. A success is the dto the engine returned; a failure is {@code {"error": "..."}} with a
 * 4xx status and a sentence a user can read, so a client only ever has to show it.
 * <p>
 * Who is calling is kept in the HTTP session, which the client's cookie carries back on every
 * request, as in the course's chat example. Every user acts only as himself.
 */
public abstract class ApiServlet extends HttpServlet {

    static final String USER_NAME = "userName";

    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new Gson();

    /** One engine call made for a user, who is null only where no login is required. */
    @FunctionalInterface
    protected interface Action {
        Object run(String userName) throws IOException, jakarta.servlet.ServletException;
    }

    protected void serve(HttpServletRequest request, HttpServletResponse response, Action action)
            throws IOException {
        serve(request, response, true, action);
    }

    protected void serve(HttpServletRequest request, HttpServletResponse response,
                         boolean loginRequired, Action action) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        String userName = loggedInUser(request);
        if (loginRequired && userName == null) {
            fail(response, HttpServletResponse.SC_UNAUTHORIZED, "You are not logged in. Please log in again.");
            return;
        }
        try {
            Object result = action.run(userName);
            response.getWriter().print(GSON.toJson(result == null ? Map.of() : result));
        } catch (UserAlreadyExistsException e) {
            fail(response, HttpServletResponse.SC_CONFLICT, Messages.describe(e));
        } catch (GuessMarketException e) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST, Messages.describe(e));
        } catch (BadRequestException e) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (RuntimeException | jakarta.servlet.ServletException e) {
            log("request to " + request.getRequestURI() + " failed", e);
            fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The server could not complete that: " + e);
        }
    }

    protected Engine engine() {
        return ServerSetup.engine(getServletContext());
    }

    protected ChatRoom chat() {
        return ServerSetup.chat(getServletContext());
    }

    static String loggedInUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object name = session == null ? null : session.getAttribute(USER_NAME);
        return name == null ? null : name.toString();
    }

    // ---------- parameters ----------

    protected static String text(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            throw new BadRequestException("The request is missing '" + name + "'.");
        }
        return value;
    }

    protected static int integer(HttpServletRequest request, String name) {
        try {
            return Integer.parseInt(text(request, name).trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("'" + request.getParameter(name) + "' is not a whole number.");
        }
    }

    protected static long quantity(HttpServletRequest request, String name) {
        try {
            return Long.parseLong(text(request, name).trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("'" + request.getParameter(name) + "' is not a whole number of shares.");
        }
    }

    protected static double amount(HttpServletRequest request, String name) {
        try {
            return Double.parseDouble(text(request, name).trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("'" + request.getParameter(name) + "' is not a number.");
        }
    }

    private static void fail(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().print(GSON.toJson(Map.of("error", message)));
    }

    /** A request that is malformed before the engine ever sees it. */
    protected static final class BadRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        BadRequestException(String message) {
            super(message);
        }
    }
}
