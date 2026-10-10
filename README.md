# guess-market

Prediction market system built for the java software development course at MTA. Users trade
shares on events — which of its answers will happen — and the price of a share doubles as the
market's estimate of the odds.

Rolling project across four exercises. The engine is extended each time rather than rewritten.

| Exercise | Front end | Status |
|---|---|---|
| 1 | Console | done |
| 2 | JavaFX, adds Order Book trading and multiple users | done, with bonus 4 |
| 3 | Client / server: Tomcat war + JavaFX client over HTTP | done, with bonus 1 (chat) |
| 4 | Web client (bonus): plain HTML/JS over the same API | done |

## Build and run

Needs **Java 25**, and Apache Tomcat 10.1 for the server.

```
./build.sh          # builds dist/guess-market.war and dist/client/
./test.sh           # engine test suite
GM_TOMCAT=/path/to/apache-tomcat-10.1.x ./servertest.sh   # HTTP suite against a real Tomcat
```

1. Copy `dist/guess-market.war` into Tomcat's `webapps` and start Tomcat. It serves at
   `http://localhost:8080/guess-market`.
2. Start a client per user: `run.bat` in `dist/client` on Windows. On macOS or Linux use
   `run.sh`, which needs a JavaFX SDK for that machine — `dist/client/lib/javafx` holds the
   Windows build, since that is what the submission ships:

```
GM_JAVAFX=/path/to/javafx-sdk-22.0.2/lib dist/client/run.sh
```

## Web client (exercise 4)

`web/` is a plain HTML, CSS and JavaScript page with a few dozen lines of Node (`server.js`, Node's own
modules only) that serves it and passes `/guess-market/...` through to Tomcat, so the browser sees one
address and Tomcat's session cookie works unchanged. With the war deployed:

```
cd web && node server.js      # or run.bat on Windows; then open http://localhost:3000
```

## Modules

Four Java modules; the dependencies run one way only:

```
client ──HTTP/JSON──> server ──> engine ──> dto
   └───────────────────────────────────────> dto
```

- **`dto/`** — immutable records, no logic. The shared vocabulary, and since exercise 3 also what
  travels over the wire as JSON: the server serializes them, the client reads them back.
- **`engine/`** — the domain model, LMSR pricing, the order book and its matching, XML loading
  and validation, the money and every account line. Passive: it does not know who is calling it
  and never prints. Every public method is synchronized, since the server calls it from many
  request threads at once.
- **`server/`** — the war. One small servlet per endpoint over a base class that resolves the
  session's user, runs one engine call, and answers in JSON. The only wording of faults lives here
  (`Messages`), so every client shows the same sentence.
- **`client/`** — the JavaFX application, grown out of the exercise 2 screens. It never sees the
  engine: every call goes through `Server`, one method per endpoint over OkHttp. Screens pull
  what they show every half second.

## HTTP API

All answers are JSON. A refusal is `{"error": "..."}` with a 4xx status.

| Method | Path | Parameters | Answer |
|---|---|---|---|
| POST | `/login` | `name` | `UserDetails`, or 409 if the name is taken |
| GET | `/users` | | `UserSummary[]`: name, balance, market maker |
| GET | `/me` | | `UserDetails` of the caller, with every account line |
| POST | `/funds` | `amount` | `UserDetails` |
| POST | `/upload` | multipart `file` | `LoadSummary` |
| GET | `/events` | | `EventSummary[]` |
| GET | `/event` | `event` | `EventState` |
| POST | `/open` | `event` | `EventState` |
| POST | `/close` | `event`, `outcome` | `CloseResult` |
| GET | `/quote` | `event`, `outcome`, `quantity` | number |
| POST | `/buy` | `event`, `outcome`, `quantity` | `PurchaseResult` |
| POST | `/order` | `event`, `outcome`, `side`, `quantity`, `price` | `OrderResult` |
| GET | `/chat` | `version` | `ChatLines` after the first `version` |
| POST | `/chat` | `text` | `{}` |

## The two trading methods

**LMSR.** For answers holding quantities `q` and liquidity `b`:

```
price(i) = e^(q_i/b) / Σ_j e^(q_j/b)
C(q)     = b · ln( Σ_j e^(q_j/b) )
```

A purchase costs `C` after minus `C` before. Opening the event costs its market maker
`C(0,…,0)` = `b·ln n` for `n` answers, which sits in the event's account until it resolves. Both
formulas are evaluated with a log-sum-exp shift, since a plain `Math.exp(q/b)` overflows a `double`
once `q/b` passes about 709. At `b = 100`, buying 100 shares costs 62.01 and moves that answer from
0.50 to 0.73.

**Order book.** Each answer has its own book of resting bids and asks, ranked by price and then
by arrival. Shares only ever come into existence in complete sets, one of each answer, against `d`
paid into the event's account: once when the market maker buys the initial stock, and again
whenever buyers on every answer between them offer at least `d`. On that mint the orders already
resting fill at their own prices and the arriving one pays the rest of `d`, which is never worse
than the limit it named. Everything else is resale, which moves shares and cash between users and
leaves the account alone.

## Tests

```
./test.sh                     # 103 engine checks
./servertest.sh               # 20 checks over HTTP against a running Tomcat
```

The LMSR figures were worked out from the exercise's own appendix, and the order book figures come
from replaying the supplied `clob_simulation.html` through its own ledger rather than from this
code. The HTTP suite logs two clients in through the client's own `Server` class and has them
upload, trade, close and chat against each other, including four clients buying at the same time.

## Bonus

Bonus 1, chat, is implemented: a Chat tab every logged in user shares, kept on the server and
pulled by each client a few lines at a time, as in the course's chat example.

## Third party

JAXB RI 4.0.5 in `lib/`, since JAXB left the JDK at Java 11. The classes in
`engine/.../xml/generated/` are produced by `xjc` from the supplied schema and are not hand
written; `tools/jaxb-xjc.jar` regenerates them.

Gson 2.11.0 in `lib/gson/`, on both sides of the wire. OkHttp 4.9.1, Okio and the Kotlin standard
library in `lib/http/`, for the client, as in the course examples. `lib/servlet/servlet-api.jar`
is compiled against only; Tomcat provides it at run time.

OpenJFX 22.0.2 in `lib/javafx-win/`, trimmed to `javafx.base`, `javafx.graphics` and
`javafx.controls` and their native libraries. It is vendored so that a fresh clone can build the
submission without downloading anything.
