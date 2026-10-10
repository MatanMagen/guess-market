package guessmarket.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import guessmarket.dto.ChatLines;
import guessmarket.dto.CloseResult;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.OrderResult;
import guessmarket.dto.OrderSide;
import guessmarket.dto.PurchaseResult;
import guessmarket.dto.UserDetails;
import guessmarket.dto.UserSummary;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every call the client makes to the server, one method per endpoint. Where the exercise 2 screens
 * called the engine, they now call this, and each call becomes an HTTP request.
 * <p>
 * The calls are synchronous: they are always made off the JavaFX thread, by the poller or by
 * {@link AppContext#run}, never on it. A refusal or an unreachable server is a
 * {@link ServerException} carrying a sentence for the user.
 * <p>
 * Plain Java with no JavaFX in it, so the tests can drive the server through it too.
 */
public final class Server {

    /** Where the grader's Tomcat serves the war, which is named for this context path. */
    public static final String DEFAULT_URL = "http://localhost:8080/guess-market";

    private static final Gson GSON = new Gson();
    private static final MediaType XML = MediaType.get("text/xml");
    private static final Type USERS = new TypeToken<List<UserSummary>>() { }.getType();
    private static final Type EVENTS = new TypeToken<List<EventSummary>>() { }.getType();

    private final String baseUrl;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .cookieJar(new SessionCookie())
            .followRedirects(false)
            .build();

    public Server(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public UserDetails login(String name) {
        return post("/login", form().add("name", name).build(), UserDetails.class);
    }

    public List<UserSummary> users() {
        return get(url("/users").build(), USERS);
    }

    public UserDetails me() {
        return get(url("/me").build(), UserDetails.class);
    }

    public List<EventSummary> events() {
        return get(url("/events").build(), EVENTS);
    }

    public EventState event(int eventId) {
        return get(url("/event").addQueryParameter("event", String.valueOf(eventId)).build(), EventState.class);
    }

    public UserDetails deposit(double amount) {
        return post("/funds", form().add("amount", String.valueOf(amount)).build(), UserDetails.class);
    }

    /** The file is sent as it is; whether it is a sound Guess Market file is for the server to say. */
    public LoadSummary upload(String fileName, byte[] content) {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, RequestBody.create(content, XML))
                .build();
        return post("/upload", body, LoadSummary.class);
    }

    public EventState open(int eventId) {
        return post("/open", form().add("event", String.valueOf(eventId)).build(), EventState.class);
    }

    public CloseResult close(int eventId, int outcomeNumber) {
        return post("/close", form()
                .add("event", String.valueOf(eventId))
                .add("outcome", String.valueOf(outcomeNumber))
                .build(), CloseResult.class);
    }

    public double quote(int eventId, int outcomeNumber, long quantity) {
        return get(url("/quote")
                .addQueryParameter("event", String.valueOf(eventId))
                .addQueryParameter("outcome", String.valueOf(outcomeNumber))
                .addQueryParameter("quantity", String.valueOf(quantity))
                .build(), Double.class);
    }

    public PurchaseResult buy(int eventId, int outcomeNumber, long quantity) {
        return post("/buy", form()
                .add("event", String.valueOf(eventId))
                .add("outcome", String.valueOf(outcomeNumber))
                .add("quantity", String.valueOf(quantity))
                .build(), PurchaseResult.class);
    }

    public OrderResult order(int eventId, int outcomeNumber, OrderSide side, long quantity, double price) {
        return post("/order", form()
                .add("event", String.valueOf(eventId))
                .add("outcome", String.valueOf(outcomeNumber))
                .add("side", side.name())
                .add("quantity", String.valueOf(quantity))
                .add("price", String.valueOf(price))
                .build(), OrderResult.class);
    }

    /** @param seen how many chat lines the caller already has. */
    public ChatLines chat(int seen) {
        return get(url("/chat").addQueryParameter("version", String.valueOf(seen)).build(), ChatLines.class);
    }

    public void say(String text) {
        post("/chat", form().add("text", text).build(), JsonObject.class);
    }

    // ---------- plumbing ----------

    private HttpUrl.Builder url(String path) {
        return HttpUrl.get(baseUrl + path).newBuilder();
    }

    private static FormBody.Builder form() {
        return new FormBody.Builder();
    }

    private <T> T get(HttpUrl url, Type type) {
        return call(new Request.Builder().url(url).get().build(), type);
    }

    private <T> T post(String path, RequestBody body, Type type) {
        return call(new Request.Builder().url(url(path).build()).post(body).build(), type);
    }

    private <T> T call(Request request, Type type) {
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new ServerException(response.code(), errorIn(text, response.code()));
            }
            return GSON.fromJson(text, type);
        } catch (IOException e) {
            throw new ServerException(0, "The Guess Market server at " + baseUrl + " cannot be reached. "
                    + "Make sure Tomcat is running with guess-market.war deployed.");
        } catch (JsonParseException e) {
            throw new ServerException(0, "The server sent an answer this client cannot read: " + e.getMessage());
        }
    }

    private static String errorIn(String body, int status) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (json != null && json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (JsonParseException notJson) {
            // fall through to the status
        }
        return "The server refused the request (HTTP " + status + ").";
    }

    /**
     * Keeps the session cookie the server hands out at login and sends it back on every request,
     * which is how the server knows who is calling. Cookies are kept by name: there is one server.
     */
    private static final class SessionCookie implements CookieJar {

        private final Map<String, Cookie> cookies = new HashMap<>();

        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
            for (Cookie cookie : received) {
                cookies.put(cookie.name(), cookie);
            }
        }

        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            return new ArrayList<>(cookies.values());
        }
    }
}
