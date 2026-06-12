# quanta-bybit

Bybit WebSocket adapter for [quanta-blotter](https://github.com/clojure-quant/quanta-blotter).


## Layout

- `quanta-bybit/` — library (`io.github.clojure-quant/quanta-bybit`)
- `demo/` — quote print demo

## Asset Mapping
Asset IDs: `BTCUSDT.S.BB` (spot), `BTCUSDT.LF.BB` (linear), etc.



## quotes test

```bash
cd quanta-bybit && clojure -X:test
cd demo && clojure -X:quote-print
cd demo && clojure -X:quote-spot-future :mode :test  # BTCUSDT spot vs linear spread
cd demo && clojure -X:quote-spot-future :mode :main
```

