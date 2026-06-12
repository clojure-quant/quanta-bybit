# quanta-bybit

Bybit WebSocket adapter for [quanta-blotter](https://github.com/clojure-quant/quanta-blotter).


## Layout

- `quanta-bybit/` — library (`io.github.clojure-quant/quanta-bybit`)
- `demo/` — quote print demo

## Asset Mapping
Asset IDs: `BTCUSDT.S.BB` (spot), `BTCUSDT.LF.BB` (linear), etc.


## create a testnet account

https://testnet.bybit.com/


## quotes test

```bash
cd quanta-bybit && clojure -X:test
cd demo && clojure -X:quote-print
cd demo && clojure -X:quote-spot-future :mode :test  # BTCUSDT spot vs linear spread
cd demo && clojure -X:quote-spot-future :mode :main
```

## trade demo

Orders are sent fire-and-forget on the trade websocket with a `reqId`; immediate
accept/reject replies are handled there. Fills and order lifecycle updates also
arrive async on the private order stream, matched by client order id (`orderLinkId`).

Configure API keys in `demo/bybit-accounts-trade-test.edn` (or `-main.edn`), then:

```bash
cd demo && clojure -X:trade-blotter
cd demo && clojure -X:trade-blotter :mode :main
```

