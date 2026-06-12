(ns quanta.bybit.demo.util.orderflow-simulated
  (:require [quanta.bybit.demo.util.time-flow :refer [create-time-flow]]))

(def demo-order-action-flow
  (create-time-flow
   [5 {:type :trader/new-order
       :account/id 2000
       :order-id "bybit-demo-2"
       :asset "BTCUSDT.S.BB"
       :side :buy
       :order-type :limit
       :limit 59647M
       :qty 0.001M}
    15 {:type :trader/new-order
       :account/id 2000
       :order-id "bybit-demo-3"
       :asset "BTCUSDT.S.BB"
       :side :sell
       :order-type :market
       :qty 0.001M}
    30 {:type :trader/cancel-order
        :account/id 2000
        :order-id "bybit-demo-2"
        :asset "BTCUSDT.S.BB"}]))
