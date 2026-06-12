# quanta-bybit

Bybit WebSocket adapter for [quanta-blotter](https://github.com/clojure-quant/quanta-blotter).

## Stage 1 (quotes)

```bash
cd quanta-bybit && clojure -X:test
cd demo && clojure -X:quote-print
```

Asset IDs: `BTCUSDT.S.BB` (spot), `BTCUSDT.LF.BB` (linear), etc.

## Layout

- `quanta-bybit/` — library (`io.github.clojure-quant/quanta-bybit`)
- `demo/` — quote print demo
