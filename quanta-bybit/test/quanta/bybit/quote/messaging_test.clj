(ns quanta.bybit.quote.messaging-test
  (:require [clojure.test :refer [deftest is]]
            [quanta.quote.protocol :as p]
            [quanta.asset.mapper :as am]
            [quanta.bybit.quote.messaging]))

(def account-config
  {:account/api :bybit-quote
   :account/settings {:connection {:category :spot}}})

(def asset-converter
  (reify am/asset-mapper
    (to-api [_ asset]
      (case asset
        "BTCUSDT.S.BB" "BTCUSDT"
        "ETHUSDT.S.BB" "ETHUSDT"
        asset))
    (from-api [_ symbol]
      (str symbol ".S.BB"))))

(defn- capture-log []
  (let [calls (atom [])]
    [(fn [entry] (swap! calls conj entry)) calls]))

(defn- messaging [log]
  (p/create-quote-messaging account-config asset-converter log))

(deftest subscribe-msg-test
  (let [[log-fn log-calls] (capture-log)
        msg (p/subscribe-msg (messaging log-fn) ["BTCUSDT.S.BB" "ETHUSDT.S.BB"])]
    (is (= "subscribe" (:op msg)))
    (is (= ["orderbook.1.BTCUSDT" "orderbook.1.ETHUSDT"] (:args msg)))
    (is (= [{:type :subscribe
             :assets ["BTCUSDT.S.BB" "ETHUSDT.S.BB"]
             :broker-topics ["orderbook.1.BTCUSDT" "orderbook.1.ETHUSDT"]}]
           @log-calls))))

(deftest subscribe-category-mismatch-test
  (let [[log-fn log-calls] (capture-log)
        msg (p/subscribe-msg (messaging log-fn) ["BTCUSDT.LF.BB"])]
    (is (= "subscribe" (:op msg)))
    (is (empty? (:args msg)))
    (is (= 1 (count (filter #(= :subscribe-category-mismatch (:type %)) @log-calls))))))

(deftest read-quote-snapshot-test
  (let [msg {:topic "orderbook.1.BTCUSDT"
             :type "snapshot"
             :data {:s "BTCUSDT"
                    :b [["50000.0" "1.5"]]
                    :a [["50001.0" "2.0"]]}}
        quote (p/read-quote (messaging (fn [_])) msg)]
    (is (= {:bid 50000.0M
            :ask 50001.0M
            :asset "BTCUSDT.S.BB"
            :price 50000.5M
            :volume 1.0M
            :spread 1.0M}
           quote))))
