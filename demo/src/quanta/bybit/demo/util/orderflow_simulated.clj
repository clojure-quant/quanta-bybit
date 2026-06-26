(ns quanta.bybit.demo.util.orderflow-simulated
  (:require
   [nano-id.core :refer [nano-id]]
   [quanta.missionary.time-flow :refer [create-time-flow]]))

(def order-id (nano-id 8))

(def demo-order-action-flow
  (create-time-flow
   [5000 {:type :trader/new-order
          :account/id 2000
          :order-id order-id
          :asset "BTCUSDT.S.BB"
          :side :buy
          :order-type :limit
          :limit 58900.0M ; 59143.2M
          :qty 0.001M}
    8000 {:type :trader/modify-order
          :account/id 2000
          :order-id order-id
          :asset "BTCUSDT.S.BB"
          :limit 58901.0M}
    8000 {:type :trader/cancel-order
          :account/id 2000
          :order-id order-id
          :asset "BTCUSDT.S.BB"}
    #_1500 #_{:type :trader/new-order
              :account/id 2000
              :order-id "bybit-demo-5"
              :asset "BTCUSDT.S.BB"
              :side :sell
              :order-type :market
              :qty 0.001M}]))
