# guess-market

Prediction market system built for the java software development course at MTA. Users trade
shares on binary events — will it happen or not — and the price of a share doubles as the
market's estimate of the odds.

Rolling project across four exercises. The engine is extended each time rather than rewritten.

| Exercise | Front end | Status |
|---|---|---|
| 1 | Console | done |
| 2 | JavaFX, adds Order Book trading and multiple users | done |
| 3 | Client / server | not started |
| 4 | Web client (bonus) | not started |

## Build and run

Needs **Java 25**.

```
./build.sh          # compiles the three modules into dist/
./test.sh           # runs the engine test suite
```

Then from `dist/`, `run.bat` on Windows. On macOS or Linux use `run.sh`, which needs a JavaFX
SDK for that machine — `dist/lib/javafx` holds the Windows build, since that is what the
submission ships:

```
GM_JAVAFX=/path/to/javafx-sdk-22.0.2/lib ./run.sh
```

JavaFX arrives on the module path, not the class path, so plain `java -jar` is not enough.
`--enable-native-access=javafx.graphics` is passed as well: without it Java 25 prints four
warnings about restricted methods before the window appears.

## Modules

Three modules, three jars, dependencies running one way only:

```
ui ──> engine ──> dto
 └──────────────> dto
```

- **`dto/`** — immutable records, no logic. The shared vocabulary. Depends on nothing, which is
  what lets both sides speak it without either depending on the other.
- **`engine/`** — the domain model, LMSR pricing, the order book and its matching, XML loading
  and validation, the money. Passive: it does not know who is calling it and never prints.
  Answers with `dto` records only, and reports faults as unchecked exceptions carrying the facts
  rather than a finished sentence.
- **`ui/`** — the JavaFX application. Holds `main` and every control on screen. Reaches the
  engine through the `Engine` interface; the concrete class is named once, in
  `GuessMarketApp.start`.

## The two trading methods

**LMSR.** For answers holding quantities `q` and liquidity `b`:

```
price(i) = e^(q_i/b) / Σ_j e^(q_j/b)
C(q)     = b · ln( Σ_j e^(q_j/b) )
```

A purchase costs `C` after minus `C` before. Opening the event costs its market maker
`C(0,0)` = `b·ln 2`, which sits in the event's account until it resolves. Both formulas are
evaluated with a log-sum-exp shift, since a plain `Math.exp(q/b)` overflows a `double` once
`q/b` passes about 709 — reachable on a low-liquidity event just by buying a lot of shares.
At `b = 100`, buying 100 shares costs 62.01 and moves that answer from 0.50 to 0.73.

**Order book.** Each answer has its own book of resting bids and asks, ranked by price and then
by arrival. Shares only ever come into existence in pairs, one of each answer, against `d` paid
into the event's account: once when the market maker buys the initial stock, and again whenever
two buyers on opposite answers between them offer at least `d`. On that mint the order already
resting fills at its own price and the arriving one pays the rest of `d`, which is never worse
than the limit it named. Everything else is resale, which moves shares and cash between users
and leaves the account alone.

Because every pair ever minted put `d` in and pays `d` out, an order book event's account
empties to nothing at resolution. An LMSR event's does not, and the leftover subsidy goes back
to the market maker.

## Tests

```
./test.sh
```

87 checks. The LMSR figures were worked out from the exercise's own appendix, and the order book
figures come from replaying the supplied `clob_simulation.html` through its own ledger rather
than from this code, so a failure means the implementation disagrees with the exercise. The
whole simulator scenario is replayed in both commission modes and every final balance is matched
to within 1e-9. Faults are checked by exception type and problem kind, never by the wording of a
message — the engine produces no wording.

## Third party

JAXB RI 4.0.5 in `lib/`, since JAXB left the JDK at Java 11. The classes in
`engine/.../xml/generated/` are produced by `xjc` from the supplied schema and are not hand
written; `tools/jaxb-xjc.jar` regenerates them.

OpenJFX 22.0.2 in `lib/javafx-win/`, trimmed to `javafx.base`, `javafx.graphics` and
`javafx.controls` and their native libraries. It is vendored so that a fresh clone can build the
submission without downloading anything.
