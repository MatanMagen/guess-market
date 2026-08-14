# guess-market

Prediction market system built for the java software development course at MTA. Users trade
shares on binary events — will it happen or not — and the price of a share doubles as the
market's estimate of the odds.

Rolling project across four exercises. Exercise 1 is done; the engine built here is meant to
carry the rest rather than be rewritten.

| Exercise | Front end | Status |
|---|---|---|
| 1 | Console | done |
| 2 | JavaFX, adds Order Book trading and multiple users | not started |
| 3 | Client / server | not started |
| 4 | Web client (bonus) | not started |

## Build and run

Needs **Java 25**.

```
./build.sh          # compiles the three modules into dist/
./test.sh           # runs the engine test suite
```

Then from `dist/`, either:

```
java -cp "guess-market-ui.jar:guess-market-engine.jar:guess-market-dto.jar:lib/*" guessmarket.ui.ConsoleApp
java -jar guess-market-ui.jar     # the manifest names its own class path
```

On Windows, `run.bat` in the same folder does the same thing.

## Modules

Three modules, three jars, dependencies running one way only:

```
ui ──> engine ──> dto
 └──────────────> dto
```

- **`dto/`** — immutable records, no logic. The shared vocabulary. Depends on nothing, which is
  what lets both sides speak it without either depending on the other.
- **`engine/`** — the domain model, LMSR pricing, XML loading and validation, the money. Passive:
  it does not know who is calling it and never prints. Answers with `dto` records only, and
  reports faults as unchecked exceptions carrying the facts rather than a finished sentence.
- **`ui/`** — the console. Holds `main`, the menu loop, the only `Scanner` and the only
  `System.out` in the project. Reaches the engine through the `Engine` interface; the concrete
  class is named once, in `ConsoleApp.main`.

## LMSR

For answers holding quantities `q` and liquidity `b`:

```
price(i) = e^(q_i/b) / Σ_j e^(q_j/b)
C(q)     = b · ln( Σ_j e^(q_j/b) )
```

A purchase costs `C` after minus `C` before. An event opens holding `C(0,0)` = `b·ln 2`, the
subsidy the market maker puts up. Both formulas are evaluated with a log-sum-exp shift, since a
plain `Math.exp(q/b)` overflows a `double` once `q/b` passes about 709 — reachable on a
low-liquidity event just by buying a lot of shares.

At `b = 100`, buying 100 shares costs 62.01 and moves that answer from 0.50 to 0.73.

## Tests

```
./test.sh
```

77 checks. Expected figures were worked out from the exercise's own appendix and simulator rather
than from this code, so a failure means the implementation disagrees with the exercise. Faults are
checked by exception type and problem kind, never by the wording of a message — the engine
produces no wording.

## Third party

JAXB RI 4.0.5 in `lib/`, since JAXB left the JDK at Java 11. The classes in
`engine/.../xml/generated/` are produced by `xjc` from the supplied schema and are not hand
written; `tools/jaxb-xjc.jar` regenerates them.
