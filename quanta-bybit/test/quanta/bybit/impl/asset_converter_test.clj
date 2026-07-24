(ns quanta.bybit.impl.asset-converter-test
  (:require [clojure.test :refer [deftest is]]
            [quanta.asset.mapper :as p]
            [quanta.bybit.impl.asset-converter :as ac]))

(def spot-account
  {:account/settings {:connection {:mode :main :category :spot}}})

(def linear-account
  {:account/settings {:connection {:mode :main :category :linear}}})

(def spot-test-account
  {:account/settings {:connection {:mode :test :category :spot}}})

(def linear-test-account
  {:account/settings {:connection {:mode :test :category :linear}}})

(deftest parse-asset-id-test
  (is (= {:symbol "BTCUSDT" :category :spot :endpoint :main}
         (ac/parse-asset-id "BTCUSDT.S.BB")))
  (is (= {:symbol "BTCUSDT" :category :linear :endpoint :main}
         (ac/parse-asset-id "BTCUSDT.LF.BB")))
  (is (= {:symbol "BTCUSDT" :category :spot :endpoint :test}
         (ac/parse-asset-id "BTCUSDT.S.BBT")))
  (is (= {:symbol "BTCUSDT" :category :linear :endpoint :test}
         (ac/parse-asset-id "BTCUSDT.LF.BBT")))
  (is (= {:symbol "BTC-26DEC25" :category :linear :endpoint :main}
         (ac/parse-asset-id "BTC-26DEC25.LF.BB")))
  (is (= {:symbol "BTCUSD" :category :inverse :endpoint :main}
         (ac/parse-asset-id "BTCUSD.IF.BB")))
  (is (nil? (ac/parse-asset-id "BTCUSDT"))))

(deftest asset-mapper-roundtrip-test
  (let [spot-mapper (p/create-asset-mapper (assoc spot-account :account/session :bybit) (fn [_]))
        linear-mapper (p/create-asset-mapper (assoc linear-account :account/session :bybit) (fn [_]))
        spot-test-mapper (p/create-asset-mapper (assoc spot-test-account :account/session :bybit) (fn [_]))
        linear-test-mapper (p/create-asset-mapper (assoc linear-test-account :account/session :bybit) (fn [_]))]
    (is (= "BTCUSDT" (p/to-api spot-mapper "BTCUSDT.S.BB")))
    (is (= "BTCUSDT" (p/to-api spot-test-mapper "BTCUSDT.S.BBT")))
    (is (= "BTCUSDT.S.BB" (p/from-api spot-mapper "BTCUSDT")))
    (is (= "BTCUSDT.LF.BB" (p/from-api linear-mapper "BTCUSDT")))
    (is (= "BTCUSDT.S.BBT" (p/from-api spot-test-mapper "BTCUSDT")))
    (is (= "BTCUSDT.LF.BBT" (p/from-api linear-test-mapper "BTCUSDT")))))

(deftest category-matches-asset-test
  (is (ac/category-matches-asset? :spot "BTCUSDT.S.BB"))
  (is (ac/category-matches-asset? :spot "BTCUSDT.S.BBT"))
  (is (not (ac/category-matches-asset? :spot "BTCUSDT.LF.BB")))
  (is (not (ac/category-matches-asset? :spot "BTCUSDT.LF.BBT"))))

(deftest from-api-with-category-uses-connection-mode-test
  (let [main-acct {:account/settings {:connection {:mode :main}}}
        test-acct {:account/settings {:connection {:mode :test}}}]
    (is (= "BTCUSDT.LF.BB" (ac/from-api-with-category main-acct "BTCUSDT" :linear)))
    (is (= "BTCUSDT.LF.BBT" (ac/from-api-with-category test-acct "BTCUSDT" :linear)))
    (is (= "BTCUSDT.S.BBT" (ac/from-api-with-category test-acct "BTCUSDT" "spot")))))

(deftest to-api-logs-endpoint-mismatch-test
  (let [events (atom [])
        mapper (p/create-asset-mapper
                (assoc linear-test-account :account/session :bybit)
                (fn [e] (swap! events conj e)))]
    (is (= "BTCUSDT" (p/to-api mapper "BTCUSDT.LF.BB")))
    (is (= :asset-endpoint-mismatch (:type (first @events))))
    (is (= :main (:endpoint (first @events))))
    (is (= :test (:mode (first @events))))))
