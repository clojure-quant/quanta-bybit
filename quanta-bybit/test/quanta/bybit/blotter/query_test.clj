(ns quanta.bybit.blotter.query-test
  (:require
   [clojure.test :refer [deftest is]]
   [quanta.bybit.blotter.query :as q]))

(deftest derivative-position->item-test
  (is (= {:type :broker/positions-item
          :account/id 2000
          :req-id "pos-req-1"
          :asset "BTCUSDT.LF.BB"
          :position [{:long-qty 0.01M :short-qty 0M}]
          :position-id "BTCUSDT-0"
          :settl-price 16800M}
         (q/derivative-position->item
          2000 "pos-req-1" :linear
          {:symbol "BTCUSDT"
           :side "Buy"
           :size "0.01"
           :avgPrice "16800"
           :positionIdx 0}))))

(deftest derivative-position-sell-test
  (is (= [{:long-qty 0M :short-qty 0.5M}]
         (:position (q/derivative-position->item
                     2000 "req" :inverse
                     {:symbol "BTCUSD"
                      :side "Sell"
                      :size "0.5"
                      :avgPrice "60000"
                      :positionIdx 0})))))

(deftest derivative-position-skips-empty-test
  (is (nil? (q/derivative-position->item 2000 "req" :linear
                                           {:symbol "BTCUSDT"
                                            :side "None"
                                            :size "0"}))))

(deftest wallet-coin->item-test
  (is (= {:type :broker/positions-item
          :account/id 2000
          :req-id "pos-req-1"
          :asset "BTCUSDT.S.BB"
          :position [{:long-qty 0.05M :short-qty 0M}]
          :position-id "BTC"
          :settl-price nil}
         (q/wallet-coin->item 2000 "pos-req-1" {:coin "BTC" :walletBalance "0.05"}))))

(deftest wallet-coin-skips-quote-test
  (is (nil? (q/wallet-coin->item 2000 "req" {:coin "USDT" :walletBalance "100"}))))

(deftest open-order->status-test
  (is (= {:type :broker/order-status
          :account/id 2000
          :order-id "bybit-demo-4"
          :asset "BTCUSDT.S.BB"
          :side :buy
          :qty 0.001M
          :order-type :limit
          :limit 59647M
          :date (java.time.Instant/ofEpochMilli 1749722400000)
          :time-in-force :good-till-cancel
          :leaves-qty 0.001M
          :cum-qty 0M
          :broker-order-id "123456"}
         (q/open-order->status
          2000 :spot
          {:orderLinkId "bybit-demo-4"
           :orderId "123456"
           :symbol "BTCUSDT"
           :side "Buy"
           :qty "0.001"
           :price "59647"
           :orderType "Limit"
           :timeInForce "GTC"
           :leavesQty "0.001"
           :cumExecQty "0"
           :orderStatus "New"
           :updatedTime "1749722400000"}))))

(deftest open-order->status-testnet-test
  (is (= "BTCUSDT.LF.BBT"
         (:asset (q/open-order->status
                  2000 :linear :test
                  {:orderLinkId "bybit-demo-4"
                   :orderId "123456"
                   :symbol "BTCUSDT"
                   :side "Buy"
                   :qty "0.1"
                   :price "50000"
                   :orderType "Limit"
                   :timeInForce "GTC"
                   :leavesQty "0.1"
                   :cumExecQty "0"
                   :orderStatus "New"
                   :updatedTime "1749722400000"})))))

(deftest open-order-skips-filled-test
  (is (nil? (q/open-order->status
             2000 :spot
             {:orderLinkId "x"
              :orderId "1"
              :symbol "BTCUSDT"
              :side "Buy"
              :qty "1"
              :orderType "Market"
              :orderStatus "Filled"}))))
