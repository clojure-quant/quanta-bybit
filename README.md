# quanta-bybit

Bybit WebSocket adapter for [quanta-blotter](https://github.com/clojure-quant/quanta-blotter).


## Layout

- `quanta-bybit/` — library (`io.github.clojure-quant/quanta-bybit`)
- `demo/` — quote print demo

## Asset Mapping
Asset IDs: `BTCUSDT.S.BB` (spot), `BTCUSDT.LF.BB` (linear), etc.

## Bybit Specific
- quote endpoints are split into 4 segments (:spot :linear :inverse :option)
- trade order/cancel routing uses RPC over websocket (req-ids)


## create a testnet account

- https://testnet.bybit.com/
- register
- create api creds (read/write)
- got to assets/dashboard - request demo funds.

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

## REST print demo

Calls `get-open-orders`, `get-positions`, and `get-wallet-balance` and prints
the raw Bybit JSON responses (`retCode`, `retMsg`, `result`, …).

```bash
cd demo && clojure -X:rest-print
cd demo && clojure -X:rest-print :mode :main
```


## future

- more market data
  - bars
  - last-trade
  - liquidations 
  - stats
  - orderbook (full orderbook)